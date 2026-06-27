package de.knutwurst.knutcut.svgcore

/**
 * Silhouette device generation, which selects the setup/plot command sequence (see
 * inkscape-silhouette Graphtec.py): [LEGACY] is the pre-Cameo3 path (Portrait 2), [CAMEO3] is the
 * Cameo 3 specifically, and [CAMEO4_LINE] covers Cameo 4/5 and Portrait 3 (tool select, acceleration,
 * the doubled pressure/offset sequence).
 */
enum class SilhouetteFamily { LEGACY, CAMEO3, CAMEO4_LINE }

/** A Silhouette cutter's fixed geometry. [widthMm]/[lengthMm] bound the cuttable area. */
data class SilhouetteDevice(
    val name: String,
    val family: SilhouetteFamily,
    val widthMm: Double,
    val lengthMm: Double,
)

/**
 * Per-cut Silhouette settings. [speed] is clamped per family (1..10 for Cameo3/legacy, 1..30 for
 * Cameo4-line), [pressure] to 1..33, [toolholder] is 1 (right) or 2 (left). [matCode] selects the
 * cutting mat ("0" = no mat). [mediaId] is the legacy media weight (FW), unused on Cameo3+.
 */
data class GpglCutSettings(
    val speed: Int,
    val pressure: Int,
    val toolholder: Int = 1,
    val bladeDiameterMm: Double = 0.9,
    val matCode: String = "0",
    val mediaId: Int = 132,
)

/** Decoded device status from an ENQ query (the byte before ETX). */
enum class GpglStatus { READY, MOVING, UNLOADED, UNKNOWN }

/**
 * GPGL command framing and the ordered command sequences for a cut. Faithfully ported from
 * inkscape-silhouette's Graphtec.py for the common path: no registration marks, no autoblade, the
 * knife in the right toolholder. Each builder returns logical commands; [delimit] adds the ETX
 * terminators and [GpglSession] handles the bare escape sequences ([INIT], [STATUS_QUERY]).
 */
object GpglProtocol {
    const val ETX = '\u0003'
    private const val ESC = '\u001b'
    private const val EOT = '\u0004'
    private const val ENQ = '\u0005'

    /** ESC EOT: initialize the plotter (sent bare, without an ETX). */
    val INIT = "$ESC$EOT"

    /** ESC ENQ: ask for device status (sent bare, without an ETX). */
    val STATUS_QUERY = "$ESC$ENQ"

    /** Decode an ENQ status token (trailing ETX already stripped by [EtxFramer]); padding is trimmed. */
    fun decodeStatus(token: String?): GpglStatus = when (token?.trim()) {
        "0" -> GpglStatus.READY
        "1" -> GpglStatus.MOVING
        "2" -> GpglStatus.UNLOADED
        else -> GpglStatus.UNKNOWN
    }

    /** Join commands into the ETX-terminated byte stream the device parses. */
    fun delimit(commands: List<String>): String = buildString {
        for (c in commands) { append(c); append(ETX) }
    }

    fun move(yMm: Double, xMm: Double) = "M${mmToSu(yMm)},${mmToSu(xMm)}"
    fun draw(yMm: Double, xMm: Double) = "D${mmToSu(yMm)},${mmToSu(xMm)}"

    private fun select(th: Int) = "J$th"
    private fun pressure(p: Int, th: Int) = "FX$p,$th"
    private fun speed(s: Int, th: Int) = "!$s,$th"
    private fun cutterOffset(xMm: Double, yMm: Double, th: Int) = "FC${mmToSu(xMm)},${mmToSu(yMm)},$th"
    private fun lift(on: Boolean, th: Int) = if (on) "FE1,$th" else "FE0,$th"
    private fun sharpenCorners(start: Int, end: Int, th: Int) = listOf("FF$start,0,$th", "FF$start,$end,$th")
    private fun acceleration(a: Int) = "TJ$a"
    private fun upperLeft(yMm: Double, xMm: Double) = "\\${mmToSu(yMm)},${mmToSu(xMm)}"
    private fun lowerRight(yMm: Double, xMm: Double) = "Z${mmToSu(yMm)},${mmToSu(xMm)}"

    private fun clampPressure(p: Int) = p.coerceIn(1, 33)
    private fun clampSpeed(s: Int, family: SilhouetteFamily) =
        s.coerceIn(1, if (family == SilhouetteFamily.CAMEO4_LINE) 30 else 10)

    /** The setup commands (mat, boundary, tool, pressure, speed, blade offset) for [device]. */
    fun setup(device: SilhouetteDevice, settings: GpglCutSettings): List<String> {
        val c = ArrayList<String>()
        val th = settings.toolholder
        val p = clampPressure(settings.pressure)
        val s = clampSpeed(settings.speed, device.family)
        when (device.family) {
            SilhouetteFamily.CAMEO3, SilhouetteFamily.CAMEO4_LINE -> {
                c += "TG${settings.matCode}"
                c += "FN0"; c += "TB50,0"
                c += upperLeft(0.0, 0.0)
                c += lowerRight(device.lengthMm, device.widthMm)
                if (device.family == SilhouetteFamily.CAMEO4_LINE) {
                    c += select(th)
                    c += pressure(p, th)
                    c += acceleration(0)
                    c += speed(s, th)
                    c += cutterOffset(0.0, 0.05, th)
                    c += lift(false, th)
                    c += sharpenCorners(1, 1, th)
                    c += pressure(p, th)
                    c += acceleration(3)
                    c += cutterOffset(settings.bladeDiameterMm, 0.05, th)
                } else {
                    c += speed(s, th)
                    c += pressure(p, th)
                    c += lift(false, th)
                    c += sharpenCorners(1, 1, th)
                    c += cutterOffset(0.0, 0.05, th)
                    c += cutterOffset(settings.bladeDiameterMm, 0.05, th)
                }
            }
            SilhouetteFamily.LEGACY -> {
                c += "FW${settings.mediaId}"
                c += "!$s"
                c += "FX$p"
                c += "FC${mmToSu(settings.bladeDiameterMm)}"
                c += "FY1"
                c += "FN0"; c += "TB50,0"
                c += "FE0,0"
            }
        }
        return c
    }

    /** The plot commands: a boundary block on legacy devices, then the M/D pen path. */
    fun plot(device: SilhouetteDevice, polylines: List<Polyline>): List<String> {
        val c = ArrayList<String>()
        if (device.family == SilhouetteFamily.LEGACY) {
            c += upperLeft(0.0, 0.0)
            c += lowerRight(device.lengthMm, device.widthMm)
            c += "L0"; c += "FE0,0"; c += "FF0,0,0"
        }
        c += GpglEncoder.encode(polylines)
        return c
    }

    /** The trailer: feed the media out below the cut (endposition "below") and park. */
    fun trailer(polylines: List<Polyline>): List<String> {
        val maxYMm = polylines.flatMap { it.points }.maxOfOrNull { it.yMm } ?: 0.0
        return listOf(move(maxYMm, 0.0), "SO0")
    }
}
