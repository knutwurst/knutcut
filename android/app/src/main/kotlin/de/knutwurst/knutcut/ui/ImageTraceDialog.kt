package de.knutwurst.knutcut.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.knutwurst.knutcut.R
import de.knutwurst.knutcut.svgcore.CropRect
import de.knutwurst.knutcut.svgcore.RasterImage
import de.knutwurst.knutcut.svgcore.TraceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The image-trace dialog: a posterised preview with a draggable crop rectangle (to isolate one
 * object) plus the four knobs (colours, drop background, simplify, speckle). Every change calls
 * [KnutcutViewModel.updateTraceParams], which recomputes the trace off-thread (debounced).
 */
@Composable
fun ImageTraceDialog(vm: KnutcutViewModel) {
    val source = vm.imageTraceSource
    if (!vm.imageDecoding && source == null) return

    val result = vm.imageTraceResult
    val params = vm.imageTraceParams
    val pathCount = result?.colors?.sumOf { it.contours.size } ?: 0
    val colorCount = result?.colors?.size ?: 0

    AlertDialog(
        onDismissRequest = { vm.cancelImageTrace() },
        title = { Text(stringResource(R.string.ui_trace_title)) },
        text = {
            Column {
                if (source != null) {
                    CropPreview(source, result) { vm.updateTraceParams(vm.imageTraceParams.copy(crop = it)) }
                } else {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (result == null || colorCount == 0) stringResource(R.string.ui_trace_empty)
                        else stringResource(R.string.ui_trace_summary, colorCount, pathCount),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (vm.imageDecoding || vm.imageTraceComputing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.height(8.dp))

                LabeledSlider(stringResource(R.string.ui_trace_colors), "${params.numColors}", params.numColors.toFloat(), 2f..12f, 9) {
                    vm.updateTraceParams(params.copy(numColors = it.roundToInt()))
                }
                LabeledSlider(stringResource(R.string.ui_trace_simplify), String.format("%.1f mm", params.detailMm), params.detailMm.toFloat(), 0.1f..2f, 0) {
                    vm.updateTraceParams(params.copy(detailMm = it.toDouble()))
                }
                LabeledSlider(stringResource(R.string.ui_trace_speckle), "${params.minAreaMm2.roundToInt()} mm²", params.minAreaMm2.toFloat(), 0f..30f, 0) {
                    vm.updateTraceParams(params.copy(minAreaMm2 = it.toDouble()))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui_trace_drop_bg), Modifier.weight(1f))
                    Switch(checked = params.dropBackground, onCheckedChange = { vm.updateTraceParams(params.copy(dropBackground = it)) })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = colorCount > 0 && !vm.imageTraceComputing, onClick = { vm.confirmImageTrace() }) {
                Text(stringResource(R.string.ui_add))
            }
        },
        dismissButton = { TextButton(onClick = { vm.cancelImageTrace() }) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

/** Four floats describing a crop rectangle in source-pixel coordinates (float for smooth dragging). */
private class CropF(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * The preview: the original image with a dimmed surround, a draggable/resizable crop rectangle, and
 * the posterised result drawn crisp inside the crop (the original shows through while you drag).
 */
@Composable
private fun CropPreview(source: RasterImage, result: TraceResult?, onCrop: (CropRect) -> Unit) {
    val original = remember(source) { source.toImageBitmap() }
    // Build the posterised preview off the main thread; the Canvas shows the previous one until ready.
    val poster by produceState<ImageBitmap?>(null, result) {
        value = withContext(Dispatchers.Default) { result?.toPreviewBitmap() }
    }

    var crop by remember(source) { mutableStateOf(CropF(0f, 0f, source.width.toFloat(), source.height.toFloat())) }
    var dragging by remember(source) { mutableStateOf(false) }
    val handleR = with(LocalDensity.current) { 13.dp.toPx() }

    val scrim = Color.Black.copy(alpha = 0.5f)
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val minPx = 8f

    Box(Modifier.fillMaxWidth().height(220.dp).background(neutral)) {
        Canvas(
            Modifier.fillMaxWidth().height(220.dp).pointerInput(source) {
                var mode = HitNone
                detectDragGestures(
                    onDragStart = { pos ->
                        val d = fitRect(size.width.toFloat(), size.height.toFloat(), source.width, source.height)
                        mode = hitTest(pos, crop, d, handleR)
                        dragging = mode != HitNone
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, delta ->
                        if (mode == HitNone) return@detectDragGestures
                        change.consume()
                        val d = fitRect(size.width.toFloat(), size.height.toFloat(), source.width, source.height)
                        val dx = delta.x / d.scale; val dy = delta.y / d.scale
                        crop = applyDrag(crop, mode, dx, dy, source.width.toFloat(), source.height.toFloat(), minPx)
                        onCrop(CropRect(crop.x.roundToInt(), crop.y.roundToInt(), crop.w.roundToInt().coerceAtLeast(1), crop.h.roundToInt().coerceAtLeast(1)))
                    },
                )
            },
        ) {
            val d = fitRect(size.width, size.height, source.width, source.height)
            // Original as context.
            drawImage(original, dstOffset = IntOffset(d.ox.roundToInt(), d.oy.roundToInt()), dstSize = IntSize((source.width * d.scale).roundToInt(), (source.height * d.scale).roundToInt()))
            val cl = d.ox + crop.x * d.scale; val ct = d.oy + crop.y * d.scale
            val cw = crop.w * d.scale; val ch = crop.h * d.scale
            // Dim everything outside the crop.
            drawRect(scrim, topLeft = Offset(d.ox, d.oy), size = Size(d.w, ct - d.oy))
            drawRect(scrim, topLeft = Offset(d.ox, ct + ch), size = Size(d.w, d.oy + d.h - (ct + ch)))
            drawRect(scrim, topLeft = Offset(d.ox, ct), size = Size(cl - d.ox, ch))
            drawRect(scrim, topLeft = Offset(cl + cw, ct), size = Size(d.ox + d.w - (cl + cw), ch))
            // Crisp posterised result inside the crop (skip while dragging — it lags the rectangle).
            val posterBmp = poster
            if (!dragging && posterBmp != null) {
                drawRect(neutral, topLeft = Offset(cl, ct), size = Size(cw, ch))
                drawImage(posterBmp, dstOffset = IntOffset(cl.roundToInt(), ct.roundToInt()), dstSize = IntSize(cw.roundToInt().coerceAtLeast(1), ch.roundToInt().coerceAtLeast(1)))
            }
            // Crop border + corner handles.
            drawRect(accent, topLeft = Offset(cl, ct), size = Size(cw, ch), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            for (h in listOf(Offset(cl, ct), Offset(cl + cw, ct), Offset(cl + cw, ct + ch), Offset(cl, ct + ch))) {
                drawCircle(accent, radius = handleR * 0.6f, center = h)
            }
        }
    }
}

// Crop drag modes.
private const val HitNone = -1
private const val HitMove = 4

private class FitRect(val ox: Float, val oy: Float, val w: Float, val h: Float, val scale: Float)

/** Uniform-fit (letterbox) the image into a canvas of the given size. */
private fun fitRect(cw: Float, ch: Float, iw: Int, ih: Int): FitRect {
    val scale = min(cw / iw, ch / ih)
    val w = iw * scale; val h = ih * scale
    return FitRect((cw - w) / 2f, (ch - h) / 2f, w, h, scale)
}

/** Which crop part a touch hit: a corner (0..3), the body ([HitMove]), or nothing ([HitNone]). */
private fun hitTest(pos: Offset, crop: CropF, d: FitRect, handleR: Float): Int {
    val cl = d.ox + crop.x * d.scale; val ct = d.oy + crop.y * d.scale
    val cw = crop.w * d.scale; val ch = crop.h * d.scale
    val corners = listOf(Offset(cl, ct), Offset(cl + cw, ct), Offset(cl + cw, ct + ch), Offset(cl, ct + ch))
    corners.forEachIndexed { i, c -> if ((pos - c).getDistance() <= handleR * 1.6f) return i }
    return if (pos.x in cl..(cl + cw) && pos.y in ct..(ct + ch)) HitMove else HitNone
}

/** Apply a source-pixel drag delta to the crop, respecting image bounds and a minimum size. */
private fun applyDrag(c: CropF, mode: Int, dx: Float, dy: Float, iw: Float, ih: Float, minPx: Float): CropF {
    val right = c.x + c.w; val bottom = c.y + c.h
    // Safe clamps: keep the coerce range ordered even when the image is smaller than minPx (else
    // coerceIn(min > max) throws and crashes the gesture). lo() drags a min edge, hi() a max edge.
    fun lo(v: Float, upper: Float) = v.coerceIn(0f, maxOf(0f, upper))
    fun hi(v: Float, lower: Float, max: Float) = v.coerceIn(minOf(max, lower), max)
    return when (mode) {
        0 -> { val l = lo(c.x + dx, right - minPx); val t = lo(c.y + dy, bottom - minPx); CropF(l, t, right - l, bottom - t) }
        1 -> { val r = hi(right + dx, c.x + minPx, iw); val t = lo(c.y + dy, bottom - minPx); CropF(c.x, t, r - c.x, bottom - t) }
        2 -> { val r = hi(right + dx, c.x + minPx, iw); val b = hi(bottom + dy, c.y + minPx, ih); CropF(c.x, c.y, r - c.x, b - c.y) }
        3 -> { val l = lo(c.x + dx, right - minPx); val b = hi(bottom + dy, c.y + minPx, ih); CropF(l, c.y, right - l, b - c.y) }
        HitMove -> { val x = (c.x + dx).coerceIn(0f, maxOf(0f, iw - c.w)); val y = (c.y + dy).coerceIn(0f, maxOf(0f, ih - c.h)); CropF(x, y, c.w, c.h) }
        else -> c
    }
}

private fun RasterImage.toImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        .apply { setPixels(pixels, 0, width, 0, 0, width, height) }
        .asImageBitmap()

private fun TraceResult.toPreviewBitmap(): ImageBitmap? {
    if (width <= 0 || height <= 0) return null
    val px = IntArray(width * height) { i ->
        val idx = indexMap[i]
        if (idx < 0 || idx == backgroundIndex) 0 else palette[idx] // 0 = transparent (dropped)
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        .apply { setPixels(px, 0, width, 0, 0, width, height) }
        .asImageBitmap()
}

@Composable
private fun LabeledSlider(
    label: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = sliderValue.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}
