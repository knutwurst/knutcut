package de.knutwurst.knutcut.svgcore

/**
 * Splits a byte stream into CRLF-terminated lines, decoding each whole line as UTF-8. Bytes are
 * buffered across [append] calls, so a line — or a multi-byte UTF-8 character — split across reads
 * is reassembled before decoding (decoding each raw socket chunk on its own would corrupt those).
 */
class LineFramer {
    private var buf = ByteArray(256)
    private var len = 0

    /** Feed [count] bytes from [data]; returns any complete lines (without their CRLF) found. */
    fun append(data: ByteArray, count: Int): List<String> {
        var out: ArrayList<String>? = null
        for (i in 0 until count) {
            ensure(len + 1)
            buf[len++] = data[i]
            if (len >= 2 && buf[len - 2] == CR && buf[len - 1] == LF) {
                val line = String(buf, 0, len - 2, Charsets.UTF_8)
                (out ?: ArrayList<String>().also { out = it }).add(line)
                len = 0
            }
        }
        return out ?: emptyList()
    }

    private fun ensure(capacity: Int) {
        if (capacity <= buf.size) return
        var n = buf.size * 2
        while (n < capacity) n *= 2
        buf = buf.copyOf(n)
    }

    private companion object {
        const val CR = 0x0D.toByte()
        const val LF = 0x0A.toByte()
    }
}
