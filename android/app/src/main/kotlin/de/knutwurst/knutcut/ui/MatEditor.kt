package de.knutwurst.knutcut.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.knutwurst.knutcut.data.Mat
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Pt
import kotlin.math.atan2
import kotlin.math.min

private sealed interface Drag {
    /** Handle 0-3 = corners TL,TR,BR,BL (uniform scale); 4-7 = sides top,right,bottom,left (one axis). */
    data class Resize(val handle: Int) : Drag
    object Rotate : Drag
    object Move : Drag
    object PanCamera : Drag
    object Camera : Drag
}

private const val HANDLE_HIT_PX = 34f   // touch radius for grabbing a handle
private const val ROTATE_ARM_PX = 44f   // length of the rotate handle's arm
private const val TAP_SLOP_PX = 12f     // movement below this counts as a tap, not a drag
private const val CORNER_PX = 14f       // drawn size of a corner handle
private const val SIDE_PX = 12f         // drawn size of a side-midpoint handle
private const val ROTATE_DOT_PX = 8f    // drawn radius of the rotate handle dot
// Smart-guide line colour: a high-contrast magenta that reads on both light and dark mats.
private val GUIDE_COLOR = Color(0xFFFF4081)

/**
 * The placement mat. Pinch or one-finger-drag on empty space moves/zooms the *work area* (like a
 * photo). The design is moved by dragging it, scaled with the corner handles, and turned with the
 * handle above it — the design itself never changes when you zoom the view.
 */
