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
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.knutwurst.knutcut.BuildConfig
import de.knutwurst.knutcut.R
import de.knutwurst.knutcut.data.Devices
import de.knutwurst.knutcut.data.Materials
import de.knutwurst.knutcut.data.Mats
import de.knutwurst.knutcut.data.ThemeMode
import de.knutwurst.knutcut.data.Tool
import java.util.Locale

@Composable
fun MainScreen(vm: KnutcutViewModel) {
    val context = LocalContext.current
    val version = appVersion(context)
    var hasBtPerm by remember { mutableStateOf(hasBluetoothPermission(context)) }
    var showDevices by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showTransform by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasBtPerm = hasBluetoothPermission(context)
        if (hasBtPerm) showDevices = true
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            runCatching { context.contentResolver.openInputStream(u)?.use { it.readBytes().toString(Charsets.UTF_8) } }
                .getOrNull()?.let { vm.loadSvg(it) }
        }
    }

    LaunchedEffect(vm.status) {
        // During a cut the status is shown persistently in the UI; don't also flash toasts for it.
        if (!vm.cutting) vm.status?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Reconnect to the last plotter on launch (once Bluetooth permission is available).
    LaunchedEffect(hasBtPerm) { vm.autoConnect() }

    fun openDevices() {
        if (hasBtPerm) showDevices = true else permLauncher.launch(bluetoothPermissions())
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(38.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Knutcut", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "v$version · Knutwurst",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { openLauncher.launch(arrayOf("image/svg+xml", "text/xml", "application/octet-stream")) }) {
                    Text("Öffnen")
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                }
            }

            MatEditor(
                vm,
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            )

            PlacementBar(vm, onLayers = { showLayers = true }, onEditSize = { showTransform = true })
            Spacer(Modifier.height(4.dp))
            MaterialBar(vm)
            Spacer(Modifier.height(8.dp))

            if (vm.cutting) {
                Text(
                    vm.status ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                if (vm.progress > 0f) {
                    LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { vm.cancelCut() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Abbrechen")
                }
            } else {
                Button(
                    onClick = { if (vm.connected) vm.cut() else openDevices() },
                    enabled = vm.hasDesign && !vm.cutting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        when {
                            !vm.connected -> "Plotter verbinden"
                            vm.layers.any { it.visible && it.tool == Tool.KNIFE } -> "Schneiden"
                            else -> "Zeichnen"
                        }
                    )
                }
            }
        }
    }

    if (showDevices) {
        DeviceDialog(
            vm = vm,
            hasPerm = hasBtPerm,
            onRequestPerm = { permLauncher.launch(bluetoothPermissions()) },
            onDismiss = { showDevices = false },
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Schließen") } },
            title = { Text("Einstellungen") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Plotter", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
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
                        Button(onClick = { showSettings = false; openDevices() }) {
                            Text(if (vm.connected) "Anderer Plotter" else "Verbinden")
                        }
                        if (vm.connected) OutlinedButton(onClick = { vm.disconnect() }) { Text("Trennen") }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text("Matte", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mats.all.forEach { m ->
                            FilterChip(selected = vm.mat == m, onClick = { vm.selectMat(m) }, label = { Text(m.name) })
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text("Erscheinungsbild", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to "System",
                            ThemeMode.LIGHT to "Hell",
                            ThemeMode.DARK to "Dunkel",
                        ).forEach { (m, lbl) ->
                            FilterChip(selected = vm.themeMode == m, onClick = { vm.selectTheme(m) }, label = { Text(lbl) })
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text("Schneiden", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Schleppmesser-Ausgleich", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Rundet scharfe Ecken minimal ab, damit das Schleppmesser sauber einschwenkt.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = vm.dragKnifeComp, onCheckedChange = { vm.changeDragKnifeComp(it) })
                    }
                    if (vm.dragKnifeComp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Klingenversatz (Einheiten, 40 = 1 mm)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Stepper("", vm.bladeOffset, 0, 40) { vm.changeBladeOffset(it) }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Knutcut v$version · Knutwurst", style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }

    if (showTransform && vm.hasDesign) {
        val b = vm.bounds
        var w by remember { mutableStateOf(((b?.widthMm ?: 0.0) * vm.scaleX).roundToInt().toString()) }
        var h by remember { mutableStateOf(((b?.heightMm ?: 0.0) * vm.scaleY).roundToInt().toString()) }
        var ang by remember { mutableStateOf(vm.rotationDeg.roundToInt().toString()) }
        AlertDialog(
            onDismissRequest = { showTransform = false },
            title = { Text("Größe & Winkel") },
            text = {
                Column {
                    OutlinedTextField(
                        value = w, onValueChange = { w = it }, singleLine = true,
                        label = { Text("Breite (mm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = h, onValueChange = { h = it }, singleLine = true,
                        label = { Text("Höhe (mm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ang, onValueChange = { ang = it }, singleLine = true,
                        label = { Text("Winkel (°)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ww = w.replace(',', '.').toDoubleOrNull()
                    val hh = h.replace(',', '.').toDoubleOrNull()
                    val aa = ang.replace(',', '.').toDoubleOrNull()
                    if (ww != null && hh != null && ww > 0 && hh > 0) vm.setSelectedSizeMm(ww, hh)
                    if (aa != null) vm.setSelectedRotation(aa)
                    showTransform = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { showTransform = false }) { Text("Abbrechen") } },
        )
    }

    if (showLayers) {
        AlertDialog(
            onDismissRequest = { showLayers = false },
            confirmButton = { TextButton(onClick = { showLayers = false }) { Text("Schließen") } },
            title = { Text("${vm.layers.size} Ebenen") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedButton(onClick = { vm.splitLayers() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Zerlegen", maxLines = 1)
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { vm.mergeLayers() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Zusammenführen", maxLines = 1)
                    }
                    if (vm.hasDesign) {
                        Spacer(Modifier.height(10.dp))
                        Text("Ausgewählte Ebene", style = MaterialTheme.typography.labelLarge)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(onClick = { vm.mirrorSelectedHorizontal() }) { Text("↔ Spiegeln") }
                            TextButton(onClick = { vm.mirrorSelectedVertical() }) { Text("↕ Spiegeln") }
                            TextButton(onClick = { vm.duplicateSelected() }) { Text("Duplizieren") }
                            TextButton(
                                onClick = { vm.deleteSelected() },
                                enabled = vm.layers.size > 1,
                            ) { Text("Löschen") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (vm.layers.size > 1) {
                        Text(
                            "Ebene antippen zum Auswählen, dann auf der Matte verschieben.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
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
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { vm.selectLayer(i); showLayers = false },
                            )
                            FilterChip(selected = layer.tool == Tool.PEN, onClick = { vm.setLayerTool(i, Tool.PEN) }, label = { Text("Stift") })
                            Spacer(Modifier.width(6.dp))
                            FilterChip(selected = layer.tool == Tool.KNIFE, onClick = { vm.setLayerTool(i, Tool.KNIFE) }, label = { Text("Messer") })
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun PlacementBar(vm: KnutcutViewModel, onLayers: () -> Unit, onEditSize: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val size = vm.designSizeMm()
        val label = if (size == null) "Kein Design – teile eine SVG zu Knutcut"
        else String.format(Locale.GERMAN, "%.0f × %.0f mm", size.first, size.second)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .then(if (vm.hasDesign) Modifier.clickable { onEditSize() } else Modifier),
        )
        TextButton(onClick = onLayers, enabled = vm.hasDesign) { Text("Ebenen (${vm.layers.size})") }
        TextButton(onClick = { vm.rotate90() }, enabled = vm.hasDesign) { Text("Drehen 90°") }
        TextButton(onClick = { vm.resetPlacement() }, enabled = vm.hasDesign) { Text("Zurücksetzen") }
    }
}

@Composable
private fun MaterialBar(vm: KnutcutViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(vm.material.name, maxLines = 1)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Materials.presets.sortedBy { it.name.lowercase() }.forEach { m ->
                    DropdownMenuItem(text = { Text(m.name) }, onClick = { vm.selectMaterial(m); expanded = false })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Werkzeug", style = MaterialTheme.typography.labelSmall)
                // Order matches the plotter: pen holder left, knife holder right.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Tool.PEN, Tool.KNIFE).forEach { t ->
                        FilterChip(
                            selected = vm.tool == t,
                            onClick = { vm.setAllTool(t) },
                            label = { Text(if (t == Tool.KNIFE) "Messer" else "Stift") },
                        )
                    }
                }
            }
            val penSelected = vm.tool == Tool.PEN
            Stepper(
                if (penSelected) "Druck (Stift)" else "Druck (Messer)",
                if (penSelected) vm.penForce else vm.force,
                Materials.FORCE_MIN,
                Materials.FORCE_MAX,
            ) { if (penSelected) vm.changePenForce(it) else vm.changeForce(it) }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
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
