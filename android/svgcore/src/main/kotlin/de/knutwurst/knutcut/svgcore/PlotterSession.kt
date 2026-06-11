package de.knutwurst.knutcut.svgcore

/**
 * A line-oriented link to the plotter: write a framed message, read back one response line.
 * The Android SPP socket implements this; tests use a fake.
 */
interface PlotterLink {
    fun write(text: String)
    /** The next response line (without its CRLF), or null on timeout / closed link. */
    fun readLine(timeoutMs: Long): String?
}

/** The physical transport a [ManagedLink] runs over. Decides which protocol stack may use it. */
enum class LinkTransport { SPP, BLE }

/**
 * A [PlotterLink] that is also closeable and reports unexpected disconnections. Both the classic
 * SPP link and the new BLE link implement this so the ViewModel can hold one typed reference.
 *
 * [transport] is authoritative about how the link is wired: the VEVOR JSON+HPGL stack only runs
 * over [LinkTransport.SPP], the Silhouette GPGL stack only over [LinkTransport.BLE]. The caller must
 * verify the selected model's transport matches before streaming, so the two never cross wires.
 */
interface ManagedLink : PlotterLink, AutoCloseable {
    /** The physical transport this link runs over — set once at construction, never guessed. */
    val transport: LinkTransport

    /** Invoked on the reader/callback thread when the remote side drops the connection unexpectedly. */
    var onClosed: (() -> Unit)?
}

/**
 * Drives a cut: frames each [PlotterMessage] with an incrementing [cseq], writes it, waits for the
 * device's response line, and resends on a `crc` rejection, an explicit failure, or a timeout
 * (matching the stock app's send-and-wait loop with up to five attempts).
 */
class PlotterSession(private val link: PlotterLink, private var cseq: Int = 0) {

    /** Frame and send one message, retrying on rejection/timeout. Returns the accepted response, or null. */
    fun send(msg: PlotterMessage, timeoutMs: Long = 5000, maxAttempts: Int = 5): String? {
        repeat(maxAttempts) {
            val sent = cseq
            link.write(Frame.encode(msg, sent))
            cseq++
            // Wait for the response to THIS message. The device echoes the cseq, so a late ack from a
            // previous, timed-out send (carrying an older cseq) is discarded instead of being accepted
            // for the current message — otherwise a stale ack could confirm the next command, pltFile
            // chunks included. Responses without a cseq (devices that omit it) are accepted as before.
            while (true) {
                val resp = link.readLine(timeoutMs) ?: break   // timeout → resend on the next attempt
                val cs = PlotterResponse.parse(resp).cseq
                if (cs != null && cs != sent) continue          // stale / foreign ack → keep waiting
                if (responseOk(resp)) return resp
                break                                           // rejection for our cseq → resend
            }
        }
        return null
    }
}

/**
 * A parsed plotter response line. The device replies with a flat JSON object; the fields we care
 * about are pulled out here so the rest of the app reasons about a structured value instead of
 * scattered string matching. Absent fields are null. (`state`: number as-is, true→1, false→0.)
 */
data class PlotterResponse(
    val type: String?,
    val success: Boolean?,
    val state: Int?,
    val cseq: Int?,
    val raw: String,
) {
    /** A crc rejection or an explicit failure — the send loop should retry. */
    val rejected: Boolean get() = type == "crc" || success == false

    companion object {
        private fun str(line: String, k: String) =
            Regex("\"$k\"\\s*:\\s*\"([^\"]*)\"").find(line)?.groupValues?.get(1)
        private fun int(line: String, k: String) =
            Regex("\"$k\"\\s*:\\s*(-?[0-9]+)").find(line)?.groupValues?.get(1)?.toIntOrNull()

        fun parse(line: String): PlotterResponse {
            val success = Regex("\"success\"\\s*:\\s*(true|false)").find(line)?.groupValues?.get(1)?.toBooleanStrictOrNull()
            val state = Regex("\"state\"\\s*:\\s*(true|false|-?[0-9]+)").find(line)?.groupValues?.get(1)?.let {
                when (it) { "true" -> 1; "false" -> 0; else -> it.toIntOrNull() }
            }
            return PlotterResponse(str(line, "type"), success, state, int(line, "cseq"), line)
        }
    }
}

/** A response counts as accepted when it is present, not a crc rejection, and not `success:false`. */
fun responseOk(line: String?): Boolean {
    if (line == null) return false
    // legacy crc shape too: a bare {"crc": ...} carrying "error"
    if (line.contains("\"crc\"") && line.contains("error")) return false
    return !PlotterResponse.parse(line).rejected
}

/**
 * The numeric `state` from a query response: a number as-is, `true`→1, `false`→0, or null if absent.
 * queryMaterial uses 3 = loaded/fed-in, 1 = at the sensor; queryStartKey uses true/false.
 */
fun responseState(line: String?): Int? = line?.let { PlotterResponse.parse(it).state }

/** True if a query response carries a truthy `state` (e.g. the start key has been pressed). */
fun responseStateReady(line: String?): Boolean = (responseState(line) ?: 0) > 0
