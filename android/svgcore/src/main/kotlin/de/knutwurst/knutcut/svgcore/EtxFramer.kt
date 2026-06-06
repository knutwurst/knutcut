package de.knutwurst.knutcut.svgcore

/**
 * Splits a byte stream into ETX-terminated tokens, decoding each whole token as UTF-8. The Silhouette
 * (GPGL) terminates every response with a single 0x03 (ETX) byte rather than CRLF, so this is the
 * GPGL counterpart of [LineFramer]. Bytes are buffered across [append] calls, so a response split
 * across reads (BLE notifications arrive in MTU-sized fragments) is reassembled before decoding.
 */
class EtxFramer {
    private var buf = ByteArray(64)
    private var len = 0

    /** Feed [count] bytes from [data]; returns any complete tokens (without their ETX) found. */
    fun append(data: ByteArray, count: Int): List<String> {
        var out: ArrayList<String>? = null
        for (i in 0 until count) {
            val b = data[i]
            if (b == ETX) {
                val token = String(buf, 0, len, Charsets.UTF_8)
                (out ?: ArrayList<String>().also { out = it }).add(token)
                len = 0
            } else {
                ensure(len + 1)
                buf[len++] = b
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
        const val ETX = 0x03.toByte()
    }
}
