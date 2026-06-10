package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class DeformTest {

    // -----------------------------------------------------------------------
    // EditablePath
    // -----------------------------------------------------------------------

    @Test
    fun editablePathStraightNodesReproduceAnchors() {
        val nodes = listOf(
            PathNode(Pt(0.0, 0.0)),
            PathNode(Pt(10.0, 0.0)),
            PathNode(Pt(10.0, 10.0)),
        )
        val poly = EditablePath(nodes, closed = false).toPolyline()
        assertEquals(false, poly.closed)
        // start, mid, end must be present
        assertTrue(poly.points.any { it.xMm == 0.0 && it.yMm == 0.0 })
        assertTrue(poly.points.any { it.xMm == 10.0 && it.yMm == 0.0 })
        assertTrue(poly.points.any { it.xMm == 10.0 && it.yMm == 10.0 })
    }

    @Test
    fun editablePathStraightSegmentsProduceTwoPointsPerSegment() {
        // Straight lines: first and last point of each segment must equal the anchors
        val nodes = listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(5.0, 0.0)), PathNode(Pt(5.0, 5.0)))
        val poly = EditablePath(nodes, closed = false).toPolyline()
        assertEquals(Pt(0.0, 0.0), poly.points.first())
        assertEquals(Pt(5.0, 5.0), poly.points.last())
    }

    @Test
    fun editablePathCubicFlattenedWithinTolerance() {
        // A cubic where handles push the curve off the chord;
        // verify the flattened points stay within tolerance of the analytic midpoint.
        val p0 = Pt(0.0, 0.0)
        val h0 = Pt(0.0, 20.0)   // handleOut of node 0
        val h1 = Pt(20.0, 20.0)  // handleIn  of node 1
        val p1 = Pt(20.0, 0.0)
        val nodes = listOf(PathNode(p0, handleOut = h0), PathNode(p1, handleIn = h1))
        val tol = 0.1
        val poly = EditablePath(nodes, closed = false).toPolyline(tol)
        // Analytic midpoint of this cubic at t=0.5:
        // B(0.5) = (1-t)^3*p0 + 3(1-t)^2*t*h0 + 3(1-t)*t^2*h1 + t^3*p1  with t=0.5
        // = 0.125*(0,0) + 0.375*(0,20) + 0.375*(20,20) + 0.125*(20,0)
        // = (0+0+7.5+2.5, 0+7.5+7.5+0) = (10, 15)
        val midX = 10.0; val midY = 15.0
        val closest = poly.points.minByOrNull { p -> hypot(p.xMm - midX, p.yMm - midY) }!!
        assertTrue(hypot(closest.xMm - midX, closest.yMm - midY) < tol * 10)
    }

    @Test
    fun editablePathClosedFlagPropagated() {
        val nodes = listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(10.0, 0.0)), PathNode(Pt(5.0, 5.0)))
        val poly = EditablePath(nodes, closed = true).toPolyline()
        assertTrue(poly.closed)
    }

    @Test
    fun editablePathClosedConnectsLastToFirst() {
        val nodes = listOf(PathNode(Pt(0.0, 0.0)), PathNode(Pt(10.0, 0.0)), PathNode(Pt(5.0, 5.0)))
        val poly = EditablePath(nodes, closed = true).toPolyline()
        // first point = anchor of node 0, last point = anchor of node 0 (closed)
        assertEquals(poly.points.first(), poly.points.last())
    }

    // -----------------------------------------------------------------------
    // ArcLengthPath
    // -----------------------------------------------------------------------

    @Test
    fun arcLengthStraightLineLength() {
        val line = Polyline(listOf(Pt(0.0, 0.0), Pt(100.0, 0.0)), closed = false)
        val alp = ArcLengthPath(line)
        assertEquals(100.0, alp.length, 1e-9)
    }

    @Test
    fun arcLengthPointAtStartAndEnd() {
        val line = Polyline(listOf(Pt(0.0, 0.0), Pt(100.0, 0.0)), closed = false)
        val alp = ArcLengthPath(line)
        assertEquals(0.0, alp.pointAt(0.0).xMm, 1e-9)
        assertEquals(0.0, alp.pointAt(0.0).yMm, 1e-9)
        assertEquals(100.0, alp.pointAt(100.0).xMm, 1e-9)
        assertEquals(0.0, alp.pointAt(100.0).yMm, 1e-9)
    }

    @Test
    fun arcLengthPointAtMidpoint() {
        val line = Polyline(listOf(Pt(0.0, 0.0), Pt(100.0, 0.0)), closed = false)
        val alp = ArcLengthPath(line)
        val mid = alp.pointAt(50.0)
        assertEquals(50.0, mid.xMm, 1e-9)
        assertEquals(0.0, mid.yMm, 1e-9)
    }

    @Test
    fun arcLengthTangentIsUnitLength() {
        val line = Polyline(listOf(Pt(0.0, 0.0), Pt(100.0, 0.0)), closed = false)
        val alp = ArcLengthPath(line)
        val t = alp.tangentAt(50.0)
        assertEquals(1.0, hypot(t.xMm, t.yMm), 1e-9)
    }

    @Test
    fun arcLengthNormalIsUnitLength() {
        val line = Polyline(listOf(Pt(0.0, 0.0), Pt(100.0, 0.0)), closed = false)
        val alp = ArcLengthPath(line)
        val n = alp.normalAt(50.0)
        assertEquals(1.0, hypot(n.xMm, n.yMm), 1e-9)
    }

    @Test
    fun arcLengthTangentAndNormalAreOrthogonal() {
        val line = Polyline(listOf(Pt(0.0, 0.0), Pt(30.0, 40.0)), closed = false)
        val alp = ArcLengthPath(line)
        val t = alp.tangentAt(25.0)
        val n = alp.normalAt(25.0)
        val dot = t.xMm * n.xMm + t.yMm * n.yMm
        assertEquals(0.0, dot, 1e-9)
    }

    @Test
    fun arcLengthNormalSign() {
        // For a rightward horizontal guide, tangent = (+1,0), normal = tangent rotated +90 = (0,+1).
        // A positive v offset (point above baseline in screen coords) should go in the -normal direction
        // so that content above the baseline ends on the OUTER side of an upward curve.
        // Here we just pin the sign of normal for a horizontal guide.
        val line = Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), closed = false)
        val alp = ArcLengthPath(line)
        val n = alp.normalAt(5.0)
        // tangent rotated +90: (1,0) -> (0,1)  i.e. y increases
        assertEquals(0.0, n.xMm, 1e-9)
        assertEquals(1.0, n.yMm, 1e-9)
    }

    @Test
    fun arcLengthClampsBeyondEnd() {
        val line = Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), closed = false)
        val alp = ArcLengthPath(line)
        assertEquals(10.0, alp.pointAt(999.0).xMm, 1e-9)
        assertEquals(0.0, alp.pointAt(-1.0).xMm, 1e-9)
    }

    @Test
    fun arcLengthClosedGuideWraps() {
        // A square with perimeter 40. pointAt(10) should equal pointAt(50) mod 40.
        val sq = Polyline(
            listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0), Pt(0.0, 10.0), Pt(0.0, 0.0)),
            closed = true
        )
        val alp = ArcLengthPath(sq)
        assertEquals(40.0, alp.length, 1e-9)
        val a = alp.pointAt(10.0)
        val b = alp.pointAt(50.0)
        assertEquals(a.xMm, b.xMm, 1e-9)
        assertEquals(a.yMm, b.yMm, 1e-9)
    }

    // -----------------------------------------------------------------------
    // PathWarp.alongPath
    // -----------------------------------------------------------------------

    @Test
    fun warpAlongStraightHorizontalGuideReproducesSourceOnLine() {
        // Source: a horizontal strip (y in [0,10], x in [0,100])
        val source = listOf(
            Polyline(listOf(Pt(0.0, 5.0), Pt(50.0, 5.0), Pt(100.0, 5.0)), closed = false)
        )
        val bounds = Bounds(0.0, 0.0, 100.0, 10.0)
        val guide = ArcLengthPath(Polyline(listOf(Pt(0.0, 0.0), Pt(200.0, 0.0)), closed = false))
        val result = PathWarp.alongPath(source, bounds, guide, PathWarp.Baseline.BOTTOM)
        // All warped points should lie on y=0 + offset; the strip's y=5 with bottom baseline (y=10)
        // means v = 5 - 10 = -5, so result y = normal(0,1)*(-v) = (0,1)*5 = 5
        for (p in result.flatMap { it.points }) {
            assertEquals(5.0, p.yMm, 0.5)
        }
    }

    @Test
    fun warpAboveBaselineGoesOuterSideOfCurve() {
        // Guide: straight rightward. Baseline BOTTOM (sourceBounds.maxY).
        // A point ABOVE the baseline (v < 0) should produce positive y (outer side = away from guide).
        val source = listOf(Polyline(listOf(Pt(50.0, 2.0)), closed = false))
        val bounds = Bounds(0.0, 0.0, 100.0, 10.0)
        val guide = ArcLengthPath(Polyline(listOf(Pt(0.0, 0.0), Pt(200.0, 0.0)), closed = false))
        val result = PathWarp.alongPath(source, bounds, guide, PathWarp.Baseline.BOTTOM)
        // v = 2 - 10 = -8 (above baseline), result.y = normal.y * 8 = 8
        val p = result.first().points.first()
        assertEquals(8.0, p.yMm, 0.5)
    }

    @Test
    fun warpBelowBaselineGoesInnerSide() {
        // A point BELOW the baseline (y > baselineY) has v > 0, result offset is in -normal direction.
        // For a rightward guide, normal = (0,+1), so result y = -v < 0.
        // But we place the source below maxY, so this won't happen in practice;
        // just verify the sign is consistent.
        val source = listOf(Polyline(listOf(Pt(50.0, 12.0)), closed = false))
        val bounds = Bounds(0.0, 0.0, 100.0, 10.0)
        val guide = ArcLengthPath(Polyline(listOf(Pt(0.0, 0.0), Pt(200.0, 0.0)), closed = false))
        val result = PathWarp.alongPath(source, bounds, guide, PathWarp.Baseline.BOTTOM)
        // v = 12 - 10 = 2 (below baseline), result.y = -2
        val p = result.first().points.first()
        assertEquals(-2.0, p.yMm, 0.5)
    }

    @Test
    fun warpCenterBaselineUsesVerticalCenter() {
        val source = listOf(Polyline(listOf(Pt(50.0, 5.0)), closed = false))
        val bounds = Bounds(0.0, 0.0, 100.0, 10.0)
        val guide = ArcLengthPath(Polyline(listOf(Pt(0.0, 0.0), Pt(200.0, 0.0)), closed = false))
        val result = PathWarp.alongPath(source, bounds, guide, PathWarp.Baseline.CENTER)
        // center baseline = (minY+maxY)/2 = 5.0; v = 5 - 5 = 0; result.y = 0
        val p = result.first().points.first()
        assertEquals(0.0, p.yMm, 0.5)
    }

    @Test
    fun warpPreservesClosedFlag() {
        val source = listOf(
            Polyline(listOf(Pt(0.0, 5.0), Pt(50.0, 5.0), Pt(100.0, 5.0), Pt(0.0, 5.0)), closed = true),
            Polyline(listOf(Pt(0.0, 5.0), Pt(50.0, 5.0)), closed = false),
        )
        val bounds = Bounds(0.0, 0.0, 100.0, 10.0)
        val guide = ArcLengthPath(Polyline(listOf(Pt(0.0, 0.0), Pt(200.0, 0.0)), closed = false))
        val result = PathWarp.alongPath(source, bounds, guide)
        assertEquals(true, result[0].closed)
        assertEquals(false, result[1].closed)
    }

    @Test
    fun warpStraightSourceOnCurvedGuideSmoothOutput() {
        // A horizontal line segment placed exactly on the baseline and mapped onto a quarter-circle
        // guide (CCW from (r,0) to (0,r)).  With v=0 every output point lands exactly on the arc,
        // so its distance from the origin should equal radius.
        val radius = 50.0
        val segments = 64
        val arcPts = (0..segments).map { i ->
            val a = (i.toDouble() / segments) * (PI / 2)
            Pt(radius * cos(a), radius * sin(a))
        }
        val guide = ArcLengthPath(Polyline(arcPts, closed = false))
        val arcLength = guide.length

        // Place source y at the baseline value (maxY=10) so v=0 for every point.
        val source = listOf(
            Polyline(
                (0..20).map { i -> Pt(i.toDouble() / 20.0 * arcLength, 10.0) },
                closed = false
            )
        )
        val bounds = Bounds(0.0, 0.0, arcLength, 10.0)
        val result = PathWarp.alongPath(source, bounds, guide, PathWarp.Baseline.BOTTOM)
        // v=0 for all source points → result lands exactly on the guide arc
        for (p in result.flatMap { it.points }) {
            val dist = hypot(p.xMm, p.yMm)
            assertEquals(radius, dist, 1.0)  // 1 mm tolerance
        }
    }

    // -----------------------------------------------------------------------
    // PathWarp.onCircle
    // -----------------------------------------------------------------------

    @Test
    fun warpOnCircleStartPointAtStartAngle() {
        val radius = 30.0
        val center = Pt(0.0, 0.0)
        val source = listOf(Polyline(listOf(Pt(0.0, 5.0)), closed = false))
        val bounds = Bounds(0.0, 0.0, 2 * PI * radius, 10.0)
        // startAngleDeg = 0: first guide point is at (radius, 0).
        // CCW circle tangent at that point is (0,+1); normal = tangent rotated +90° = (-1,0) (inward).
        // v = 5 - 10 = -5 (above baseline); offset along normal = -v = 5 in the normal (-1,0) direction.
        // result = (radius, 0) + (-1,0)*5 = (radius-5, 0); dist from center = radius - 5.
        val result = PathWarp.onCircle(source, bounds, center, radius, startAngleDeg = 0.0,
            clockwise = false, baseline = PathWarp.Baseline.BOTTOM)
        val p = result.first().points.first()
        val dist = hypot(p.xMm - center.xMm, p.yMm - center.yMm)
        assertEquals(radius - 5.0, dist, 1.0)
    }

    @Test
    fun warpOnCirclePointsNearRadius() {
        val radius = 40.0
        val center = Pt(10.0, 10.0)
        // Source on the baseline (y == maxY): v = 0, all points should land at radius
        val n = 10
        val source = listOf(
            Polyline((0..n).map { i -> Pt(i.toDouble() / n * 2 * PI * radius, 10.0) }, closed = false)
        )
        val bounds = Bounds(0.0, 0.0, 2 * PI * radius, 10.0)
        val result = PathWarp.onCircle(source, bounds, center, radius, startAngleDeg = 0.0)
        for (p in result.flatMap { it.points }) {
            val dist = hypot(p.xMm - center.xMm, p.yMm - center.yMm)
            assertEquals(radius, dist, 1.0)
        }
    }

    // -----------------------------------------------------------------------
    // Bug #4: ArcLengthPath with zero-length guide must not produce NaN
    // -----------------------------------------------------------------------

    @Test
    fun arcLengthZeroLengthGuideDoesNotProduceNaN() {
        // Two coincident points: total length == 0. pointAt / normalAt must not return NaN.
        val pt = Pt(5.0, 3.0)
        val guide = Polyline(listOf(pt, pt), closed = false)
        val alp = ArcLengthPath(guide)
        val p = alp.pointAt(0.0)
        val n = alp.normalAt(0.0)
        assertTrue("pointAt must not be NaN", !p.xMm.isNaN() && !p.yMm.isNaN())
        assertTrue("normalAt must not be NaN", !n.xMm.isNaN() && !n.yMm.isNaN())
        assertTrue("normalAt must not be Infinite", !n.xMm.isInfinite() && !n.yMm.isInfinite())
    }

    @Test
    fun arcLengthZeroLengthGuidePointAtReturnsThePoint() {
        val pt = Pt(7.0, 2.0)
        val guide = Polyline(listOf(pt, Pt(7.0 + 1e-11, 2.0)), closed = false)
        val alp = ArcLengthPath(guide)
        val p = alp.pointAt(42.0)
        assertTrue("should return a finite point", !p.xMm.isNaN() && !p.yMm.isNaN())
    }

    // -----------------------------------------------------------------------
    // Bug #5: PathWarp.alongPath densification — no long chords on tight curves
    // -----------------------------------------------------------------------

    @Test
    fun warpAlongPathDensifiesOnTightCircle() {
        // A single straight horizontal source segment (only 2 source points) warped onto a
        // small circle (radius = 15 mm). The source y equals the baseline (v = 0), so every
        // warped point lands exactly on the guide arc at distance `radius` from the center.
        //
        // Without densification the output is just 2 points — a chord that cuts through the
        // circle. With densification the output is a dense polyline that hugs the circle, so
        // the output count must exceed 2 AND every point must be within 0.05 mm of `radius`.
        val radius = 15.0
        val center = Pt(0.0, 0.0)
        val circum = 2 * PI * radius

        // Build a circle guide (256 segments).
        val circlePoints = (0..256).map { i ->
            val a = (i.toDouble() / 256) * 2 * PI
            Pt(center.xMm + radius * cos(a), center.yMm + radius * sin(a))
        }
        val guide = ArcLengthPath(Polyline(circlePoints, closed = true))

        // Source: a single 2-point segment spanning the full circumference, at the baseline (v=0).
        val source = listOf(Polyline(listOf(Pt(0.0, 10.0), Pt(circum, 10.0)), closed = false))
        val bounds = Bounds(0.0, 0.0, circum, 10.0)
        val result = PathWarp.alongPath(source, bounds, guide, PathWarp.Baseline.BOTTOM)

        // Densification must produce more than 2 output points.
        val outPts = result.flatMap { it.points }
        assertTrue("output must be densified (got ${outPts.size} points)", outPts.size > 2)

        // Every output point must lie within 0.05 mm of the circle (v=0 → no offset from arc).
        val tolerance = 0.05
        for (p in outPts) {
            val dist = hypot(p.xMm - center.xMm, p.yMm - center.yMm)
            assertEquals("point dist from center ($dist) not near $radius", radius, dist, tolerance)
        }
    }

    @Test
    fun warpOnCircleFullCircleMapsBackToStart() {
        val radius = 20.0
        val center = Pt(0.0, 0.0)
        val circum = 2 * PI * radius
        // A polyline whose first point is at x=0 and last is at x=circumference (full circle)
        val source = listOf(
            Polyline(listOf(Pt(0.0, 10.0), Pt(circum, 10.0)), closed = false)
        )
        val bounds = Bounds(0.0, 0.0, circum, 10.0)
        val result = PathWarp.onCircle(source, bounds, center, radius, startAngleDeg = 0.0)
        val pts = result.first().points
        val first = pts.first()
        val last = pts.last()
        // Both should be at the same angle (start), therefore same point
        assertEquals(first.xMm, last.xMm, 1.0)
        assertEquals(first.yMm, last.yMm, 1.0)
    }
}
