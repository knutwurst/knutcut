package de.knutwurst.knutcut.svgcore

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.hypot

/**
 * A stand-in for a VEVOR/Smart plotter, so the whole cut flow can be tried without a machine.
 *
 * It answers every framed message the way the real device does, echoing its cseq. The material is
 * reported "at the sensor" and the start key "not pressed" for the first [keyDelayPolls] polls of a
 * session, then "fed in" / "pressed", so the app walks through the same prompts as with a real
 * machine. Nothing is cut.
 *
 * Path chunks are acknowledged only after the time the tool would need to drive them: pen-down
 * moves at [cutMmPerS], pen-up travel at [travelMmPerS], scaled by [timeScale] (0 = instant, for
 * tests). So a big design takes as long as it would on a machine and the live trace keeps pace. The
 * speeds are estimates; the machine chooses its own speed and doesn't report it.
 */
class DemoPlotterLink(
    private val keyDelayPolls: Int = 1,
    private val cutMmPerS: Double = CUT_MM_PER_S,
    private val travelMmPerS: Double = TRAVEL_MM_PER_S,
    private val timeScale: Double = 1.0,
) : ManagedLink {
    override val transport = LinkTransport.SPP
    override var onClosed: (() -> Unit)? = null

    private class Reply(val line: String, val delayMs: Long)

    private val replies = LinkedBlockingQueue<Reply>()
    @Volatile private var closed = false
    private var materialPolls = 0
    private var startPolls = 0
    // Where the simulated tool head is, in plotter units; it starts at the origin each session.
    private var headX = 0
    private var headY = 0

    override fun write(text: String) {
        if (closed) return
        // A frame is compact JSON + CRC hex + CRLF; cseq is always the last key.
        val json = text.substringBeforeLast('}') + "}"
        val cseq = CSEQ.findAll(json).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: return
        val type = TYPE.find(json)?.groupValues?.get(1) ?: return
        if (type == "handshake") { materialPolls = 0; startPolls = 0; headX = 0; headY = 0 }   // each cut starts fresh
        val data = if (type == "query") {
            val state = when (ACTION.find(json)?.groupValues?.get(1)) {
                "queryMaterial" -> if (materialPolls++ < keyDelayPolls) "1" else "3"
                "queryStartKey" -> if (startPolls++ < keyDelayPolls) "false" else "true"
                else -> "true"
            }
            ""","data":{"state":$state}"""
        } else ""
        val delay = if (type == "pltFile") driveTimeMs(DATA.find(json)?.groupValues?.get(1).orEmpty()) else 0L
        replies.put(Reply("""{"type":"$type","success":true$data,"cseq":$cseq}""", delay))
    }

    /** How long the tool needs for the PU/PD moves in one chunk, and move the head to its end. */
    private fun driveTimeMs(hpgl: String): Long {
        var cutMm = 0.0
        var travelMm = 0.0
        for (cmd in hpgl.split(';')) {
            val m = MOVE.matchEntire(cmd.trim()) ?: continue
            val x = m.groupValues[2].toInt()
            val y = m.groupValues[3].toInt()
            val mm = hypot((x - headX).toDouble(), (y - headY).toDouble()) / UNITS_PER_MM
            if (m.groupValues[1] == "PD") cutMm += mm else travelMm += mm
            headX = x; headY = y
        }
        val seconds = cutMm / cutMmPerS + travelMm / travelMmPerS
        return (seconds * 1000 * timeScale).toLong().coerceIn(0L, MAX_CHUNK_MS)
    }

    override fun readLine(timeoutMs: Long): String? {
        if (closed) return null
        val r = replies.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return null
        // Sleep in short slices so closing the link (disconnect) doesn't wait out a long chunk.
        var left = r.delayMs
        while (left > 0 && !closed) { val step = minOf(left, 50L); Thread.sleep(step); left -= step }
        return if (closed) null else r.line
    }

    override fun close() {
        closed = true
        replies.clear()
    }

    companion object {
        /** Estimated cutting speed (pen down), mm/s. A guess: the machine doesn't report its speed. */
        const val CUT_MM_PER_S = 50.0
        /** Estimated travel speed (pen up), mm/s. Also a guess. */
        const val TRAVEL_MM_PER_S = 120.0
        /** Cap per chunk, so one huge chunk can't stall the demo for ages. */
        private const val MAX_CHUNK_MS = 8_000L

        private val CSEQ = Regex("\"cseq\":(\\d+)")
        private val TYPE = Regex("^\\{\"type\":\"([^\"]+)\"")
        private val ACTION = Regex("\"action\":\"([^\"]+)\"")
        private val DATA = Regex("\"data\":\"([^\"]*)\"")
        private val MOVE = Regex("(PU|PD)(-?\\d+),(-?\\d+)")
    }
}
