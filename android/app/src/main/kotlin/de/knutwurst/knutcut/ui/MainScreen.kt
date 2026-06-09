package de.knutwurst.knutcut.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.knutwurst.knutcut.BuildConfig
import de.knutwurst.knutcut.R
import de.knutwurst.knutcut.data.CircleDeform
import de.knutwurst.knutcut.data.DeformBaseline
import de.knutwurst.knutcut.data.DeformSpec
import de.knutwurst.knutcut.data.EnvelopeDeform
import de.knutwurst.knutcut.data.PathDeform
import de.knutwurst.knutcut.data.bendDeformDefault
import de.knutwurst.knutcut.data.circleDeformDefault
import de.knutwurst.knutcut.data.envelopeDeformDefault
import de.knutwurst.knutcut.data.Devices
import de.knutwurst.knutcut.data.DisplayUnit
import de.knutwurst.knutcut.data.Materials
import de.knutwurst.knutcut.data.Mats
import de.knutwurst.knutcut.data.display
import de.knutwurst.knutcut.data.ColorMode
import de.knutwurst.knutcut.data.PlotterSvgCategory
import de.knutwurst.knutcut.data.PlotterSvgItem
import de.knutwurst.knutcut.data.PlotterSvgLibrary
import de.knutwurst.knutcut.data.PlotterSvgPreviewCache
import de.knutwurst.knutcut.data.ThemeMode
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Shapes
import de.knutwurst.knutcut.ui.EditorTool
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(vm: KnutcutViewModel) {
    val context = LocalContext.current
    val version = appVersion(context)
    var hasBtPerm by remember { mutableStateOf(hasBluetoothPermission(context)) }
    var showDevices by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showMaterial by remember { mutableStateOf(false) }
    var showTransform by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showCut by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showNewConfirm by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var showDeform by remember { mutableStateOf(false) }
    val fontRepo = remember(context) { FontRepository(context) }
    var showChangelog by remember { mutableStateOf(false) }
    val changelogText = remember { runCatching { context.assets.open("changelog.md").bufferedReader().use { it.readText() } }.getOrDefault("") }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasBtPerm = hasBluetoothPermission(context)
        if (hasBtPerm) showDevices = true
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importUris(uris)
    }
    fun openFile() = openLauncher.launch(arrayOf("image/svg+xml", "text/xml", "text/plain", "application/octet-stream", "image/vnd.dxf", "application/dxf"))
    fun openDevices() { if (hasBtPerm) showDevices = true else permLauncher.launch(bluetoothPermissions()) }

    LaunchedEffect(vm.status) {
        if (!vm.cutting) vm.status?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(hasBtPerm) { vm.autoConnect() }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Top bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp).clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/knutwurst"))) }
                    },
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Knutcut", style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.ui_header_sub, version),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { showChangelog = true },
                    )
                }
                if (vm.hasDesign) {
                    IconButton(onClick = { showNewConfirm = true }, enabled = !vm.cutting) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = stringResource(R.string.ui_neu))
                    }
                }
                IconButton(onClick = { vm.undo() }, enabled = vm.canUndo && !vm.cutting) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.ui_undo))
                }
                IconButton(onClick = { vm.redo() }, enabled = vm.canRedo && !vm.cutting) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.ui_redo))
                }
                // DRAW mode toggle: active = filled/tinted button, inactive = plain icon button.
                val inDrawMode = vm.editorTool == EditorTool.DRAW
                if (inDrawMode) {
                    FilledTonalIconButton(onClick = { vm.editorTool = EditorTool.SELECT }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.ui_draw_mode_desc))
                    }
                } else {
                    IconButton(onClick = { vm.editorTool = EditorTool.DRAW }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.ui_draw_mode_desc))
                    }
                }
                Box {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ui_add))
                    }
                    AddMenu(
                        expanded = showAdd,
                        onDismiss = { showAdd = false },
                        onOpenFile = { showAdd = false; openFile() },
                        onLibrary = { showAdd = false; showLibrary = true },
                        onText = { showAdd = false; showText = true },
                        onShape = { vm.addLayer(it.first, listOf(it.second)); showAdd = false },
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.ui_settings))
                }
            }

            MatEditor(
                vm,
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
            )

            when {
                vm.cutting -> CuttingBar(vm)
                !vm.hasDesign -> EmptyState(onOpen = { openFile() }, onLibrary = { showLibrary = true }, onAddShape = { showAdd = true })
                else -> EditingBar(
                    vm,
                    onSize = { showTransform = true },
                    onLayers = { showLayers = true },
                    onMaterial = { showMaterial = true },
                    onConnectOrCut = { if (vm.connected) showCut = true else openDevices() },
                    onDelete = { showDeleteConfirm = true },
                    onDeform = { showDeform = true },
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.ui_del_layer_q)) },
            text = { Text(stringResource(R.string.ui_del_layer_msg)) },
            confirmButton = { TextButton(onClick = { vm.deleteSelected(); showDeleteConfirm = false }) { Text(stringResource(R.string.ui_delete)) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }
    if (showNewConfirm) {
        AlertDialog(
            onDismissRequest = { showNewConfirm = false },
            title = { Text(stringResource(R.string.ui_new_q)) },
            text = { Text(stringResource(R.string.ui_new_msg)) },
            confirmButton = { TextButton(onClick = { vm.clearAll(); showNewConfirm = false }) { Text(stringResource(R.string.ui_clear_all)) } },
            dismissButton = { TextButton(onClick = { showNewConfirm = false }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }
    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text(stringResource(R.string.ui_changelog)) },
            text = { Text(changelogText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showChangelog = false }) { Text(stringResource(R.string.ui_close)) } },
        )
    }
    if (vm.pendingImport != null) {
        AlertDialog(
            onDismissRequest = { vm.cancelImport() },
            title = { Text(stringResource(R.string.ui_load_design)) },
            text = { Text(stringResource(R.string.ui_import_msg)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.resolveImport(replace = true) }) { Text(stringResource(R.string.ui_replace)) }
                    TextButton(onClick = { vm.resolveImport(replace = false) }) { Text(stringResource(R.string.ui_add)) }
                }
            },
            dismissButton = { TextButton(onClick = { vm.cancelImport() }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }
    if (showDevices) {
        DeviceDialog(vm, hasBtPerm, onRequestPerm = { permLauncher.launch(bluetoothPermissions()) }, onDismiss = { showDevices = false })
    }
    LaunchedEffect(Unit) {
        if (vm.consumeReopenSettings()) showSettings = true // came back from a language-change recreate
        vm.cleanupUpdates()
        if (vm.autoUpdate) vm.checkForUpdate(silent = true)
    }

    // When a design is loaded, back press backgrounds the app rather than finishing the activity.
    // This keeps the ViewModel alive so a subsequent share from another app (e.g. CricutExport)
    // always hits onNewIntent on the live instance, which correctly shows the replace/add dialog.
    // Without this, the finished activity would be recreated with an empty ViewModel, silently
    // replacing the loaded design on the second share.
    BackHandler(enabled = vm.hasDesign) {
        (context as? android.app.Activity)?.moveTaskToBack(true)
    }
    vm.updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text(stringResource(R.string.ui_update_avail)) },
            text = {
                Column {
                    Text(stringResource(R.string.ui_update_dialog, appVersion(context), info.versionName))
                    if (info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(info.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.runUpdate() }, enabled = !vm.updateBusy) { Text(stringResource(R.string.ui_update)) } },
            dismissButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }
    if (showSettings) SettingsSheet(vm, version, onConnect = { showSettings = false; openDevices() }, onDismiss = { showSettings = false })
    if (showMaterial) MaterialSheet(vm, onDismiss = { showMaterial = false })
    if (showLayers) LayersSheet(vm, onDismiss = { showLayers = false })
    if (showTransform && vm.hasDesign) TransformDialog(vm, onDismiss = { showTransform = false })
    if (showCut && vm.hasDesign) CutSheet(vm, onDismiss = { showCut = false })
    if (showText) TextDialog(vm, fontRepo, onDismiss = { showText = false })
    if (showLibrary) LibrarySheet(vm, onDismiss = { showLibrary = false })
    if (showDeform && !vm.matSelected) DeformSheet(vm, onDismiss = { showDeform = false })
}

