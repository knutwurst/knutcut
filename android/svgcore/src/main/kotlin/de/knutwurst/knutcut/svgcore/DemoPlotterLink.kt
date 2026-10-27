package de.knutwurst.knutcut.svgcore

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A stand-in for a VEVOR/Smart plotter, so the whole cut flow can be tried without a machine.
 *
 * It answers every framed message the way the real device does, echoing its cseq. The material is
 * reported "at the sensor" and the start key "not pressed" for the first [keyDelayPolls] polls of a
 * session, then "fed in" / "pressed", so the app walks through the same prompts as with a real
 * machine. Path chunks are acknowledged after [chunkDelayMs], so progress moves like a real transfer.
 * Nothing is cut.
 */
class DemoPlotterLink(
    private val chunkDelayMs: Long = 40,
    private val keyDelayPolls: Int = 1,
) : ManagedLink {
    override val transport = LinkTransport.SPP
    override var onClosed: (() -> Unit)? = null

    private class Reply(val line: String, val delayMs: Long)

    private val replies = LinkedBlockingQueue<Reply>()
    @Volatile private var closed = false
    private var materialPolls = 0
    private var startPolls = 0

    override fun write(text: String) {
        if (closed) return
        // A frame is compact JSON + CRC hex + CRLF; cseq is always the last key.
        val json = text.substringBeforeLast('}') + "}"
        val cseq = CSEQ.findAll(json).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: return
        val type = TYPE.find(json)?.groupValues?.get(1) ?: return
        if (type == "handshake") { materialPolls = 0; startPolls = 0 }   // each cut starts fresh
        val data = if (type == "query") {
            val state = when (ACTION.find(json)?.groupValues?.get(1)) {
                "queryMaterial" -> if (materialPolls++ < keyDelayPolls) "1" else "3"
                "queryStartKey" -> if (startPolls++ < keyDelayPolls) "false" else "true"
                else -> "true"
            }
            ""","data":{"state":$state}"""
        } else ""
        val delay = if (type == "pltFile") chunkDelayMs else 0L
        replies.put(Reply("""{"type":"$type","success":true$data,"cseq":$cseq}""", delay))
    }

    override fun readLine(timeoutMs: Long): String? {
        if (closed) return null
        val r = replies.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return null
        if (r.delayMs > 0) Thread.sleep(r.delayMs)
        return r.line
    }

    override fun close() {
        closed = true
        replies.clear()
    }

    private companion object {
        val CSEQ = Regex("\"cseq\":(\\d+)")
        val TYPE = Regex("^\\{\"type\":\"([^\"]+)\"")
        val ACTION = Regex("\"action\":\"([^\"]+)\"")
    }
}
