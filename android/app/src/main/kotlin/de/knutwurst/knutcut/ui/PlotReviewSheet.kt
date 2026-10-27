package de.knutwurst.knutcut.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.knutwurst.knutcut.R
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.data.display
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import kotlin.math.max
import kotlin.math.min

/**
 * The last step before a plot. It shows the mat exactly as it will be plotted, lets you pick which
 * layers go (tap a row, or the shape itself in the preview), shows and switches each layer's tool,
 * and holds the copies setting and the Start button. Skipped layers stay in the preview as faint
 * dashed outlines, so it's obvious what is being left out.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlotReviewSheet(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // A new or replaced design (e.g. an import resolved while the review is open) would leave the
    // picks pointing at the wrong layers, so start over with all layers when the geometry changes.
    // Tool and visibility edits keep each layer's polylines, so they keep the picks.
    val designKey = vm.layers.map { System.identityHashCode(it.polylines) }
    var seenDesign by remember { mutableStateOf(designKey) }
    LaunchedEffect(designKey) {
        if (designKey != seenDesign) { seenDesign = designKey; vm.resetPlotSelection() }
    }
    val visible = vm.layers.indices.filter { vm.layers[it].visible }
    val hiddenCount = vm.layers.size - visible.size
    val ls = vm.cutLayers()
    val multi = visible.size > 1
    val withinMat = vm.designWithinMat()
    val knifeN = ls.count { it.tool == Tool.KNIFE }
    val penN = ls.size - knifeN
    val single = vm.cutSingleTool()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            // Header: title, material, and a visible way out besides swiping down.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_ready_to_plot), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.ui_material_label, vm.material.display()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.ui_close)) }
            }
            Spacer(Modifier.height(12.dp))

            // Everything between header and the Start button scrolls, so Start is always reachable.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                PlotPreview(vm, visible, multi)
                if (multi) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.ui_plot_review_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))

                // At-a-glance summary of what Start will do.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill(Icons.Default.Layers, pluralStringResource(R.plurals.ui_plot_n_of_m, visible.size, ls.size, visible.size))
                    vm.extentReadout(ls)?.let { StatPill(Icons.Default.Straighten, it) }
                    if (knifeN > 0 && penN > 0) {
                        StatPill(Icons.Default.ContentCut, pluralStringResource(R.plurals.ui_cut_n, knifeN, knifeN))
                        StatPill(Icons.Default.Edit, pluralStringResource(R.plurals.ui_draw_n, penN, penN))
                    }
                }

                if (!withinMat && ls.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.ui_design_off_area_warn), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_layers_heading), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (multi) {
                        TextButton(onClick = { vm.setPlotAll(true) }, enabled = ls.size < visible.size) { Text(stringResource(R.string.ui_select_all)) }
                        TextButton(onClick = { vm.setPlotAll(false) }, enabled = ls.isNotEmpty()) { Text(stringResource(R.string.ui_select_none)) }
                    }
                }
                visible.forEach { i -> PlotLayerRow(vm, i, selectable = multi) }
                if (hiddenCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pluralStringResource(R.plurals.ui_hidden_not_plotted, hiddenCount, hiddenCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_copies), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.ui_copies_hint), style = MaterialTheme.typography.bodySmall, color = muted)
                }
                EditableStepper(vm.cutCopies, 1, 10, step = 1) { vm.changeCutCopies(it) }
            }
            Spacer(Modifier.height(12.dp))
            val verb = when (single) {
                Tool.KNIFE -> stringResource(R.string.ui_cut)
                Tool.PEN -> stringResource(R.string.ui_draw)
                else -> stringResource(R.string.ui_plot)
            }
            Button(
                // cut() captures the layer set before the sheet closes and resets the selection.
                onClick = { vm.cut(); onDismiss() },
                enabled = withinMat && ls.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(if (single == Tool.PEN) Icons.Default.Edit else Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ui_start_label, verb) +
                        if (multi && ls.isNotEmpty()) " · " + pluralStringResource(R.plurals.ui_layers_count, ls.size, ls.size) else "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ls.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.ui_pick_a_layer),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The whole mat, scaled to fit, with included layers drawn as they'll be plotted and skipped ones ghosted. */
@Composable
private fun PlotPreview(vm: KnutcutViewModel, visible: List<Int>, selectable: Boolean) {
    val mat = vm.mat
    val aspect = (mat.widthMm / mat.heightMm).toFloat().coerceIn(0.2f, 5f)
    val knifeColor = MaterialTheme.colorScheme.primary
    val penColor = MaterialTheme.colorScheme.secondary
    val offMatColor = MaterialTheme.colorScheme.error
    val ghost = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val border = MaterialTheme.colorScheme.outline
    val placed = visible.mapNotNull { i -> vm.placedLayerAt(i)?.let { i to it } }
    val desc = stringResource(R.string.ui_plot_preview_desc) + ": " +
        pluralStringResource(R.plurals.ui_plot_n_of_m, visible.size, vm.cutLayers().size, visible.size)

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .heightIn(max = 300.dp)
                .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (selectable) Modifier.pointerInput(mat) {
                        detectTapGestures { o ->
                            val ppm = size.width / mat.widthMm
                            val i = vm.layerAt(Pt(o.x / ppm, o.y / ppm))
                            if (i >= 0) vm.togglePlotLayer(i)
                        }
                    } else Modifier,
                )
                .semantics { contentDescription = desc },
        ) {
            val ppm = (size.width / mat.widthMm).toFloat()
            // Faint one-inch grid, like the printed grid on the real mat.
            val step = 25.4f * ppm
            var x = step
            while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = step
            while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }

            // Skipped layers first (underneath), then the ones that will be plotted on top.
            val (inc, skip) = placed.partition { (i, _) -> vm.isPlotIncluded(i) }
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            skip.forEach { (_, pl) ->
                pl.polylines.forEach { drawPath(pathOf(it) { p -> Offset(p.xMm.toFloat() * ppm, p.yMm.toFloat() * ppm) }, ghost, style = Stroke(1.dp.toPx(), pathEffect = dash)) }
            }
            inc.forEach { (_, pl) ->
                val toolColor = if (pl.tool == Tool.PEN) penColor else knifeColor
                drawPlaced(pl, toolColor, offMatColor, 1.6.dp.toPx(), { vm.isOutsideMat(it) }) { p ->
                    Offset(p.xMm.toFloat() * ppm, p.yMm.toFloat() * ppm)
                }
            }
            drawRect(border, style = Stroke(1.dp.toPx()))
        }
    }
}

