package de.knutwurst.knutcut.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.knutwurst.knutcut.BuildConfig
import de.knutwurst.knutcut.R
import de.knutwurst.knutcut.data.Devices
import de.knutwurst.knutcut.data.DisplayUnit
import de.knutwurst.knutcut.data.Materials
import de.knutwurst.knutcut.data.Mats
import de.knutwurst.knutcut.data.display
import de.knutwurst.knutcut.data.ThemeMode
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Shapes
import java.util.Locale

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

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasBtPerm = hasBluetoothPermission(context)
        if (hasBtPerm) showDevices = true
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u ->
            runCatching { context.contentResolver.openInputStream(u)?.use { it.readBytes().toString(Charsets.UTF_8) } }
                .getOrNull()?.let { vm.loadDesign(it) }
        }
    }
    fun openFile() = openLauncher.launch(arrayOf("image/svg+xml", "text/xml", "text/plain", "application/octet-stream"))
    fun openDevices() { if (hasBtPerm) showDevices = true else permLauncher.launch(bluetoothPermissions()) }

    LaunchedEffect(vm.status) {
        if (!vm.cutting) vm.status?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(hasBtPerm) { vm.autoConnect() }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Top bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Knutcut", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "v$version · Knutwurst",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                    }
                    AddMenu(expanded = showAdd, onDismiss = { showAdd = false }, onOpenFile = { showAdd = false; openFile() }) {
                        vm.addLayer(it.first, listOf(it.second)); showAdd = false
                    }
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
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
                !vm.hasDesign -> EmptyState(onOpen = { openFile() }, onAddShape = { showAdd = true })
                else -> EditingBar(
                    vm,
                    onSize = { showTransform = true },
                    onLayers = { showLayers = true },
                    onMaterial = { showMaterial = true },
                    onConnectOrCut = { if (vm.connected) showCut = true else openDevices() },
                )
            }
        }
    }

    if (showDevices) {
        DeviceDialog(vm, hasBtPerm, onRequestPerm = { permLauncher.launch(bluetoothPermissions()) }, onDismiss = { showDevices = false })
    }
    if (showSettings) SettingsSheet(vm, version, onConnect = { showSettings = false; openDevices() }, onDismiss = { showSettings = false })
    if (showMaterial) MaterialSheet(vm, onDismiss = { showMaterial = false })
    if (showLayers) LayersSheet(vm, onDismiss = { showLayers = false })
    if (showTransform && vm.hasDesign) TransformDialog(vm, onDismiss = { showTransform = false })
    if (showCut && vm.hasDesign) CutSheet(vm, onDismiss = { showCut = false })
}

/** Add menu: open a file or drop in a primitive shape. */
@Composable
private fun AddMenu(expanded: Boolean, onDismiss: () -> Unit, onOpenFile: () -> Unit, onShape: (Pair<String, de.knutwurst.knutcut.svgcore.Polyline>) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("SVG / PLT öffnen…") }, onClick = onOpenFile)
        HorizontalDivider()
        listOf(
            "Quadrat" to Shapes.rect(40.0, 40.0),
            "Rechteck" to Shapes.rect(60.0, 40.0),
            "Kreis" to Shapes.circle(40.0),
            "Dreieck" to Shapes.regularPolygon(3, 40.0),
            "Fünfeck" to Shapes.regularPolygon(5, 40.0),
            "Sechseck" to Shapes.regularPolygon(6, 40.0),
            "Stern" to Shapes.star(5, 40.0),
        ).forEach { shape -> DropdownMenuItem(text = { Text(shape.first) }, onClick = { onShape(shape) }) }
    }
}

