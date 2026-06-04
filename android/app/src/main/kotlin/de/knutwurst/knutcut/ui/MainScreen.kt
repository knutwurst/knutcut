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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasBtPerm = hasBluetoothPermission(context)
        if (hasBtPerm) showDevices = true
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
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
                    onConnectOrCut = { if (vm.connected) vm.cut() else openDevices() },
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

/** The contextual bar shown while editing: actions on the selected layer, then material + cut. */
@Composable
private fun EditingBar(
    vm: KnutcutViewModel,
    onSize: () -> Unit,
    onLayers: () -> Unit,
    onMaterial: () -> Unit,
    onConnectOrCut: () -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(onClick = onSize, label = { Text("Größe & Winkel") })
        AssistChip(onClick = { vm.rotate90() }, label = { Text("Drehen 90°") })
        AssistChip(onClick = { vm.mirrorSelectedHorizontal() }, label = { Text("↔ Spiegeln") })
        AssistChip(onClick = { vm.mirrorSelectedVertical() }, label = { Text("↕ Spiegeln") })
        AssistChip(onClick = { vm.duplicateSelected() }, label = { Text("Duplizieren") })
        if (vm.layers.size > 1) AssistChip(onClick = { vm.deleteSelected() }, label = { Text("Löschen") })
        AssistChip(onClick = { vm.resetPlacement() }, label = { Text("Zurücksetzen") })
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onLayers, modifier = Modifier.weight(1f)) {
            Text("Ebenen (${vm.layers.size})", maxLines = 1)
        }
        OutlinedButton(onClick = onMaterial, modifier = Modifier.weight(1f)) {
            Text(vm.material.display(), maxLines = 1)
        }
    }
    if (vm.layers.size > 1) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = vm.cutSelectedOnly, onCheckedChange = { vm.changeCutSelectedOnly(it) })
            Text("Nur ausgewählte Ebene schneiden", style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onConnectOrCut, enabled = vm.hasDesign && !vm.cutting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(
            when {
                !vm.connected -> "Plotter verbinden"
                vm.layers.any { it.visible && it.tool == Tool.KNIFE } -> "Schneiden"
                else -> "Zeichnen"
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersSheet(vm: KnutcutViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Ebenen (${vm.layers.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.splitLayers() }, modifier = Modifier.weight(1f)) { Text("Zerlegen", maxLines = 1) }
                OutlinedButton(onClick = { vm.mergeLayers() }, modifier = Modifier.weight(1f)) { Text("Zusammenführen", maxLines = 1) }
            }
            Spacer(Modifier.height(10.dp))
            Text("Ausgewählte Ebene ausrichten", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { vm.alignSelected(0, 0) }, label = { Text("Mitte") })
                AssistChip(onClick = { vm.alignSelected(-1, 0) }, label = { Text("Links") })
                AssistChip(onClick = { vm.alignSelected(1, 0) }, label = { Text("Rechts") })
                AssistChip(onClick = { vm.alignSelected(0, -1) }, label = { Text("Oben") })
                AssistChip(onClick = { vm.alignSelected(0, 1) }, label = { Text("Unten") })
            }
            Spacer(Modifier.height(10.dp))
            if (vm.layers.size > 1) {
                Text("Antippen wählt die Ebene; auf der Matte verschieben.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
            }
            vm.layers.forEachIndexed { i, layer ->
                val selected = vm.selectedLayer == i
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        selected = vm.tool == t,
                        onClick = { vm.setAllTool(t) },
                        label = { Text(if (t == Tool.KNIFE) "Messer" else "Stift") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val penSelected = vm.tool == Tool.PEN
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (penSelected) "Druck (Stift)" else "Druck (Messer)", Modifier.weight(1f))
                Stepper("", if (penSelected) vm.penForce else vm.force, Materials.FORCE_MIN, Materials.FORCE_MAX) {
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
            Text("Schneiden", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Schleppmesser-Ausgleich", style = MaterialTheme.typography.bodyMedium)
                    Text("Rundet scharfe Ecken minimal ab, damit das Schleppmesser sauber einschwenkt.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = vm.dragKnifeComp, onCheckedChange = { vm.changeDragKnifeComp(it) })
            }
            if (vm.dragKnifeComp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Klingenversatz (Einheiten, 40 = 1 mm)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Stepper("", vm.bladeOffset, 0, 40) { vm.changeBladeOffset(it) }
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
                    Stepper("", force, Materials.FORCE_MIN, Materials.FORCE_MAX) { force = it }
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

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange((value - 1).coerceIn(min, max)) }) { Text("−") }
            Text("$value", modifier = Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center)
            TextButton(onClick = { onChange((value + 1).coerceIn(min, max)) }) { Text("+") }
        }
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
