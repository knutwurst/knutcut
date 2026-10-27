package de.knutwurst.knutcut.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.knutwurst.knutcut.R
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.data.display
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the plot sheet is showing: picking layers, a running plot, or how the plot ended. */
private enum class PlotPhase { REVIEW, RUNNING, FINISHED }

/**
 * The plot sheet. It starts as the review — the mat exactly as it will be plotted, a list to pick
 * the layers, each layer's tool, copies and Start — and then stays open for the whole plot: the
 * preview traces the cut paths live in the order they go to the machine, with a large progress bar
 * and the machine's current step, until a finish screen says how it ended. It can't be swiped away
 * while a plot runs; the app shouldn't be used then, and a cancel button is always at hand.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlotReviewSheet(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    val phase = when {
        vm.cutting -> PlotPhase.RUNNING
        vm.cutOutcome != null -> PlotPhase.FINISHED
        else -> PlotPhase.REVIEW
    }
    // While a plot runs the sheet refuses to hide, so swipe, back and the scrim can't close it.
    // The rule must be one remembered instance: Material3 keys the sheet state on it, and a fresh
    // lambda per recomposition (every progress frame) created a new, hidden sheet each time, so the
    // sheet kept sliding back in and out during the plot.
    val refuseHideWhileCutting = remember(vm) { { v: SheetValue -> v != SheetValue.Hidden || !vm.cutting } }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = refuseHideWhileCutting,
    )
    // A new or replaced design (e.g. an import resolved while the review is open) would leave the
    // picks pointing at the wrong layers, so start over with all layers when the geometry changes.
    // Tool and visibility edits keep each layer's polylines, so they keep the picks.
    val designKey = vm.layers.map { System.identityHashCode(it.polylines) }
    var seenDesign by remember { mutableStateOf(designKey) }
    LaunchedEffect(designKey) {
        if (designKey != seenDesign) { seenDesign = designKey; vm.resetPlotSelection() }
    }
    // Keep the screen on for the whole plot.
    val view = LocalView.current
    DisposableEffect(vm.cutting) {
        view.keepScreenOn = vm.cutting
        onDispose { view.keepScreenOn = false }
    }
    // One short buzz when a plot finishes well.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(vm.cutOutcome) {
        if (vm.cutOutcome == KnutcutViewModel.CutOutcome.SUCCESS) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Smooth the chunk-by-chunk progress, but jump straight back to 0 when a new plot starts.
    val progress = remember { Animatable(vm.progress) }
    LaunchedEffect(vm.progress) {
        if (vm.progress < progress.value) progress.snapTo(vm.progress)
        else progress.animateTo(vm.progress, tween(350, easing = LinearEasing))
    }
    var startFailed by remember { mutableStateOf(false) }

    val visible = vm.layers.indices.filter { vm.layers[it].visible }
    val multi = visible.size > 1
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(onDismissRequest = { if (!vm.cutting) onDismiss() }, sheetState = sheetState) {
        // Full height in every phase, so the sheet doesn't jump when the plot starts or ends.
        Column(Modifier.fillMaxHeight().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetHeader(vm, phase, onClose = onDismiss)
            Spacer(Modifier.height(12.dp))

            // Everything between header and the bottom buttons scrolls, so they're always reachable.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).animateContentSize()) {
                val passes = vm.cutCopies.coerceAtLeast(1)
                val trace = when (phase) {
                    PlotPhase.REVIEW -> null
                    PlotPhase.RUNNING -> {
                        val p = progress.value
                        val pp = p * passes
                        val pass = min(floor(pp).toInt(), passes - 1)
                        TraceState(
                            fraction = (pp - pass).coerceIn(0f, 1f),
                            head = p > 0f && p < 1f,
                            waiting = p <= 0f,
                            success = false,
                        )
                    }
                    PlotPhase.FINISHED -> {
                        val ok = vm.cutOutcome == KnutcutViewModel.CutOutcome.SUCCESS
                        TraceState(fraction = if (ok) 1f else (vm.progress * passes).let { it - floor(it).coerceAtMost(passes - 1f) }.coerceIn(0f, 1f), head = false, waiting = false, success = ok)
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PlotPreview(
                        vm, visible, selectable = multi && phase == PlotPhase.REVIEW, trace = trace,
                        maxHeight = if (phase == PlotPhase.REVIEW) 300.dp else 440.dp,
                    )
                    if (phase == PlotPhase.FINISHED) OutcomeBadge(vm.cutOutcome == KnutcutViewModel.CutOutcome.SUCCESS)
                }
                when (phase) {
                    PlotPhase.REVIEW -> ReviewBody(vm, visible, multi, startFailed)
                    PlotPhase.RUNNING -> RunningBody(vm, progress.value, passes)
                    PlotPhase.FINISHED -> FinishedBody(vm)
                }
            }

            when (phase) {
                PlotPhase.REVIEW -> {
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
                    val ls = vm.cutLayers()
                    val single = vm.cutSingleTool()
                    val verb = when (single) {
                        Tool.KNIFE -> stringResource(R.string.ui_cut)
                        Tool.PEN -> stringResource(R.string.ui_draw)
                        else -> stringResource(R.string.ui_plot)
                    }
                    Button(
                        // The sheet stays open: it turns into the live view of the plot. If cut()
                        // refuses (not connected, …), say why right here.
                        onClick = { vm.cut(); startFailed = !vm.cutting },
                        enabled = vm.designWithinMat() && ls.isNotEmpty(),
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
                PlotPhase.RUNNING -> {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { vm.cancelCut() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.ui_cancel_plot)) }
                }
                PlotPhase.FINISHED -> {
                    Spacer(Modifier.height(12.dp))
                    if (vm.cutOutcome == KnutcutViewModel.CutOutcome.SUCCESS) {
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.ui_close)) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { vm.clearCutOutcome(); startFailed = false }, modifier = Modifier.weight(1f).height(52.dp)) {
                                Text(stringResource(R.string.ui_back_to_review))
                            }
                            Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.ui_close)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(vm: KnutcutViewModel, phase: PlotPhase, onClose: () -> Unit) {
    val title = when (phase) {
        PlotPhase.REVIEW -> stringResource(R.string.ui_ready_to_plot)
        PlotPhase.RUNNING -> stringResource(R.string.ui_plotting)
        PlotPhase.FINISHED -> when (vm.cutOutcome) {
            KnutcutViewModel.CutOutcome.SUCCESS -> stringResource(R.string.ui_plot_done_title)
            KnutcutViewModel.CutOutcome.CANCELED -> stringResource(R.string.ui_plot_canceled_title)
            else -> stringResource(R.string.ui_plot_failed_title)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.ui_material_label, vm.material.display()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // No way out mid-plot except the cancel button.
        if (phase != PlotPhase.RUNNING) IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.ui_close)) }
    }
}

/** The picking part: hint, summary, warnings and the layer list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewBody(vm: KnutcutViewModel, visible: List<Int>, multi: Boolean, startFailed: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ls = vm.cutLayers()
    val knifeN = ls.count { it.tool == Tool.KNIFE }
    val penN = ls.size - knifeN
    val hiddenCount = vm.layers.size - visible.size
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatPill(Icons.Default.Layers, pluralStringResource(R.plurals.ui_plot_n_of_m, visible.size, ls.size, visible.size))
        vm.extentReadout(ls)?.let { StatPill(Icons.Default.Straighten, it) }
        if (knifeN > 0 && penN > 0) {
            StatPill(Icons.Default.ContentCut, pluralStringResource(R.plurals.ui_cut_n, knifeN, knifeN))
            StatPill(Icons.Default.Edit, pluralStringResource(R.plurals.ui_draw_n, penN, penN))
        }
    }
    if (!vm.designWithinMat() && ls.isNotEmpty()) WarningBanner(stringResource(R.string.ui_design_off_area_warn))
    if (startFailed) vm.status?.let { WarningBanner(it) }

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
        Text(pluralStringResource(R.plurals.ui_hidden_not_plotted, hiddenCount, hiddenCount), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    Spacer(Modifier.height(8.dp))
}

/** The live part of a running plot: what the machine is doing, a big percentage and the bar. */
@Composable
private fun RunningBody(vm: KnutcutViewModel, progress: Float, passes: Int) {
    val waiting = progress <= 0f      // setup, feed and start key
    val draining = progress >= 1f     // everything sent; the machine is finishing its buffer
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            vm.status ?: stringResource(R.string.ui_plotting),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (!waiting) {
            Text(
                stringResource(R.string.ui_percent, (progress * 100).roundToInt()),
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    PlotProgressBar(progress, shimmer = waiting || draining)
    if (passes > 1 && !waiting) {
        Spacer(Modifier.height(6.dp))
        val pass = min(floor(progress * passes).toInt() + 1, passes)
        Text(stringResource(R.string.ui_pass_n_of_m, pass, passes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How the plot ended, in words (the finish message, e.g. the eject hint, or the error). */
@Composable
private fun FinishedBody(vm: KnutcutViewModel) {
    val ok = vm.cutOutcome == KnutcutViewModel.CutOutcome.SUCCESS
    Spacer(Modifier.height(16.dp))
    vm.status?.let {
        if (ok) Text(it, style = MaterialTheme.typography.bodyLarge)
        else WarningBanner(it)
    }
}

/**
 * A thick, rounded progress bar. The fill carries slowly moving stripes while data flows; with
 * [shimmer] a soft light band sweeps across instead (waiting for the machine, or the machine still
 * finishing after everything was sent).
 */
@Composable
private fun PlotProgressBar(fraction: Float, shimmer: Boolean) {
    val fill = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val stripe = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
    val motion = rememberInfiniteTransition(label = "bar")
    val sweep by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sweep")
    val stripeShift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "stripes")
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(50))
            .semantics {
                progressBarRangeInfo = if (fraction <= 0f) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(fraction, 0f..1f)
            },
    ) {
        drawRect(track)
        val fw = size.width * fraction.coerceIn(0f, 1f)
        if (fw > 0f) {
            drawRect(Brush.horizontalGradient(listOf(fill.copy(alpha = 0.75f), fill), endX = max(fw, 1f)), size = Size(fw, size.height))
            if (!shimmer) {
                // Diagonal stripes drifting to the right, clipped to the filled part.
                val gap = 14.dp.toPx()
                clipRect(right = fw) {
                    var x = -size.height - gap + stripeShift * gap
                    while (x < fw) {
                        val p = Path().apply {
                            moveTo(x, size.height); lineTo(x + size.height, 0f)
                            lineTo(x + size.height + gap / 2, 0f); lineTo(x + gap / 2, size.height); close()
                        }
                        drawPath(p, stripe)
                        x += gap
                    }
                }
            }
        }
        if (shimmer) {
            val bw = size.width * 0.3f
            val x = -bw + (size.width + bw) * sweep
            val light = if (fw > 0f) Color.White.copy(alpha = 0.45f) else fill.copy(alpha = 0.7f)
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, light, Color.Transparent), startX = x, endX = x + bw),
                topLeft = Offset(x, 0f),
                size = Size(bw, size.height),
            )
        }
    }
}