/** Add a text layer: type the text, pick a font (outline or single-stroke), choose a height. */
@Composable
private fun TextDialog(vm: KnutcutViewModel, fonts: FontRepository, onDismiss: () -> Unit) {
    val options = fonts.options
    var text by remember { mutableStateOf("") }
    var fontIndex by remember { mutableStateOf(0) }
    var height by remember { mutableStateOf(25) }
    var fontMenu by remember { mutableStateOf(false) }
    val chosen = options.getOrNull(fontIndex)
    val namePrefix = stringResource(R.string.ui_text_colon)
    val simplifiedMsg = stringResource(R.string.ui_text_added_simplified)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_add_text)) },
        text = {
            Column {
                OutlinedTextField(text, { if (it.length <= MAX_TEXT_CHARS) text = it }, label = { Text(stringResource(R.string.ui_text)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(onClick = { fontMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(chosen?.let { it.label + if (it.stroke) stringResource(R.string.ui_suffix_singleline) else "" } ?: stringResource(R.string.ui_pick_font), maxLines = 1)
                    }
                    DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                        options.forEachIndexed { i, o ->
                            DropdownMenuItem(
                                text = { Text(o.label + if (o.stroke) stringResource(R.string.ui_font_singleline_pen) else "") },
                                onClick = { fontIndex = i; fontMenu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (chosen?.stroke == true) stringResource(R.string.ui_singleline_hint)
                    else stringResource(R.string.ui_outline_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_height_mm), Modifier.weight(1f))
                    EditableStepper(height, 3, 300, step = 1) { height = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && chosen != null,
                onClick = {
                    val opt = chosen ?: return@TextButton
                    val result = opt.render(text, height.toDouble())
                    if (result.polylines.isNotEmpty()) {
                        val name = namePrefix + text.replace("\n", " ").trim().take(16)
                        vm.addLayer(name, result.polylines, if (opt.stroke) Tool.PEN else vm.tool)
                        if (result.simplified) vm.status = simplifiedMsg
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.ui_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

/** Add menu: open a file, add text, open the library, or drop in a primitive shape. */
@Composable
private fun AddMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit,
    onLibrary: () -> Unit,
    onText: () -> Unit,
    onShape: (Pair<String, de.knutwurst.knutcut.svgcore.Polyline>) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.ui_open_svg_plt)) }, onClick = onOpenFile)
        DropdownMenuItem(text = { Text(stringResource(R.string.ui_library_menu)) }, onClick = onLibrary)
        DropdownMenuItem(text = { Text(stringResource(R.string.ui_text_menu)) }, onClick = onText)
        HorizontalDivider()
        listOf(
            stringResource(R.string.ui_square) to Shapes.rect(40.0, 40.0),
            stringResource(R.string.ui_rect) to Shapes.rect(60.0, 40.0),
            stringResource(R.string.ui_circle) to Shapes.circle(40.0),
            stringResource(R.string.ui_triangle) to Shapes.regularPolygon(3, 40.0),
            stringResource(R.string.ui_pentagon) to Shapes.regularPolygon(5, 40.0),
            stringResource(R.string.ui_hexagon) to Shapes.regularPolygon(6, 40.0),
            stringResource(R.string.ui_star) to Shapes.star(5, 40.0),
        ).forEach { shape -> DropdownMenuItem(text = { Text(shape.first) }, onClick = { onShape(shape) }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LibrarySheet(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<PlotterSvgCategory?>(null) }
    val gridState = rememberLazyGridState()
    // The motif list is built on a background thread the first time the sheet opens.
    LaunchedEffect(Unit) { vm.loadLibrary() }
    val all = vm.libraryItems
    val items = remember(query, category, all) {
        all.filter { (category == null || it.category == category) && it.matches(query) }
    }
    LaunchedEffect(query, category) { gridState.scrollToItem(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.96f).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.ui_library), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.ui_library_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_clear_search))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LibraryCategoryPicker(category = category, onCategory = { category = it })
            if (all.isNotEmpty()) {
                Text(
                    stringResource(R.string.ui_library_count, items.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    all.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    items.isEmpty() -> Text(
                        stringResource(R.string.ui_library_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(vertical = 20.dp),
                        textAlign = TextAlign.Center,
                    )
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 132.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(items, key = { it.id }) { item ->
                                LibraryItem(
                                    item = item,
                                    filled = vm.colorMode == ColorMode.COLOR,
                                    onClick = {
                                        vm.addLibrarySvg(item.name, item.svg)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                        LibraryGridScrollbar(gridState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(6.dp))
                    }
                }
            }
            Text(
                stringResource(R.string.ui_library_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun LibraryCategoryPicker(category: PlotterSvgCategory?, onCategory: (PlotterSvgCategory?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.ui_category) + ": " + (category?.let { stringResource(it.labelRes) } ?: stringResource(R.string.ui_all)),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ui_all)) },
                onClick = {
                    onCategory(null)
                    expanded = false
                },
            )
            PlotterSvgLibrary.categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(stringResource(cat.labelRes)) },
                    onClick = {
                        onCategory(cat)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryGridScrollbar(state: LazyGridState, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val thumb = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    Canvas(modifier) {
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (total <= 0 || visible.isEmpty()) return@Canvas
        val first = visible.first().index
        val last = visible.last().index
        val visibleCount = (last - first + 1).coerceAtLeast(1)
        val thumbHeight = (size.height * visibleCount / total).coerceIn(24f, size.height)
        val travel = (size.height - thumbHeight).coerceAtLeast(0f)
        val maxFirst = (total - visibleCount).coerceAtLeast(1)
        val top = travel * (first.toFloat() / maxFirst).coerceIn(0f, 1f)
        val barWidth = size.width.coerceAtMost(5f)
        val left = (size.width - barWidth) / 2f
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        drawRoundRect(track, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(barWidth, size.height), cornerRadius = radius)
        drawRoundRect(thumb, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(barWidth, thumbHeight), cornerRadius = radius)
    }
}

@Composable
private fun LibraryItem(item: PlotterSvgItem, filled: Boolean, onClick: () -> Unit) {
    // Parse off the main thread and memoise; scrolling never blocks the UI or re-parses a seen item.
    val polylines by produceState(initialValue = emptyList<Polyline>(), item.id) {
        value = withContext(Dispatchers.Default) { PlotterSvgPreviewCache.preview(item.id, item.svg) }
    }
    // Follows the global display toggle: "Farbig" → filled silhouette, "Nur Outline" → toolpath.
    // The motifs carry no colour of their own (currentColor), so the fill uses a neutral theme tint.
    val color = if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().height(128.dp).border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(8.dp),
        ).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                drawLibraryPreview(polylines, color, filled)
            }
            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Renders the motif following the global display mode. [filled] (the "Farbig" setting) shows the
 *  silhouette filled with an even-odd rule so interior holes carve through, matching the cut-out
 *  piece; otherwise each contour is stroked — the toolpath the plotter traces. Open contours are
 *  always stroked. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLibraryPreview(polylines: List<Polyline>, color: Color, filled: Boolean) {
    if (polylines.isEmpty()) return
    val points = polylines.flatMap { it.points }
    val bounds = Bounds.ofOrNull(points) ?: return
    if (bounds.widthMm <= 0.0 || bounds.heightMm <= 0.0) return
    val pad = 8f
    val usableW = (size.width - pad * 2).coerceAtLeast(1f)
    val usableH = (size.height - pad * 2).coerceAtLeast(1f)
    val scale = min(usableW / bounds.widthMm.toFloat(), usableH / bounds.heightMm.toFloat())
    val ox = (size.width - bounds.widthMm.toFloat() * scale) / 2f - bounds.minX.toFloat() * scale
    val oy = (size.height - bounds.heightMm.toFloat() * scale) / 2f - bounds.minY.toFloat() * scale

    fun Path.trace(pl: Polyline) {
        val first = pl.points.first()
        moveTo(first.xMm.toFloat() * scale + ox, first.yMm.toFloat() * scale + oy)
        for (i in 1 until pl.points.size) {
            val p = pl.points[i]
            lineTo(p.xMm.toFloat() * scale + ox, p.yMm.toFloat() * scale + oy)
        }
        if (pl.closed) close()
    }

    if (filled) {
        val fillPath = Path().apply { fillType = PathFillType.EvenOdd }
        val openPath = Path()
        for (pl in polylines) {
            if (pl.points.size < 2) continue
            (if (pl.closed) fillPath else openPath).trace(pl)
        }
        drawPath(fillPath, color)
        drawPath(openPath, color, style = Stroke(width = 2.4f))
    } else {
        for (pl in polylines) {
            if (pl.points.size < 2) continue
            val path = Path().apply { trace(pl) }
            drawPath(path, color, style = Stroke(width = 2.4f))
        }
    }
}

@Composable
private fun EmptyState(onOpen: () -> Unit, onLibrary: () -> Unit, onAddShape: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.ui_no_design), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.ui_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_file_short), maxLines = 1)
            }
            OutlinedButton(onClick = onLibrary, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_library), maxLines = 1)
            }
            OutlinedButton(onClick = onAddShape, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_shape_short), maxLines = 1)
            }
        }
    }
}