/** One layer in the review list: include toggle (the whole row), thumbnail, name + size, tool switch. */
@Composable
private fun PlotLayerRow(vm: KnutcutViewModel, i: Int, selectable: Boolean) {
    val layer = vm.layers.getOrNull(i) ?: return
    val included = vm.isPlotIncluded(i)
    val contentAlpha by animateFloatAsState(if (included) 1f else 0.45f, tween(200), label = "rowAlpha")
    val toolColor = if (layer.tool == Tool.PEN) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (selectable) Modifier.toggleable(value = included, role = Role.Checkbox, onValueChange = { vm.togglePlotLayer(i) }) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectable) {
            // The row carries the toggle; the box is only its visual state.
            Checkbox(checked = included, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
        }
        LayerThumb(vm.placedLayerAt(i), toolColor, Modifier.alpha(contentAlpha))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).alpha(contentAlpha)) {
            Text(layer.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            vm.extentReadout(listOf(layer))?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        val knife = layer.tool == Tool.KNIFE
        val toolLabel = stringResource(if (knife) R.string.ui_cut else R.string.ui_draw)
        val toolDesc = stringResource(R.string.ui_switch_tool_desc, toolLabel)
        AssistChip(
            onClick = { vm.setLayerTool(i, if (knife) Tool.PEN else Tool.KNIFE) },
            label = { Text(toolLabel) },
            leadingIcon = {
                Icon(if (knife) Icons.Default.ContentCut else Icons.Default.Edit, contentDescription = null, tint = toolColor, modifier = Modifier.size(18.dp))
            },
            modifier = Modifier.semantics { contentDescription = toolDesc },
        )
    }
}

/** A small picture of one layer, fitted into a rounded tile. */
@Composable
private fun LayerThumb(pl: KnutcutViewModel.PlacedLayer?, toolColor: Color, modifier: Modifier = Modifier) {
    val offMat = MaterialTheme.colorScheme.error
    Canvas(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val pts = pl?.polylines?.flatMap { it.points }.orEmpty()
        if (pts.isEmpty()) return@Canvas
        val minX = pts.minOf { it.xMm }; val maxX = pts.maxOf { it.xMm }
        val minY = pts.minOf { it.yMm }; val maxY = pts.maxOf { it.yMm }
        val pad = 6.dp.toPx()
        val bw = max(maxX - minX, 1e-6); val bh = max(maxY - minY, 1e-6)
        val s = min((size.width - 2 * pad) / bw, (size.height - 2 * pad) / bh).toFloat()
        val ox = (size.width - (bw * s).toFloat()) / 2f
        val oy = (size.height - (bh * s).toFloat()) / 2f
        // The thumbnail only shows the shape; off-mat warnings belong to the big preview.
        drawPlaced(pl!!, toolColor, offMat, 1.2.dp.toPx(), { false }) { p ->
            Offset(ox + ((p.xMm - minX) * s).toFloat(), oy + ((p.yMm - minY) * s).toFloat())
        }
    }
}

@Composable
private fun StatPill(icon: ImageVector, text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Draw a placed layer: its own color as a light fill (holes kept), cut lines in the tool color on top. */
private fun DrawScope.drawPlaced(
    pl: KnutcutViewModel.PlacedLayer,
    toolColor: Color,
    offMatColor: Color,
    strokePx: Float,
    isOff: (Pt) -> Boolean,
    map: (Pt) -> Offset,
) {
    val paths = pl.polylines.map { pathOf(it, map) }
    for (g in pl.fillGroups) {
        val c = pl.colors.getOrNull(g.first()) ?: continue
        val group = Path().apply { fillType = PathFillType.EvenOdd; g.forEach { addPath(paths[it]) } }
        drawPath(group, Color(c).copy(alpha = 0.35f), style = Fill)
    }
    paths.forEachIndexed { k, p ->
        val off = pl.polylines[k].points.any(isOff)
        drawPath(p, if (off) offMatColor else toolColor, style = Stroke(strokePx))
    }
}

private fun pathOf(pl: Polyline, map: (Pt) -> Offset): Path = Path().apply {
    if (pl.points.isEmpty()) return@apply
    val f = map(pl.points.first()); moveTo(f.x, f.y)
    for (k in 1 until pl.points.size) { val q = map(pl.points[k]); lineTo(q.x, q.y) }
    if (pl.closed) close()
}