/** A check (done) or warning badge that pops in over the preview when the plot ends. */
@Composable
private fun OutcomeBadge(success: Boolean) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(success) { scale.snapTo(0f); scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
    Surface(
        shape = CircleShape,
        color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (success) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer,
        shadowElevation = 6.dp,
        modifier = Modifier.size(64.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    ) {
        Icon(if (success) Icons.Default.Check else Icons.Default.Warning, contentDescription = null, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun WarningBanner(text: String) {
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
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** How far the live trace has got: [fraction] of the current pass, plus what to decorate it with. */
private class TraceState(val fraction: Float, val head: Boolean, val waiting: Boolean, val success: Boolean)

/** Lengths of the cut paths, to map a 0..1 progress onto a point along the whole cut. */
private class TraceGeometry(val strokes: List<KnutcutViewModel.CutStroke>) {
    val pts: List<List<Pt>> = strokes.map { s ->
        val p = s.polyline.points
        if (s.polyline.closed && p.size > 1) p + p.first() else p
    }
    val lens: DoubleArray = DoubleArray(pts.size) { i ->
        val p = pts[i]
        var l = 0.0
        for (k in 1 until p.size) l += hypot(p[k].xMm - p[k - 1].xMm, p[k].yMm - p[k - 1].yMm)
        l
    }
    val total: Double = lens.sum()
}

/**
 * The whole mat, scaled to fit. In review, included layers are drawn as they'll be plotted and
 * skipped ones ghosted; while plotting ([trace]), the cut paths draw themselves live with a glowing
 * head where the tool is, and on success the mat briefly lights up.
 */
@Composable
private fun PlotPreview(vm: KnutcutViewModel, visible: List<Int>, selectable: Boolean, trace: TraceState?, maxHeight: Dp) {
    val mat = vm.mat
    val aspect = (mat.widthMm / mat.heightMm).toFloat().coerceIn(0.2f, 5f)
    val knifeColor = MaterialTheme.colorScheme.primary
    val penColor = MaterialTheme.colorScheme.secondary
    val offMatColor = MaterialTheme.colorScheme.error
    val ghost = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val border = MaterialTheme.colorScheme.outline
    val glow = MaterialTheme.colorScheme.tertiary
    val placed = visible.mapNotNull { i -> vm.placedLayerAt(i)?.let { i to it } }
    val geometry = remember(vm.cutStrokes) { TraceGeometry(vm.cutStrokes) }
    val desc = stringResource(R.string.ui_plot_preview_desc) + ": " +
        pluralStringResource(R.plurals.ui_plot_n_of_m, visible.size, vm.cutLayers().size, visible.size)

    val pulse = rememberInfiniteTransition(label = "head")
    val headPulse by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    val successTint by animateFloatAsState(if (trace?.success == true) 1f else 0f, tween(700), label = "tint")

    Canvas(
        Modifier
            .heightIn(max = maxHeight)
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
        fun map(p: Pt) = Offset(p.xMm.toFloat() * ppm, p.yMm.toFloat() * ppm)
        // Faint one-inch grid, like the printed grid on the real mat.
        val step = 25.4f * ppm
        var x = step
        while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = step
        while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }

        if (trace == null) {
            // Review: skipped layers first (underneath), then the ones that will be plotted on top.
            val (inc, skip) = placed.partition { (i, _) -> vm.isPlotIncluded(i) }
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            skip.forEach { (_, pl) -> pl.polylines.forEach { drawPath(pathOf(it, ::map), ghost, style = Stroke(1.dp.toPx(), pathEffect = dash)) } }
            inc.forEach { (_, pl) ->
                val toolColor = if (pl.tool == Tool.PEN) penColor else knifeColor
                drawPlaced(pl, toolColor, offMatColor, 1.6.dp.toPx(), { vm.isOutsideMat(it) }, ::map)
            }
        } else {
            fun colorOf(t: Tool) = if (t == Tool.PEN) penColor else knifeColor
            // What's still ahead: a faint trail of every path.
            geometry.pts.forEachIndexed { i, p ->
                if (p.size > 1) drawPath(pathOfPts(p, ::map), colorOf(geometry.strokes[i].tool).copy(alpha = 0.22f), style = Stroke(1.dp.toPx()))
            }
            // What's done: the paths drawn so far, ending where the tool is now.
            val head = drawTrace(geometry, trace.fraction, ::colorOf, 2.2.dp.toPx(), ::map)
            val headAt = when {
                trace.waiting -> geometry.pts.firstOrNull { it.isNotEmpty() }?.first()?.let(::map)
                trace.head -> head
                else -> null
            }
            headAt?.let { c ->
                val r = 5.dp.toPx()
                drawCircle(glow.copy(alpha = 0.18f + 0.22f * headPulse), radius = r * (2.2f + 1.2f * headPulse), center = c)
                drawCircle(glow, radius = r, center = c)
                drawCircle(Color.White, radius = r * 0.45f, center = c)
            }
            if (successTint > 0f) {
                // Done: the cut shapes fill up in their tool color, one even-odd path per tool so
                // nested contours (a ring's inner circle) stay open as holes.
                geometry.strokes.indices.groupBy { geometry.strokes[it].tool }.forEach { (t, idx) ->
                    val fill = Path().apply {
                        fillType = PathFillType.EvenOdd
                        idx.filter { geometry.strokes[it].polyline.closed && geometry.pts[it].size > 2 }
                            .forEach { addPath(pathOfPts(geometry.pts[it], ::map)) }
                    }
                    drawPath(fill, colorOf(t).copy(alpha = 0.28f * successTint), style = Fill)
                }
            }
        }
        drawRect(border, style = Stroke(1.dp.toPx()))
    }
}

/**
 * Draw the first [fraction] (by length) of the cut paths in their tool colors. Returns the point
 * where the trace currently ends, i.e. where the tool is.
 */
private fun DrawScope.drawTrace(
    geo: TraceGeometry,
    fraction: Float,
    colorOf: (Tool) -> Color,
    strokePx: Float,
    map: (Pt) -> Offset,
): Offset? {
    if (geo.total <= 0.0) return null
    val style = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    var remaining = geo.total * fraction
    var end: Offset? = null
    for (i in geo.pts.indices) {
        val p = geo.pts[i]
        if (p.size < 2) continue
        val color = colorOf(geo.strokes[i].tool)
        if (remaining >= geo.lens[i]) {
            drawPath(pathOfPts(p, map), color, style = style)
            remaining -= geo.lens[i]
            end = map(p.last())
            continue
        }
        if (remaining <= 0.0) break
        // The path the tool is on right now: draw up to the exact point along it.
        val path = Path()
        var h = map(p[0])
        path.moveTo(h.x, h.y)
        for (k in 1 until p.size) {
            val a = p[k - 1]; val b = p[k]
            val seg = hypot(b.xMm - a.xMm, b.yMm - a.yMm)
            if (seg >= remaining) {
                val t = if (seg > 0.0) remaining / seg else 0.0
                h = map(Pt(a.xMm + (b.xMm - a.xMm) * t, a.yMm + (b.yMm - a.yMm) * t))
                path.lineTo(h.x, h.y)
                break
            }
            h = map(b)
            path.lineTo(h.x, h.y)
            remaining -= seg
        }
        drawPath(path, color, style = style)
        return h
    }
    return end
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
        if (pl == null || pts.isEmpty()) return@Canvas
        val minX = pts.minOf { it.xMm }; val maxX = pts.maxOf { it.xMm }
        val minY = pts.minOf { it.yMm }; val maxY = pts.maxOf { it.yMm }
        val pad = 6.dp.toPx()
        val bw = max(maxX - minX, 1e-6); val bh = max(maxY - minY, 1e-6)
        val s = min((size.width - 2 * pad) / bw, (size.height - 2 * pad) / bh).toFloat()
        val ox = (size.width - (bw * s).toFloat()) / 2f
        val oy = (size.height - (bh * s).toFloat()) / 2f
        // The thumbnail only shows the shape; off-mat warnings belong to the big preview.
        drawPlaced(pl, toolColor, offMat, 1.2.dp.toPx(), { false }) { p ->
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

/** A path through [pts] as given (a closed contour already ends on its first point). */
private fun pathOfPts(pts: List<Pt>, map: (Pt) -> Offset): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    val f = map(pts.first()); moveTo(f.x, f.y)
    for (k in 1 until pts.size) { val q = map(pts[k]); lineTo(q.x, q.y) }
}