@Composable
private fun CuttingBar(vm: KnutcutViewModel) {
    Text(vm.status ?: "", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    if (vm.progress > 0f) LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    OutlinedButton(onClick = { vm.cancelCut() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.ui_cancel)) }
}

/** A compact icon button used in the editing toolbar (Canva/Figma style). */
@Composable
private fun IconAction(label: String, icon: ImageVector, rotate: Float = 0f, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp).rotate(rotate))
    }
}

/** The contextual bar shown while editing: a single icon toolbar, then material + a cut summary. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditingBar(
    vm: KnutcutViewModel,
    onSize: () -> Unit,
    onLayers: () -> Unit,
    onMaterial: () -> Unit,
    onConnectOrCut: () -> Unit,
    onDelete: () -> Unit,
    onDeform: () -> Unit,
) {
    // Per-layer actions only make sense with a layer selected; greyed out when the mat is selected.
    val perLayer = !vm.matSelected
    val layer = vm.layers.getOrNull(vm.selectedLayer)
    // The nodes button is available when the selected layer has an editPath OR exactly one polyline
    // (which can be converted on tap).
    val canEditNodes = perLayer && layer != null && (layer.editPath != null || layer.polylines.size == 1)
    val inNodesMode = vm.editorTool == EditorTool.NODES
    // One toolbar row, spread across the full width (wraps to a new row if it can't fit).
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconAction(stringResource(R.string.ui_size_angle), Icons.Default.AspectRatio, enabled = perLayer, onClick = onSize)
        IconAction(stringResource(R.string.ui_rotate90), Icons.AutoMirrored.Filled.RotateRight, enabled = perLayer) { vm.rotate90() }
        IconAction(stringResource(R.string.ui_flip_h), Icons.Default.Flip, enabled = perLayer) { vm.mirrorSelectedHorizontal() }
        IconAction(stringResource(R.string.ui_flip_v), Icons.Default.Flip, rotate = 90f, enabled = perLayer) { vm.mirrorSelectedVertical() }
        IconAction(stringResource(R.string.ui_duplicate), Icons.Default.ContentCopy, enabled = perLayer) { vm.duplicateSelected() }
        IconAction(stringResource(R.string.ui_deform), Icons.Default.AutoFixHigh, enabled = perLayer, onClick = onDeform)
        // Node editor toggle: enters NODES mode (converting first if needed) or returns to SELECT.
        if (inNodesMode && canEditNodes) {
            // Active state: filled tonal button
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { vm.editorTool = EditorTool.SELECT },
                enabled = canEditNodes,
            ) {
                Icon(Icons.Default.Timeline, contentDescription = stringResource(R.string.ui_nodes_desc))
            }
        } else {
            IconAction(stringResource(R.string.ui_nodes), Icons.Default.Timeline, enabled = canEditNodes) {
                if (layer?.editPath == null) vm.convertSelectedToEditablePath()
                vm.editorTool = EditorTool.NODES
            }
        }
        IconAction(stringResource(R.string.ui_delete), Icons.Default.Delete, enabled = perLayer, onClick = onDelete)
        IconAction(
            if (vm.matSelected) stringResource(R.string.ui_reset_all) else stringResource(R.string.ui_reset_layer),
            Icons.Default.Refresh,
        ) { if (vm.matSelected) vm.resetAll() else vm.resetSelectedPlacement() }
        IconAction(stringResource(R.string.ui_reset_view), Icons.Default.CenterFocusStrong) { vm.resetView() }
    }

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onLayers, modifier = Modifier.weight(1f)) { Text(pluralStringResource(R.plurals.ui_layers_title, vm.layers.size, vm.layers.size), maxLines = 1) }
        OutlinedButton(onClick = onMaterial, modifier = Modifier.weight(1f)) { Text(vm.material.display(), maxLines = 1) }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onConnectOrCut, enabled = vm.hasDesign && !vm.cutting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(
            when {
                !vm.connected -> stringResource(R.string.ui_connect_plotter)
                else -> vm.cutActionLabel()
            }
        )
    }
}

/** Alignment as two rows of standard align icons (Figma/Office style). */
@Composable
private fun AlignmentControls(vm: KnutcutViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ui_horizontal), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
            OutlinedIconButton(onClick = { vm.alignHorizontal(-1) }) { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, stringResource(R.string.ui_left)) }
            OutlinedIconButton(onClick = { vm.alignHorizontal(0) }) { Icon(Icons.Default.FormatAlignCenter, stringResource(R.string.ui_center)) }
            OutlinedIconButton(onClick = { vm.alignHorizontal(1) }) { Icon(Icons.AutoMirrored.Filled.FormatAlignRight, stringResource(R.string.ui_right)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ui_vertical), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
            OutlinedIconButton(onClick = { vm.alignVertical(-1) }) { Icon(Icons.Default.VerticalAlignTop, stringResource(R.string.ui_top)) }
            OutlinedIconButton(onClick = { vm.alignVertical(0) }) { Icon(Icons.Default.VerticalAlignCenter, stringResource(R.string.ui_center)) }
            OutlinedIconButton(onClick = { vm.alignVertical(1) }) { Icon(Icons.Default.VerticalAlignBottom, stringResource(R.string.ui_bottom)) }
        }
    }
}