@Composable
fun MatEditor(vm: KnutcutViewModel, modifier: Modifier = Modifier) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    // Derive the grid/ruler tones from the theme so they stay visible on both light and dark.
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridMinor = onSurface.copy(alpha = 0.16f)
    val gridMajor = onSurface.copy(alpha = 0.38f)
    val matColor = onSurface.copy(alpha = 0.55f)
    val matFill = onSurface.copy(alpha = 0.05f)
    val rulerColor = onSurface.copy(alpha = 0.7f).toArgb()
    val knifeColor = MaterialTheme.colorScheme.primary
    val penColor = MaterialTheme.colorScheme.secondary
    val handleColor = MaterialTheme.colorScheme.tertiary
    val offMatColor = MaterialTheme.colorScheme.error
    val guideColor = GUIDE_COLOR
    val readout = vm.selectionReadout() ?: vm.overallReadout()
    val matSummary = readout?.let { "Arbeitsfläche: $it" } ?: "Arbeitsfläche, Matte ausgewählt"

    Box(modifier.semantics { contentDescription = matSummary }) {
      Canvas(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var ppm = ppmFor(sizePx, vm.mat, vm.camScale)
                if (ppm <= 0f) return@awaitEachGesture
                var origin = originFor(sizePx, vm.mat, vm.camScale, vm.camOffset)

                val handlesScreen = handleWorldPoints(vm.placedCorners()).map { worldToScreen(it, origin, ppm) }
                val cornersScreen = vm.placedCorners().map { worldToScreen(it, origin, ppm) }
                val centerScreen = worldToScreen(vm.centerMm, origin, ppm)
                var drag: Drag = hitTest(down.position, handlesScreen, cornersScreen, centerScreen)

                // Missed the selected layer's box/handles? If the touch is inside another layer,
                // select it and move that one instead of panning the view.
                if (drag is Drag.PanCamera && vm.layers.isNotEmpty()) {
                    val hit = vm.layerAt(screenToWorld(down.position, origin, ppm))
                    if (hit >= 0) { vm.selectLayer(hit); drag = Drag.Move }
                }

                val startRotation = vm.rotationDeg
                val startAngle = atan2((down.position.y - centerScreen.y).toDouble(), (down.position.x - centerScreen.x).toDouble())

                // Resize anchor: the handle opposite the grabbed one stays fixed in world space.
                val b = vm.bounds
                val localHandles = if (b != null) handleLocalPoints(b) else emptyList()
                val centerLocal = if (b != null) Pt((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2) else Pt(0.0, 0.0)
                val rsRot = Math.toRadians(vm.rotationDeg)
                val resizeHandle = (drag as? Drag.Resize)?.handle ?: -1
                val anchorIdx = if (resizeHandle >= 0) anchorOf(resizeHandle) else -1
                val worldHandles = handleWorldPoints(vm.placedCorners())
                val anchorLocal = if (anchorIdx >= 0) localHandles[anchorIdx] else Pt(0.0, 0.0)
                val draggedLocal = if (resizeHandle >= 0) localHandles[resizeHandle] else Pt(0.0, 0.0)
                val anchorWorld = if (anchorIdx >= 0 && worldHandles.size == 8) worldHandles[anchorIdx] else Pt(0.0, 0.0)
                // Captured once so a corner drag scales the start size uniformly (keeps the aspect ratio).
                val startScaleX = vm.scaleX
                val startScaleY = vm.scaleY
                var totalDrag = 0f
                var pushedHistory = false   // snapshot once, on the first real move/resize/rotate
                // Running, un-snapped centre for the move (so snapping never swallows small drags).
                var moveCenter = vm.centerMm

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break

                    if (pressed.size >= 2) {
                        val p0 = pressed[0]; val p1 = pressed[1]
                        val prevC = (p0.previousPosition + p1.previousPosition) / 2f
                        val curC = (p0.position + p1.position) / 2f
                        val prevD = (p0.previousPosition - p1.previousPosition).getDistance()
                        val curD = (p0.position - p1.position).getDistance()
                        val z = if (prevD > 0f) curD / prevD else 1f
                        vm.camOffset = curC - (prevC - vm.camOffset) * z
                        vm.camScale *= z
                        drag = Drag.Camera
                        event.changes.forEach { it.consume() }
                    } else {
                        val p = pressed[0]
                        val moved = p.positionChange().getDistance()
                        totalDrag += moved
                        if (!pushedHistory && moved > 0f && (drag is Drag.Move || drag is Drag.Resize || drag is Drag.Rotate)) {
                            vm.pushHistory(); pushedHistory = true
                        }
                        ppm = ppmFor(sizePx, vm.mat, vm.camScale)
                        origin = originFor(sizePx, vm.mat, vm.camScale, vm.camOffset)
                        val cs = worldToScreen(vm.centerMm, origin, ppm)
                        when (val d = drag) {
                            Drag.Camera, Drag.PanCamera -> vm.camOffset += p.positionChange()
                            Drag.Move -> {
                                val dp = p.positionChange()
                                moveCenter = Pt(moveCenter.xMm + dp.x / ppm, moveCenter.yMm + dp.y / ppm)
                                // ~8 px alignment tolerance, converted to mm so it feels the same at any zoom.
                                vm.moveSelectedTo(moveCenter, (8f / ppm).toDouble())
                            }
                            is Drag.Resize -> {
                                val pw = screenToWorld(p.position, origin, ppm)
                                val dxw = pw.xMm - anchorWorld.xMm
                                val dyw = pw.yMm - anchorWorld.yMm
                                // un-rotate into the design's local axes
                                val cn = Math.cos(-rsRot); val sn = Math.sin(-rsRot)
                                val ux = dxw * cn - dyw * sn
                                val uy = dxw * sn + dyw * cn
                                val ldx = draggedLocal.xMm - anchorLocal.xMm
                                val ldy = draggedLocal.yMm - anchorLocal.yMm
                                var sx = vm.scaleX; var sy = vm.scaleY
                                when {
                                    d.handle < 4 -> { // corner: scale uniformly, keeping the current aspect ratio
                                        val dxs = ldx * startScaleX
                                        val dys = ldy * startScaleY
                                        val len2 = dxs * dxs + dys * dys
                                        val k = if (len2 > 1e-9) ((ux * dxs + uy * dys) / len2).coerceAtLeast(0.02) else 1.0
                                        sx = startScaleX * k
                                        sy = startScaleY * k
                                    }
                                    d.handle == 5 || d.handle == 7 -> // right/left: width only
                                        if (kotlin.math.abs(ldx) > 1e-6) sx = (ux / ldx).coerceAtLeast(0.02)
                                    else -> // top/bottom: height only
                                        if (kotlin.math.abs(ldy) > 1e-6) sy = (uy / ldy).coerceAtLeast(0.02)
                                }
                                vm.scaleX = sx; vm.scaleY = sy
                                // move the centre so the anchor handle stays put
                                val ax = (centerLocal.xMm - anchorLocal.xMm) * sx
                                val ay = (centerLocal.yMm - anchorLocal.yMm) * sy
                                val cp = Math.cos(rsRot); val sp = Math.sin(rsRot)
                                vm.centerMm = Pt(anchorWorld.xMm + (ax * cp - ay * sp), anchorWorld.yMm + (ax * sp + ay * cp))
                            }
                            Drag.Rotate -> {
                                val a = atan2((p.position.y - cs.y).toDouble(), (p.position.x - cs.x).toDouble())
                                vm.rotationDeg = startRotation + Math.toDegrees(a - startAngle)
                            }
                        }
                        p.consume()
                    }
                }
                // A tap on empty mat space (no real drag) clears the selection — the mat is selected.
                if (drag is Drag.PanCamera && totalDrag < TAP_SLOP_PX) vm.deselectLayers()
                vm.clearGuides()
            }
        },
    ) {
        sizePx = IntSize(size.width.toInt(), size.height.toInt())
        val ppm = ppmFor(sizePx, vm.mat, vm.camScale)
        if (ppm <= 0f) return@Canvas
        val origin = originFor(sizePx, vm.mat, vm.camScale, vm.camOffset)
        fun s(p: Pt) = worldToScreen(p, origin, ppm)

        drawGrid(vm.mat, origin, ppm, gridMinor, gridMajor)

        // mat border
        val tl = s(Pt(0.0, 0.0)); val br = s(Pt(vm.mat.widthMm, vm.mat.heightMm))
        drawRect(matFill, topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y))
        drawRect(matColor, topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y), style = Stroke(width = 2f))

        drawRulers(vm.mat, origin, ppm, rulerColor)

        // design — knife layers in the primary colour, pen layers in the secondary; anything that
        // runs off the mat is drawn in the error colour as a warning.
        for ((tool, pls) in vm.placedLayers()) {
            val toolColor = if (tool == Tool.PEN) penColor else knifeColor
            for (pl in pls) {
                if (pl.points.isEmpty()) continue
                val col = if (pl.points.any { vm.isOutsideMat(it) }) offMatColor else toolColor
                val path = Path()
                val f = s(pl.points.first()); path.moveTo(f.x, f.y)
                for (k in 1 until pl.points.size) { val q = s(pl.points[k]); path.lineTo(q.x, q.y) }
                if (pl.closed) path.close()
                drawPath(path, col, style = Stroke(width = 2.5f))
            }
        }

        // selection box + handles
        val corners = vm.placedCorners().map { s(it) }
        if (corners.size == 4) {
            val box = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                corners.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(box, handleColor, style = Stroke(width = 1.5f))
            // corner handles (square) and side-midpoint handles (square, slightly smaller)
            corners.forEach { drawRect(handleColor, topLeft = Offset(it.x - CORNER_PX / 2, it.y - CORNER_PX / 2), size = Size(CORNER_PX, CORNER_PX)) }
            for (i in 0 until 4) {
                val m = (corners[i] + corners[(i + 1) % 4]) / 2f
                drawRect(handleColor, topLeft = Offset(m.x - SIDE_PX / 2, m.y - SIDE_PX / 2), size = Size(SIDE_PX, SIDE_PX))
            }
            val rot = rotateHandlePos(corners, s(vm.centerMm))
            val topMid = (corners[0] + corners[1]) / 2f
            drawLine(handleColor, topMid, rot, strokeWidth = 1.5f)
            drawCircle(handleColor, radius = ROTATE_DOT_PX, center = rot)
        }

        // smart alignment guides (centre snapping) while dragging
        vm.alignGuideX?.let { gx ->
            val x = s(Pt(gx, 0.0)).x
            drawLine(guideColor, Offset(x, s(Pt(0.0, 0.0)).y), Offset(x, s(Pt(0.0, vm.mat.heightMm)).y), strokeWidth = 1.5f)
        }
        vm.alignGuideY?.let { gy ->
            val y = s(Pt(0.0, gy)).y
            drawLine(guideColor, Offset(s(Pt(0.0, 0.0)).x, y), Offset(s(Pt(vm.mat.widthMm, 0.0)).x, y), strokeWidth = 1.5f)
        }
      }

      // Bottom-left readout: the selected layer's position + size, or the whole design's total size
      // when the mat is selected (so you can see how much material it needs).
      readout?.let { readout ->
          Surface(
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
              contentColor = MaterialTheme.colorScheme.onSurface,
              shape = RoundedCornerShape(6.dp),
              tonalElevation = 2.dp,
              modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
          ) {
              Text(
                  readout,
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
              )
          }
      }
    }
}

