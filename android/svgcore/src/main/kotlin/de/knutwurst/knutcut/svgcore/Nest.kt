package de.knutwurst.knutcut.svgcore

/**
 * Bounding-box shelf packing to save material: arrange pieces into rows of a fixed width, top-left
 * origin, with a gap between them and no box overlaps. With [allow90] a piece may be rotated 90°
 * (w/h swapped) so it sits landscape, which keeps rows short. This is not true polygon nesting —
 * each piece is treated as its axis-aligned bounding box — but it's robust and packs toward the top.
 * Android-free so it can be unit-tested.
 */
object Nest {
    data class Box(val id: Int, val w: Double, val h: Double)
    data class Placed(val id: Int, val x: Double, val y: Double, val w: Double, val h: Double, val rotated: Boolean)

    fun pack(boxes: List<Box>, areaWidth: Double, gap: Double, allow90: Boolean): List<Placed> {
        // Normalize to landscape when allowed (and it still fits the width) so rows stay short,
        // then pack tallest-first (first-fit decreasing height).
        data class Item(val id: Int, val w: Double, val h: Double, val rotated: Boolean)
        val items = boxes.map { b ->
            if (allow90 && b.h > b.w && b.h <= areaWidth) Item(b.id, b.h, b.w, true)
            else Item(b.id, b.w, b.h, false)
        }.sortedByDescending { it.h }

        val out = ArrayList<Placed>()
        var x = 0.0
        var y = 0.0
        var rowH = 0.0
        for (it in items) {
            // Wrap to a new row when this piece would run past the width (unless the row is empty).
            if (x > 0.0 && x + it.w > areaWidth + 1e-6) {
                x = 0.0
                y += rowH + gap
                rowH = 0.0
            }
            out.add(Placed(it.id, x, y, it.w, it.h, it.rotated))
            x += it.w + gap
            rowH = maxOf(rowH, it.h)
        }
        return out
    }
}