private enum class DeformMode { CIRCLE, ARC, ENVELOPE }

/**
 * Sheet for non-destructive geometric deformation of the selected layer. Supports circle warp
 * and arc/bend warp; structured so additional modes can be added as tabs without a full rewrite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("NAME_SHADOWING")
private fun DeformSheet(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    val sourceBounds = vm.selectedDeformSourceBounds() ?: return

    val existingDeform = vm.layers.getOrNull(vm.selectedLayer)?.deform

    // Determine the opening mode and seed control values from the existing spec (if any).
    val openMode = when (existingDeform) {
        is PathDeform     -> DeformMode.ARC
        is EnvelopeDeform -> DeformMode.ENVELOPE
        else              -> DeformMode.CIRCLE
    }

    var mode by remember { mutableStateOf(openMode) }

    // Circle controls
    val circleInitial = (existingDeform as? CircleDeform) ?: circleDeformDefault(sourceBounds)
    var radius by remember { mutableStateOf(circleInitial.radiusMm.toInt().coerceIn(5, 500)) }
    var startAngle by remember { mutableStateOf(circleInitial.startAngleDeg.toInt().coerceIn(0, 359)) }
    var clockwise by remember { mutableStateOf(circleInitial.clockwise) }

    // Arc controls
    val arcInitial = (existingDeform as? PathDeform)
    var curvature by remember { mutableStateOf(arcInitial?.let {
        // Recover the curvature from the midpoint node offset.
        val baselineY = when (it.baseline) {
            DeformBaseline.TOP    -> sourceBounds.minY
            DeformBaseline.CENTER -> (sourceBounds.minY + sourceBounds.maxY) / 2.0
            DeformBaseline.BOTTOM -> sourceBounds.maxY
        }
        val midAnchorY = it.guide.getOrNull(1)?.anchor?.yMm ?: baselineY
        // curvatureMm = baselineY - midAnchorY (positive = bows up)
        (baselineY - midAnchorY).toInt()
    } ?: 0) }

    // Shared baseline state — seeded from whichever spec is active.
    var baseline by remember {
        mutableStateOf(
            (existingDeform as? CircleDeform)?.baseline
                ?: (existingDeform as? PathDeform)?.baseline
                ?: DeformBaseline.CENTER
        )
    }

    fun circleSpec() = CircleDeform(
        centerXMm = (sourceBounds.minX + sourceBounds.maxX) / 2.0,
        centerYMm = (sourceBounds.minY + sourceBounds.maxY) / 2.0,
        radiusMm = radius.toDouble(),
        startAngleDeg = startAngle.toDouble(),
        clockwise = clockwise,
        baseline = baseline,
    )

    fun arcSpec() = bendDeformDefault(sourceBounds, curvature.toDouble(), baseline)

    fun currentSpec(): DeformSpec? = when (mode) {
        DeformMode.CIRCLE   -> circleSpec()
        DeformMode.ARC      -> arcSpec()
        DeformMode.ENVELOPE -> null  // envelope is applied on mode-select, not here
    }

    fun applyLive() { val spec = currentSpec(); if (spec != null) vm.setSelectedDeform(spec, pushHistory = false) }

    // Push one history snapshot on open (so a single Undo reverts all changes from this sheet).
    LaunchedEffect(Unit) {
        if (mode != DeformMode.ENVELOPE) {
            val spec = currentSpec()
            if (spec != null) vm.setSelectedDeform(spec, pushHistory = existingDeform == null)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.ui_deform), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            // Mode selector: "Auf Kreis" / "Bogen" / "Hülle"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == DeformMode.CIRCLE,
                    onClick = { if (mode != DeformMode.CIRCLE) { mode = DeformMode.CIRCLE; applyLive() } },
                    label = { Text(stringResource(R.string.ui_deform_circle)) },
                )
                FilterChip(
                    selected = mode == DeformMode.ARC,
                    onClick = { if (mode != DeformMode.ARC) { mode = DeformMode.ARC; applyLive() } },
                    label = { Text(stringResource(R.string.ui_deform_arc)) },
                )
                FilterChip(
                    selected = mode == DeformMode.ENVELOPE,
                    onClick = {
                        if (mode != DeformMode.ENVELOPE) {
                            // Apply the identity envelope, switch the editor tool, and close the sheet
                            // so the user can drag the corners directly on the canvas.
                            val defaultEnvelope = envelopeDeformDefault(sourceBounds)
                            vm.setSelectedDeform(defaultEnvelope, pushHistory = true)
                            vm.editorTool = EditorTool.ENVELOPE
                            onDismiss()
                        }
                    },
                    label = { Text(stringResource(R.string.ui_deform_envelope)) },
                )
            }

            Spacer(Modifier.height(12.dp))

            when (mode) {
                DeformMode.CIRCLE -> {
                    // Radius
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ui_deform_radius_mm), Modifier.weight(1f))
                        EditableStepper(radius, 5, 500, step = 1) { radius = it; applyLive() }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Start angle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ui_deform_start_angle), Modifier.weight(1f))
                        EditableStepper(startAngle, 0, 359, step = 5) { startAngle = it; applyLive() }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Direction toggle (inside / outside the circle)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ui_deform_direction), Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !clockwise,
                                onClick = { clockwise = false; applyLive() },
                                label = { Text(stringResource(R.string.ui_deform_outside)) },
                            )
                            FilterChip(
                                selected = clockwise,
                                onClick = { clockwise = true; applyLive() },
                                label = { Text(stringResource(R.string.ui_deform_inside)) },
                            )
                        }
                    }
                }

                DeformMode.ARC -> {
                    // Curvature slider: -200..200 mm, 0 = straight.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ui_deform_curvature), Modifier.weight(1f))
                        EditableStepper(curvature, -200, 200, step = 5) { curvature = it; applyLive() }
                    }
                }

                DeformMode.ENVELOPE -> {
                    // Envelope mode uses the canvas directly; just show the hint.
                    Text(
                        stringResource(R.string.ui_deform_envelope_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (mode != DeformMode.ENVELOPE) {
                Spacer(Modifier.height(8.dp))

                // Shared baseline chips (not applicable to envelope mode)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_deform_baseline), Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = baseline == DeformBaseline.TOP,
                            onClick = { baseline = DeformBaseline.TOP; applyLive() },
                            label = { Text(stringResource(R.string.ui_top)) },
                        )
                        FilterChip(
                            selected = baseline == DeformBaseline.CENTER,
                            onClick = { baseline = DeformBaseline.CENTER; applyLive() },
                            label = { Text(stringResource(R.string.ui_center)) },
                        )
                        FilterChip(
                            selected = baseline == DeformBaseline.BOTTOM,
                            onClick = { baseline = DeformBaseline.BOTTOM; applyLive() },
                            label = { Text(stringResource(R.string.ui_bottom)) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = {
                    vm.clearSelectedDeform()
                    if (vm.editorTool == EditorTool.ENVELOPE) vm.editorTool = EditorTool.SELECT
                    onDismiss()
                },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(stringResource(R.string.ui_deform_remove))
            }
        }
    }
}

/** Pre-cut step: confirm what will run (and, with several layers, all vs. only the selected one). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CutSheet(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.ui_ready_to_plot), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (vm.layers.size > 1) {
                Text(stringResource(R.string.ui_extent), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !vm.cutSelectedOnly, onClick = { vm.changeCutSelectedOnly(false) }, label = { Text(stringResource(R.string.ui_all_layers)) })
                    FilterChip(selected = vm.cutSelectedOnly, onClick = { vm.changeCutSelectedOnly(true) }, label = { Text(stringResource(R.string.ui_only_selected)) })
                }
                Spacer(Modifier.height(10.dp))
            }
            // Compute the cut set once for the whole sheet (avoids re-filtering the layers repeatedly).
            val ls = vm.cutLayers()
            val knifeN = ls.count { it.tool == Tool.KNIFE }
            val penN = ls.size - knifeN
            val single = if (ls.isEmpty()) null else if (penN == 0) Tool.KNIFE else if (knifeN == 0) Tool.PEN else null
            val withinMat = vm.designWithinMat()
            // When every layer uses the same tool, offer a quick draw/cut switch. When the layers mix
            // tools, show how many will be cut and how many drawn instead.
            if (single != null) {
                val knife = single == Tool.KNIFE
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (knife) stringResource(R.string.ui_tool_knife) else stringResource(R.string.ui_tool_pen),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = knife, onCheckedChange = { vm.setCutTool(if (it) Tool.KNIFE else Tool.PEN) })
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Text(pluralStringResource(R.plurals.ui_cut_n, knifeN, knifeN) + " · " + pluralStringResource(R.plurals.ui_draw_n, penN, penN), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            val ebenen = pluralStringResource(R.plurals.ui_layers_count, ls.size, ls.size)
            val toolLabel = when (single) { Tool.KNIFE -> stringResource(R.string.ui_knife); Tool.PEN -> stringResource(R.string.ui_pen); else -> if (ls.isEmpty()) "–" else stringResource(R.string.ui_knife_pen) }
            Text(
                "$ebenen · $toolLabel",
                style = MaterialTheme.typography.bodyLarge,
                color = if (knifeN > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.ui_material_label, vm.material.display()), style = MaterialTheme.typography.bodySmall)
            vm.extentReadout(ls)?.let { Text(stringResource(R.string.ui_needed_material, it), style = MaterialTheme.typography.bodySmall) }
            if (!withinMat) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.ui_design_off_area_warn),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_copies), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.ui_copies_hint), style = MaterialTheme.typography.bodySmall)
                }
                EditableStepper(vm.cutCopies, 1, 10, step = 1) { vm.changeCutCopies(it) }
            }
            Spacer(Modifier.height(14.dp))
            val label = when (single) { Tool.KNIFE -> stringResource(R.string.ui_cut); Tool.PEN -> stringResource(R.string.ui_draw); else -> stringResource(R.string.ui_plot) }
            Button(
                onClick = { onDismiss(); vm.cut() },
                enabled = withinMat && ls.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.ui_start_label, label)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersSheet(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    var allowRotate by remember { mutableStateOf(true) }
    var renaming by remember { mutableStateOf(-1) }
    var renameText by remember { mutableStateOf("") }
    if (renaming in vm.layers.indices) {
        AlertDialog(
            onDismissRequest = { renaming = -1 },
            title = { Text(stringResource(R.string.ui_rename)) },
            text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.renameLayer(renaming, renameText); renaming = -1 }) { Text(stringResource(R.string.ui_apply)) } },
            dismissButton = { TextButton(onClick = { renaming = -1 }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(pluralStringResource(R.plurals.ui_layers_title, vm.layers.size, vm.layers.size), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val marked = vm.markedLayers.count { it in vm.layers.indices }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.splitLayers() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ui_explode), maxLines = 1) }
                OutlinedButton(
                    onClick = { if (marked >= 2) vm.mergeMarked() else vm.mergeLayers() },
                    modifier = Modifier.weight(1f),
                ) { Text(if (marked >= 2) stringResource(R.string.ui_merge_n, marked) else stringResource(R.string.ui_merge_all), maxLines = 1) }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { vm.mergeByColor() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_group_by_color), maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            var cols by remember { mutableStateOf(2) }
            var rows by remember { mutableStateOf(2) }
            Text(stringResource(R.string.ui_tiles), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditableStepper(cols, 1, 20, step = 1) { cols = it }
                Text("×")
                EditableStepper(rows, 1, 20, step = 1) { rows = it }
            }
            OutlinedButton(onClick = { vm.tileSelected(cols, rows) }, enabled = vm.selectedLayer in vm.layers.indices, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_tile_selected), maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.ui_save_material), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Button(onClick = { vm.autoArrange(allowRotate) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_auto_arrange))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allowRotate, onCheckedChange = { allowRotate = it })
                Text(stringResource(R.string.ui_allow_rot90), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.ui_align_on_mat), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            AlignmentControls(vm)
            Spacer(Modifier.height(12.dp))
            if (vm.layers.size > 1) {
                Text(stringResource(R.string.ui_layers_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
            }
            vm.layers.forEachIndexed { i, layer ->
                val selected = vm.selectedLayer == i
                // Representative colour: per-layer colorArgb, or the first non-null polyline colour.
                val layerColor = layer.colorArgb ?: layer.colorList().firstOrNull { it != null }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vm.layers.size > 1) {
                            Checkbox(checked = i in vm.markedLayers, onCheckedChange = { vm.toggleMarked(i) })
                        }
                        IconButton(onClick = { vm.toggleLayerVisible(i) }) {
                            Icon(
                                if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = stringResource(R.string.ui_visible),
                            )
                        }
                        if (layerColor != null) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(layerColor))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            layer.name,
                            maxLines = 1,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f).clickable { vm.selectLayer(i) },
                        )
                        FilterChip(selected = layer.tool == Tool.PEN, onClick = { vm.setLayerTool(i, Tool.PEN) }, label = { Text(stringResource(R.string.ui_pen)) })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = layer.tool == Tool.KNIFE, onClick = { vm.setLayerTool(i, Tool.KNIFE) }, label = { Text(stringResource(R.string.ui_knife)) })
                    }
                    // Rename/reorder for the selected layer go on their own row, so the row above never
                    // overflows and pushes the tool chips off screen.
                    if (selected) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { renameText = layer.name; renaming = i }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.ui_rename), modifier = Modifier.size(20.dp))
                            }
                            if (vm.layers.size > 1) {
                                IconButton(onClick = { vm.moveLayer(i, -1) }, enabled = i > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.ui_move_up))
                                }
                                IconButton(onClick = { vm.moveLayer(i, 1) }, enabled = i < vm.layers.size - 1) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.ui_move_down))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialSheet(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    var manage by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.ui_material_pressure), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val recent = vm.recentMaterials()
            if (recent.isNotEmpty()) {
                Text(stringResource(R.string.ui_recent), style = MaterialTheme.typography.labelLarge)
                recent.forEach { m ->
                    val selected = vm.material.id == m.id
                    Text(
                        m.display(),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { vm.selectMaterial(m) }.padding(vertical = 10.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            Text(stringResource(R.string.ui_all_materials), style = MaterialTheme.typography.labelLarge)
            vm.allMaterials().forEach { m ->
                val selected = vm.material.id == m.id
                Text(
                    m.display(),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().clickable { vm.selectMaterial(m) }.padding(vertical = 10.dp),
                )
            }
            TextButton(onClick = { manage = true }) { Text(stringResource(R.string.ui_manage_materials)) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.ui_tool), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Order matches the plotter: pen holder left, knife holder right.
                listOf(Tool.PEN, Tool.KNIFE).forEach { t ->
                    FilterChip(
                        selected = vm.activeTool == t,
                        onClick = { vm.setActiveTool(t) },
                        label = { Text(if (t == Tool.KNIFE) stringResource(R.string.ui_knife) else stringResource(R.string.ui_pen)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val penSelected = vm.activeTool == Tool.PEN
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (penSelected) stringResource(R.string.ui_pressure_pen) else stringResource(R.string.ui_pressure_knife), Modifier.weight(1f))
                EditableStepper(if (penSelected) vm.penForce else vm.force, Materials.FORCE_MIN, Materials.FORCE_MAX, step = 5) {
                    if (penSelected) vm.changePenForce(it) else vm.changeForce(it)
                }
            }
        }
    }
    if (manage) MaterialManageDialog(vm, onDismiss = { manage = false })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(vm: KnutcutViewModel, version: String, onConnect: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var showChangelog by remember { mutableStateOf(false) }
    if (showChangelog) {
        val text = remember { runCatching { ctx.assets.open("changelog.md").bufferedReader().use { it.readText() } }.getOrDefault("") }
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text(stringResource(R.string.ui_changelog)) },
            text = { Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showChangelog = false }) { Text(stringResource(R.string.ui_close)) } },
        )
    }
    // Open fully and stay there: expanding a section must not snap the sheet back to half height.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.ui_settings), style = MaterialTheme.typography.titleMedium)

            SettingsGroup(stringResource(R.string.ui_project), initiallyExpanded = true) {
                vm.projectName?.let {
                    Text(stringResource(R.string.ui_current_file, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                }
                val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { vm.saveProject(it) } }
                val loadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.loadProject(it) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { loadLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ui_load), maxLines = 1) }
                    OutlinedButton(onClick = { saveLauncher.launch("knutcut.kcp") }, enabled = vm.hasDesign, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ui_save), maxLines = 1) }
                }
            }

            SettingsGroup(stringResource(R.string.ui_grp_cut)) {
                Text(stringResource(R.string.ui_mat), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mats.all.forEach { m -> FilterChip(selected = vm.mat == m, onClick = { vm.selectMat(m) }, label = { Text(m.name) }) }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_origin_offset), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.ui_origin_offset_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    EditableStepper(vm.originOffsetMm, 0, 100, step = 1) { vm.changeOriginOffset(it) }
                }
                if (vm.model.family == de.knutwurst.knutcut.data.PlotterFamily.BLE_SILHOUETTE) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ui_sil_speed), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.ui_speed_hint, vm.silhouetteSpeedMax), style = MaterialTheme.typography.bodySmall)
                        }
                        EditableStepper(vm.silhouetteSpeed, 1, vm.silhouetteSpeedMax, step = 1) { vm.changeSilhouetteSpeed(it) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_optimize_order), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.ui_optimize_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = vm.optimizeCutOrder, onCheckedChange = { vm.changeOptimizeCutOrder(it) })
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_dragknife), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.ui_dragknife_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = vm.dragKnifeComp, onCheckedChange = { vm.changeDragKnifeComp(it) })
                }
                if (vm.dragKnifeComp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ui_blade_offset), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        EditableStepper(vm.bladeOffset, 0, 40, step = 1) { vm.changeBladeOffset(it) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.resetCutSettings() }) { Text(stringResource(R.string.ui_to_default)) }
                }
            }

            SettingsGroup(stringResource(R.string.ui_grp_editor)) {
                Text(stringResource(R.string.ui_snap), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.ui_snap_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0f to stringResource(R.string.ui_off), 0.1f to "0,1 mm", 1f to "1 mm", 5f to "5 mm", 10f to "1 cm").forEach { (v, lbl) ->
                        FilterChip(selected = vm.snapMm == v, onClick = { vm.changeSnap(v) }, label = { Text(lbl) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_align_guides), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.ui_align_guides_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = vm.alignGuides, onCheckedChange = { vm.changeAlignGuides(it) })
                }
            }

            SettingsGroup(stringResource(R.string.ui_grp_display)) {
                Text(stringResource(R.string.ui_unit), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DisplayUnit.entries.forEach { u -> FilterChip(selected = vm.displayUnit == u, onClick = { vm.changeDisplayUnit(u) }, label = { Text(u.label) }) }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.ui_colors), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = vm.colorMode == ColorMode.OUTLINE, onClick = { vm.changeColorMode(ColorMode.OUTLINE) }, label = { Text(stringResource(R.string.ui_outline_only)) })
                    FilterChip(selected = vm.colorMode == ColorMode.COLOR, onClick = { vm.changeColorMode(ColorMode.COLOR) }, label = { Text(stringResource(R.string.ui_colorful)) })
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.ui_appearance), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.SYSTEM to stringResource(R.string.ui_system), ThemeMode.LIGHT to stringResource(R.string.ui_light), ThemeMode.DARK to stringResource(R.string.ui_dark)).forEach { (m, lbl) ->
                        FilterChip(selected = vm.themeMode == m, onClick = { vm.selectTheme(m) }, label = { Text(lbl) })
                    }
                }
            }

            SettingsGroup(stringResource(R.string.ui_grp_language)) {
                val langActivity = LocalContext.current as? android.app.Activity
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("system", stringResource(R.string.ui_system), null as Int?),
                        Triple("de", "Deutsch", R.drawable.flag_de),
                        Triple("en", "English", R.drawable.flag_gb),
                    ).forEach { (code, lbl, flag) ->
                        FilterChip(
                            selected = vm.appLanguage == code,
                            onClick = { if (vm.appLanguage != code) { vm.changeAppLanguage(code); langActivity?.recreate() } },
                            leadingIcon = {
                                if (flag != null) Image(painterResource(flag), contentDescription = null, modifier = Modifier.size(20.dp, 14.dp))
                                else Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            label = { Text(lbl) },
                        )
                    }
                }
            }

            SettingsGroup(stringResource(R.string.ui_plotter)) {
                Text(
                    when {
                        vm.connecting -> stringResource(R.string.ui_connecting)
                        vm.connectedToPlotter -> stringResource(R.string.ui_connected, vm.model.displayName)
                        vm.connectedToSilhouette -> stringResource(R.string.ui_connected_ble2, vm.model.displayName)
                        vm.connected -> stringResource(R.string.ui_connected_unsupported, vm.device?.name ?: "?")
                        else -> stringResource(R.string.ui_not_connected, vm.model.displayName)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConnect) { Text(if (vm.connected) stringResource(R.string.ui_other_plotter) else stringResource(R.string.ui_connect)) }
                    if (vm.connected) OutlinedButton(onClick = { vm.disconnect() }) { Text(stringResource(R.string.ui_disconnect)) }
                }
            }

            SettingsGroup(stringResource(R.string.ui_grp_updates)) {
                OutlinedButton(onClick = { vm.checkForUpdate(silent = false) }, enabled = !vm.updateBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.updateBusy) stringResource(R.string.ui_updating) else stringResource(R.string.ui_check_update), maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_auto_check), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = vm.autoUpdate, onCheckedChange = { vm.changeAutoUpdate(it) })
                }
            }

            SettingsGroup(stringResource(R.string.ui_grp_donate)) {
                val donateCtx = LocalContext.current
                OutlinedButton(
                    onClick = { runCatching { donateCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/oliverkoester"))) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(painterResource(R.drawable.ic_paypal), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_donate_paypal))
                }
            }

            SettingsGroup(stringResource(R.string.ui_about)) {
                val aboutCtx = LocalContext.current
                TextButton(
                    onClick = { runCatching { aboutCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/knutwurst/knutcut"))) } },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Text(stringResource(R.string.ui_about_github)) }
                Text(stringResource(R.string.ui_about_fonts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showChangelog = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.ui_changelog))
                }
                Text(stringResource(R.string.ui_footer_version, version), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = {
                        vm.shutdown()
                        (aboutCtx as? android.app.Activity)?.finishAndRemoveTask()
                        kotlin.system.exitProcess(0)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.ui_exit_app))
                    }
                }
            }
        }
    }
}

/** A titled, collapsible card that groups related settings, so the sheet reads as distinct
 *  sections the user can fold away. Tap the header to expand/collapse. */
