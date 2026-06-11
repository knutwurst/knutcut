package de.knutwurst.knutcut.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.knutwurst.knutcut.R

// ---------------------------------------------------------------------------
// Pure colour helpers (no Android types — unit-tested in ColorHexTest).
// ---------------------------------------------------------------------------

/** Parse "#RRGGBB", "RRGGBB" or "#AARRGGBB" into a packed ARGB int, or null when invalid.
 *  A 6-digit value is treated as fully opaque. */
fun hexToArgb(text: String): Int? {
    val h = text.trim().removePrefix("#")
    if (h.isEmpty() || !h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return when (h.length) {
        6 -> 0xFF000000.toInt() or h.toInt(16)
        8 -> h.toLong(16).toInt()
        else -> null
    }
}

/** Format the RGB part of a packed ARGB int as an uppercase "#RRGGBB". */
fun argbToHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/** Curated display palette (packed ARGB). One row of neutrals, one of vivid hues. */
private val PALETTE: List<Int> = listOf(
    0xFF000000, 0xFF616161, 0xFF9E9E9E, 0xFFE0E0E0, 0xFFFFFFFF, 0xFF795548, 0xFF6D4C41, 0xFF3E2723,
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1, 0xFF00897B, 0xFF43A047,
    0xFF7CB342, 0xFFFDD835, 0xFFFB8C00, 0xFFF4511E,
).map { it.toInt() }

/**
 * Bottom-sheet colour picker for the selected layer. [current] is the layer's current display colour
 * (null = none / tool default). [onPick] receives the chosen ARGB, or null for "no colour".
 *
 * A curated swatch palette covers the common case (tap = apply); an expandable custom section offers
 * a full hue + saturation/value picker with a hex field for an exact colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerColorSheet(current: Int?, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.ui_layer_color), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            // "No colour" resets to the tool default.
            OutlinedButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_no_color))
            }
            Spacer(Modifier.height(12.dp))

            // Swatch palette — tap applies immediately.
            PALETTE.chunked(8).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { argb -> Swatch(argb, selected = argb == current) { onPick(argb) } }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Custom HSV picker, collapsed by default.
            var expanded by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.ui_custom_color), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (expanded) CustomColorPicker(current) { onPick(it) }
        }
    }
}

@Composable
private fun Swatch(argb: Int, selected: Boolean, onClick: () -> Unit) {
    val color = Color(argb)
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 1.dp, if (selected) ring else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            // Tick in a contrasting colour so it shows on light and dark swatches alike.
            val lum = (0.299 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.114 * (argb and 0xFF)) / 255.0
            Icon(Icons.Default.Check, contentDescription = null, tint = if (lum > 0.6) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CustomColorPicker(current: Int?, onApply: (Int) -> Unit) {
    val start = current ?: 0xFF1E88E5.toInt()
    val initHsv = remember { rgbToHsv(start) }
    var hue by remember { mutableStateOf(initHsv[0]) }
    var sat by remember { mutableStateOf(initHsv[1]) }
    var value by remember { mutableStateOf(initHsv[2]) }
    var hex by remember { mutableStateOf(argbToHex(start)) }

    val color = Color.hsv(hue, sat, value)
    val argb = color.toArgb()
    // Keep the hex field in sync while the user drags the wheel/square.
    fun syncHexFromHsv() { hex = argbToHex(argb) }

    // Saturation/value square for the current hue.
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    sat = (off.x / size.width).coerceIn(0f, 1f)
                    value = (1f - off.y / size.height).coerceIn(0f, 1f)
                    syncHexFromHsv()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    sat = (change.position.x / size.width).coerceIn(0f, 1f)
                    value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                    syncHexFromHsv()
                    change.consume()
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cx = sat * size.width
        val cy = (1f - value) * size.height
        drawCircle(Color.White, radius = 11f, center = Offset(cx, cy), style = Stroke(width = 3f))
        drawCircle(Color.Black, radius = 13f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
    }
    Spacer(Modifier.height(10.dp))

    // Hue bar.
    val hueStops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it, 1f, 1f) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .pointerInput(Unit) {
                detectTapGestures { off -> hue = (off.x / size.width).coerceIn(0f, 1f) * 360f; syncHexFromHsv() }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f; syncHexFromHsv(); change.consume() }
            },
    ) {
        drawRect(Brush.horizontalGradient(hueStops))
        val x = hue / 360f * size.width
        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f)
        drawLine(Color.Black, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
    }
    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)))
        OutlinedTextField(
            value = hex,
            onValueChange = { s ->
                hex = s
                hexToArgb(s)?.let { parsed ->
                    val h = rgbToHsv(parsed); hue = h[0]; sat = h[1]; value = h[2]
                }
            },
            label = { Text(stringResource(R.string.ui_hex)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii,
            ),
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { onApply(argb) }) { Text(stringResource(R.string.ui_apply)) }
    }
}

/** RGB (packed ARGB, alpha ignored) → HSV [hue 0..360, sat 0..1, value 0..1]. */
private fun rgbToHsv(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (max == 0f) 0f else d / max
    return floatArrayOf(h, s, max)
}
