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
            if (!sendOne(msg, ackTimeoutMs, maxAttempts)) {
                return CutResult.Failed(i, "no valid response after $maxAttempts attempts")
            }
            onProgress?.invoke(i + 1, messages.size)
        }
        return CutResult.Done
    }

    private fun sendOne(msg: PlotterMessage, timeoutMs: Long, maxAttempts: Int): Boolean {
        repeat(maxAttempts) {
            link.write(Frame.encode(msg, cseq))
            cseq++
            val resp = link.readLine(timeoutMs)
            if (resp != null && !isCrcError(resp)) return true
            // null (timeout) or crc rejection: try again with the next cseq
        }
        return false
    }

    private fun isCrcError(line: String): Boolean =
        line.contains("\"type\":\"crc\"") || line.contains("\"crc\"") && line.contains("error")
}
