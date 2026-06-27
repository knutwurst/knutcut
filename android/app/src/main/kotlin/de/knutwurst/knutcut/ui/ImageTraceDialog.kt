package de.knutwurst.knutcut.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.knutwurst.knutcut.R
import de.knutwurst.knutcut.svgcore.TraceParams
import de.knutwurst.knutcut.svgcore.TraceResult
import kotlin.math.roundToInt

/**
 * The image-trace dialog: a live posterised preview of the picked raster image plus the four knobs
 * (colours, drop background, simplify, speckle). Every change calls [KnutcutViewModel.updateTraceParams],
 * which recomputes the trace off-thread (debounced). "Add" turns the result into coloured layers.
 */
@Composable
fun ImageTraceDialog(vm: KnutcutViewModel) {
    if (!vm.imageDecoding && vm.imageTraceSource == null) return

    val result = vm.imageTraceResult
    val params = vm.imageTraceParams
    val pathCount = result?.colors?.sumOf { it.contours.size } ?: 0
    val colorCount = result?.colors?.size ?: 0

    AlertDialog(
        onDismissRequest = { vm.cancelImageTrace() },
        title = { Text(stringResource(R.string.ui_trace_title)) },
        text = {
            Column {
                TracePreview(result)
                Spacer(Modifier.height(10.dp))

                // Summary + a spinner while a recompute is in flight.
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

                LabeledSlider(
                    label = stringResource(R.string.ui_trace_colors),
                    value = "${params.numColors}",
                    sliderValue = params.numColors.toFloat(),
                    range = 2f..12f, steps = 9,
                ) { vm.updateTraceParams(params.copy(numColors = it.roundToInt())) }

                LabeledSlider(
                    label = stringResource(R.string.ui_trace_simplify),
                    value = String.format("%.1f mm", params.detailMm),
                    sliderValue = params.detailMm.toFloat(),
                    range = 0.1f..2f, steps = 0,
                ) { vm.updateTraceParams(params.copy(detailMm = it.toDouble())) }

                LabeledSlider(
                    label = stringResource(R.string.ui_trace_speckle),
                    value = "${params.minAreaMm2.roundToInt()} mm²",
                    sliderValue = params.minAreaMm2.toFloat(),
                    range = 0f..30f, steps = 0,
                ) { vm.updateTraceParams(params.copy(minAreaMm2 = it.toDouble())) }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui_trace_drop_bg), Modifier.weight(1f))
                    Switch(checked = params.dropBackground, onCheckedChange = { vm.updateTraceParams(params.copy(dropBackground = it)) })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = colorCount > 0 && !vm.imageTraceComputing,
                onClick = { vm.confirmImageTrace() },
            ) { Text(stringResource(R.string.ui_add)) }
        },
        dismissButton = { TextButton(onClick = { vm.cancelImageTrace() }) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

/** The posterised preview; transparent/background pixels show the surface behind them. */
@Composable
private fun TracePreview(result: TraceResult?) {
    val image = remember(result) {
        result?.takeIf { it.width > 0 && it.height > 0 }?.let { r ->
            val px = IntArray(r.width * r.height) { i ->
                val idx = r.indexMap[i]
                if (idx < 0 || idx == r.backgroundIndex) 0 else r.palette[idx] // 0 = transparent
            }
            Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888)
                .apply { setPixels(px, 0, r.width, 0, 0, r.width, r.height) }
                .asImageBitmap()
        }
    }
    Box(
        Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(200.dp).padding(4.dp))
        } else {
            CircularProgressIndicator()
        }
    }
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
