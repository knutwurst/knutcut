package de.knutwurst.knutcut.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.knutwurst.knutcut.data.Devices
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
import de.knutwurst.knutcut.svgcore.PlotterSession
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Protocol
import de.knutwurst.knutcut.svgcore.Pt
import de.knutwurst.knutcut.svgcore.Query
import de.knutwurst.knutcut.svgcore.SvgParser
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

    // Loaded design (in mm, at the SVG's own origin).
    var polylines by mutableStateOf<List<Polyline>>(emptyList()); private set
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

    val hasDesign: Boolean get() = polylines.isNotEmpty()

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
            val polys = SvgParser.parse(text)
            if (polys.isEmpty()) { status = "Keine schneidbaren Pfade in der SVG gefunden."; return }
            polylines = polys
            val b = Bounds.of(polys.flatMap { it.points })
            bounds = b
            scaleX = 1.0
            scaleY = 1.0
            rotationDeg = 0.0
            centerMm = Pt(b.widthMm / 2, b.heightMm / 2) // design top-left at the origin (0,0)
            status = "Design geladen."
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

    fun selectTool(t: Tool) { tool = t; settings.toolSp = t.sp }

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

    fun connect(dev: BluetoothDevice) {
        if (connecting) return
        connecting = true
        status = "Verbinde mit ${dev.name}…"
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
                status = "Verbindung fehlgeschlagen: ${e.message}"
            } finally {
                connecting = false
            }
        }
    }

    fun disconnect() {
        link?.close(); link = null; connected = false; device = null; status = "Getrennt."
    }

    /** The design placed on the mat (mm, screen orientation: y grows downward from the top-left). */
    fun placedPolylines(): List<Polyline> {
        if (bounds == null) return emptyList()
        val m = placementMatrix()
        return polylines.map { pl -> Polyline(pl.points.map { m.apply(it) }, pl.closed) }
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

    /** Plotter-space polylines: the placed design with Y flipped, since the plotter is Y-up. */
    private fun plotterPolylines(): List<Polyline> {
        val h = mat.heightMm
        return placedPolylines().map { pl -> Polyline(pl.points.map { Pt(it.xMm, h - it.yMm) }, pl.closed) }
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
                    Log.d(TAG, "SP/FS -> ${session.send(PltCommand("SP${tool.sp};FS$force;"))}")
                }

                if (model.hasPaperKey) {
                    status = "Lege Matte/Material ein und drücke die Laden-Taste am Plotter."
                    if (!pollState(session, Query("queryPulled"))) { finishCut("Kein Material geladen (Zeitüberschreitung)."); return@launch }
                }
                if (model.hasStartKey) {
                    status = "Drücke die Start-Taste am Plotter."
                    if (!pollState(session, Query("queryStartKey"))) { finishCut("Start-Taste nicht gedrückt (Zeitüberschreitung)."); return@launch }
                }

                status = tool.progress
                val commands = HpglEncoder.encode(plotterPolylines())
                val fileMsgs = buildList {
                    add(PltCommand("TB66;"))
                    addAll(Protocol.pathFile(commands))
                    add(PltCommand("TB66;"))
                }
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
