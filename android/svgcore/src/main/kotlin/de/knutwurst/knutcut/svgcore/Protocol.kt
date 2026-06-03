package de.knutwurst.knutcut.svgcore

/**
 * Cut parameters. [materialId] is the cloud material id, [tool] is the SP tool number (1 = knife/right,
 * 2 = pen/left), [force] is the FS pressure. The machine handles speed itself; it is not sent.
 */
data class CutSettings(val materialId: String, val tool: Int, val force: Int)

/**
 * A message sent to the plotter. Each serialises to the exact compact JSON the device expects,
 * with [cseq] appended last (matching the stock app's key order).
 */
sealed class PlotterMessage {
    abstract fun json(cseq: Int): String
}

object Handshake : PlotterMessage() {
    override fun json(cseq: Int) = """{"type":"handshake","cseq":$cseq}"""
}

object Bye : PlotterMessage() {
    override fun json(cseq: Int) = """{"type":"bye","cseq":$cseq}"""
}

data class Query(val action: String) : PlotterMessage() {
    override fun json(cseq: Int) = """{"type":"query","action":"$action","cseq":$cseq}"""
}

data class PltCommand(val data: String) : PlotterMessage() {
    override fun json(cseq: Int) = """{"type":"pltCommand","data":"${jsonEscape(data)}","cseq":$cseq}"""
}

data class PltFile(val index: Int, val total: Int, val data: String) : PlotterMessage() {
    override fun json(cseq: Int) =
        """{"type":"pltFile","index":$index,"total":$total,"data":"${jsonEscape(data)}","cseq":$cseq}"""
}

private fun jsonEscape(s: String): String {
    if (s.none { it == '"' || it == '\\' || it < ' ' }) return s
    val sb = StringBuilder(s.length + 8)
    for (ch in s) when (ch) {
        '"' -> sb.append("\\\"")
        '\\' -> sb.append("\\\\")
        '\n' -> sb.append("\\n")
        '\r' -> sb.append("\\r")
        '\t' -> sb.append("\\t")
        else -> sb.append(ch)
    }
    return sb.toString()
}

/** Frames a message exactly as the stock app does: compact JSON + CRC-16/X25 (hex) + CRLF. */
object Frame {
    fun encode(msg: PlotterMessage, cseq: Int): String {
        val json = msg.json(cseq)
        return json + crc16X25(json) + "\r\n"
    }

    /**
     * CRC-16/X25 (init 0xFFFF, reflected poly 0x8408, final xor 0xFFFF) as uppercase hex.
     * Reproduces the stock app's quirk: a three-hex-digit result is zero-padded to four,
     * but one/two/four-digit results are left as-is.
     */
    fun crc16X25(s: String): String {
        var crc = 0xFFFF
        for (byte in s.toByteArray(Charsets.UTF_8)) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0x8408 else crc ushr 1
            }
        }
        crc = crc.inv() and 0xFFFF
        val hex = crc.toString(16).uppercase()
        return if (hex.length == 3) "0$hex" else hex
    }
}

/**
 * Builds the ordered message sequence for a cut. The frame format and individual message types are
 * confirmed from the stock app; the exact high-level ordering is a faithful reconstruction and will
 * be re-checked against the device on the first real cut.
 */
object Protocol {
    const val DEFAULT_CHUNK = 40

    fun buildCut(pathCommands: List<String>, settings: CutSettings, chunk: Int = DEFAULT_CHUNK): List<PlotterMessage> {
        val msgs = ArrayList<PlotterMessage>()
        msgs += Handshake
        msgs += Query("queryMaterial")
        msgs += Query("queryPulled")
        msgs += PltCommand("TB66;")
        msgs += PltCommand("setmat:${settings.materialId};")
        msgs += PltCommand("SP${settings.tool};FS${settings.force};")
        msgs.addAll(pathFile(pathCommands, chunk))
        msgs += PltCommand("TB66;")
        msgs += Bye
        return msgs
    }

    /** Split the path commands into chunked pltFile messages (data joined by ';', later chunks prefixed with ';'). */
    fun pathFile(pathCommands: List<String>, chunk: Int = DEFAULT_CHUNK): List<PltFile> {
        if (pathCommands.isEmpty()) return emptyList()
        val chunks = pathCommands.chunked(chunk)
        return chunks.mapIndexed { i, ch ->
            val data = (if (i > 0) ";" else "") + ch.joinToString(";")
            PltFile(i, chunks.size, data)
        }
    }
}
