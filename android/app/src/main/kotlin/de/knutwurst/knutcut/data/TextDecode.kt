package de.knutwurst.knutcut.data

/**
 * Decode file bytes to text, honouring a leading byte-order mark (BOM).
 *
 * Some editors (notably CorelDRAW) export SVG as UTF-16; decoding those bytes as UTF-8 yields garbage
 * that the XML parser rejects, which surfaced as "no cuttable paths". Detect the BOM and decode with
 * the matching charset (stripping the BOM), falling back to UTF-8 when there is none.
 */
object TextDecode {
    fun decode(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> String(bytes, Charsets.UTF_8)
    }
}
