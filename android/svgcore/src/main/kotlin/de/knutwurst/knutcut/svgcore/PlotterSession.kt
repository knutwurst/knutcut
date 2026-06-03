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

/** Result of running a job, for the UI. */
sealed class CutResult {
    object Done : CutResult()
    data class Failed(val atMessage: Int, val reason: String) : CutResult()
}

/**
 * Drives a cut: frames each [PlotterMessage] with an incrementing [cseq], writes it, waits for the
 * device's response line, and resends on a `crc` rejection or timeout (matching the stock app's
 * send-and-wait loop with up to five attempts).
 */
class PlotterSession(private val link: PlotterLink, private var cseq: Int = 0) {

    var onProgress: ((sent: Int, total: Int) -> Unit)? = null

    fun run(messages: List<PlotterMessage>, ackTimeoutMs: Long = 5000, maxAttempts: Int = 5): CutResult {
        messages.forEachIndexed { i, msg ->
            if (send(msg, ackTimeoutMs, maxAttempts) == null) {
                return CutResult.Failed(i, "no valid response after $maxAttempts attempts")
            }
            onProgress?.invoke(i + 1, messages.size)
        }
        return CutResult.Done
    }

    /** Frame and send one message, retrying on crc/timeout. Returns the device's response line, or null. */
    fun send(msg: PlotterMessage, timeoutMs: Long = 5000, maxAttempts: Int = 5): String? {
        repeat(maxAttempts) {
            link.write(Frame.encode(msg, cseq))
            cseq++
            val resp = link.readLine(timeoutMs)
            if (resp != null && !isCrcError(resp)) return resp
            // null (timeout) or crc rejection: try again with the next cseq
        }
        return null
    }

    private fun isCrcError(line: String): Boolean =
        line.contains("\"type\":\"crc\"") || (line.contains("\"crc\"") && line.contains("error"))
}

/**
 * The numeric `state` from a query response: a number as-is, `true`→1, `false`→0, or null if absent.
 * queryMaterial uses 3 = loaded/fed-in, 1 = at the sensor; queryStartKey uses true/false.
 */
fun responseState(line: String?): Int? {
    if (line == null) return null
    val m = Regex("\"state\"\\s*:\\s*(true|false|-?[0-9]+)").find(line) ?: return null
    return when (m.groupValues[1]) {
        "true" -> 1
        "false" -> 0
        else -> m.groupValues[1].toIntOrNull()
    }
}

/** True if a query response carries a truthy `state` (e.g. the start key has been pressed). */
fun responseStateReady(line: String?): Boolean = (responseState(line) ?: 0) > 0
