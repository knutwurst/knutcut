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
import de.knutwurst.knutcut.svgcore.CutResult
import de.knutwurst.knutcut.svgcore.CutSettings
import de.knutwurst.knutcut.svgcore.HpglEncoder
import de.knutwurst.knutcut.svgcore.Matrix
import de.knutwurst.knutcut.svgcore.PlotterSession
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Protocol
import de.knutwurst.knutcut.svgcore.Pt
import de.knutwurst.knutcut.svgcore.SvgParser
import de.knutwurst.knutcut.transport.BluetoothPlotter
import de.knutwurst.knutcut.transport.SppPlotterLink
import kotlinx.coroutines.Dispatchers
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
            bounds = Bounds.of(polys.flatMap { it.points })
            scale = 1.0
            rotationDeg = 0.0
            centerMm = Pt(model.matWidthMm / 2, model.matHeightMm / 2)
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
        centerMm = Pt(model.matWidthMm / 2, model.matHeightMm / 2)
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

    fun cut() {
        val l = link
        if (!connected || l == null) { status = "Bitte zuerst den Plotter verbinden."; return }
        if (!hasDesign) { status = "Kein Design geladen."; return }
        if (cutting) return
        cutting = true
        progress = 0f
        status = "Sende an den Plotter…"
        viewModelScope.launch {
            val commands = HpglEncoder.encode(placedPolylines())
            val msgs = Protocol.buildCut(commands, CutSettings(material.id, speed, force))
            val session = PlotterSession(l).apply {
                onProgress = { sent, total -> progress = sent.toFloat() / total }
            }
            val result = withContext(Dispatchers.IO) { session.run(msgs) }
            cutting = false
            status = when (result) {
                is CutResult.Done -> "Schnitt gesendet."
                is CutResult.Failed -> "Abbruch bei Schritt ${result.atMessage}: ${result.reason}"
            }
        }
    }

    override fun onCleared() {
        link?.close()
    }
}
