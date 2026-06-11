package de.knutwurst.knutcut.svgcore

/**
 * Groups contours for COLOUR-mode filling.
 *
 * The renderer fills each returned group as one even-odd path, and draws groups independently. The
 * grouping rule makes the two cases behave the way a user expects when a whole layer shares a colour:
 *
 *  - Contours of the same colour where one is fully nested inside another share a group, so the inner
 *    one carves a real hole (letter counters, donut holes).
 *  - Contours that merely overlap (neither contains the other) — or differ in colour — go into
 *    separate groups, so overlapping same-colour shapes union instead of cancelling each other out.
 *
 * Without this, every same-colour contour was merged into one even-odd path; after merging layers and
 * assigning one colour, overlapping shapes then cancelled in their overlap and left gaps.
 *
 * Containment is affine-invariant, so the result is independent of the layer's placement.
 */
object FillNesting {

    private const val EPS = 1e-6

    /**
     * Group indices into [contours] for filling. [colors] is the per-contour colour (aligned 1:1;
     * null = no fill). Returns groups as index lists in input order, the groups themselves ordered by
     * first appearance (document order) so painter's order is preserved.
     */
    fun fillGroups(contours: List<Polyline>, colors: List<Int?>): List<List<Int>> {
        val n = contours.size
        if (n == 0) return emptyList()
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != c) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }
        fun union(a: Int, b: Int) { val ra = find(a); val rb = find(b); if (ra != rb) parent[ra] = rb }

        val bounds = contours.map { Bounds.ofOrNull(it.points) }
        val fillable = BooleanArray(n) { contours[it].closed && contours[it].points.size >= 3 }

        for (i in 0 until n) {
            if (!fillable[i]) continue
            for (j in 0 until n) {
                if (i == j || !fillable[j]) continue
                // Same colour AND i fully contains j → they belong to one nesting tree (even-odd hole).
                if (colors[i] == colors[j] && contains(contours[i], bounds[i], contours[j], bounds[j])) {
                    union(i, j)
                }
            }
        }

        val byRoot = LinkedHashMap<Int, MutableList<Int>>()
        for (i in 0 until n) byRoot.getOrPut(find(i)) { mutableListOf() }.add(i)
        return byRoot.values.map { it.toList() }
    }

    /** True when [outer] fully contains [inner]: inner's bbox sits inside outer's and every inner
     *  vertex lies inside the outer polygon (so partial overlaps are NOT treated as containment). */
    private fun contains(outer: Polyline, ob: Bounds?, inner: Polyline, ib: Bounds?): Boolean {
        if (ob == null || ib == null) return false
        if (ib.minX < ob.minX - EPS || ib.maxX > ob.maxX + EPS ||
            ib.minY < ob.minY - EPS || ib.maxY > ob.maxY + EPS) return false
        return inner.points.all { pointInPolygon(it, outer.points) }
    }

    /** Even-odd ray-cast point-in-polygon. Points on the boundary are reported inconsistently, which
     *  is fine here: a contour resting exactly on another's edge isn't a clean nesting anyway. */
    private fun pointInPolygon(p: Pt, poly: List<Pt>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.yMm > p.yMm) != (b.yMm > p.yMm)) {
                val x = a.xMm + (p.yMm - a.yMm) / (b.yMm - a.yMm) * (b.xMm - a.xMm)
                if (p.xMm < x) inside = !inside
            }
            j = i
        }
        return inside
    }
}