@Composable
private fun EmptyState(onOpen: () -> Unit, onAddShape: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Kein Design", style = MaterialTheme.typography.titleMedium)
        Text(
            "Öffne eine SVG- oder PLT-Datei (oder teile sie aus einer anderen App) oder füge eine Form hinzu.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpen) { Text("Datei öffnen") }
            OutlinedButton(onClick = onAddShape) { Text("Form hinzufügen") }
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
    OutlinedButton(onClick = { vm.cancelCut() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Abbrechen") }
}

/** A compact icon button used in the editing toolbar (Canva/Figma style). */
@Composable
private fun IconAction(label: String, icon: ImageVector, rotate: Float = 0f, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
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
) {
    // One toolbar row, spread across the full width (wraps to a new row if it can't fit).
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconAction("Größe & Winkel", Icons.Default.AspectRatio, onClick = onSize)
        IconAction("Drehen 90°", Icons.Default.RotateRight) { vm.rotate90() }
        IconAction("Horizontal spiegeln", Icons.Default.Flip) { vm.mirrorSelectedHorizontal() }
        IconAction("Vertikal spiegeln", Icons.Default.Flip, rotate = 90f) { vm.mirrorSelectedVertical() }
        IconAction("Duplizieren", Icons.Default.ContentCopy) { vm.duplicateSelected() }
        IconAction("Löschen", Icons.Default.Delete) { vm.deleteSelected() }
        IconAction("Zurücksetzen", Icons.Default.Refresh) { vm.resetPlacement() }
    }

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onLayers, modifier = Modifier.weight(1f)) { Text("Ebenen (${vm.layers.size})", maxLines = 1) }
        OutlinedButton(onClick = onMaterial, modifier = Modifier.weight(1f)) { Text(vm.material.display(), maxLines = 1) }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onConnectOrCut, enabled = vm.hasDesign && !vm.cutting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(
            when {
                !vm.connected -> "Plotter verbinden"
                vm.cutUsesKnife() -> "Schneiden"
                else -> "Zeichnen"
            }
        )
    }
}

/** Alignment as two rows of standard align icons (Figma/Office style). */
@Composable
private fun AlignmentControls(vm: KnutcutViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Horizontal", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
            OutlinedIconButton(onClick = { vm.alignHorizontal(-1) }) { Icon(Icons.Default.FormatAlignLeft, "Links") }
            OutlinedIconButton(onClick = { vm.alignHorizontal(0) }) { Icon(Icons.Default.FormatAlignCenter, "Mitte") }
            OutlinedIconButton(onClick = { vm.alignHorizontal(1) }) { Icon(Icons.Default.FormatAlignRight, "Rechts") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vertikal", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
            OutlinedIconButton(onClick = { vm.alignVertical(-1) }) { Icon(Icons.Default.VerticalAlignTop, "Oben") }
            OutlinedIconButton(onClick = { vm.alignVertical(0) }) { Icon(Icons.Default.VerticalAlignCenter, "Mitte") }
            OutlinedIconButton(onClick = { vm.alignVertical(1) }) { Icon(Icons.Default.VerticalAlignBottom, "Unten") }
        }
    }
}

