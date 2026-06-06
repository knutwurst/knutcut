package de.knutwurst.knutcut.svgcore

/**
 * Drives a Silhouette cut over a [PlotterLink] whose [PlotterLink.readLine] yields ETX-terminated
 * tokens (the BLE link frames with [EtxFramer]). Unlike the VEVOR [PlotterSession], GPGL has no
 * per-command ack: the device is paced by polling its status (ESC ENQ) until it reports ready, so
 * setup/plot/trailer commands are written and the long plot stream is split into device-safe chunks
 * with a ready-wait between each (mirroring Graphtec.py's safe_write).
 */
class GpglSession(
    private val link: PlotterLink,
    private val sleep: (Long) -> Unit = { if (it > 0) Thread.sleep(it) },
) {

    /** Query the device once for its status. */
    fun status(timeoutMs: Long = STATUS_TIMEOUT_MS): GpglStatus {
        link.write(GpglProtocol.STATUS_QUERY)
        return GpglProtocol.decodeStatus(link.readLine(timeoutMs))
    }

    /** Poll status until the device is [GpglStatus.READY], or the attempts run out. */
    fun waitForReady(attempts: Int = READY_ATTEMPTS, pollMs: Long = READY_POLL_MS): Boolean {
        repeat(attempts) {
            when (status()) {
                GpglStatus.READY -> return true
                GpglStatus.UNKNOWN -> return@repeat
                else -> {}
            }
            sleep(pollMs)
        }
        return status() == GpglStatus.READY
    }

    /**
     * Run a full cut: initialise, wait for ready, send setup, stream the plot in safe chunks, then
     * the trailer. [onProgress] is called with 0f..1f as the plot stream is sent. Returns true once
     * the whole sequence has been written.
     */
    fun cut(
        device: SilhouetteDevice,
        settings: GpglCutSettings,
        polylines: List<Polyline>,
        shouldContinue: () -> Boolean = { true },
        onProgress: (Float) -> Unit = {},
    ): Boolean {
        link.write(GpglProtocol.INIT)
        if (!waitForReady()) return false
        write(GpglProtocol.setup(device, settings))
        if (!streamPlot(GpglProtocol.plot(device, polylines), shouldContinue, onProgress)) {
            link.write(GpglProtocol.INIT) // stop/reset the device on an aborted or stalled stream
            return false
        }
        write(GpglProtocol.trailer(polylines))
        return true
    }

    /** Write a block of commands as one ETX-delimited payload. */
    private fun write(commands: List<String>) {
        if (commands.isEmpty()) return
        link.write(GpglProtocol.delimit(commands))
    }

    /**
     * Stream the plot commands in chunks that stay under the device's command buffer, waiting for
     * ready after each chunk so a long path can't overrun it.
     */
    private fun streamPlot(commands: List<String>, shouldContinue: () -> Boolean, onProgress: (Float) -> Unit): Boolean {
        if (commands.isEmpty()) { onProgress(1f); return true }
        var i = 0
        while (i < commands.size) {
            if (!shouldContinue()) return false
            val end = minOf(i + CHUNK_COMMANDS, commands.size)
            write(commands.subList(i, end))
            if (!waitForReady()) return false
            i = end
            onProgress(i.toFloat() / commands.size)
        }
        return true
    }

    private companion object {
        const val STATUS_TIMEOUT_MS = 5000L
        const val READY_ATTEMPTS = 120
        const val READY_POLL_MS = 250L
        const val CHUNK_COMMANDS = 64
    }
}
