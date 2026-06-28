package de.knutwurst.knutcut.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import de.knutwurst.knutcut.data.ColorMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.knutwurst.knutcut.data.Mat
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.EditablePath
import de.knutwurst.knutcut.svgcore.HandleSide
import de.knutwurst.knutcut.svgcore.nearestHandle
import de.knutwurst.knutcut.svgcore.nearestNode
import de.knutwurst.knutcut.svgcore.nearestSegment
import de.knutwurst.knutcut.svgcore.Placement
import de.knutwurst.knutcut.svgcore.Pt
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

// Minimum spacing between consecutive sampled points while a DRAW stroke is already in progress.
// This is NOT used to gate the first point — that's handled by touchSlop.
private const val DRAW_SAMPLE_MIN_PX = 4f

private sealed interface Drag {
    /** Handle 0-3 = corners TL,TR,BR,BL (uniform scale); 4-7 = sides top,right,bottom,left (one axis). */
    data class Resize(val handle: Int) : Drag
    object Rotate : Drag
    object Move : Drag
    object PanCamera : Drag
    object Camera : Drag
    // Node-editor drags
    data class NodeAnchor(val index: Int) : Drag
    data class NodeHandle(val nodeIndex: Int, val side: HandleSide) : Drag
}

// SELECT-mode handle sizes / hit zones, in dp (× screen density at the use site) so they keep a
// sensible physical size on dense displays instead of shrinking to a few raw pixels.
private const val HANDLE_HIT_DP = 16f   // touch radius for grabbing a resize/rotate handle
private const val ROTATE_ARM_DP = 22f   // length of the rotate handle's arm
private const val TAP_SLOP_DP = 6f      // movement below this counts as a tap, not a drag
private const val CORNER_DP = 8f        // drawn size of a corner handle square
private const val SIDE_DP = 7f          // drawn size of a side-midpoint handle square
private const val ROTATE_DOT_DP = 5f    // drawn radius of the rotate handle dot
// Smart-guide line color: a high-contrast magenta that reads on both light and dark mats.
private val GUIDE_COLOR = Color(0xFFFF4081)

// Node editor visual constants, in dp (multiplied by the screen density at the draw site so the
// dots stay a sensible physical size on every screen — raw pixels are far too small on a dense
// phone display).
private const val NODE_ANCHOR_RADIUS_DP = 7f     // filled dot for an anchor
private const val NODE_HANDLE_RADIUS_DP = 5f     // smaller dot for a control handle
private const val NODE_SELECTED_GROW_DP = 4f     // the selected anchor grows by this much
private const val NODE_HIT_DP = 16f              // touch radius for nodes/handles in the node editor
private const val DOUBLE_TAP_MS = 350L           // max gap between taps to count as a double-tap
// Vertical drag (in mm) that sweeps the text curve across its full ±100 range when bending on the mat.
private const val BEND_DRAG_RANGE_MM = 80.0

/** One segment of the on-mat display-mode toggle: an icon that highlights when its mode is active
 *  and switches to it on tap (the mat redraws instantly, so you see what each mode does). */