private fun DrawScope.drawGrid(mat: Mat, origin: Offset, ppm: Float, minor: Color, major: Color) {
    val step = 10.0 // mm
    var x = 0.0
    var i = 0
    while (x <= mat.widthMm + 0.01) {
        val sx = (origin.x + x * ppm).toFloat()
        val c = if (i % 5 == 0) major else minor
        drawLine(c, Offset(sx, origin.y), Offset(sx, (origin.y + mat.heightMm * ppm).toFloat()), strokeWidth = 1f)
        x += step; i++
    }
    var y = 0.0; i = 0
    while (y <= mat.heightMm + 0.01) {
        val sy = (origin.y + y * ppm).toFloat()
        val c = if (i % 5 == 0) major else minor
        drawLine(c, Offset(origin.x, sy), Offset((origin.x + mat.widthMm * ppm).toFloat(), sy), strokeWidth = 1f)
        y += step; i++
    }
}

/** Small cm tick labels along the top and left of the mat. */
private fun DrawScope.drawRulers(mat: Mat, origin: Offset, ppm: Float, textColor: Int) {
    val paint = android.graphics.Paint().apply {
        color = textColor
        textSize = 22f
        isAntiAlias = true
    }
    val canvas = drawContext.canvas.nativeCanvas
    var x = 0.0
    while (x <= mat.widthMm + 0.01) {
        canvas.drawText((x / 10).toInt().toString(), (origin.x + x * ppm).toFloat() + 3f, origin.y - 6f, paint)
        x += 50.0
    }
    var y = 0.0
    while (y <= mat.heightMm + 0.01) {
        canvas.drawText((y / 10).toInt().toString(), origin.x - 26f, (origin.y + y * ppm).toFloat() + 7f, paint)
        y += 50.0
    }
    canvas.drawText("cm", origin.x - 28f, origin.y - 6f, paint)
}