@Composable
private fun SettingsGroup(title: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Spacer(Modifier.height(12.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(bottom = 8.dp)) { content() }
            }
        }
    }
}

@Composable
private fun TransformDialog(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    val b = vm.bounds
    val unit = vm.displayUnit
    fun fmt(mm: Double) = String.format(Locale.GERMAN, "%.2f", unit.fromMm(mm))
    var w by remember { mutableStateOf(fmt((b?.widthMm ?: 0.0) * vm.scaleX)) }
    var h by remember { mutableStateOf(fmt((b?.heightMm ?: 0.0) * vm.scaleY)) }
    var ang by remember { mutableStateOf(vm.rotationDeg.toInt().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_size_angle)) },
        text = {
            Column {
                OutlinedTextField(w, { w = it }, singleLine = true, label = { Text(stringResource(R.string.ui_width_unit, unit.label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(h, { h = it }, singleLine = true, label = { Text(stringResource(R.string.ui_height_unit, unit.label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(ang, { ang = it }, singleLine = true, label = { Text(stringResource(R.string.ui_angle)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ww = w.replace(',', '.').toDoubleOrNull()
                val hh = h.replace(',', '.').toDoubleOrNull()
                val aa = ang.replace(',', '.').toDoubleOrNull()
                if (ww != null && hh != null && ww > 0 && hh > 0) vm.setSelectedSizeMm(unit.toMm(ww), unit.toMm(hh))
                if (aa != null) vm.setSelectedRotation(aa)
                onDismiss()
            }) { Text(stringResource(R.string.ui_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
private fun MaterialManageDialog(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    var editId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var force by remember { mutableStateOf(180) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close)) } },
        title = { Text(stringResource(R.string.ui_materials)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val custom = vm.customMaterials
                if (custom.isEmpty()) {
                    Text(stringResource(R.string.ui_no_custom_materials), style = MaterialTheme.typography.bodySmall)
                } else {
                    custom.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ui_material_item, m.name, m.force), maxLines = 1, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editId = m.id; name = m.name; force = m.force }) { Text(stringResource(R.string.ui_edit)) }
                            TextButton(onClick = { vm.deleteMaterial(m.id) }) { Text(stringResource(R.string.ui_delete)) }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(if (editId == null) stringResource(R.string.ui_new_material) else stringResource(R.string.ui_edit_material), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.ui_name)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_knife_pressure), Modifier.weight(1f))
                    EditableStepper(force, Materials.FORCE_MIN, Materials.FORCE_MAX, step = 5) { force = it }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = name.isNotBlank(), onClick = {
                        val id = editId
                        if (id == null) vm.addMaterial(name, force) else vm.updateMaterial(id, name, force)
                        editId = null; name = ""; force = 180
                    }) { Text(if (editId == null) stringResource(R.string.ui_add) else stringResource(R.string.ui_save)) }
                    if (editId != null) OutlinedButton(onClick = { editId = null; name = ""; force = 180 }) { Text(stringResource(R.string.ui_neu)) }
                }
            }
        },
    )
}

/** A number control you can type into directly, with − / + buttons for fine steps. */
@Composable
private fun EditableStepper(value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (text.toIntOrNull() != value) text = value.toString() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onChange((value - step).coerceIn(min, max)) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−") }
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                val digits = t.filter { it.isDigit() }
                text = digits
                digits.toIntOrNull()?.let { onChange(it.coerceIn(min, max)) }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
            modifier = Modifier.width(72.dp).padding(horizontal = 4.dp).onFocusChanged { f ->
                // On blur, snap a blank or out-of-range entry back to the committed value.
                if (!f.isFocused) {
                    val n = text.toIntOrNull()
                    if (n == null || n < min || n > max) text = value.toString()
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedButton(onClick = { onChange((value + step).coerceIn(min, max)) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+") }
    }
}

@Composable
private fun DeviceDialog(vm: KnutcutViewModel, hasPerm: Boolean, onRequestPerm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    var showOther by remember { mutableStateOf(false) }
    var confirmOther by remember { mutableStateOf<android.bluetooth.BluetoothDevice?>(null) }
    val found = remember { mutableStateMapOf<String, android.bluetooth.BluetoothDevice>() }
    val foundLe = remember { mutableStateMapOf<String, android.bluetooth.BluetoothDevice>() }
    val foundLeOther = remember { mutableStateMapOf<String, android.bluetooth.BluetoothDevice>() }
    var confirmOtherLe by remember { mutableStateOf<android.bluetooth.BluetoothDevice?>(null) }
    val allBonded = remember(hasPerm) {
        if (hasPerm) de.knutwurst.knutcut.transport.BluetoothPlotter.bondedDevices(context) else emptyList()
    }
    // Compatible plotters (BT name contains "VEVOR"/"Smart"), like the stock app — these are the default.
    val bonded = allBonded.filter { Devices.isCompatible(it.name) }
    val others = allBonded.filterNot { Devices.isCompatible(it.name) || Devices.isCompatibleLe(it.name) }
    val devices = (bonded + found.values).distinctBy { it.address }

    // Classic discovery while [scanning]; auto-stops after 30s. Stops + unregisters on dispose.
    DisposableEffect(scanning, hasPerm) {
        val handle = if (scanning && hasPerm) {
            runCatching {
                de.knutwurst.knutcut.transport.BluetoothPlotter.discover(context) { dev ->
                    if (Devices.isCompatible(dev.name)) found[dev.address] = dev
                }
            }.getOrNull()
        } else null
        onDispose { handle?.close() }
    }
    // BLE scan while [scanning]; delivers Silhouette devices into [foundLe].
    DisposableEffect(scanning, hasPerm) {
        val handle = if (scanning && hasPerm) {
            runCatching {
                de.knutwurst.knutcut.transport.BluetoothLePlotter.startScanLe(context) { dev, name ->
                    if (Devices.isCompatibleLe(name)) foundLe[dev.address] = dev else foundLeOther[dev.address] = dev
                }
            }.getOrNull()
        } else null
        onDispose { handle?.close() }
    }
    LaunchedEffect(scanning) { if (scanning) { kotlinx.coroutines.delay(30_000); scanning = false } }

    fun stopAndDismiss() { scanning = false; onDismiss() }

    AlertDialog(
        onDismissRequest = { stopAndDismiss() },
        confirmButton = { TextButton(onClick = { stopAndDismiss() }) { Text(stringResource(R.string.ui_close)) } },
        title = { Text(stringResource(R.string.ui_connect_plotter)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!hasPerm) {
                    Text(stringResource(R.string.ui_bt_perm_msg))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestPerm) { Text(stringResource(R.string.ui_grant_perm)) }
                } else {
                    Text(stringResource(R.string.ui_model), style = MaterialTheme.typography.labelLarge)
                    Devices.models.forEach { m ->
                        val sel = vm.model.modelId == m.modelId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { vm.selectModel(m) }.padding(vertical = 8.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_plotter), contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                m.displayName,
                                modifier = Modifier.weight(1f),
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (sel) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.ui_chosen), tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ui_devices), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { found.clear(); foundLe.clear(); foundLeOther.clear(); scanning = !scanning }) { Text(if (scanning) stringResource(R.string.ui_stop) else stringResource(R.string.ui_search)) }
                    }
                    if (devices.isEmpty()) {
                        Text(
                            if (scanning) stringResource(R.string.ui_searching)
                            else stringResource(R.string.ui_no_compatible),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        devices.forEach { d ->
                            TextButton(onClick = { scanning = false; vm.connect(d); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(painterResource(R.drawable.ic_plotter), contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(d.name ?: d.address, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    // Silhouette BLE devices found during scan
                    if (foundLe.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(stringResource(R.string.ui_sil_ble), style = MaterialTheme.typography.labelLarge)
                        foundLe.values.forEach { d ->
                            TextButton(onClick = { scanning = false; vm.connectLe(d); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(painterResource(R.drawable.ic_plotter), contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(d.name ?: d.address, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (scanning) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    // Explicit escape hatch: connect to a non-plotter (e.g. for testing), with a warning.
                    // Classic devices use the SPP path; unknown BLE devices (found during the scan) use
                    // the BLE/GPGL path so a Silhouette-like cutter under a different name is still reachable.
                    if (others.isNotEmpty() || foundLeOther.isNotEmpty()) {
                        TextButton(onClick = { showOther = !showOther }) {
                            Text(if (showOther) stringResource(R.string.ui_hide_others) else stringResource(R.string.ui_show_others))
                        }
                        if (showOther) {
                            Text(
                                stringResource(R.string.ui_others_warn),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            others.forEach { d ->
                                TextButton(onClick = { confirmOther = d }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    Text(d.name ?: d.address, modifier = Modifier.weight(1f))
                                }
                            }
                            foundLeOther.values.forEach { d ->
                                TextButton(onClick = { confirmOtherLe = d }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.ui_device_ble, d.name ?: d.address), modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                    Text(stringResource(R.string.ui_open_bt_settings))
                }
            }
        },
    )

    confirmOther?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmOther = null },
            title = { Text(stringResource(R.string.ui_unsupported_plotter)) },
            text = { Text(stringResource(R.string.ui_confirm_other_msg, d.name ?: d.address)) },
            confirmButton = {
                TextButton(onClick = { confirmOther = null; scanning = false; vm.connect(d); onDismiss() }) { Text(stringResource(R.string.ui_connect_anyway)) }
            },
            dismissButton = { TextButton(onClick = { confirmOther = null }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }

    confirmOtherLe?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmOtherLe = null },
            title = { Text(stringResource(R.string.ui_unknown_ble)) },
            text = { Text(stringResource(R.string.ui_confirm_ble_msg, d.name ?: d.address)) },
            confirmButton = {
                TextButton(onClick = { confirmOtherLe = null; scanning = false; vm.connectLe(d); onDismiss() }) { Text(stringResource(R.string.ui_connect_anyway)) }
            },
            dismissButton = { TextButton(onClick = { confirmOtherLe = null }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }
}

/** App version read at runtime from the installed package (the manifest's versionName), like CricutExport. */
private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        ?: BuildConfig.VERSION_NAME

private fun hasBluetoothPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
