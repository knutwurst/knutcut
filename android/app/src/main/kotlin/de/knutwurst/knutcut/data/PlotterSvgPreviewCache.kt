package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.SvgParser

/**
 * Bounded LRU cache of parsed preview polylines for library motifs. The grid would otherwise
 * re-parse each motif's SVG (a full DOM parse) on every (re)composition while scrolling; this keeps
 * a recently-seen set so scrolling back is instant. Parsing itself is still done by the caller on a
 * background dispatcher — [preview] only memoises the result.
 */
object PlotterSvgPreviewCache {
    private const val MAX_ENTRIES = 256

    // access-order LinkedHashMap = LRU; evicts the least-recently-used once over capacity.
    private val cache = object : LinkedHashMap<String, List<Polyline>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Polyline>>): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized private fun get(id: String): List<Polyline>? = cache[id]
    @Synchronized private fun put(id: String, value: List<Polyline>) { cache[id] = value }

    /** Parsed polylines for [svg], cached by [id]. Safe to call from a background thread.
     *  Contours are force-closed to match how the motif is cut (filled glyphs close implicitly;
     *  see KnutcutViewModel.mergeLibraryLayers), so the outline preview shows the real cut path. */
    fun preview(id: String, svg: String): List<Polyline> {
        get(id)?.let { return it }
        val parsed = runCatching { SvgParser.parse(svg) }.getOrDefault(emptyList())
            .map { if (it.closed) it else it.copy(closed = true) }
        put(id, parsed)
        return parsed
    }
}
