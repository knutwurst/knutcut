package de.knutwurst.knutcut.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.knutwurst.knutcut.data.Devices
import de.knutwurst.knutcut.data.Layer
import de.knutwurst.knutcut.data.Mat
import de.knutwurst.knutcut.data.Mats
import de.knutwurst.knutcut.data.Material
import de.knutwurst.knutcut.data.Materials
import de.knutwurst.knutcut.data.PlotterModel
import de.knutwurst.knutcut.data.Settings
import de.knutwurst.knutcut.data.ThemeMode
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.Bye
import de.knutwurst.knutcut.svgcore.Handshake
import de.knutwurst.knutcut.svgcore.HpglEncoder
import de.knutwurst.knutcut.svgcore.Matrix
import de.knutwurst.knutcut.svgcore.PltCommand
import de.knutwurst.knutcut.svgcore.PlotterMessage
import de.knutwurst.knutcut.svgcore.PlotterSession
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Protocol
import de.knutwurst.knutcut.svgcore.Pt
import de.knutwurst.knutcut.svgcore.Query
import de.knutwurst.knutcut.svgcore.SvgParser
import de.knutwurst.knutcut.svgcore.responseState
import de.knutwurst.knutcut.svgcore.responseStateReady
import de.knutwurst.knutcut.transport.BluetoothPlotter
import de.knutwurst.knutcut.transport.SppPlotterLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KnutcutViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)

    // Loaded design as layers (one per SVG shape), in mm at the SVG's own origin.
    var layers by mutableStateOf<List<Layer>>(emptyList()); private set
    var bounds by mutableStateOf<Bounds?>(null); private set

    // Work area + placement.
    var model by mutableStateOf<PlotterModel>(Devices.default); private set
    var mat by mutableStateOf<Mat>(Mats.default); private set
    var scaleX by mutableStateOf(1.0)
    var scaleY by mutableStateOf(1.0)
    var rotationDeg by mutableStateOf(0.0)
    var centerMm by mutableStateOf(Pt(0.0, 0.0))

    // Mat view camera (zoom + pan of the work area).
    var camScale by mutableStateOf(1f)
    var camOffset by mutableStateOf(Offset.Zero)

    // Material / cut settings.
    var material by mutableStateOf<Material>(Materials.default); private set
    var tool by mutableStateOf(Tool.KNIFE); private set
    var force by mutableStateOf(Materials.default.force); private set

    // Connection.
    var device by mutableStateOf<BluetoothDevice?>(null); private set
    var connecting by mutableStateOf(false); private set
    var connected by mutableStateOf(false); private set
    private var link: SppPlotterLink? = null

    // Cut.
    var cutting by mutableStateOf(false); private set
    var progress by mutableStateOf(0f); private set

    var status by mutableStateOf<String?>(null)

    var themeMode by mutableStateOf(settings.themeMode); private set

    val hasDesign: Boolean get() = layers.isNotEmpty()

    private fun allPolylines(): List<Polyline> = layers.flatMap { it.polylines }

    fun selectTheme(m: ThemeMode) { settings.themeMode = m; themeMode = m }

    init {
        settings.materialId?.let { id ->
            Materials.presets.firstOrNull { it.id == id }?.let { material = it; force = it.force }
        }
        Mats.byName(settings.matName)?.let { mat = it }
        tool = Tool.entries.firstOrNull { it.sp == settings.toolSp } ?: Tool.KNIFE
        if (settings.force > 0) force = settings.force
    }

    fun loadSvg(text: String) {
        try {
            val shapes = SvgParser.parseShapes(text)
            val pts = shapes.flatMap { it.polylines }.flatMap { it.points }
            if (pts.isEmpty()) { status = "Keine schneidbaren Pfade in der SVG gefunden."; return }
            layers = shapes.map { Layer(it.name, it.polylines, tool, visible = true) }
            val b = Bounds.of(pts)
            bounds = b
            scaleX = 1.0
            scaleY = 1.0
            rotationDeg = 0.0
            centerMm = Pt(b.widthMm / 2, b.heightMm / 2) // design top-left at the origin (0,0)
            status = "Design geladen (${layers.size} Ebenen)."
        } catch (e: Exception) {
            status = "SVG konnte nicht gelesen werden: ${e.message}"
        }
    }

    fun selectMaterial(m: Material) {
        material = m
        force = m.force
        settings.materialId = m.id
        settings.force = m.force
    }

    /** Set the tool for all layers (the bottom quick-select). */
    fun setAllTool(t: Tool) {
        tool = t
        settings.toolSp = t.sp
        layers = layers.map { it.copy(tool = t) }
    }

    fun setLayerTool(index: Int, t: Tool) {
        layers = layers.mapIndexed { i, l -> if (i == index) l.copy(tool = t) else l }
    }

    fun toggleLayerVisible(index: Int) {
        layers = layers.mapIndexed { i, l -> if (i == index) l.copy(visible = !l.visible) else l }
    }

    fun changeForce(v: Int) { force = v; settings.force = v }

    fun selectMat(m: Mat) {
        mat = m
        settings.matName = m.name
        if (hasDesign) resetPlacement()
    }

    fun rotate90() { rotationDeg = (rotationDeg + 90.0) % 360.0 }

    fun resetPlacement() {
        scaleX = 1.0
        scaleY = 1.0
        rotationDeg = 0.0
        val b = bounds
        centerMm = if (b != null) Pt(b.widthMm / 2, b.heightMm / 2) else Pt(0.0, 0.0)
        camScale = 1f
        camOffset = Offset.Zero
    }

    /** Size of the placed design in millimetres (bounding box after scale). */
    fun designSizeMm(): Pair<Double, Double>? {
        val b = bounds ?: return null
        return b.widthMm * scaleX to b.heightMm * scaleY
    }

    fun connect(dev: BluetoothDevice, silent: Boolean = false) {
        if (connecting || connected) return
        connecting = true
        if (!silent) status = "Verbinde mit ${dev.name}…"
        viewModelScope.launch {
            try {
                val l = withContext(Dispatchers.IO) { BluetoothPlotter.connect(getApplication(), dev) }
                link?.close()
                link = l
                device = dev
                connected = true
                Devices.matchByName(dev.name)?.let { model = it }
                settings.deviceAddress = dev.address
                status = "Verbunden mit ${dev.name}."
            } catch (e: Exception) {
                connected = false
                if (!silent) status = "Verbindung fehlgeschlagen: ${e.message}"
            } finally {
                connecting = false
            }
        }
    }

    /** On launch (or when permission arrives): reconnect to the last device if Bluetooth is on. */
    fun autoConnect() {
        if (connected || connecting) return
        val addr = settings.deviceAddress ?: return
        val ctx = getApplication<Application>()
        if (!hasConnectPermission(ctx) || !BluetoothPlotter.isEnabled(ctx)) return
        val dev = runCatching { BluetoothPlotter.adapter(ctx)?.getRemoteDevice(addr) }.getOrNull() ?: return
        connect(dev, silent = true)
    }

    private fun hasConnectPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    fun disconnect() {
        link?.close(); link = null; connected = false; device = null; status = "Getrennt."
    }

    /** Visible layers placed on the mat (mm, y-down), each paired with its tool — for drawing. */
    fun placedLayers(): List<Pair<Tool, List<Polyline>>> {
        if (bounds == null) return emptyList()
        val m = placementMatrix()
        return layers.filter { it.visible }.map { layer ->
            layer.tool to layer.polylines.map { pl -> Polyline(pl.points.map { m.apply(it) }, pl.closed) }
        }
    }

    private fun placementMatrix(): Matrix {
        val b = bounds ?: return Matrix.IDENTITY
        val cx = (b.minX + b.maxX) / 2
        val cy = (b.minY + b.maxY) / 2
        return Matrix.translate(centerMm.xMm, centerMm.yMm) *
            Matrix.rotate(rotationDeg) *
            Matrix.scale(scaleX, scaleY) *
            Matrix.translate(-cx, -cy)
    }

    /** The four corners of the placed design's box, in mm (TL, TR, BR, BL; rotated with the design). */
    fun placedCorners(): List<Pt> {
        val b = bounds ?: return emptyList()
        val m = placementMatrix()
        return listOf(Pt(b.minX, b.minY), Pt(b.maxX, b.minY), Pt(b.maxX, b.maxY), Pt(b.minX, b.maxY))
            .map { m.apply(it) }
    }

    /** Plotter-space polylines for one tool's visible layers (placed + Y-flipped, since the plotter is Y-up). */
    private fun plotterPolylinesFor(t: Tool): List<Polyline> {
        val m = placementMatrix()
        val h = mat.heightMm
        return layers.filter { it.visible && it.tool == t }
            .flatMap { it.polylines }
            .map { pl -> Polyline(pl.points.map { val w = m.apply(it); Pt(w.xMm, h - w.yMm) }, pl.closed) }
    }

    private var cutJob: Job? = null

    /**
     * Follows the machine's workflow so it never cuts into the air: set up, wait for the media to be
     * loaded (Laden-Taste) and the start key on machines that have them, and only then send the path.
     */
    fun cut() {
        val l = link
        if (!connected || l == null) { status = "Bitte zuerst den Plotter verbinden."; return }
        if (!hasDesign) { status = "Kein Design geladen."; return }
        if (cutting) return
        cutting = true
        progress = 0f
        cutJob = viewModelScope.launch {
            val session = PlotterSession(l)
            try {
                status = "Verbinde mit dem Plotter…"
                val hs = withContext(Dispatchers.IO) { session.send(Handshake) }
                Log.d(TAG, "handshake -> $hs")
                if (hs == null) { finishCut("Keine Antwort vom Plotter."); return@launch }
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "TB66 -> ${session.send(PltCommand("TB66;"))}")
                    Log.d(TAG, "setmat -> ${session.send(PltCommand("setmat:${material.id};"))}")
                }

                // Wait until the material is actually fed in (queryMaterial state 3), not just sitting
                // at the sensor (state 1, which only means "detected").
                if (!pollMaterialLoaded(session)) { finishCut("Material nicht geladen (Zeitüberschreitung)."); return@launch }

                if (model.hasStartKey) {
                    status = "Bereit – drücke jetzt die Start-Taste am Plotter."
                    if (!pollState(session, Query("queryStartKey"))) { finishCut("Start-Taste nicht gedrückt (Zeitüberschreitung)."); return@launch }
                }

                val toolsUsed = layers.filter { it.visible }.map { it.tool }.distinct()
                status = if (Tool.KNIFE in toolsUsed) Tool.KNIFE.progress else Tool.PEN.progress
                // One SP/FS + path group per tool, so pen layers draw and knife layers cut.
                val fileMsgs = ArrayList<PlotterMessage>()
                for (t in toolsUsed) {
                    val cmds = HpglEncoder.encode(plotterPolylinesFor(t))
                    if (cmds.isEmpty()) continue
                    fileMsgs += PltCommand("SP${t.sp};FS$force;")
                    fileMsgs += PltCommand("TB66;")
                    fileMsgs.addAll(Protocol.pathFile(cmds))
                    fileMsgs += PltCommand("TB66;")
                }
                if (fileMsgs.isEmpty()) { finishCut("Keine sichtbaren Ebenen."); return@launch }
                fileMsgs.forEachIndexed { i, m ->
                    if (withContext(Dispatchers.IO) { session.send(m) } == null) {
                        finishCut("Übertragung abgebrochen bei Schritt ${i + 1}."); return@launch
                    }
                    progress = (i + 1).toFloat() / fileMsgs.size
                }
                withContext(Dispatchers.IO) { session.send(Bye) }
                finishCut("Fertig an den Plotter gesendet.")
            } catch (e: CancellationException) {
                cutting = false
                status = "Abgebrochen."
                throw e
            } catch (e: Exception) {
                finishCut("Fehler: ${e.message}")
            }
        }
    }

    private fun finishCut(msg: String) {
        cutting = false
        progress = 0f
        status = msg
    }

    /** Poll queryMaterial until the material is fed in (state 3). While at the sensor (state 1), ask to press feed. */
    private suspend fun pollMaterialLoaded(session: PlotterSession, attempts: Int = 240, intervalMs: Long = 700): Boolean {
        repeat(attempts) {
            val resp = withContext(Dispatchers.IO) { session.send(Query("queryMaterial")) }
            Log.d(TAG, "queryMaterial -> $resp")
            when (responseState(resp)) {
                3 -> return true
                1 -> status = "Material erkannt – drücke die Einzugstaste am Plotter."
                else -> status = "Lege Material ein und drücke die Einzugstaste am Plotter."
            }
            delay(intervalMs)
        }
        return false
    }

    private suspend fun pollState(session: PlotterSession, query: Query, attempts: Int = 150, intervalMs: Long = 800): Boolean {
        repeat(attempts) {
            val resp = withContext(Dispatchers.IO) { session.send(query) }
            Log.d(TAG, "${query.action} -> $resp")
            if (responseStateReady(resp)) return true
            delay(intervalMs)
        }
        return false
    }

    fun cancelCut() { cutJob?.cancel() }

    override fun onCleared() { link?.close() }

    private companion object { const val TAG = "Knutcut" }
}
