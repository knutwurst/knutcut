package de.knutwurst.knutcut.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import de.knutwurst.knutcut.data.PlotterModel
import de.knutwurst.knutcut.svgcore.Pt
import kotlin.math.min

/** The placement mat with the design drawn on it. Drag to move, pinch to scale, twist to rotate. */
@Composable
fun MatEditor(vm: KnutcutViewModel, modifier: Modifier = Modifier) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val matColor = Color(0xFF9E9E9E)
    val matFill = Color(0x11000000)
    val designColor = androidx.compose.material3.MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, rotation ->
                if (!vm.hasDesign) return@detectTransformGestures
                val s = matScaleFor(sizePx, vm.model)
                if (s > 0f) {
                    vm.centerMm = Pt(vm.centerMm.xMm + pan.x / s, vm.centerMm.yMm + pan.y / s)
                }
                if (zoom != 1f) vm.scale = (vm.scale * zoom).coerceIn(0.05, 50.0)
                if (rotation != 0f) vm.rotationDeg = vm.rotationDeg + rotation
            }
        },
    ) {
        sizePx = IntSize(size.width.toInt(), size.height.toInt())
        val matScale = matScaleFor(sizePx, vm.model)
        if (matScale <= 0f) return@Canvas
        val matW = (vm.model.matWidthMm * matScale).toFloat()
        val matH = (vm.model.matHeightMm * matScale).toFloat()
        val ox = (size.width - matW) / 2f
        val oy = (size.height - matH) / 2f

        // mat
        drawRect(matFill, topLeft = Offset(ox, oy), size = androidx.compose.ui.geometry.Size(matW, matH))
        drawRect(matColor, topLeft = Offset(ox, oy), size = androidx.compose.ui.geometry.Size(matW, matH), style = Stroke(width = 2f))

        // design
        fun toPx(p: Pt) = Offset(ox + (p.xMm * matScale).toFloat(), oy + (p.yMm * matScale).toFloat())
        for (pl in vm.placedPolylines()) {
            if (pl.points.isEmpty()) continue
            val path = Path()
            val first = toPx(pl.points.first())
            path.moveTo(first.x, first.y)
            for (k in 1 until pl.points.size) {
                val q = toPx(pl.points[k]); path.lineTo(q.x, q.y)
            }
            if (pl.closed) path.close()
            drawPath(path, designColor, style = Stroke(width = 2.5f))
        }
    }
}

private fun matScaleFor(sizePx: IntSize, model: PlotterModel): Float {
    if (sizePx.width == 0 || sizePx.height == 0) return 0f
    val pad = 0.92f
    return min(sizePx.width / model.matWidthMm, sizePx.height / model.matHeightMm).toFloat() * pad
}