private fun baseScale(sizePx: IntSize, mat: Mat): Float {
    if (sizePx.width == 0 || sizePx.height == 0) return 0f
    return (min(sizePx.width / mat.widthMm, sizePx.height / mat.heightMm) * 0.9).toFloat()
}

private fun ppmFor(sizePx: IntSize, mat: Mat, camScale: Float): Float = baseScale(sizePx, mat) * camScale

private fun originFor(sizePx: IntSize, mat: Mat, camScale: Float, camOffset: Offset): Offset {
    val bs = baseScale(sizePx, mat)
    val baseOrigin = Offset(
        (sizePx.width - mat.widthMm * bs).toFloat() / 2f,
        (sizePx.height - mat.heightMm * bs).toFloat() / 2f,
    )
    return camOffset + baseOrigin * camScale
}

private fun worldToScreen(p: Pt, origin: Offset, ppm: Float): Offset =
    Offset(origin.x + (p.xMm * ppm).toFloat(), origin.y + (p.yMm * ppm).toFloat())

private fun screenToWorld(o: Offset, origin: Offset, ppm: Float): Pt =
    Pt(((o.x - origin.x) / ppm).toDouble(), ((o.y - origin.y) / ppm).toDouble())

private fun rotateHandlePos(corners: List<Offset>, center: Offset): Offset {
    val topMid = (corners[0] + corners[1]) / 2f
    val dir = topMid - center
    val len = dir.getDistance()
    val unit = if (len > 0f) dir / len else Offset(0f, -1f)
    return topMid + unit * ROTATE_ARM_PX
}

private fun hitTest(p: Offset, handles: List<Offset>, corners: List<Offset>, center: Offset): Drag {
    if (corners.size == 4 && handles.size == 8) {
        // Test all 8 handles; corners (0-3) win ties over sides (4-7) since they're checked first.
        handles.forEachIndexed { i, h -> if ((h - p).getDistance() < HANDLE_HIT_PX) return Drag.Resize(i) }
        if ((rotateHandlePos(corners, center) - p).getDistance() < HANDLE_HIT_PX) return Drag.Rotate
        if (inQuad(p, corners)) return Drag.Move
    }
    return Drag.PanCamera
}

/** Local handle points for a layer's bounds: 0-3 corners (TL,TR,BR,BL), 4-7 side midpoints (T,R,B,L). */
private fun handleLocalPoints(b: de.knutwurst.knutcut.svgcore.Bounds): List<Pt> = listOf(
    Pt(b.minX, b.minY), Pt(b.maxX, b.minY), Pt(b.maxX, b.maxY), Pt(b.minX, b.maxY),
    Pt((b.minX + b.maxX) / 2, b.minY), Pt(b.maxX, (b.minY + b.maxY) / 2),
    Pt((b.minX + b.maxX) / 2, b.maxY), Pt(b.minX, (b.minY + b.maxY) / 2),
)

/** World handle points from the 4 placed corners: 0-3 corners, 4-7 side midpoints (T,R,B,L). */
private fun handleWorldPoints(corners: List<Pt>): List<Pt> {
    if (corners.size != 4) return emptyList()
    fun mid(a: Pt, b: Pt) = Pt((a.xMm + b.xMm) / 2, (a.yMm + b.yMm) / 2)
    return listOf(
        corners[0], corners[1], corners[2], corners[3],
        mid(corners[0], corners[1]), mid(corners[1], corners[2]), mid(corners[2], corners[3]), mid(corners[3], corners[0]),
    )
}

/** The handle opposite [handle]: corners across the diagonal, sides across to the opposite edge. */
private fun anchorOf(handle: Int): Int = if (handle < 4) (handle + 2) % 4 else 4 + ((handle - 4 + 2) % 4)

private fun inQuad(p: Offset, q: List<Offset>): Boolean {
    var sign = 0
    for (i in 0 until 4) {
        val a = q[i]; val b = q[(i + 1) % 4]
        val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
        if (s != 0) { if (sign == 0) sign = s else if (sign != s) return false }
    }
    return true
}