@Composable
private fun DisplayModeChip(vm: KnutcutViewModel, mode: ColorMode, icon: ImageVector, descRes: Int, enabled: Boolean = true) {
    val active = vm.colorMode == mode
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (active && enabled) MaterialTheme.colorScheme.primary else Color.Transparent)
            .then(if (enabled) Modifier.clickable { vm.changeColorMode(mode) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = androidx.compose.ui.res.stringResource(descRes),
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                active -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The placement mat. Pinch or one-finger-drag on empty space moves/zooms the *work area* (like a
 * photo). The design is moved by dragging it, scaled with the corner handles, and turned with the
 * handle above it — the design itself never changes when you zoom the view.
 */
@Composable
fun MatEditor(vm: KnutcutViewModel, modifier: Modifier = Modifier) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    // World-space points collected during a DRAW gesture; empty when not drawing.
    var liveStroke by remember { mutableStateOf<List<Pt>>(emptyList()) }
    // Index of the node currently selected in NODES mode (-1 = none).
    var selectedNodeIndex by remember { mutableStateOf(-1) }
    // Last anchor tap (node index + time), so a genuine double-tap on the same node toggles smooth.
    var lastTapNode by remember { mutableStateOf(-1) }
    var lastTapMs by remember { mutableStateOf(0L) }

    // Fix 1: reset selected node when the selected layer or the layer count changes, so
    // selectedNodeIndex can never point at a node that no longer exists.
    LaunchedEffect(vm.selectedLayer, vm.layers.size) { selectedNodeIndex = -1 }

    // Fix 3: clear the live stroke preview when the user leaves DRAW mode mid-stroke.
    LaunchedEffect(vm.editorTool) { if (vm.editorTool != EditorTool.DRAW) liveStroke = emptyList() }

    // Derive the grid/ruler tones from the theme so they stay visible on both light and dark.
    // OUTLINE = strokes only; COLOR = fill + outline overlay; FILL = fill only (no outline).
    val drawFills = vm.colorMode != ColorMode.OUTLINE
    val drawOutlines = vm.colorMode != ColorMode.FILL
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
    val deformGuideColor = MaterialTheme.colorScheme.outlineVariant
    // Captured for use inside the Canvas draw scope (which is not a Composable context).
    val nodeSurfaceColor = MaterialTheme.colorScheme.surface
    val nodeSelectedColor = MaterialTheme.colorScheme.error
    // Screen density: used to size node dots and touch targets in physical dp, not raw pixels.
    val density = LocalDensity.current.density
    val readout = vm.selectionReadout() ?: vm.overallReadout()
    val matSummary = readout?.let {
        androidx.compose.ui.res.stringResource(de.knutwurst.knutcut.R.string.cd_workarea, it)
    } ?: androidx.compose.ui.res.stringResource(de.knutwurst.knutcut.R.string.cd_workarea_empty)

    Box(modifier.semantics { contentDescription = matSummary }) {
      Canvas(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            // Extract the two-finger camera pan/zoom into one helper to avoid repeating it.
            // Updates vm.camOffset and vm.camScale in-place; returns updated ppm and origin.
            fun applyTwoFingerCamera(
                p0: androidx.compose.ui.input.pointer.PointerInputChange,
                p1: androidx.compose.ui.input.pointer.PointerInputChange,
                curPpm: Float,
                curOrigin: androidx.compose.ui.geometry.Offset,
            ): Pair<Float, androidx.compose.ui.geometry.Offset> {
                val prevC = (p0.previousPosition + p1.previousPosition) / 2f
                val curC  = (p0.position + p1.position) / 2f
                val prevD = (p0.previousPosition - p1.previousPosition).getDistance()
                val curD  = (p0.position - p1.position).getDistance()
                val z = if (prevD > 0f) curD / prevD else 1f
                vm.camOffset = curC - (prevC - vm.camOffset) * z
                vm.camScale *= z
                val newPpm = ppmFor(sizePx, vm.mat, vm.camScale)
                val newOrigin = originFor(sizePx, vm.mat, vm.camScale, vm.camOffset)
                return newPpm to newOrigin
            }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var ppm = ppmFor(sizePx, vm.mat, vm.camScale)
                if (ppm <= 0f) return@awaitEachGesture
                var origin = originFor(sizePx, vm.mat, vm.camScale, vm.camOffset)

                // ---- BEND mode (on-mat text curve handle) ----
                // Grab the knob and drag up/down to bend the selected text (up = arch, down = bow the
                // other way). A drag on empty mat just pans; two fingers pan/zoom. One undo per bend.
                if (vm.bendingText) {
                    val layerIdx = vm.selectedLayer
                    val spec = vm.layers.getOrNull(layerIdx)?.textSpec
                    if (spec != null) {
                        // Knob position in screen space — must match the overlay's knob.
                        val corners = vm.layerCorners(layerIdx)
                        val knobScreen = if (corners.size == 4) {
                            val cx = (corners.minOf { it.xMm } + corners.maxOf { it.xMm }) / 2.0
                            val cyc = (corners.minOf { it.yMm } + corners.maxOf { it.yMm }) / 2.0
                            worldToScreen(Pt(cx, cyc - spec.curve / 100.0 * BEND_DRAG_RANGE_MM), origin, ppm)
                        } else null
                        val grabbedKnob = knobScreen != null &&
                            (down.position - knobScreen).getDistance() <= NODE_HIT_DP * density

                        if (grabbedKnob) {
                            val startCurve = spec.curve
                            val downWorldY = screenToWorld(down.position, origin, ppm).yMm
                            var pushed = false
                            var totalMoved = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    if (pushed) vm.undo()   // abort the partial bend, hand off to camera
                                    val (np, no) = applyTwoFingerCamera(pressed[0], pressed[1], ppm, origin)
                                    ppm = np; origin = no
                                    event.changes.forEach { it.consume() }
                                    while (true) {
                                        val ev2 = awaitPointerEvent()
                                        val pr2 = ev2.changes.filter { it.pressed }
                                        if (pr2.isEmpty()) break
                                        if (pr2.size >= 2) { val (a, b) = applyTwoFingerCamera(pr2[0], pr2[1], ppm, origin); ppm = a; origin = b }
                                        ev2.changes.forEach { it.consume() }
                                    }
                                    return@awaitEachGesture
                                }
                                val p = pressed[0]
                                totalMoved += p.positionChange().getDistance()
                                if (!pushed && totalMoved > viewConfiguration.touchSlop) { vm.beginTextCurve(); pushed = true }
                                if (pushed) {
                                    ppm = ppmFor(sizePx, vm.mat, vm.camScale)
                                    origin = originFor(sizePx, vm.mat, vm.camScale, vm.camOffset)
                                    val curWorldY = screenToWorld(p.position, origin, ppm).yMm
                                    // Screen y grows downward, so dragging up is a negative dy → +curve.
                                    val dyMm = curWorldY - downWorldY
                                    val curve = (startCurve - dyMm / BEND_DRAG_RANGE_MM * 100.0).roundToInt()
                                    vm.setSelectedTextCurve(curve)
                                }
                                p.consume()
                            }
                            return@awaitEachGesture
                        }

                        // Not on the knob: a drag pans (one finger) / pans+zooms (two fingers); a plain
                        // tap on the mat leaves bend mode (back to Select), per the editing convention.
                        var bendMoved = 0f
                        var bendTwoFinger = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                bendTwoFinger = true
                                val (np, no) = applyTwoFingerCamera(pressed[0], pressed[1], ppm, origin)
                                ppm = np; origin = no
                                event.changes.forEach { it.consume() }
                            } else {
                                val p = pressed[0]
                                bendMoved += p.positionChange().getDistance()
                                vm.camOffset += p.positionChange()
                                p.consume()
                            }
                        }
                        if (!bendTwoFinger && bendMoved < TAP_SLOP_DP * density) vm.stopBendingText()
                        return@awaitEachGesture
                    }
                }

                // ---- DRAW mode ----
                // State machine: finger-down does NOT seed a stroke. Only after the finger has moved
                // more than touchSlop (started=true) does the stroke begin. A second finger at any
                // time cancels the stroke and hands off to two-finger camera. Stroke is committed on
                // lift only if started==true and at least 2 points were collected.
                if (vm.editorTool == EditorTool.DRAW) {
                    val stroke = mutableListOf<Pt>()
                    val downPos = down.position
                    var started = false
                    var lastScreenPt = downPos

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            // Second finger: abort any in-progress stroke, switch to camera.
                            started = false
                            stroke.clear()
                            liveStroke = emptyList()
                            val (np, no) = applyTwoFingerCamera(pressed[0], pressed[1], ppm, origin)
                            ppm = np; origin = no
                            event.changes.forEach { it.consume() }
                            // Drain remaining camera events until all fingers lift.
                            while (true) {
                                val ev2 = awaitPointerEvent()
                                val pr2 = ev2.changes.filter { it.pressed }
                                if (pr2.isEmpty()) break
                                if (pr2.size >= 2) {
                                    val (np2, no2) = applyTwoFingerCamera(pr2[0], pr2[1], ppm, origin)
                                    ppm = np2; origin = no2
                                }
                                ev2.changes.forEach { it.consume() }
                            }
                            liveStroke = emptyList()
                            return@awaitEachGesture
                        }

                        val p = pressed[0]
                        val screenPt = p.position

                        if (!started) {
                            // Only begin the stroke once the finger has moved past touchSlop.
                            val totalMoved = (screenPt - downPos).getDistance()
                            if (totalMoved >= viewConfiguration.touchSlop) {
                                started = true
                                // Seed with the original down position + current position.
                                stroke.add(screenToWorld(downPos, origin, ppm))
                                stroke.add(screenToWorld(screenPt, origin, ppm))
                                liveStroke = stroke.toList()
                                lastScreenPt = screenPt
                            }
                        } else {
                            val moved = (screenPt - lastScreenPt).getDistance()
                            if (moved >= DRAW_SAMPLE_MIN_PX) {
                                stroke.add(screenToWorld(screenPt, origin, ppm))
                                liveStroke = stroke.toList()
                                lastScreenPt = screenPt
                            }
                        }
                        p.consume()
                    }

                    // Commit only when a stroke was actually started and has at least 2 points.
                    if (started && stroke.size >= 2) vm.addDrawnPath(stroke)
                    liveStroke = emptyList()
                    return@awaitEachGesture
                }

                // ---- NODES mode ----
                if (vm.editorTool == EditorTool.NODES) {
                    val editPath = vm.selectedEditPath
                    if (editPath == null) {
                        // No editable path: escape to SELECT.
                        vm.editorTool = EditorTool.SELECT
                        // Fall through to camera/selection handling below.
                    } else {
                        val layerIdx = vm.selectedLayer
                        // Hit-radius in mm. Cap to 9 mm so the targets don't grow huge at low zoom.
                        val hitMm = min(NODE_HIT_DP * density / ppm, 9f).toDouble()
                        val downWorld = screenToWorld(down.position, origin, ppm)
                        val downLocal = vm.worldToLayerLocal(layerIdx, downWorld) ?: downWorld

                        // Priority: handles → anchors → segments. Only the SELECTED node's handles are
                        // drawn, so only those are grabbable; ignore hits on other nodes' hidden handles.
                        val handleHit = editPath.nearestHandle(downLocal, hitMm)?.takeIf { it.nodeIndex == selectedNodeIndex }
                        val anchorHit = if (handleHit == null) editPath.nearestNode(downLocal, hitMm) else null

                        val nodeDrag: Drag? = when {
                            handleHit != null -> Drag.NodeHandle(handleHit.nodeIndex, handleHit.side)
                            anchorHit != null -> Drag.NodeAnchor(anchorHit)
                            else -> null
                        }

                        if (nodeDrag != null) {
                            // Started on a node or handle — prepare for drag.
                            var pushedNodeHistory = false
                            var totalNodeDrag = 0f
                            var cancelledByTwoFinger = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    // Two-finger hands off to the camera, keeping the partial node edit
                                    // (same as Select/move). Drain camera events until all fingers lift,
                                    // so the pinch tracks fully instead of stopping after a single frame.
                                    cancelledByTwoFinger = true
                                    val (np, no) = applyTwoFingerCamera(pressed[0], pressed[1], ppm, origin)
                                    ppm = np; origin = no
                                    event.changes.forEach { it.consume() }
                                    while (true) {
                                        val ev2 = awaitPointerEvent()
                                        val pr2 = ev2.changes.filter { it.pressed }
                                        if (pr2.isEmpty()) break
                                        if (pr2.size >= 2) { val (a, b) = applyTwoFingerCamera(pr2[0], pr2[1], ppm, origin); ppm = a; origin = b }
                                        ev2.changes.forEach { it.consume() }
                                    }
                                    break
                                }
                                val p = pressed[0]
                                val moved = p.positionChange().getDistance()
                                totalNodeDrag += moved
                                // Only start the edit after touchSlop to distinguish tap from drag.
                                if (!pushedNodeHistory && totalNodeDrag > viewConfiguration.touchSlop) {
                                    vm.beginNodeEdit()
                                    pushedNodeHistory = true
                                }
                                if (pushedNodeHistory) {
                                    ppm = ppmFor(sizePx, vm.mat, vm.camScale)
                                    origin = originFor(sizePx, vm.mat, vm.camScale, vm.camOffset)
                                    val curWorld = clampToMat(screenToWorld(p.position, origin, ppm), vm.mat.widthMm, vm.mat.heightMm)
                                    val curLocal = vm.worldToLayerLocal(layerIdx, curWorld) ?: curWorld
                                    when (nodeDrag) {
                                        is Drag.NodeAnchor -> vm.moveSelectedAnchor(nodeDrag.index, curLocal)
                                        is Drag.NodeHandle -> vm.moveSelectedHandle(nodeDrag.nodeIndex, nodeDrag.side, curLocal)
                                        else -> Unit
                                    }
                                }
                                p.consume()
                            }
                            if (!cancelledByTwoFinger && totalNodeDrag < TAP_SLOP_DP * density) {
                                // Tap (no real drag): select the anchor node. A genuine double-tap on
                                // the same node (two taps within DOUBLE_TAP_MS) toggles smooth/corner.
                                val nowMs = System.currentTimeMillis()
                                val nodeIndex = when (nodeDrag) {
                                    is Drag.NodeAnchor -> nodeDrag.index
                                    is Drag.NodeHandle -> nodeDrag.nodeIndex
                                    else -> -1
                                }
                                if (nodeIndex >= 0 && nodeIndex == lastTapNode && nowMs - lastTapMs <= DOUBLE_TAP_MS) {
                                    vm.toggleSelectedNodeSmooth(nodeIndex)
                                    lastTapNode = -1   // consume, so a third tap doesn't immediately re-toggle
                                } else {
                                    lastTapNode = nodeIndex
                                    lastTapMs = nowMs
                                }
                                selectedNodeIndex = nodeIndex
                            }
                            return@awaitEachGesture
                        }

                        // Missed handles and anchors. Dragging a LINE bends that part of the curve (it
                        // pulls the on-curve point to the finger, creating handles as needed — so even a
                        // corner-node shape from an imported SVG bends). Dragging the inside of a closed
                        // shape MOVES the whole layer. A long-press or double-tap on a line inserts a
                        // node. A drag on empty mat pans the view.
                        val segHitMm = min(NODE_HIT_DP * density * 2 / ppm, 12f).toDouble()
                        val segHit = editPath.nearestSegment(downLocal, segHitMm)
                        val onLayer = segHit != null || vm.layerAt(downWorld) == layerIdx
                        if (onLayer) {
                            val downTime = System.currentTimeMillis()
                            var total = 0f
                            var longPressed = false
                            var moving = false
                            var bending = false
                            var cancelledByTwoFinger = false
                            var moveCenter = vm.centerMm

                            while (true) {
                                val event = awaitPointerEvent()
                                val elapsed = System.currentTimeMillis() - downTime
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                if (pressed.size >= 2) {
                                    // Hand off to the camera, keeping the partial move (same as Select).
                                    // Drain camera events until all fingers lift, so the pinch tracks fully.
                                    cancelledByTwoFinger = true
                                    val (np, no) = applyTwoFingerCamera(pressed[0], pressed[1], ppm, origin)
                                    ppm = np; origin = no
                                    event.changes.forEach { it.consume() }
                                    while (true) {
                                        val ev2 = awaitPointerEvent()
                                        val pr2 = ev2.changes.filter { it.pressed }
                                        if (pr2.isEmpty()) break
                                        if (pr2.size >= 2) { val (a, b) = applyTwoFingerCamera(pr2[0], pr2[1], ppm, origin); ppm = a; origin = b }
                                        ev2.changes.forEach { it.consume() }
                                    }
                                    break
                                }

                                val p = pressed[0]
                                total += p.positionChange().getDistance()

                                // Long-press on a line (no drag) inserts a node.
                                if (!longPressed && !moving && !bending && segHit != null &&
                                    elapsed >= viewConfiguration.longPressTimeoutMillis &&
                                    total < TAP_SLOP_DP * density) {
                                    longPressed = true
                                    vm.insertSelectedNode(segHit.segmentIndex, segHit.t)
                                    selectedNodeIndex = -1
                                }

                                // Drag: bend the grabbed line, or (inside a shape) move the whole layer.
                                if (!longPressed && total > viewConfiguration.touchSlop) {
                                    ppm = ppmFor(sizePx, vm.mat, vm.camScale)
                                    origin = originFor(sizePx, vm.mat, vm.camScale, vm.camOffset)
                                    if (segHit != null) {
                                        // Pull the on-curve point at the grabbed spot to the finger.
                                        if (!bending) { vm.beginNodeEdit(); bending = true }
                                        val curWorld = clampToMat(screenToWorld(p.position, origin, ppm), vm.mat.widthMm, vm.mat.heightMm)
                                        val curLocal = vm.worldToLayerLocal(layerIdx, curWorld) ?: curWorld
                                        vm.dragSelectedSegment(segHit.segmentIndex, segHit.t, curLocal)
                                    } else {
                                        // No line under the finger: move the whole layer (one undo step).
                                        if (!moving) { vm.pushHistory(); moving = true }
                                        val dp = p.positionChange()
                                        moveCenter = Pt(moveCenter.xMm + dp.x / ppm, moveCenter.yMm + dp.y / ppm)
                                        vm.moveSelectedTo(moveCenter, (8f / ppm).toDouble())
                                    }
                                }
                                p.consume()
                            }

                            if (!cancelledByTwoFinger && !longPressed && !moving && !bending) {
                                // A tap. If it landed on a DIFFERENT contour of this layer, switch the
                                // node editor to that contour (multi-contour shapes edit one at a time).
                                val pickedContour = vm.nodeContourAt(downLocal, segHitMm)
                                if (pickedContour != null && pickedContour != vm.selectedEditPathIndex) {
                                    vm.setEditContour(pickedContour)
                                    selectedNodeIndex = -1
                                } else if (segHit != null) {
                                // On a line, wait up to DOUBLE_TAP_MS for a second tap (= insert a
                                // node); otherwise deselect the active node. withTimeoutOrNull so the
                                // gesture isn't parked waiting for a tap that never comes.
                                    val down2 = withTimeoutOrNull(DOUBLE_TAP_MS) { awaitFirstDown(requireUnconsumed = false) }
                                    var gotSecondTap = false
                                    if (down2 != null) {
                                        val w2 = screenToWorld(down2.position, origin, ppm)
                                        val l2 = vm.worldToLayerLocal(layerIdx, w2) ?: w2
                                        val seg2 = editPath.nearestSegment(l2, segHitMm)
                                        if (seg2 != null) {
                                            vm.insertSelectedNode(seg2.segmentIndex, seg2.t)
                                            gotSecondTap = true
                                        }
                                    }
                                    if (!gotSecondTap) selectedNodeIndex = -1
                                } else {
                                    selectedNodeIndex = -1
                                }
                            }
                            return@awaitEachGesture
                        }

                        // Tap/drag on truly empty space: deselect + pan.
                        selectedNodeIndex = -1
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val (np, no) = applyTwoFingerCamera(pressed[0], pressed[1], ppm, origin)
                                ppm = np; origin = no
                                event.changes.forEach { it.consume() }
                            } else {
                                val p = pressed[0]
                                vm.camOffset += p.positionChange()
                                p.consume()
                            }
                        }
                        return@awaitEachGesture
                    }
                }

                // ---- SELECT mode (default): move / resize / rotate / pan ----

                val handlesScreen = handleWorldPoints(vm.placedCorners()).map { worldToScreen(it, origin, ppm) }
                val cornersScreen = vm.placedCorners().map { worldToScreen(it, origin, ppm) }
                val centerScreen = worldToScreen(vm.centerMm, origin, ppm)
                var drag: Drag = hitTest(down.position, handlesScreen, cornersScreen, centerScreen, density)

                // Free-rotate mode: the corner/side handles turn the layer instead of resizing it.
                if (vm.editorTool == EditorTool.ROTATE && drag is Drag.Resize) drag = Drag.Rotate

                // Missed the selected layer's box/handles? If the touch is inside another layer,
                // select it and move that one instead of panning the view.
                if (drag is Drag.PanCamera && vm.layers.isNotEmpty()) {
                    val hit = vm.layerAt(screenToWorld(down.position, origin, ppm))
                    if (hit >= 0) { vm.selectLayer(hit); drag = Drag.Move }
                }

                val startRotation = vm.rotationDeg
                val startAngle = atan2((down.position.y - centerScreen.y).toDouble(), (down.position.x - centerScreen.x).toDouble())

                val b = vm.bounds
                val localHandles = if (b != null) handleLocalPoints(b) else emptyList()
                val centerLocal = if (b != null) Pt((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2) else Pt(0.0, 0.0)
                val resizeHandle = (drag as? Drag.Resize)?.handle ?: -1
                val anchorIdx = if (resizeHandle >= 0) anchorOf(resizeHandle) else -1
                val worldHandles = handleWorldPoints(vm.placedCorners())
                val anchorLocal = if (anchorIdx >= 0) localHandles[anchorIdx] else Pt(0.0, 0.0)
                val draggedLocal = if (resizeHandle >= 0) localHandles[resizeHandle] else Pt(0.0, 0.0)
                val anchorWorld = if (anchorIdx >= 0 && worldHandles.size == 8) worldHandles[anchorIdx] else Pt(0.0, 0.0)
                val startScaleX = vm.scaleX
                val startScaleY = vm.scaleY
                var totalDrag = 0f
                var pushedHistory = false
                // Running, un-snapped center for the move (so snapping never swallows small drags).
                var moveCenter = vm.centerMm

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break

                    if (pressed.size >= 2) {
                        val (np, no) = applyTwoFingerCamera(pressed[0], pressed[1], ppm, origin)
                        ppm = np; origin = no
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
                                vm.moveSelectedTo(moveCenter, (8f / ppm).toDouble())
                            }
                            is Drag.Resize -> {
                                val pw = screenToWorld(p.position, origin, ppm)
                                val r = Placement.resize(
                                    handle = d.handle,
                                    dragWorld = pw,
                                    anchorWorld = anchorWorld,
                                    anchorLocal = anchorLocal,
                                    draggedLocal = draggedLocal,
                                    centerLocal = centerLocal,
                                    startScaleX = startScaleX,
                                    startScaleY = startScaleY,
                                    rotationDeg = vm.rotationDeg,
                                    flipX = vm.flipX,
                                    flipY = vm.flipY,
                                )
                                vm.scaleX = r.scaleX; vm.scaleY = r.scaleY
                                vm.centerMm = r.center
                            }
                            Drag.Rotate -> {
                                val a = atan2((p.position.y - cs.y).toDouble(), (p.position.x - cs.x).toDouble())
                                vm.rotationDeg = startRotation + Math.toDegrees(a - startAngle)
                            }
                            is Drag.NodeAnchor, is Drag.NodeHandle -> Unit
                        }
                        p.consume()
                    }
                }

                // Tap on empty mat space: deselect. Double-tap on empty space: reset camera.
                if (drag is Drag.PanCamera && totalDrag < TAP_SLOP_DP * density) {
                    vm.deselectLayers()
                    // Wait up to DOUBLE_TAP_MS for a second tap on empty space (double-tap = reset
                    // view). withTimeoutOrNull so a single tap doesn't park the gesture.
                    val down2 = withTimeoutOrNull(DOUBLE_TAP_MS) { awaitFirstDown(requireUnconsumed = false) }
                    if (down2 != null) {
                        val w2 = screenToWorld(down2.position, origin, ppm)
                        if (vm.layerAt(w2) < 0) vm.resetView()
                    }
                }
                vm.clearGuides()
            }
        },
    ) {
        sizePx = IntSize(size.width.toInt(), size.height.toInt())
        val ppm = ppmFor(sizePx, vm.mat, vm.camScale)
        if (ppm <= 0f) return@Canvas
        val origin = originFor(sizePx, vm.mat, vm.camScale, vm.camOffset)
        fun s(p: Pt) = worldToScreen(p, origin, ppm)

        val tl = s(Pt(0.0, 0.0)); val br = s(Pt(vm.mat.widthMm, vm.mat.heightMm))
        drawRect(matFill, topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y))
        drawGrid(vm.mat, origin, ppm, gridMinor, gridMajor)
        drawRect(matColor, topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y), style = Stroke(width = 2f))

        drawRulers(vm.mat, origin, ppm, rulerColor)

        // design — in OUTLINE mode knife layers use the primary color, pen layers the secondary.
        // In COLOR mode each path is filled with its SVG color and outlined in the theme's onSurface
        // color (white in dark mode, near-black in light mode), so it stays readable on any background.
        // Same-color paths are grouped into a single even-odd path so holes within a shape are kept.
        // Anything off the mat is always drawn in the error color regardless of mode.
        for ((tool, pls, cols, fillGroups) in vm.placedLayers()) {
            val toolColor = if (tool == Tool.PEN) penColor else knifeColor
            val paths = pls.map { pl ->
                Path().apply {
                    if (pl.points.isEmpty()) return@apply
                    val f = s(pl.points.first()); moveTo(f.x, f.y)
                    for (k in 1 until pl.points.size) { val q = s(pl.points[k]); lineTo(q.x, q.y) }
                    if (pl.closed) close()
                }
            }
            if (drawFills) {
                // Fill groups (precomputed + cached in the view model, see FillNesting): a contour
                // nested inside another same-color contour carves a real hole via EvenOdd, while
                // shapes that merely overlap stay separate and union. This keeps holes (letter
                // counters) yet fills a uniformly-colored, merged layer solidly where shapes overlap.
                for (g in fillGroups) {
                    val c = cols.getOrNull(g.first()) ?: continue
                    val group = Path().apply { fillType = PathFillType.EvenOdd; g.forEach { addPath(paths[it]) } }
                    drawPath(group, Color(c), style = Fill)
                }
            }
            // Outlines on top — theme onSurface in COLOR mode guarantees contrast on both dark and
            // light backgrounds; tool color in OUTLINE mode.
            val outlineColor = if (drawFills) onSurface else toolColor
            pls.forEachIndexed { idx, pl ->
                if (pl.points.isEmpty()) return@forEachIndexed
                val off = pl.points.any { vm.isOutsideMat(it) }
                // FILL mode is pure color with no outline overlay — but always flag off-mat paths, and
                // fall back to an outline for any path with no fill color (it would be invisible
                // otherwise: a freshly drawn, colorless shape under a leftover "Color only" setting).
                val hasFill = cols.getOrNull(idx) != null
                if (!drawOutlines && !off && hasFill) return@forEachIndexed
                val stroke = if (off) offMatColor else outlineColor
                drawPath(paths[idx], stroke, style = Stroke(width = 2.5f))
            }
        }

        // Live stroke preview in DRAW mode: draw the collected points as a thin polyline.
        val stroke = liveStroke
        if (stroke.size >= 2) {
            val strokePath = Path().apply {
                val f = s(stroke.first()); moveTo(f.x, f.y)
                for (k in 1 until stroke.size) { val q = s(stroke[k]); lineTo(q.x, q.y) }
            }
            drawPath(strokePath, penColor, style = Stroke(width = 2f))
        }

        // Selection box + resize/rotate handles — in SELECT and ROTATE modes. In DRAW/NODES they are
        // noise and their rotate/resize handles must not compete with drawing or node editing. In
        // ROTATE mode the corner/side grips are drawn as dots, signaling that they turn (not resize).
        val rotating = vm.editorTool == EditorTool.ROTATE
        val corners = if (vm.editorTool == EditorTool.SELECT || rotating) vm.placedCorners().map { s(it) } else emptyList()
        if (corners.size == 4) {
            val box = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                corners.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(box, handleColor, style = Stroke(width = 1.5f))
            val cornerPx = CORNER_DP * density
            val sidePx = SIDE_DP * density
            if (rotating) {
                // Round grips = rotate; corners and side midpoints both turn the layer.
                corners.forEach { drawCircle(handleColor, radius = cornerPx * 0.6f, center = it) }
                for (i in 0 until 4) {
                    val m = (corners[i] + corners[(i + 1) % 4]) / 2f
                    drawCircle(handleColor, radius = sidePx * 0.5f, center = m)
                }
            } else {
                // Square handles = resize: corners (uniform) and side-midpoints (one axis).
                corners.forEach { drawRect(handleColor, topLeft = Offset(it.x - cornerPx / 2, it.y - cornerPx / 2), size = Size(cornerPx, cornerPx)) }
                for (i in 0 until 4) {
                    val m = (corners[i] + corners[(i + 1) % 4]) / 2f
                    drawRect(handleColor, topLeft = Offset(m.x - sidePx / 2, m.y - sidePx / 2), size = Size(sidePx, sidePx))
                }
            }
            val rot = rotateHandlePos(corners, s(vm.centerMm), density)
            val topMid = (corners[0] + corners[1]) / 2f
            drawLine(handleColor, topMid, rot, strokeWidth = 1.5f)
            drawCircle(handleColor, radius = ROTATE_DOT_DP * density, center = rot)
        }

        // smart alignment guides (center snapping) while dragging
        vm.alignGuideX?.let { gx ->
            val x = s(Pt(gx, 0.0)).x
            drawLine(guideColor, Offset(x, s(Pt(0.0, 0.0)).y), Offset(x, s(Pt(0.0, vm.mat.heightMm)).y), strokeWidth = 1.5f)
        }
        vm.alignGuideY?.let { gy ->
            val y = s(Pt(0.0, gy)).y
            drawLine(guideColor, Offset(s(Pt(0.0, 0.0)).x, y), Offset(s(Pt(vm.mat.widthMm, 0.0)).x, y), strokeWidth = 1.5f)
        }

        // Node editor overlay: draw anchors and handles when in NODES mode.
        val nodeEditPath = if (vm.editorTool == EditorTool.NODES) vm.selectedEditPath else null
        if (nodeEditPath != null) {
            val layerIdx = vm.selectedLayer
            // Convert a local point to screen space via the layer matrix.
            fun localToScreen(p: Pt): Offset {
                val w = vm.layerLocalToWorld(layerIdx, p) ?: p
                return s(w)
            }
            val nodeColor = handleColor
            val handleLineColor = handleColor.copy(alpha = 0.55f)
            val anchorR = NODE_ANCHOR_RADIUS_DP * density
            val handleR = NODE_HANDLE_RADIUS_DP * density
            val selectedR = anchorR + NODE_SELECTED_GROW_DP * density
            val ringWidth = 2f * density

            // Hint that the layer's OTHER contours are editable too: stroke them faintly so it's clear
            // they can be tapped to switch the node editor to them. The active contour keeps its nodes.
            val activeContour = vm.selectedEditPathIndex
            val otherContourColor = handleColor.copy(alpha = 0.4f)
            vm.layers.getOrNull(layerIdx)?.polylines?.forEachIndexed { ci, pl ->
                if (ci == activeContour || pl.points.size < 2) return@forEachIndexed
                val path = Path().apply {
                    val first = localToScreen(pl.points.first())
                    moveTo(first.x, first.y)
                    for (k in 1 until pl.points.size) {
                        val sp = localToScreen(pl.points[k]); lineTo(sp.x, sp.y)
                    }
                    if (pl.closed) close()
                }
                drawPath(path, otherContourColor, style = Stroke(width = 1.6f * density))
            }

            for ((ni, node) in nodeEditPath.nodes.withIndex()) {
                val anchorScreen = localToScreen(node.anchor)
                val isSelected = ni == selectedNodeIndex

                // Bézier handles are drawn ONLY for the selected node, so the path stays readable and
                // you only manipulate the node you actually picked.
                if (isSelected) {
                    node.handleIn?.let { hin ->
                        val hinScreen = localToScreen(hin)
                        drawLine(handleLineColor, anchorScreen, hinScreen, strokeWidth = 1.4f * density)
                        drawCircle(nodeSurfaceColor, radius = handleR + density, center = hinScreen, style = Fill)
                        drawCircle(nodeColor, radius = handleR, center = hinScreen, style = Fill)
                    }
                    node.handleOut?.let { hout ->
                        val houtScreen = localToScreen(hout)
                        drawLine(handleLineColor, anchorScreen, houtScreen, strokeWidth = 1.4f * density)
                        drawCircle(nodeSurfaceColor, radius = handleR + density, center = houtScreen, style = Fill)
                        drawCircle(nodeColor, radius = handleR, center = houtScreen, style = Fill)
                    }
                }

                // Anchor dot. Every node first gets a surface-colored backing disc so it reads on any
                // mat color. The selected node is noticeably larger, uses the highlight color and is
                // wrapped in an extra ring, so it's obvious at a glance which node is active.
                if (isSelected) {
                    drawCircle(nodeSelectedColor.copy(alpha = 0.22f), radius = selectedR + 7f * density, center = anchorScreen, style = Fill)
                    drawCircle(nodeSurfaceColor, radius = selectedR + ringWidth, center = anchorScreen, style = Fill)
                    drawCircle(nodeSelectedColor, radius = selectedR, center = anchorScreen, style = Fill)
                    drawCircle(nodeSurfaceColor, radius = selectedR, center = anchorScreen, style = Stroke(width = ringWidth))
                } else {
                    drawCircle(nodeSurfaceColor, radius = anchorR + ringWidth, center = anchorScreen, style = Fill)
                    drawCircle(nodeColor, radius = anchorR, center = anchorScreen, style = Fill)
                    drawCircle(nodeSurfaceColor, radius = anchorR, center = anchorScreen, style = Stroke(width = 1.5f * density))
                }
            }
        }

        // On-mat text-bend overlay: a vertical guide, a ghost circle of the arc, and a draggable knob.
        if (vm.bendingText) {
            val bi = vm.selectedLayer
            val bspec = vm.layers.getOrNull(bi)?.textSpec
            val corners = if (bspec != null) vm.layerCorners(bi) else emptyList()
            if (bspec != null && corners.size == 4) {
                val cx = (corners.minOf { it.xMm } + corners.maxOf { it.xMm }) / 2.0
                val cyc = (corners.minOf { it.yMm } + corners.maxOf { it.yMm }) / 2.0
                val wMm = (corners.maxOf { it.xMm } - corners.minOf { it.xMm }).coerceAtLeast(1.0)
                val c = bspec.curve

                // Ghost circle: the arc the text wraps around (skip when nearly straight).
                if (abs(c) >= 4) {
                    val r = wMm / (c / 100.0 * 2.0 * PI)   // signed radius; >0 = center below baseline
                    val rPx = (abs(r) * ppm).toFloat()
                    if (rPx < 6000f) drawCircle(deformGuideColor, radius = rPx, center = s(Pt(cx, cyc + r)), style = Stroke(width = 1.5f))
                }

                // Vertical guide track through the text center.
                drawLine(guideColor.copy(alpha = 0.5f), s(Pt(cx, cyc - BEND_DRAG_RANGE_MM)), s(Pt(cx, cyc + BEND_DRAG_RANGE_MM)), strokeWidth = 1.5f)

                // Knob at the current curve (up = arch up).
                val knobW = s(Pt(cx, cyc - c / 100.0 * BEND_DRAG_RANGE_MM))
                val knobR = 11f * density
                drawCircle(nodeSurfaceColor, radius = knobR + 2f * density, center = knobW, style = Fill)
                drawCircle(guideColor, radius = knobR, center = knobW, style = Fill)
                drawCircle(nodeSurfaceColor, radius = knobR, center = knobW, style = Stroke(width = 2f * density))
            }
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

      // Top-right quick display-mode toggle: outline only / color + outline / color only, with the
      // active mode highlighted. Tapping redraws the mat instantly so the effect is obvious. Shown
      // only when there's a design to preview.
      if (vm.layers.isNotEmpty()) {
          Surface(
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
              contentColor = MaterialTheme.colorScheme.onSurface,
              shape = RoundedCornerShape(18.dp),
              tonalElevation = 2.dp,
              modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
          ) {
              androidx.compose.foundation.layout.Row(modifier = Modifier.padding(2.dp)) {
                  DisplayModeChip(vm, ColorMode.OUTLINE, Icons.Outlined.CheckBoxOutlineBlank, de.knutwurst.knutcut.R.string.ui_outline_only)
                  DisplayModeChip(vm, ColorMode.COLOR, Icons.Default.Gradient, de.knutwurst.knutcut.R.string.ui_colorful)
                  // "Color only" is only useful when something has a color; otherwise colorless
                  // outlines would vanish, so it's disabled until colors exist.
                  DisplayModeChip(vm, ColorMode.FILL, Icons.Filled.Square, de.knutwurst.knutcut.R.string.ui_color_only, enabled = vm.hasColors)
              }
          }
      }

      // Node-editor controls, bottom-right. The open/close (lock) toggle is a path-level action and
      // shows whenever the node editor is active; the per-node smooth/delete buttons appear once a
      // node is selected. A closed lock = closed (cuttable) contour; an open lock = open line.
      if (vm.editorTool == EditorTool.NODES && vm.selectedEditPath != null) {
          val nodeSelected = selectedNodeIndex >= 0 &&
              selectedNodeIndex < (vm.selectedEditPath?.nodes?.size ?: 0)
          Surface(
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
              contentColor = MaterialTheme.colorScheme.onSurface,
              shape = RoundedCornerShape(8.dp),
              tonalElevation = 2.dp,
              modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
          ) {
              androidx.compose.foundation.layout.Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
              ) {
                  // Open / close the whole path (lock = closed contour, open lock = open line).
                  val closed = vm.selectedPathClosed
                  IconButton(onClick = { vm.toggleSelectedPathClosed() }) {
                      Icon(
                          if (closed) Icons.Default.Lock else Icons.Default.LockOpen,
                          contentDescription = androidx.compose.ui.res.stringResource(
                              if (closed) de.knutwurst.knutcut.R.string.ui_path_open
                              else de.knutwurst.knutcut.R.string.ui_path_close,
                          ),
                      )
                  }
                  if (nodeSelected) {
                      val selNode = vm.selectedEditPath!!.nodes[selectedNodeIndex]
                      // Smooth / corner toggle.
                      IconButton(onClick = { vm.toggleSelectedNodeSmooth(selectedNodeIndex) }) {
                          val desc = if (selNode.smooth)
                              androidx.compose.ui.res.stringResource(de.knutwurst.knutcut.R.string.ui_node_corner)
                          else
                              androidx.compose.ui.res.stringResource(de.knutwurst.knutcut.R.string.ui_node_smooth)
                          Icon(
                              if (selNode.smooth) Icons.Default.RadioButtonChecked
                              else Icons.Default.RadioButtonUnchecked,
                              contentDescription = desc,
                          )
                      }
                      // Delete node.
                      IconButton(onClick = {
                          val prevCount = vm.selectedEditPath?.nodes?.size ?: 0
                          vm.deleteSelectedNode(selectedNodeIndex)
                          val newCount = vm.selectedEditPath?.nodes?.size ?: 0
                          // If the index is now out of bounds, adjust the selection.
                          if (newCount < prevCount) {
                              selectedNodeIndex = if (selectedNodeIndex >= newCount) newCount - 1 else selectedNodeIndex
                              if (selectedNodeIndex < 0) selectedNodeIndex = -1
                          }
                      }) {
                          Icon(
                              Icons.Default.Delete,
                              contentDescription = androidx.compose.ui.res.stringResource(de.knutwurst.knutcut.R.string.ui_node_delete),
                          )
                      }
                  }
              }
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

/** Keep a dragged world point on (or just around) the mat, so a node/handle can't be flung far off
 *  the work area and out of sight. Clamps to the mat rectangle expanded by [marginMm]. */
private fun clampToMat(p: Pt, matWidthMm: Double, matHeightMm: Double, marginMm: Double = 30.0): Pt =
    Pt(
        p.xMm.coerceIn(-marginMm, matWidthMm + marginMm),
        p.yMm.coerceIn(-marginMm, matHeightMm + marginMm),
    )

private fun rotateHandlePos(corners: List<Offset>, center: Offset, density: Float): Offset {
    val topMid = (corners[0] + corners[1]) / 2f
    val dir = topMid - center
    val len = dir.getDistance()
    val unit = if (len > 0f) dir / len else Offset(0f, -1f)
    return topMid + unit * (ROTATE_ARM_DP * density)
}

private fun hitTest(p: Offset, handles: List<Offset>, corners: List<Offset>, center: Offset, density: Float): Drag {
    val hit = HANDLE_HIT_DP * density
    if (corners.size == 4 && handles.size == 8) {
        // Test all 8 handles; corners (0-3) win ties over sides (4-7) since they're checked first.
        handles.forEachIndexed { i, h -> if ((h - p).getDistance() < hit) return Drag.Resize(i) }
        if ((rotateHandlePos(corners, center, density) - p).getDistance() < hit) return Drag.Rotate
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
