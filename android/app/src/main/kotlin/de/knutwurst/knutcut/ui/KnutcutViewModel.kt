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
import de.knutwurst.knutcut.svgcore.DragKnife
import de.knutwurst.knutcut.svgcore.Handshake
import de.knutwurst.knutcut.svgcore.HpglEncoder
import de.knutwurst.knutcut.svgcore.Matrix
import de.knutwurst.knutcut.svgcore.mmToUnits
import de.knutwurst.knutcut.svgcore.PltCommand
import de.knutwurst.knutcut.svgcore.PlotterMessage
import de.knutwurst.knutcut.svgcore.PlotterSession
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Protocol
import de.knutwurst.knutcut.svgcore.Pt
import de.knutwurst.knutcut.svgcore.Query
import de.knutwurst.knutcut.svgcore.SvgParser
import de.knutwurst.knutcut.svgcore.UNITS_PER_MM
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

    // Loaded design as layers (one per SVG shape), each with its own placement.
    var layers by mutableStateOf<List<Layer>>(emptyList()); private set
    var selectedLayer by mutableStateOf(0); private set

    // Work area.
    var model by mutableStateOf<PlotterModel>(Devices.default); private set
    var mat by mutableStateOf<Mat>(Mats.default); private set

    // Placement of the currently selected layer. These delegate to the layers list so the mat editor
    // (which reads and writes them) manipulates one layer at a time.
    var scaleX: Double
        get() = layers.getOrNull(selectedLayer)?.scaleX ?: 1.0
        set(v) = updateSelected { it.copy(scaleX = v) }
    var scaleY: Double
        get() = layers.getOrNull(selectedLayer)?.scaleY ?: 1.0
        set(v) = updateSelected { it.copy(scaleY = v) }
    var rotationDeg: Double
        get() = layers.getOrNull(selectedLayer)?.rotationDeg ?: 0.0
        set(v) = updateSelected { it.copy(rotationDeg = v) }
    var centerMm: Pt
        get() = layers.getOrNull(selectedLayer)?.centerMm ?: Pt(0.0, 0.0)
        set(v) = updateSelected { it.copy(centerMm = v) }

    /** Bounds of the selected layer in its own coordinates — the handles operate on this. */
    val bounds: Bounds? get() = layers.getOrNull(selectedLayer)?.let { layerBounds(it) }

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

    var dragKnifeComp by mutableStateOf(settings.dragKnifeComp); private set

    val hasDesign: Boolean get() = layers.isNotEmpty()

    private fun allPolylines(): List<Polyline> = layers.flatMap { it.polylines }

    fun selectTheme(m: ThemeMode) { settings.themeMode = m; themeMode = m }

    fun changeDragKnifeComp(v: Boolean) { settings.dragKnifeComp = v; dragKnifeComp = v }

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
            layers = placeAtHome(shapes.map { Layer(it.name, it.polylines, tool, visible = true) })
            selectedLayer = 0
            camScale = 1f
            camOffset = Offset.Zero
            status = "Design geladen (${layers.size} Ebenen)."
        } catch (e: Exception) {
            status = "SVG konnte nicht gelesen werden: ${e.message}"
        }
    }

    fun selectLayer(i: Int) { if (i in layers.indices) selectedLayer = i }

    private fun updateSelected(f: (Layer) -> Layer) {
        val i = selectedLayer
        if (i !in layers.indices) return
        layers = layers.mapIndexed { idx, l -> if (idx == i) f(l) else l }
    }

    private fun layerBounds(layer: Layer): Bounds = Bounds.of(layer.polylines.flatMap { it.points })

    /** Place layers at their original relative positions with the whole design's top-left at (0,0). */
    private fun placeAtHome(ls: List<Layer>): List<Layer> {
        val allPts = ls.flatMap { it.polylines }.flatMap { it.points }
        if (allPts.isEmpty()) return ls
        val gb = Bounds.of(allPts)
        return ls.map { l ->
            val b = layerBounds(l)
            val lcx = (b.minX + b.maxX) / 2
            val lcy = (b.minY + b.maxY) / 2
            l.copy(centerMm = Pt(lcx - gb.minX, lcy - gb.minY), scaleX = 1.0, scaleY = 1.0, rotationDeg = 0.0)
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

    /** Break every layer into one layer per contour, so a single-path SVG can be separated. */
    fun splitLayers() {
        var n = 0
        layers = placeAtHome(layers.flatMap { layer ->
            layer.polylines.map { pl -> Layer("Form ${++n}", listOf(pl), layer.tool, layer.visible) }
        })
        selectedLayer = 0
    }

    /** Merge all layers back into one. */
    fun mergeLayers() {
        if (layers.isEmpty()) return
        layers = placeAtHome(listOf(Layer("Alle Formen", allPolylines(), layers.first().tool, visible = true)))
        selectedLayer = 0
    }

    fun changeForce(v: Int) { force = v; settings.force = v }

    fun selectMat(m: Mat) {
        mat = m
        settings.matName = m.name
        if (hasDesign) resetPlacement()
    }

    /** Rotate the selected layer by 90°. */
    fun rotate90() { rotationDeg = (rotationDeg + 90.0) % 360.0 }

    fun resetPlacement() {
        layers = placeAtHome(layers)
        selectedLayer = selectedLayer.coerceIn(0, (layers.size - 1).coerceAtLeast(0))
        camScale = 1f
        camOffset = Offset.Zero
    }

    /** Size of the selected layer in millimetres (bounding box after its scale). */
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
    fun placedLayers(): List<Pair<Tool, List<Polyline>>> =
        layers.filter { it.visible }.map { layer ->
            val m = layerMatrix(layer)
            layer.tool to layer.polylines.map { pl -> Polyline(pl.points.map { m.apply(it) }, pl.closed) }
        }

    /** Placement matrix for one layer: rotate/scale about its own centre, then move to [Layer.centerMm]. */
    private fun layerMatrix(layer: Layer): Matrix {
        val b = layerBounds(layer)
        val cx = (b.minX + b.maxX) / 2
        val cy = (b.minY + b.maxY) / 2
        return Matrix.translate(layer.centerMm.xMm, layer.centerMm.yMm) *
            Matrix.rotate(layer.rotationDeg) *
            Matrix.scale(layer.scaleX, layer.scaleY) *
            Matrix.translate(-cx, -cy)
    }

    /** The four corners of a layer's placed box, in mm (TL, TR, BR, BL; rotated with the layer). */
    fun layerCorners(index: Int): List<Pt> {
        val layer = layers.getOrNull(index) ?: return emptyList()
        val b = layerBounds(layer)
        val m = layerMatrix(layer)
        return listOf(Pt(b.minX, b.minY), Pt(b.maxX, b.minY), Pt(b.maxX, b.maxY), Pt(b.minX, b.maxY))
            .map { m.apply(it) }
    }

    /** Corners of the selected layer's box — the editor draws and manipulates these. */
    fun placedCorners(): List<Pt> = layerCorners(selectedLayer)

    /** The topmost visible layer whose placed box contains [ptMm], or -1 if none. */
    fun layerAt(ptMm: Pt): Int {
        for (i in layers.indices.reversed()) {
            if (!layers[i].visible) continue
            val c = layerCorners(i)
            if (c.size == 4 && pointInQuad(ptMm, c)) return i
        }
        return -1
    }

    private fun pointInQuad(p: Pt, q: List<Pt>): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val a = q[i]; val b = q[(i + 1) % 4]
            val cross = (b.xMm - a.xMm) * (p.yMm - a.yMm) - (b.yMm - a.yMm) * (p.xMm - a.xMm)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s != 0) { if (sign == 0) sign = s else if (sign != s) return false }
        }
        return true
    }

    /** Plotter-space polylines for one tool's visible layers (placed + Y-flipped, since the plotter is Y-up). */
    private fun plotterPolylinesFor(t: Tool): List<Polyline> {
        val h = mat.heightMm
        return layers.filter { it.visible && it.tool == t }.flatMap { layer ->
            val m = layerMatrix(layer)
            layer.polylines.map { pl -> Polyline(pl.points.map { val w = m.apply(it); Pt(w.xMm, h - w.yMm) }, pl.closed) }
        }
    }

    private var cutJob: Job? = null

    /**
     * Mirrors the stock app's workflow so the plotter never cuts into the air. After the handshake it
     * sets the material, then the work-area scale (JS, the step the machine needs before it will load
     * or accept a path), waits for the media to be fed in (Laden-Taste) and the start key on machines
     * that have them, and only then streams the path.
     */
    fun cut() {
        val l = link
        if (!connected || l == null) { status = "Bitte zuerst den Plotter verbinden."; return }
        if (!hasDesign) { status = "Kein Design geladen."; return }
        if (cutting) return
        cutting = true
        progress = 0f
        cutJob = viewModelScope.launch { runCut(l) }
    }

    private suspend fun runCut(l: SppPlotterLink) {
        val session = PlotterSession(l)
        try {
            status = "Verbinde mit dem Plotter…"
            val hs = withContext(Dispatchers.IO) { session.send(Handshake) }
            Log.d(TAG, "handshake -> $hs")
            if (hs == null) { finishCut("Keine Antwort vom Plotter."); return }

            val toolsUsed = layers.filter { it.visible }.map { it.tool }.distinct()
            if (toolsUsed.isEmpty()) { finishCut("Keine sichtbaren Ebenen."); return }
            val primaryTool = if (Tool.KNIFE in toolsUsed) Tool.KNIFE else toolsUsed.first()

            // Setup, in the stock app's order: material, pressure (SP/FS), then the work-area scale.
            // Width is always 12" (12192 units); the second value is the mat length. The machine needs
            // this JS scale before it will feed material or accept a path (stock setPosition page).
            val widthUnits = (mat.widthMm * UNITS_PER_MM).toInt()
            val lengthUnits = (mat.heightMm * UNITS_PER_MM).toInt()
            withContext(Dispatchers.IO) {
                Log.d(TAG, "setmat -> ${session.send(PltCommand("setmat:${material.id};"))}")
                Log.d(TAG, "setPressure -> ${session.send(PltCommand("SP${primaryTool.sp};FS$force;"))}")
                Log.d(TAG, "setScale -> ${session.send(PltCommand("JS$widthUnits,$lengthUnits;"))}")
            }

            // Load gate (machines with a paper key): wait until the media is fed in (queryMaterial
            // state 3), not just sitting at the sensor (state 1).
            if (model.hasPaperKey) {
                status = "Lege Material ein und drücke die Einzugstaste am Plotter."
                if (!pollMaterialLoaded(session)) { finishCut("Material nicht geladen (Zeitüberschreitung)."); return }
            }
            // Start gate (machines with a start key): wait for the physical Start button.
            if (model.hasStartKey) {
                status = "Bereit – drücke jetzt die Start-Taste am Plotter."
                if (!pollState(session, Query("queryStartKey"))) { finishCut("Start-Taste nicht gedrückt (Zeitüberschreitung)."); return }
            }

            status = if (Tool.KNIFE in toolsUsed) Tool.KNIFE.progress else Tool.PEN.progress
            // Build one continuous command stream (the stock app's "plt"): the path for each visible
            // tool, with a tool switch (SP/FS) inlined before any later tool. Pressure for the first
            // tool was set above.
            val plt = ArrayList<String>()
            toolsUsed.forEachIndexed { idx, t ->
                val polys = plotterPolylinesFor(t)
                var cmds = HpglEncoder.encode(polys)
                if (cmds.isEmpty()) return@forEachIndexed
                val raw = cmds.size
                if (dragKnifeComp) cmds = DragKnife.process(cmds)
                val xs = polys.flatMap { it.points }.map { mmToUnits(it.xMm) }
                val ys = polys.flatMap { it.points }.map { mmToUnits(it.yMm) }
                Log.d(TAG, "tool=${t.sp} force=$force polylines=${polys.size} cmds=$raw->${cmds.size} (comp=$dragKnifeComp) " +
                    "x=[${xs.minOrNull()}..${xs.maxOrNull()}] y=[${ys.minOrNull()}..${ys.maxOrNull()}]")
                if (idx > 0) { plt += "SP${t.sp}"; plt += "FS$force" }
                plt.addAll(cmds)
            }
            if (plt.isEmpty()) { finishCut("Keine sichtbaren Ebenen."); return }

            // Stream as one continuous pltFile sequence (index 0..total-1, 30 commands per chunk),
            // exactly like the stock sendFile — no pltCommands interleaved, no trailing TB66.
            val fileMsgs = Protocol.pathFile(plt, 30)
            Log.d(TAG, "sendFile: ${plt.size} cmds in ${fileMsgs.size} chunks")
            fileMsgs.forEachIndexed { i, m ->
                val resp = withContext(Dispatchers.IO) { session.send(m) }
                Log.d(TAG, "pltFile[$i/${fileMsgs.size}] -> $resp")
                if (resp == null) { finishCut("Übertragung abgebrochen bei Chunk ${i + 1}."); return }
                progress = (i + 1).toFloat() / fileMsgs.size
            }
            finishCut("Fertig an den Plotter gesendet.")
        } catch (e: CancellationException) {
            cutting = false
            status = "Abgebrochen."
            throw e
        } catch (e: Exception) {
            finishCut("Fehler: ${e.message}")
        }
    }

    private fun finishCut(msg: String) {
        cutting = false
        progress = 0f
        status = msg
    }

    /**
     * Smart-series load gate: wait until the material is fed in (queryMaterial state 3), not just
     * sitting at the sensor (state 1, which only means "detected").
     */
    private suspend fun pollMaterialLoaded(session: PlotterSession, attempts: Int = 240, intervalMs: Long = 700): Boolean {
        repeat(attempts) {
            val resp = withContext(Dispatchers.IO) { session.send(Query("queryMaterial")) }
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

    fun cancelCut() {
        cutJob?.cancel()
        cutJob = null
        cutting = false
        progress = 0f
        status = "Abgebrochen."
    }

    override fun onCleared() { link?.close() }

    private companion object { const val TAG = "Knutcut" }
}
