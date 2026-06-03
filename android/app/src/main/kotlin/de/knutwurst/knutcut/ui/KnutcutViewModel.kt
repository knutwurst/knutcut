package de.knutwurst.knutcut.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.knutwurst.knutcut.data.Devices
import de.knutwurst.knutcut.data.Material
import de.knutwurst.knutcut.data.Materials
import de.knutwurst.knutcut.data.PlotterModel
import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.HpglEncoder
import de.knutwurst.knutcut.svgcore.Matrix
import de.knutwurst.knutcut.svgcore.PlotterSession
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Protocol
import de.knutwurst.knutcut.svgcore.Pt
import de.knutwurst.knutcut.svgcore.SvgParser
import de.knutwurst.knutcut.transport.BluetoothPlotter
import de.knutwurst.knutcut.transport.SppPlotterLink
import de.knutwurst.knutcut.svgcore.Query
import de.knutwurst.knutcut.svgcore.Handshake
import de.knutwurst.knutcut.svgcore.Bye
import de.knutwurst.knutcut.svgcore.PltCommand
import de.knutwurst.knutcut.svgcore.responseStateReady
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KnutcutViewModel(app: Application) : AndroidViewModel(app) {

    // Loaded design (in mm, at the SVG's own origin).
    var polylines by mutableStateOf<List<Polyline>>(emptyList()); private set
    var bounds by mutableStateOf<Bounds?>(null); private set

    // Placement on the mat.
    var model by mutableStateOf<PlotterModel>(Devices.default)
    var scale by mutableStateOf(1.0)
    var rotationDeg by mutableStateOf(0.0)
    var centerMm by mutableStateOf(Pt(model.matWidthMm / 2, model.matHeightMm / 2))

    // Material / cut settings.
    var material by mutableStateOf<Material>(Materials.default); private set
    var speed by mutableStateOf(Materials.default.speed)
    var force by mutableStateOf(Materials.default.force)

    // Connection.
    var device by mutableStateOf<BluetoothDevice?>(null); private set
    var connecting by mutableStateOf(false); private set
    var connected by mutableStateOf(false); private set
    private var link: SppPlotterLink? = null

    // Cut.
    var cutting by mutableStateOf(false); private set
    var progress by mutableStateOf(0f); private set

    var status by mutableStateOf<String?>(null)

    val hasDesign: Boolean get() = polylines.isNotEmpty()

    fun loadSvg(text: String) {
        try {
            val polys = SvgParser.parse(text)
            if (polys.isEmpty()) { status = "Keine schneidbaren Pfade in der SVG gefunden."; return }
            polylines = polys
            val b = Bounds.of(polys.flatMap { it.points })
            bounds = b
            scale = 1.0
            rotationDeg = 0.0
            // place the design's top-left at the plotter origin (0,0)
            centerMm = Pt(b.widthMm / 2, b.heightMm / 2)
            status = "Design geladen."
        } catch (e: Exception) {
            status = "SVG konnte nicht gelesen werden: ${e.message}"
        }
    }

    fun selectMaterial(m: Material) {
        material = m
        speed = m.speed
        force = m.force
    }

    fun rotate90() { rotationDeg = (rotationDeg + 90.0) % 360.0 }

    fun resetPlacement() {
        scale = 1.0
        rotationDeg = 0.0
        val b = bounds
        centerMm = if (b != null) Pt(b.widthMm / 2, b.heightMm / 2)
        else Pt(model.matWidthMm / 2, model.matHeightMm / 2)
    }

    /** Size of the placed design in millimetres (bounding box after scale). */
    fun designSizeMm(): Pair<Double, Double>? {
        val b = bounds ?: return null
        return b.widthMm * scale to b.heightMm * scale
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
        link?.close()
        link = null
        connected = false
        device = null
        status = "Getrennt."
    }

    /** The design transformed onto the mat (mm), ready to encode or preview. */
    fun placedPolylines(): List<Polyline> {
        val b = bounds ?: return emptyList()
        val cx = (b.minX + b.maxX) / 2
        val cy = (b.minY + b.maxY) / 2
        val m = Matrix.translate(centerMm.xMm, centerMm.yMm) *
            Matrix.rotate(rotationDeg) *
            Matrix.scale(scale, scale) *
            Matrix.translate(-cx, -cy)
        return polylines.map { pl -> Polyline(pl.points.map { m.apply(it) }, pl.closed) }
    }

    private var cutJob: Job? = null

    /**
     * The cut follows the machine's own workflow so it never cuts into the air: set up, then wait for
     * the media to be loaded (Laden-Taste), then wait for the start key, and only then send the path.
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
                if (withContext(Dispatchers.IO) { session.send(Handshake) } == null) {
                    finishCut("Keine Antwort vom Plotter."); return@launch
                }
                withContext(Dispatchers.IO) {
                    session.send(PltCommand("TB66;"))
                    session.send(PltCommand("setmat:${material.id};"))
                    session.send(PltCommand("SP$speed;FS$force;"))
                }

                status = "Lege Matte/Material ein und drücke die Laden-Taste am Plotter."
                if (!pollState(session, Query("queryPulled"))) { finishCut("Kein Material geladen (Zeitüberschreitung)."); return@launch }

                status = "Drücke die Start-Taste am Plotter."
                if (!pollState(session, Query("queryStartKey"))) { finishCut("Start-Taste nicht gedrückt (Zeitüberschreitung)."); return@launch }

                status = "Schneide…"
                val commands = HpglEncoder.encode(placedPolylines())
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
                finishCut("Schnitt fertig gesendet.")
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

    /** Poll a query until the device reports a ready state, or give up after [attempts] tries. */
    private suspend fun pollState(session: PlotterSession, query: Query, attempts: Int = 150, intervalMs: Long = 800): Boolean {
        repeat(attempts) {
            val resp = withContext(Dispatchers.IO) { session.send(query) }
            if (responseStateReady(resp)) return true
            delay(intervalMs)
        }
        return false
    }

    fun cancelCut() {
        cutJob?.cancel()
    }

    override fun onCleared() {
        link?.close()
    }
}
