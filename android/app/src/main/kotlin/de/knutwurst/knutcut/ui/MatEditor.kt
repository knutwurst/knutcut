package de.knutwurst.knutcut.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import de.knutwurst.knutcut.data.Mat
import de.knutwurst.knutcut.svgcore.Pt
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

private sealed interface Drag {
    data class Resize(val corner: Int) : Drag
    object Rotate : Drag
    object Move : Drag
    object PanCamera : Drag
    object Camera : Drag
}

private const val HANDLE_HIT_PX = 34f
private const val ROTATE_ARM_PX = 44f

/**
 * The placement mat. Pinch or one-finger-drag on empty space moves/zooms the *work area* (like a
 * photo). The design is moved by dragging it, scaled with the corner handles, and turned with the
 * handle above it — the design itself never changes when you zoom the view.
 */
@Composable
fun MatEditor(vm: KnutcutViewModel, modifier: Modifier = Modifier) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    val gridMinor = Color(0x1AFFFFFF)
    val gridMajor = Color(0x40FFFFFF)
    val matColor = Color(0xFFBDBDBD)
    val matFill = Color(0x14FFFFFF)
    val designColor = MaterialTheme.colorScheme.primary
    val handleColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var ppm = ppmFor(sizePx, vm.mat, vm.camScale)
                if (ppm <= 0f) return@awaitEachGesture
                var origin = originFor(sizePx, vm.mat, vm.camScale, vm.camOffset)

                val corners = vm.placedCorners().map { worldToScreen(it, origin, ppm) }
                val centerScreen = worldToScreen(vm.centerMm, origin, ppm)
                var drag: Drag = hitTest(down.position, corners, centerScreen)

                val startScale = vm.scale
                val startRotation = vm.rotationDeg
                val startDist = hypot((down.position.x - centerScreen.x).toDouble(), (down.position.y - centerScreen.y).toDouble())
                val startAngle = atan2((down.position.y - centerScreen.y).toDouble(), (down.position.x - centerScreen.x).toDouble())

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
                        ppm = ppmFor(sizePx, vm.mat, vm.camScale)
                        origin = originFor(sizePx, vm.mat, vm.camScale, vm.camOffset)
                        val cs = worldToScreen(vm.centerMm, origin, ppm)
                        when (val d = drag) {
                            Drag.Camera, Drag.PanCamera -> vm.camOffset += p.positionChange()
                            Drag.Move -> {
                                val dp = p.positionChange()
                                vm.centerMm = Pt(vm.centerMm.xMm + dp.x / ppm, vm.centerMm.yMm + dp.y / ppm)
                            }
                            is Drag.Resize -> {
                                val curDist = hypot((p.position.x - cs.x).toDouble(), (p.position.y - cs.y).toDouble())
                                if (startDist > 1.0) vm.scale = (startScale * (curDist / startDist)).coerceIn(0.05, 50.0)
                            }
                            Drag.Rotate -> {
                                val a = atan2((p.position.y - cs.y).toDouble(), (p.position.x - cs.x).toDouble())
                                vm.rotationDeg = startRotation + Math.toDegrees(a - startAngle)
                            }
                        }
                        p.consume()
                    }
                }
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

        drawRulers(vm.mat, origin, ppm)

        // design
        for (pl in vm.placedPolylines()) {
            if (pl.points.isEmpty()) continue
            val path = Path()
            val f = s(pl.points.first()); path.moveTo(f.x, f.y)
            for (k in 1 until pl.points.size) { val q = s(pl.points[k]); path.lineTo(q.x, q.y) }
            if (pl.closed) path.close()
            drawPath(path, designColor, style = Stroke(width = 2.5f))
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
            corners.forEach { drawRect(handleColor, topLeft = Offset(it.x - 7f, it.y - 7f), size = Size(14f, 14f)) }
            val rot = rotateHandlePos(corners, s(vm.centerMm))
            val topMid = (corners[0] + corners[1]) / 2f
            drawLine(handleColor, topMid, rot, strokeWidth = 1.5f)
            drawCircle(handleColor, radius = 8f, center = rot)
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
private fun DrawScope.drawRulers(mat: Mat, origin: Offset, ppm: Float) {
    val paint = android.graphics.Paint().apply {
        color = 0xFFBDBDBD.toInt()
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

private fun rotateHandlePos(corners: List<Offset>, center: Offset): Offset {
    val topMid = (corners[0] + corners[1]) / 2f
    val dir = topMid - center
    val len = dir.getDistance()
    val unit = if (len > 0f) dir / len else Offset(0f, -1f)
    return topMid + unit * ROTATE_ARM_PX
}

private fun hitTest(p: Offset, corners: List<Offset>, center: Offset): Drag {
    if (corners.size == 4) {
        corners.forEachIndexed { i, c -> if ((c - p).getDistance() < HANDLE_HIT_PX) return Drag.Resize(i) }
        if ((rotateHandlePos(corners, center) - p).getDistance() < HANDLE_HIT_PX) return Drag.Rotate
        if (inQuad(p, corners)) return Drag.Move
    }
    return Drag.PanCamera
}

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