/** Pre-cut step: confirm what will run (and, with several layers, all vs. only the selected one). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CutSheet(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Bereit zum Plotten", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (vm.layers.size > 1) {
                Text("Umfang", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !vm.cutSelectedOnly, onClick = { vm.changeCutSelectedOnly(false) }, label = { Text("Alle Ebenen") })
                    FilterChip(selected = vm.cutSelectedOnly, onClick = { vm.changeCutSelectedOnly(true) }, label = { Text("Nur ausgewählte") })
                }
                Spacer(Modifier.height(10.dp))
            }
            Text(
                vm.cutPlanSummary(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (vm.cutUsesKnife()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Material: ${vm.material.display()}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onDismiss(); vm.cut() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (vm.cutUsesKnife()) "Schneiden starten" else "Zeichnen starten") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersSheet(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Ebenen (${vm.layers.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val marked = vm.markedLayers.count { it in vm.layers.indices }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.splitLayers() }, modifier = Modifier.weight(1f)) { Text("Zerlegen", maxLines = 1) }
                OutlinedButton(
                    onClick = { if (marked >= 2) vm.mergeMarked() else vm.mergeLayers() },
                    modifier = Modifier.weight(1f),
                ) { Text(if (marked >= 2) "$marked zusammenführen" else "Alle zusammenführen", maxLines = 1) }
            }
            Spacer(Modifier.height(10.dp))
            Text("Auf der Matte ausrichten", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            AlignmentControls(vm)
            Spacer(Modifier.height(12.dp))
            if (vm.layers.size > 1) {
                Text("Tippen = bearbeiten. Haken = für „Zusammenführen“ markieren.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
            }
            vm.layers.forEachIndexed { i, layer ->
                val selected = vm.selectedLayer == i
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.layers.size > 1) {
                        Checkbox(checked = i in vm.markedLayers, onCheckedChange = { vm.toggleMarked(i) })
                    }
                    IconButton(onClick = { vm.toggleLayerVisible(i) }) {
                        Icon(
                            if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Sichtbar",
                        )
                    }
                    Text(
                        layer.name,
                        maxLines = 1,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f).clickable { vm.selectLayer(i) },
                    )
                    FilterChip(selected = layer.tool == Tool.PEN, onClick = { vm.setLayerTool(i, Tool.PEN) }, label = { Text("Stift") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = layer.tool == Tool.KNIFE, onClick = { vm.setLayerTool(i, Tool.KNIFE) }, label = { Text("Messer") })
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
            Text("Material & Druck", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Material", style = MaterialTheme.typography.labelLarge)
            vm.allMaterials().forEach { m ->
                val selected = vm.material.id == m.id
                Text(
                    m.display(),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().clickable { vm.selectMaterial(m) }.padding(vertical = 10.dp),
                )
            }
            TextButton(onClick = { manage = true }) { Text("Materialien verwalten…") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Werkzeug", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Order matches the plotter: pen holder left, knife holder right.
                listOf(Tool.PEN, Tool.KNIFE).forEach { t ->
                    FilterChip(
                        selected = vm.selectedTool == t,
                        onClick = { vm.setAllTool(t) },
                        label = { Text(if (t == Tool.KNIFE) "Messer" else "Stift") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val penSelected = vm.selectedTool == Tool.PEN
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (penSelected) "Druck (Stift)" else "Druck (Messer)", Modifier.weight(1f))
                EditableStepper(if (penSelected) vm.penForce else vm.force, Materials.FORCE_MIN, Materials.FORCE_MAX, step = 5) {
                    if (penSelected) vm.changePenForce(it) else vm.changeForce(it)
                }
            }
        }
    }
    if (manage) MaterialManageDialog(vm, onDismiss = { manage = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(vm: KnutcutViewModel, version: String, onConnect: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Einstellungen", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Text("Plotter", style = MaterialTheme.typography.labelLarge)
            Text(
                when {
                    vm.connecting -> "Verbinde…"
                    vm.connected -> "Verbunden: ${vm.device?.name ?: ""}"
                    else -> "Nicht verbunden"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConnect) { Text(if (vm.connected) "Anderer Plotter" else "Verbinden") }
                if (vm.connected) OutlinedButton(onClick = { vm.disconnect() }) { Text("Trennen") }
            }

            Spacer(Modifier.height(18.dp))
            Text("Matte", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mats.all.forEach { m -> FilterChip(selected = vm.mat == m, onClick = { vm.selectMat(m) }, label = { Text(m.name) }) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nullpunkt-Versatz Y (mm)", style = MaterialTheme.typography.bodyMedium)
                    Text("Greifrand der Matte oben — der Plot beginnt so weit unterhalb.", style = MaterialTheme.typography.bodySmall)
                }
                EditableStepper(vm.originOffsetMm, 0, 100, step = 1) { vm.changeOriginOffset(it) }
            }

            Spacer(Modifier.height(18.dp))
            Text("Einheit", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DisplayUnit.entries.forEach { u -> FilterChip(selected = vm.displayUnit == u, onClick = { vm.changeDisplayUnit(u) }, label = { Text(u.label) }) }
            }

            Spacer(Modifier.height(18.dp))
            Text("Erscheinungsbild", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Hell", ThemeMode.DARK to "Dunkel").forEach { (m, lbl) ->
                    FilterChip(selected = vm.themeMode == m, onClick = { vm.selectTheme(m) }, label = { Text(lbl) })
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Schneiden", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.resetCutSettings() }) { Text("Auf Standard") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Schleppmesser-Ausgleich", style = MaterialTheme.typography.bodyMedium)
                    Text("Rundet scharfe Ecken minimal ab, damit das Schleppmesser sauber einschwenkt.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = vm.dragKnifeComp, onCheckedChange = { vm.changeDragKnifeComp(it) })
            }
            if (vm.dragKnifeComp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Klingenversatz (Einh., Standard 13)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    EditableStepper(vm.bladeOffset, 0, 40, step = 1) { vm.changeBladeOffset(it) }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Knutcut v$version · Knutwurst", style = MaterialTheme.typography.bodySmall)
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
        title = { Text("Größe & Winkel") },
        text = {
            Column {
                OutlinedTextField(w, { w = it }, singleLine = true, label = { Text("Breite (${unit.label})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(h, { h = it }, singleLine = true, label = { Text("Höhe (${unit.label})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(ang, { ang = it }, singleLine = true, label = { Text("Winkel (°)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
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
            }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun MaterialManageDialog(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    var editId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var force by remember { mutableStateOf(180) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        title = { Text("Materialien") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val custom = vm.customMaterials
                if (custom.isEmpty()) {
                    Text("Noch keine eigenen Materialien.", style = MaterialTheme.typography.bodySmall)
                } else {
                    custom.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${m.name} · ${m.force}", maxLines = 1, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editId = m.id; name = m.name; force = m.force }) { Text("Bearbeiten") }
                            TextButton(onClick = { vm.deleteMaterial(m.id) }) { Text("Löschen") }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(if (editId == null) "Neues Material" else "Material bearbeiten", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Messer-Druck", Modifier.weight(1f))
                    EditableStepper(force, Materials.FORCE_MIN, Materials.FORCE_MAX, step = 5) { force = it }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = name.isNotBlank(), onClick = {
                        val id = editId
                        if (id == null) vm.addMaterial(name, force) else vm.updateMaterial(id, name, force)
                        editId = null; name = ""; force = 180
                    }) { Text(if (editId == null) "Hinzufügen" else "Speichern") }
                    if (editId != null) OutlinedButton(onClick = { editId = null; name = ""; force = 180 }) { Text("Neu") }
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
            modifier = Modifier.width(72.dp).padding(horizontal = 4.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedButton(onClick = { onChange((value + step).coerceIn(min, max)) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+") }
    }
}

@Composable
private fun DeviceDialog(vm: KnutcutViewModel, hasPerm: Boolean, onRequestPerm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showAll by remember { mutableStateOf(false) }
    val all = remember(hasPerm) {
        if (hasPerm) de.knutwurst.knutcut.transport.BluetoothPlotter.bondedDevices(context) else emptyList()
    }
    val shown = if (showAll) all else all.filter { Devices.looksLikePlotter(it.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        title = { Text("Plotter verbinden") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    !hasPerm -> {
                        Text("Knutcut braucht die Bluetooth-Berechtigung, um den Plotter zu finden.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRequestPerm) { Text("Berechtigung erteilen") }
                    }
                    shown.isEmpty() -> Text(
                        if (all.isEmpty()) "Kein gekoppeltes Gerät gefunden. Koppele den Plotter zuerst in den Android-Bluetooth-Einstellungen."
                        else "Kein Plotter gefunden. Falls dein Plotter anders heißt, aktiviere „Alle Geräte anzeigen“."
                    )
                    else -> shown.forEach { d ->
                        TextButton(onClick = { vm.connect(d); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                            Text(d.name ?: d.address, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (hasPerm) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = showAll, onCheckedChange = { showAll = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Alle Geräte anzeigen")
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                    Text("Bluetooth-Einstellungen öffnen")
                }
            }
        },
    )
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
