package de.knutwurst.knutcut.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.knutwurst.knutcut.BuildConfig
import de.knutwurst.knutcut.R
import de.knutwurst.knutcut.data.ColorMode
import de.knutwurst.knutcut.data.Devices
import de.knutwurst.knutcut.data.DisplayUnit
import de.knutwurst.knutcut.data.Layer
import de.knutwurst.knutcut.data.Mat
import de.knutwurst.knutcut.data.Mats
import de.knutwurst.knutcut.data.Material
import de.knutwurst.knutcut.data.Materials
import de.knutwurst.knutcut.data.display
import de.knutwurst.knutcut.data.PlotterModel
import de.knutwurst.knutcut.data.PlotterSvgItem
import de.knutwurst.knutcut.data.PlotterSvgLibrary
import de.knutwurst.knutcut.data.DeformEngine
import de.knutwurst.knutcut.data.DeformSpec
import de.knutwurst.knutcut.data.EnvelopeDeform
import de.knutwurst.knutcut.data.TextSpec
import de.knutwurst.knutcut.data.Settings
import de.knutwurst.knutcut.data.ThemeMode
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.CutOrder
import de.knutwurst.knutcut.svgcore.EditablePath
import de.knutwurst.knutcut.svgcore.HandleSide
import de.knutwurst.knutcut.svgcore.deleteNode
import de.knutwurst.knutcut.svgcore.dragSegment
import de.knutwurst.knutcut.svgcore.insertNode
import de.knutwurst.knutcut.svgcore.inverse
import de.knutwurst.knutcut.svgcore.moveAnchor
import de.knutwurst.knutcut.svgcore.moveHandle
import de.knutwurst.knutcut.svgcore.setSmooth
import de.knutwurst.knutcut.svgcore.looksClosed
import de.knutwurst.knutcut.svgcore.simplifyRdp
import de.knutwurst.knutcut.svgcore.simplifyToBudget
import de.knutwurst.knutcut.svgcore.toEditablePath
import de.knutwurst.knutcut.svgcore.DragKnife
import de.knutwurst.knutcut.svgcore.Handshake
import de.knutwurst.knutcut.svgcore.History
import de.knutwurst.knutcut.data.PlotterFamily
import de.knutwurst.knutcut.svgcore.GpglCutSettings
import de.knutwurst.knutcut.svgcore.GpglProtocol
import de.knutwurst.knutcut.svgcore.GpglSession
import de.knutwurst.knutcut.svgcore.SilhouetteFamily
import de.knutwurst.knutcut.svgcore.HpglEncoder
import de.knutwurst.knutcut.svgcore.LinkTransport
import de.knutwurst.knutcut.svgcore.ManagedLink
import de.knutwurst.knutcut.svgcore.Matrix
import de.knutwurst.knutcut.svgcore.mmToUnits
import de.knutwurst.knutcut.svgcore.Nest
import de.knutwurst.knutcut.svgcore.PltCommand
import de.knutwurst.knutcut.svgcore.PltParser
import de.knutwurst.knutcut.svgcore.Placement
import de.knutwurst.knutcut.svgcore.PlotterMessage
import de.knutwurst.knutcut.svgcore.PlotterSession
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Protocol
import de.knutwurst.knutcut.svgcore.Pt
import de.knutwurst.knutcut.svgcore.Query
import de.knutwurst.knutcut.svgcore.Snap
import de.knutwurst.knutcut.svgcore.SvgExport
import de.knutwurst.knutcut.svgcore.SvgParser
import de.knutwurst.knutcut.svgcore.TextArc
import de.knutwurst.knutcut.svgcore.UNITS_PER_MM
import de.knutwurst.knutcut.svgcore.responseState
import de.knutwurst.knutcut.svgcore.responseStateReady
import de.knutwurst.knutcut.transport.BluetoothLePlotter
import de.knutwurst.knutcut.transport.BluetoothPlotter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Editor interaction mode: normal SELECT/move/resize, freehand DRAW, or node editor NODES. */
enum class EditorTool { SELECT, DRAW, NODES }

class KnutcutViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)

    // Loaded design as layers (one per SVG shape), each with its own placement.
    var layers by mutableStateOf<List<Layer>>(emptyList()); private set
    var selectedLayer by mutableStateOf(0); private set

    // Undo/redo of the layer arrangement (placement, selection and the default tool), captured before
    // each edit. De-duped by structural equality (robust to lists being re-created with the same
    // content) so nested calls and no-op edits don't add steps.
    private data class EditState(val layers: List<Layer>, val selectedLayer: Int, val tool: Tool)
    private val history = History<EditState>(MAX_HISTORY) { a, b -> a == b }
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    /** Snapshot the current arrangement before a mutating edit (the editor calls this once per drag). */
    fun pushHistory() {
        history.push(EditState(layers, selectedLayer, tool))
        syncHistory()
    }

    fun undo() = applyEdit(history.undo(EditState(layers, selectedLayer, tool)))
    fun redo() = applyEdit(history.redo(EditState(layers, selectedLayer, tool)))

    private fun applyEdit(s: EditState?) {
        if (s == null) return
        layers = s.layers
        selectedLayer = s.selectedLayer
        tool = s.tool
        settings.toolSp = s.tool.sp
        markedLayers = emptySet()
        syncHistory()
    }

    private fun syncHistory() { canUndo = history.canUndo; canRedo = history.canRedo }
    // Layers ticked in the list for a multi-layer merge (separate from the single edit selection).
    var markedLayers by mutableStateOf<Set<Int>>(emptySet()); private set

    // Work area.
    var model by mutableStateOf<PlotterModel>(Devices.default); private set
    var mat by mutableStateOf<Mat>(Mats.default); private set

    // Placement of the currently selected layer. These delegate to the layers list so the mat editor
    // (which reads and writes them) manipulates one layer at a time.
    var scaleX: Double
        get() = layers.getOrNull(selectedLayer)?.scaleX ?: 1.0
        set(v) = updateSelected { it.copy(scaleX = v) }
    var scaleY: Double
        get() = layers.getOrNull(selectedLayer)?.scaleY ?: 1.0
        set(v) = updateSelected { it.copy(scaleY = v) }
    var rotationDeg: Double
        get() = layers.getOrNull(selectedLayer)?.rotationDeg ?: 0.0
        set(v) = updateSelected { it.copy(rotationDeg = v) }
    var centerMm: Pt
        get() = layers.getOrNull(selectedLayer)?.centerMm ?: Pt(0.0, 0.0)
        set(v) = updateSelected { it.copy(centerMm = v) }

    /** Bounds of the selected layer in its own coordinates — the handles operate on this. */
    val bounds: Bounds? get() = layers.getOrNull(selectedLayer)?.let { layerBounds(it) }

    /** True when no layer is selected — the mat itself is the active selection. */
    val matSelected: Boolean get() = selectedLayer !in layers.indices

    // Mat view camera (zoom + pan of the work area).
    var camScale by mutableStateOf(1f)
    var camOffset by mutableStateOf(Offset.Zero)

    // Material / cut settings.
    var material by mutableStateOf<Material>(Materials.default); private set
    var tool by mutableStateOf(Tool.KNIFE); private set
    var force by mutableStateOf(Materials.default.force); private set

    /** Freehand draw mode toggle; SELECT is the default (normal move/resize interaction). */
    var editorTool by mutableStateOf(EditorTool.SELECT)

    /** When true, the selected text layer is being bent via the on-mat handle (overlay + drag).
     *  Mutually exclusive with the normal move/resize gesture while active. */
    var bendingText by mutableStateOf(false); private set

    /** Fonts for re-rendering curved text during a live bend. Built lazily off the app context. */
    private val fontRepo by lazy { FontRepository(getApplication()) }

    var penForce by mutableStateOf(15); private set

    // Connection.
    var device by mutableStateOf<BluetoothDevice?>(null); private set
    var connecting by mutableStateOf(false); private set
    var connected by mutableStateOf(false); private set
    private var link: ManagedLink? = null

    // Cut.
    var cutSelectedOnly by mutableStateOf(false); private set
    var cutCopies by mutableStateOf(1); private set
    var cutting by mutableStateOf(false); private set
    var progress by mutableStateOf(0f); private set

    var status by mutableStateOf<String?>(null)

    var themeMode by mutableStateOf(settings.themeMode); private set
    var colorMode by mutableStateOf(settings.colorMode); private set
    var appLanguage by mutableStateOf(settings.appLanguage); private set

    var displayUnit by mutableStateOf(settings.displayUnit); private set
    var originOffsetMm by mutableStateOf(settings.originOffsetMm); private set
    var silhouetteSpeed by mutableStateOf(settings.silhouetteSpeed); private set
    var optimizeCutOrder by mutableStateOf(settings.optimizeCutOrder); private set

    // Self-update: the newer release (if any) and whether a download/install is in flight.
    var updateInfo by mutableStateOf<de.knutwurst.knutcut.update.UpdateInfo?>(null); private set
    var updateBusy by mutableStateOf(false); private set
    var autoUpdate by mutableStateOf(settings.autoUpdate); private set
    var snapMm by mutableStateOf(settings.snapMm); private set
    var alignGuides by mutableStateOf(settings.alignGuides); private set
    var autoCloseDrawn by mutableStateOf(settings.autoCloseDrawn); private set
    // Transient guide lines shown while a drag is snapped to another layer's (or the mat's) centre.
    var alignGuideX by mutableStateOf<Double?>(null); private set
    var alignGuideY by mutableStateOf<Double?>(null); private set

    var dragKnifeComp by mutableStateOf(settings.dragKnifeComp); private set
    var bladeOffset by mutableStateOf(settings.bladeOffset); private set

    val hasDesign: Boolean get() = layers.isNotEmpty()

    fun selectTheme(m: ThemeMode) { settings.themeMode = m; themeMode = m }

    /** Switch the mat preview between tool-coloured outlines and the SVG's own colours. Persisted. */
    fun changeColorMode(m: ColorMode) { settings.colorMode = m; colorMode = m }

    fun changeDisplayUnit(u: DisplayUnit) { settings.displayUnit = u; displayUnit = u }

    /** Pick the plotter model (decides the load/start gates and the name shown). Persisted. */
    fun selectModel(m: PlotterModel) { model = m; settings.modelId = m.modelId }

    /** True when the connected device is a supported VEVOR plotter (classic BT). */
    val connectedToPlotter: Boolean get() = connected && Devices.isCompatible(device?.name)

    /** True when the live connection is a BLE link (the transport Silhouette cutters use). Keyed off
     *  the link itself, not the selected model, so it can't disagree with what's actually wired. */
    val connectedToSilhouette: Boolean get() = connected && link?.transport == LinkTransport.BLE

    fun changeOriginOffset(mm: Int) { originOffsetMm = mm.coerceIn(0, 100); settings.originOffsetMm = originOffsetMm }

    /** Max GPGL speed for the selected Silhouette family (Cameo4-line allows 30, others 10). */
    val silhouetteSpeedMax: Int get() =
        if (model.silhouetteDevice?.family == SilhouetteFamily.CAMEO4_LINE) 30 else 10

    fun changeSilhouetteSpeed(v: Int) {
        silhouetteSpeed = v.coerceIn(1, silhouetteSpeedMax); settings.silhouetteSpeed = silhouetteSpeed
    }

    fun changeSnap(mm: Float) { snapMm = mm; settings.snapMm = mm }

    fun changeOptimizeCutOrder(on: Boolean) { optimizeCutOrder = on; settings.optimizeCutOrder = on }

    /** Persist + apply the UI language; the caller recreates the activity to reload resources. The
     *  settings sheet is reopened after the recreate so the change doesn't dump the user to the editor. */
    fun changeAppLanguage(lang: String) { settings.appLanguage = lang; appLanguage = lang; settings.reopenSettings = true }

    /** True once, right after a language-change recreate, so the UI can reopen the settings sheet. */
    fun consumeReopenSettings(): Boolean { val r = settings.reopenSettings; if (r) settings.reopenSettings = false; return r }

    /** Release the link and cancel any cut — called from onCleared and before a hard exit. */
    fun shutdown() { cutJob?.cancel(); cutJob = null; link?.close(); link = null }

    /** Context wrapped with the chosen language, so toasts honour it like the rest of the UI. */
    private fun locCtx(): android.content.Context =
        LocaleUtil.wrap(getApplication<Application>(), settings.appLanguage)

    /** Localized string / quantity-string helpers for status (toast) messages. */
    private fun s(id: Int, vararg a: Any?): String = locCtx().getString(id, *a)
    private fun qty(id: Int, n: Int, vararg a: Any?): String =
        locCtx().resources.getQuantityString(id, n, *a)

    /** Remove leftover update APKs at launch, but keep a downloaded installer that's still newer than
     *  what's running (the user may not have completed the install yet). */
    fun cleanupUpdates() {
        runCatching {
            val keep = if (settings.pendingApkCode > installedVersionCode()) settings.pendingApk else null
            if (keep == null) { settings.pendingApk = null; settings.pendingApkCode = 0 }
            de.knutwurst.knutcut.update.Updater.cleanup(getApplication(), keep)
        }
    }

    /** Check the release repo; show the update prompt if a newer versionCode is published. */
    fun changeAutoUpdate(on: Boolean) { autoUpdate = on; settings.autoUpdate = on }

    /** The versionCode actually installed right now (not the compile-time BuildConfig, which a
     *  not-yet-restarted process reports stale after an in-place update). */
    private fun installedVersionCode(): Long = runCatching {
        val app = getApplication<Application>()
        androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(
            app.packageManager.getPackageInfo(app.packageName, 0)
        )
    }.getOrDefault(BuildConfig.VERSION_CODE.toLong())

    private fun installedVersionName(): String = runCatching {
        val app = getApplication<Application>()
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull() ?: BuildConfig.VERSION_NAME

    fun checkForUpdate(silent: Boolean) {
        if (updateBusy) return
        if (!silent) status = s(R.string.st_update_checking) // no toast on the silent launch check
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { de.knutwurst.knutcut.update.Updater.fetchLatest() }
            when {
                info != null && info.versionCode > installedVersionCode() -> updateInfo = info
                !silent && info != null -> status = s(R.string.st_update_none, installedVersionName())
                !silent -> status = s(R.string.st_update_check_failed)
            }
        }
    }

    fun dismissUpdate() { updateInfo = null }

    /** Download the pending update and hand it to the installer (the user confirms the install). */
    fun runUpdate() {
        val info = updateInfo ?: return
        updateBusy = true
        status = s(R.string.st_update_downloading, info.versionName)
        viewModelScope.launch {
            val apk = withContext(Dispatchers.IO) { de.knutwurst.knutcut.update.Updater.download(getApplication(), info) }
            updateBusy = false
            if (apk == null) { status = s(R.string.st_update_download_failed); return@launch }
            // Remember the verified APK so launch cleanup keeps it until the install actually lands.
            settings.pendingApk = info.apk; settings.pendingApkCode = info.versionCode
            // Keep updateInfo set if the installer can't run yet (permission), so the user can retry.
            if (de.knutwurst.knutcut.update.Updater.install(getApplication(), apk)) updateInfo = null
            else status = s(R.string.st_install_permission)
        }
    }

    fun changeAlignGuides(on: Boolean) { alignGuides = on; settings.alignGuides = on; if (!on) clearGuides() }

    /** Toggle whether a freehand stroke auto-closes into a plottable shape. Persisted. */
    fun changeAutoCloseDrawn(on: Boolean) { autoCloseDrawn = on; settings.autoCloseDrawn = on }

    /** Format a millimetre length in the chosen display unit, e.g. "4.0 cm". */
    fun formatLen(mm: Double): String =
        String.format(java.util.Locale.GERMAN, "%.1f %s", displayUnit.fromMm(mm), displayUnit.label)

    fun changeDragKnifeComp(v: Boolean) { settings.dragKnifeComp = v; dragKnifeComp = v }

    fun changeBladeOffset(v: Int) { bladeOffset = v.coerceIn(0, 40); settings.bladeOffset = bladeOffset }

    init {
        settings.materialId?.let { id ->
            Materials.presets.firstOrNull { it.id == id }?.let { material = it; force = it.force }
        }
        Mats.byName(settings.matName)?.let { mat = it }
        model = Devices.byId(settings.modelId)
        tool = Tool.entries.firstOrNull { it.sp == settings.toolSp } ?: Tool.KNIFE
        if (settings.force > 0) force = settings.force
        penForce = settings.penForce
    }

    private sealed interface ImportText {
        data class Ok(val content: String) : ImportText
        object TooBig : ImportText
        object Failed : ImportText
    }

    /** A shared/opened import waiting for the user's "replace or add" choice while a design is already loaded. */
    sealed class PendingImport {
        data class Files(val uris: List<Uri>) : PendingImport()
        data class Content(val text: String) : PendingImport()
    }

    // Set when an import arrives with a design already on the mat; the UI shows the replace/add dialog.
    var pendingImport by mutableStateOf<PendingImport?>(null); private set

    /**
     * Import shared/opened files: read each off the UI thread with a size cap, then load it. A file
     * that's too big or unreadable is skipped with its own status message instead of blocking the UI.
     * With a design already on the mat the choice (replace or add) is deferred to the import dialog.
     */
    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (hasDesign) { pendingImport = PendingImport.Files(uris); return }
        readAndLoad(uris, replace = false)
    }

    private fun readAndLoad(uris: List<Uri>, replace: Boolean) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            var replaceNext = replace
            for (uri in uris) {
                when (val r = withContext(Dispatchers.IO) { readTextLimited(resolver, uri) }) {
                    is ImportText.Ok -> if (loadDesignContent(r.content, replaceNext)) replaceNext = false
                    ImportText.TooBig -> status = s(R.string.st_import_too_big, MAX_IMPORT_MB)
                    ImportText.Failed -> status = s(R.string.st_import_unreadable)
                }
            }
        }
    }

    /** Display name of the currently saved/loaded project file (shown in settings), or null. */
    var projectName by mutableStateOf<String?>(null); private set

    /** The human-readable file name behind a content uri (OpenableColumns), best-effort. */
    private fun displayNameOf(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    /** Save the current layers (placement + geometry) as a .kcp project file (JSON inside). */
    fun saveProject(uri: Uri) {
        val json = de.knutwurst.knutcut.data.ProjectIO.toJson(layers)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }.isSuccess
            }
            if (ok) projectName = displayNameOf(uri)
            status = if (ok) s(R.string.st_project_saved) else s(R.string.st_project_save_failed)
        }
    }

    /**
     * Export the visible design to a plain SVG of stroked outlines (the cut/draw paths), in
     * millimetres at its real size. Placement (scale/rotation/position) is baked in. Unlike a .kcp
     * project this is a portable vector other tools can open.
     */
    fun exportSvg(uri: Uri) {
        // Snapshot the (immutable) placed geometry on the caller, then build the SVG and write it off
        // the UI thread — a large design can have a lot of points.
        val placed = placedLayers()
        viewModelScope.launch {
            // Build the SVG on the CPU pool, write the file on the IO pool.
            val svg = withContext(Dispatchers.Default) {
                val strokes = placed.flatMap { pl ->
                    pl.polylines.mapIndexed { i, poly -> SvgExport.Stroke(poly, pl.colors.getOrNull(i)) }
                }
                SvgExport.toSvg(strokes)
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(svg.toByteArray()) } }.isSuccess
            }
            status = if (ok) s(R.string.st_svg_exported) else s(R.string.st_svg_export_failed)
        }
    }

    /** Load a .kcp project file, replacing the current layers with the saved arrangement. */
    fun loadProject(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val ls = text?.let { runCatching { de.knutwurst.knutcut.data.ProjectIO.fromJson(it) }.getOrNull() }
            if (ls.isNullOrEmpty()) { status = s(R.string.st_project_invalid); return@launch }
            pushHistory()
            layers = ls
            selectedLayer = 0
            markedLayers = emptySet()
            pruneBoundsCache()
            projectName = displayNameOf(uri)
            status = qty(R.plurals.st_project_loaded, ls.size, ls.size)
        }
    }

    /** Resolve the pending import once the user picked replace or add. */
    fun resolveImport(replace: Boolean) {
        val p = pendingImport ?: return
        pendingImport = null
        when (p) {
            is PendingImport.Files -> readAndLoad(p.uris, replace)
            is PendingImport.Content -> loadDesignContent(p.text, replace)
        }
    }

    /** Dismiss the import dialog without loading anything. */
    fun cancelImport() { pendingImport = null }

    private fun readTextLimited(resolver: ContentResolver, uri: Uri): ImportText = try {
        resolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_IMPORT_MB * 1024L * 1024L) return ImportText.TooBig
                out.write(buf, 0, n)
            }
            ImportText.Ok(out.toString("UTF-8"))
        } ?: ImportText.Failed
    } catch (e: Exception) {
        ImportText.Failed
    }

    /**
     * Load a shared/opened SVG or PLT file. With nothing on the mat it becomes the design; with a
     * design already loaded the choice (replace or add) is deferred to the import dialog.
     */
    fun loadDesign(text: String) {
        if (hasDesign) { pendingImport = PendingImport.Content(text); return }
        loadDesignContent(text, replace = false)
    }

    // --- SVG motif library (large, fully offline) ---------------------------------------------
    /** The motif list, published once it has been built off the main thread. Empty until then. */
    var libraryItems by mutableStateOf<List<PlotterSvgItem>>(emptyList()); private set
    var libraryLoading by mutableStateOf(false); private set

    /** Build the 7,600+ item list once, off the main thread, then publish it. Idempotent, so the
     *  library sheet can call it on every open without rebuilding or freezing the UI. */
    fun loadLibrary() {
        if (libraryItems.isNotEmpty() || libraryLoading) return
        libraryLoading = true
        viewModelScope.launch {
            val built = withContext(Dispatchers.Default) { PlotterSvgLibrary.items }
            libraryItems = built
            libraryLoading = false
        }
    }

    fun addLibrarySvg(name: String, svg: String) {
        // One motif's SVG is small; parsing on tap is quick (the grid's per-cell parsing during
        // scroll — the real cost — is handled off-thread in the picker).
        val parsed = parseDesign(svg)
        if (parsed == null || parsed.layers.isEmpty()) { status = s(R.string.st_no_cuttable_paths); return }
        val layer = mergeLibraryLayers(name, parsed.layers)
        pushHistory()
        if (layers.isEmpty()) {
            layers = placeAtHome(listOf(layer))
            selectedLayer = 0
            camScale = 1f
            camOffset = Offset.Zero
        } else {
            val added = appendPlaced(placeAtHome(listOf(layer)))
            layers = layers + added
            selectedLayer = layers.lastIndex
        }
        markedLayers = emptySet()
        pruneBoundsCache()
        status = s(R.string.st_shape_added, name)
    }

    /** Parse and place a single file. Returns true when something was loaded. */
    private fun loadDesignContent(text: String, replace: Boolean): Boolean {
        // A .kcp project (opened via the file button too, not just "Open project"): load the saved
        // arrangement as-is. Detected by content, so the extension/MIME doesn't matter.
        val proj = runCatching { de.knutwurst.knutcut.data.ProjectIO.fromJson(text) }.getOrNull()
        if (!proj.isNullOrEmpty()) {
            pushHistory()
            layers = proj
            selectedLayer = 0
            markedLayers = emptySet()
            pruneBoundsCache()
            status = qty(R.plurals.st_project_loaded, proj.size, proj.size)
            return true
        }
        val parsed = parseDesign(text)
        if (parsed == null || parsed.layers.isEmpty()) { status = s(R.string.st_no_cuttable_paths); return false }
        pushHistory()
        if (replace || layers.isEmpty()) {
            layers = placeAtHome(parsed.layers)
            selectedLayer = 0
            camScale = 1f
            camOffset = Offset.Zero
        } else {
            val added = appendPlaced(placeAtHome(parsed.layers))
            layers = layers + added
            selectedLayer = layers.size - added.size
        }
        markedLayers = emptySet()
        pruneBoundsCache()
        val n = parsed.layers.size
        val skip = if (parsed.skipped > 0) qty(R.plurals.st_skipped_suffix, parsed.skipped, parsed.skipped) else ""
        status = qty(R.plurals.st_design_loaded, n, n, skip)
        return true
    }

    private class ParsedDesign(val layers: List<Layer>, val skipped: Int)

    private fun mergeLibraryLayers(name: String, parsedLayers: List<Layer>): Layer {
        // Library motifs are filled glyphs (fill, no stroke): SVG closes each subpath implicitly when
        // filling, so authors often omit the trailing "Z". For cutting we must close every contour,
        // otherwise the plotter leaves open lines (e.g. the inner counters of "Ab Testing").
        val polylines = parsedLayers.flatMap { it.polylines }.map { if (it.closed) it else it.copy(closed = true) }
        val colors = parsedLayers.flatMap { it.colorList() }
        val singleColor = colors.firstOrNull()?.takeIf { c -> colors.all { it == c } }
        return Layer(
            name = name,
            polylines = polylines,
            tool = tool,
            visible = true,
            colorArgb = singleColor,
            polylineColors = if (colors.any { it != singleColor }) colors else null,
        )
    }

    /** Parse a file into layers (SVG → DXF → PLT, detected by content). Null if nothing usable. */
    private fun parseDesign(text: String): ParsedDesign? {
        val head = text.trimStart()
        return try {
            when {
                head.startsWith("<") || head.contains("<svg", ignoreCase = true) -> {
                    val result = SvgParser.parseShapesResult(text)
                    val out = result.shapes.map { Layer(it.name, it.polylines, tool, visible = true, colorArgb = it.colorArgb) }
                    if (out.flatMap { it.polylines }.flatMap { it.points }.isEmpty()) null
                    else ParsedDesign(out, result.skipped)
                }
                de.knutwurst.knutcut.svgcore.DxfParser.looksLikeDxf(text) -> {
                    val result = de.knutwurst.knutcut.svgcore.DxfParser.parseShapes(text)
                    val out = result.shapes.map { Layer(it.name, it.polylines, tool, visible = true, colorArgb = it.colorArgb) }
                    if (out.flatMap { it.polylines }.flatMap { it.points }.isEmpty()) null
                    else ParsedDesign(out, result.skipped)
                }
                else -> {
                    val polys = PltParser.parse(text)
                    if (polys.isEmpty()) null
                    else ParsedDesign(listOf(Layer("PLT-Datei", polys, tool, visible = true)), 0)
                }
            }
        } catch (e: Exception) {
            status = s(R.string.st_file_read_error, e.message)
            null
        }
    }

    /** Shift a freshly home-placed group so it sits to the right of the existing content (wrapping rows). */
    private fun appendPlaced(group: List<Layer>): List<Layer> {
        val gap = MAT_GAP_MM
        fun placedPoints(ls: List<Layer>) = ls.flatMap { l ->
            val m = layerMatrix(l); l.polylines.flatMap { pl -> pl.points.map { m.apply(it) } }
        }
        val eb = Bounds.ofOrNull(placedPoints(layers)) ?: return group
        val gb = Bounds.ofOrNull(placedPoints(group)) ?: return group
        val (dx, dy) = if (eb.maxX + gap + gb.widthMm <= mat.widthMm)
            (eb.maxX + gap - gb.minX) to (-gb.minY)
        else
            (-gb.minX) to (eb.maxY + gap - gb.minY)
        return group.map { it.copy(centerMm = Pt(it.centerMm.xMm + dx, it.centerMm.yMm + dy)) }
    }

    fun selectLayer(i: Int) { if (i in layers.indices) { selectedLayer = i; bendingText = false } }

    /** Rename a layer (trimmed; blank keeps the old name). */
    fun renameLayer(i: Int, name: String) {
        if (i !in layers.indices) return
        val n = name.trim()
        if (n.isEmpty() || n == layers[i].name) return
        pushHistory()
        layers = layers.toMutableList().also { it[i] = it[i].copy(name = n) }
    }

    /** Move a layer up (delta -1) or down (+1); reorder also changes the plot order. */
    fun moveLayer(i: Int, delta: Int) {
        val j = i + delta
        if (i !in layers.indices || j !in layers.indices) return
        pushHistory()
        layers = layers.toMutableList().also { it.add(j, it.removeAt(i)) }
        selectedLayer = j
        markedLayers = emptySet() // indices no longer map to the same layers after a reorder
    }

    /** Reset the camera (zoom + pan) to the default framing of the mat. */
    fun resetView() { camScale = 1f; camOffset = Offset.Zero }

    /** Clear the layer selection and any marks — the mat itself becomes the active selection. */
    fun deselectLayers() { selectedLayer = -1; markedLayers = emptySet(); bendingText = false }

    /** Start over: remove every layer and reset the view (undoable). Keeps device/material settings. */
    fun clearAll() {
        if (layers.isEmpty()) return
        pushHistory()
        layers = emptyList()
        selectedLayer = -1
        markedLayers = emptySet()
        // A node-edit, deform, or bend mode must not survive the loss of all layers.
        editorTool = EditorTool.SELECT
        bendingText = false
        pruneBoundsCache()
        camScale = 1f
        camOffset = Offset.Zero
        status = s(R.string.st_cleared)
    }

    /**
     * Auto-arrange every layer to save material: pack the pieces' bounding boxes into rows from the
     * top-left, with a gap, no overlaps. With [allow90] a piece may be turned 90° to fit better. This
     * normalises each layer's rotation to 0°/90° (it's a reflow), keeping size, scale and mirroring.
     */
    fun autoArrange(allow90: Boolean) {
        if (layers.isEmpty()) return
        pushHistory()
        val gap = MAT_GAP_MM
        val boxes = layers.mapIndexed { i, l ->
            val b = layerBounds(l)
            Nest.Box(i, b.widthMm * l.scaleX, b.heightMm * l.scaleY)
        }
        val placed = Nest.pack(boxes, mat.widthMm, gap, allow90).associateBy { it.id }
        layers = layers.mapIndexed { i, l ->
            val p = placed[i] ?: return@mapIndexed l
            // placed bbox is centred on centerMm, so its top-left = centerMm - (w/2, h/2)
            l.copy(rotationDeg = if (p.rotated) 90.0 else 0.0, centerMm = Pt(p.x + p.w / 2, p.y + p.h / 2))
        }
        selectedLayer = -1
        status = qty(R.plurals.st_arranged, layers.size, layers.size)
    }

    /** Add a new layer (e.g. a primitive shape or text) centred on the mat and select it. */
    fun addLayer(name: String, polylines: List<Polyline>, layerTool: Tool = tool, textSpec: TextSpec? = null) {
        if (polylines.isEmpty()) return
        pushHistory()
        val layer = Layer(name, polylines, layerTool, visible = true, centerMm = Pt(mat.widthMm / 2, mat.heightMm / 2), textSpec = textSpec)
        layers = layers + layer
        selectedLayer = layers.lastIndex
        markedLayers = emptySet()
        status = s(R.string.st_shape_added, name)
    }

    /**
     * Re-render the selected text layer with a new arc curve and update both the polylines and the
     * stored [TextSpec.curve].  No-op when the layer has no [TextSpec].  Pushes one undo step the
     * first time it is called for a given gesture (caller controls [pushHistory]).
     */
    fun applyTextCurve(index: Int, curve: Int, newPolylines: List<Polyline>) {
        if (index !in layers.indices) return
        val layer = layers[index]
        val spec = layer.textSpec ?: return
        pushHistory()
        layers = layers.mapIndexed { i, l ->
            if (i == index) l.copy(polylines = newPolylines, textSpec = spec.copy(curve = curve)) else l
        }
        pruneBoundsCache()
    }

    // -------------------------------------------------------------------------
    // On-mat text bending (drag a handle on the canvas instead of a slider)
    // -------------------------------------------------------------------------

    /** Enter on-mat bend mode for the selected text layer. No-op when it isn't a text layer. */
    fun startBendingText() {
        if (layers.getOrNull(selectedLayer)?.textSpec == null) return
        editorTool = EditorTool.SELECT
        bendingText = true
    }

    /** Leave on-mat bend mode. */
    fun stopBendingText() { bendingText = false }

    /**
     * Leave any active editor mode (bend, or draw/nodes) back to plain SELECT. Returns true if a mode
     * was actually exited — the Back handler uses this so Back steps out of a mode before it
     * backgrounds the app.
     */
    fun exitActiveMode(): Boolean = when {
        bendingText -> { bendingText = false; true }
        editorTool != EditorTool.SELECT -> { editorTool = EditorTool.SELECT; true }
        else -> false
    }

    /** Snapshot history once at the start of a bend drag (one undo step for the whole gesture). */
    fun beginTextCurve() { pushHistory() }

    /**
     * Re-render the selected text layer at [curve] (−100..100) and update its polylines + stored
     * curve, WITHOUT pushing history — call [beginTextCurve] once at the drag start. No-op when the
     * selected layer is not a text layer or the font is unavailable.
     */
    fun setSelectedTextCurve(curve: Int) {
        val idx = selectedLayer
        val layer = layers.getOrNull(idx) ?: return
        val spec = layer.textSpec ?: return
        val c = curve.coerceIn(-100, 100)
        val opt = fontRepo.options.getOrNull(spec.fontIndex) ?: return
        val poly = TextArc.layoutOnArc(opt.renderGlyphs(spec.text, spec.heightMm), c / 100.0)
        layers = layers.mapIndexed { i, l ->
            if (i == idx) l.copy(polylines = poly, textSpec = spec.copy(curve = c)) else l
        }
        pruneBoundsCache()
    }

    /**
     * Convert a freehand stroke (world-space mm points collected during a DRAW gesture) into a
     * smooth, editable PEN layer placed exactly where the stroke was drawn.
     *
     * The stroke is de-jittered with RDP (~0.6 mm tolerance) and then reduced to a small, editable
     * Bézier path via [simplifyToBudget] — a hand-drawn shape is easier to edit with a handful of
     * nodes than with dozens.  The resulting [Layer] stores the flattened polyline in
     * [Layer.polylines] (the render/cut source of truth) and the editable path in [Layer.editPath].
     *
     * [closed] decides whether the shape is closed: null (the default, used by the draw gesture)
     * closes the stroke when [autoCloseDrawn] is on and the ends roughly meet ([looksClosed]) — so
     * sketching a heart and lifting near the start yields a closed, plottable outline. Pass
     * true/false to force it. The open/close state can always be flipped later in the node editor.
     *
     * No-op when fewer than 2 distinct points are supplied.
     */
    fun addDrawnPath(worldPoints: List<Pt>, closed: Boolean? = null) {
        // Drop only CONSECUTIVE duplicate samples (zero-length segments). Revisited points are kept,
        // so a self-intersecting or back-traced stroke isn't silently altered.
        val samples = worldPoints.filterIndexed { i, p ->
            i == 0 || p.xMm != worldPoints[i - 1].xMm || p.yMm != worldPoints[i - 1].yMm
        }
        if (samples.size < 2) return

        val shouldClose = closed ?: (autoCloseDrawn && looksClosed(samples))
        val simplified = simplifyRdp(samples, RDP_TOLERANCE_MM)
        val path = simplifyToBudget(simplified, shouldClose)
        val poly = path.toPolyline()
        if (poly.points.size < 2) return

        val center = centerOf(poly.points)
        val name = s(R.string.ui_draw_layer_name)
        pushHistory()
        val layer = Layer(
            name = name,
            polylines = listOf(poly),
            tool = Tool.PEN,
            visible = true,
            centerMm = center,
            editPath = path,
            // Freeze the local frame at the creation centre; node edits then move only the node, not
            // the whole shape (centre == bounds centre here, so the frame starts as world = local).
            editOriginMm = center,
        )
        layers = layers + layer
        selectedLayer = layers.lastIndex
        markedLayers = emptySet()
        pruneBoundsCache()
        status = s(R.string.st_shape_added, name)
    }

    // -------------------------------------------------------------------------
    // Node editor (EditorTool.NODES)
    // -------------------------------------------------------------------------

    /** The selected layer's editable path, or null when none is available. */
    val selectedEditPath: EditablePath?
        get() = layers.getOrNull(selectedLayer)?.editPath

    /**
     * Convert a world-space point to local (design) coordinates for layer [index].
     * Returns null when the layer doesn't exist or its transform is degenerate.
     */
    fun worldToLayerLocal(index: Int, p: Pt): Pt? {
        val layer = layers.getOrNull(index) ?: return null
        return layerMatrix(layer).inverse()?.apply(p)
    }

    /**
     * Convert a local-coordinate point on layer [index] to world (mat) coordinates.
     * Returns null when the layer doesn't exist.
     */
    fun layerLocalToWorld(index: Int, p: Pt): Pt? {
        val layer = layers.getOrNull(index) ?: return null
        return layerMatrix(layer).apply(p)
    }

    /** Apply a new editPath to the selected layer and keep [polylines] in sync.
     *  Node editing bakes away any active warp: deform and deformSource are cleared. */
    private fun applyEditPath(newPath: EditablePath) {
        val i = selectedLayer
        if (i !in layers.indices) return
        val poly = newPath.toPolyline()
        layers = layers.mapIndexed { idx, l ->
            if (idx == i) l.copy(editPath = newPath, polylines = listOf(poly), deform = null, deformSource = null) else l
        }
        pruneBoundsCache()
    }

    /**
     * Snapshot history once at the start of a node-drag gesture.  Call once on finger-down;
     * the continuous drag updates don't add more steps.
     */
    fun beginNodeEdit() { pushHistory() }

    /**
     * Snapshot history once at the start of a deform-edit gesture (envelope corner drag, etc.).
     * Identical to [beginNodeEdit] but named for clarity at the call site.
     */
    fun beginDeformEdit() { pushHistory() }

    // -------------------------------------------------------------------------
    // Envelope editor (EditorTool.ENVELOPE)
    // -------------------------------------------------------------------------

    /**
     * Move one corner of the selected layer's [EnvelopeDeform] to [toLocal] (layer-local coords).
     *
     * [corner] 0 = TL, 1 = TR, 2 = BR, 3 = BL.  No-op when the selected layer has no
     * [EnvelopeDeform] active, the index is out of range, or no layer is selected.
     * Does NOT push history — call [beginDeformEdit] once at the start of the drag gesture.
     */
    fun moveEnvelopeCorner(corner: Int, toLocal: Pt) {
        val i = selectedLayer
        if (i !in layers.indices) return
        val spec = layers[i].deform as? EnvelopeDeform ?: return
        val updated = when (corner) {
            0 -> spec.copy(tl = toLocal)
            1 -> spec.copy(tr = toLocal)
            2 -> spec.copy(br = toLocal)
            3 -> spec.copy(bl = toLocal)
            else -> return
        }
        setLayerDeform(i, updated, pushHistory = false)
    }

    /** Move anchor [i] to [toLocal] (local coords). Does not push history — caller does that. */
    fun moveSelectedAnchor(i: Int, toLocal: Pt) {
        val path = selectedEditPath ?: return
        applyEditPath(path.moveAnchor(i, toLocal))
    }

    /** Move handle [side] of node [i] to [toLocal] (local coords). Does not push history. */
    fun moveSelectedHandle(i: Int, side: HandleSide, toLocal: Pt) {
        val path = selectedEditPath ?: return
        applyEditPath(path.moveHandle(i, side, toLocal))
    }

    /**
     * Bend the curve by dragging a point on segment [segmentIndex] at parameter [t] to [toLocal].
     * Does not push history — call [beginNodeEdit] once at the start of the drag gesture.
     * No-op when there is no selected editable path or the segment index is out of range.
     */
    fun dragSelectedSegment(segmentIndex: Int, t: Double, toLocal: Pt) {
        val path = selectedEditPath ?: return
        if (segmentIndex < 0 || segmentIndex >= path.nodes.size) return
        applyEditPath(path.dragSegment(segmentIndex, t, toLocal))
    }

    /** Insert a node on segment [segmentIndex] at parameter [t]. Pushes one undo step. */
    fun insertSelectedNode(segmentIndex: Int, t: Double) {
        val path = selectedEditPath ?: return
        pushHistory()
        applyEditPath(path.insertNode(segmentIndex, t))
    }

    /** Toggle the smooth flag on node [i]. Pushes one undo step. */
    fun toggleSelectedNodeSmooth(i: Int) {
        val path = selectedEditPath ?: return
        pushHistory()
        applyEditPath(path.setSmooth(i, !path.nodes[i].smooth))
    }

    /** True when the selected editable path is a closed (plottable) shape. */
    val selectedPathClosed: Boolean get() = selectedEditPath?.closed == true

    /** Flip the selected editable path between open and closed. Pushes one undo step.
     *  This is the manual override for the freehand auto-close: close any shape for a clean cut,
     *  or open one back up. No-op when there is no editable path. */
    fun toggleSelectedPathClosed() {
        val path = selectedEditPath ?: return
        pushHistory()
        applyEditPath(EditablePath(path.nodes, !path.closed))
    }

    /** Delete node [i]. No-op when the index is out of range or too few nodes remain. Pushes one undo step. */
    fun deleteSelectedNode(i: Int) {
        val path = selectedEditPath ?: return
        if (i !in path.nodes.indices) return   // bounds guard: out-of-range index is a no-op
        pushHistory()
        applyEditPath(path.deleteNode(i))
    }

    /**
     * Convert the selected layer's single polyline to an editable path.
     *
     * No-op when the layer already has an editPath, or has more than one polyline.
     * When the layer has an active deform the current (warped) geometry is baked into the editable
     * path and the deform is dropped — the warp becomes permanent node positions.
     * Pushes one undo step on success.
     */
    fun convertSelectedToEditablePath() {
        val i = selectedLayer
        val layer = layers.getOrNull(i) ?: return
        if (layer.editPath != null) return
        if (layer.polylines.size != 1) return
        pushHistory()
        val poly = layer.polylines[0]
        // simplifyToBudget keeps the node count small even for dense warped polylines; sparse paths
        // are smoothed directly without further reduction.
        val newPath = simplifyToBudget(poly.points, poly.closed)
        // Freeze the local pivot at the centre the matrix is using right now, so converting does not
        // shift the shape and later node edits stay in a fixed frame.
        val b = layerBounds(layer)
        val origin = Pt((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2)
        layers = layers.mapIndexed { idx, l ->
            // Bake away any active warp: the node positions come from the warped polyline.
            if (idx == i) l.copy(editPath = newPath, editOriginMm = origin, deform = null, deformSource = null) else l
        }
    }

    /** Mirror the selected layer (around its own centre). */
    fun mirrorSelectedHorizontal() { pushHistory(); updateSelected { it.copy(flipX = !it.flipX) } }
    fun mirrorSelectedVertical() { pushHistory(); updateSelected { it.copy(flipY = !it.flipY) } }

    /** Duplicate the selected layer, offset a little, and select the copy. */
    fun duplicateSelected() {
        val i = selectedLayer
        val src = layers.getOrNull(i) ?: return
        pushHistory()
        val copy = src.copy(
            name = src.name + " (Kopie)",
            centerMm = Pt(src.centerMm.xMm + 5.0, src.centerMm.yMm + 5.0),
        )
        layers = layers.toMutableList().apply { add(i + 1, copy) }
        selectedLayer = i + 1
    }

    /** Remove the selected layer. */
    fun deleteSelected() {
        val i = selectedLayer
        if (i !in layers.indices) return
        pushHistory()
        layers = layers.filterIndexed { idx, _ -> idx != i }
        selectedLayer = selectedLayer.coerceIn(0, (layers.size - 1).coerceAtLeast(0))
        markedLayers = emptySet()
        // A node-edit, deform, or bend mode must not outlive the layer it was editing.
        editorTool = EditorTool.SELECT
        bendingText = false
        pruneBoundsCache()
    }

    /** Set the selected layer's size in mm (bounding box after scale), keeping its top-left corner fixed. */
    fun setSelectedSizeMm(widthMm: Double, heightMm: Double) {
        val layer = layers.getOrNull(selectedLayer) ?: return
        pushHistory()
        val b = layerBounds(layer)
        resizeKeepingTopLeft(Placement.scaleFor(b.widthMm, widthMm), Placement.scaleFor(b.heightMm, heightMm), layer.rotationDeg)
    }

    /** Set the selected layer's rotation (degrees), keeping its top-left corner fixed. */
    fun setSelectedRotation(deg: Double) {
        val layer = layers.getOrNull(selectedLayer) ?: return
        pushHistory()
        resizeKeepingTopLeft(layer.scaleX, layer.scaleY, deg)
    }

    /** Apply a new scale/rotation to the selected layer while its top-left corner stays where it was. */
    private fun resizeKeepingTopLeft(newSx: Double, newSy: Double, newRot: Double) {
        val layer = layers.getOrNull(selectedLayer) ?: return
        val b = layerBounds(layer)
        // Pivot must match layerMatrix (frozen origin for editable layers, else bounds centre).
        val lc = layerPivot(layer)
        val tlLocal = Pt(b.minX, b.minY)
        val oldTopLeft = layerMatrix(layer).apply(tlLocal)
        val fx = if (layer.flipX) -1.0 else 1.0
        val fy = if (layer.flipY) -1.0 else 1.0
        val rotScale = Matrix.rotate(newRot) * Matrix.scale(newSx * fx, newSy * fy)
        val off = rotScale.apply(tlLocal.xMm - lc.xMm, tlLocal.yMm - lc.yMm)
        val newCenter = Pt(oldTopLeft.xMm - off.xMm, oldTopLeft.yMm - off.yMm)
        updateSelected {
            it.copy(scaleX = newSx, scaleY = newSy, rotationDeg = ((newRot % 360) + 360) % 360, centerMm = newCenter)
        }
    }

    /** Align the selected layer on the mat per axis (-1 = left/top, 0 = centre, 1 = right/bottom; null = leave). */
    fun alignSelected(hx: Int?, vy: Int?) {
        val corners = placedCorners()
        if (corners.size != 4) return
        pushHistory()
        val minX = corners.minOf { it.xMm }; val maxX = corners.maxOf { it.xMm }
        val minY = corners.minOf { it.yMm }; val maxY = corners.maxOf { it.yMm }
        val w = maxX - minX; val h = maxY - minY
        var dx = 0.0; var dy = 0.0
        if (hx != null) { val tx = when { hx < 0 -> 0.0; hx > 0 -> mat.widthMm - w; else -> (mat.widthMm - w) / 2 }; dx = tx - minX }
        if (vy != null) { val ty = when { vy < 0 -> 0.0; vy > 0 -> mat.heightMm - h; else -> (mat.heightMm - h) / 2 }; dy = ty - minY }
        centerMm = Pt(centerMm.xMm + dx, centerMm.yMm + dy)
    }

    fun alignHorizontal(hx: Int) = alignSelected(hx, null)
    fun alignVertical(vy: Int) = alignSelected(null, vy)

    private fun updateSelected(f: (Layer) -> Layer) {
        val i = selectedLayer
        if (i !in layers.indices) return
        layers = layers.mapIndexed { idx, l -> if (idx == i) f(l) else l }
    }

    private val EMPTY_BOUNDS = Bounds(0.0, 0.0, 0.0, 0.0)
    // Bounds depend only on a layer's polylines, which don't change while it is dragged/scaled
    // (copy() keeps the same list reference), so memoise them by identity to keep redraws cheap.
    private val boundsCache = java.util.IdentityHashMap<List<Polyline>, Bounds>()
    private fun layerBounds(layer: Layer): Bounds =
        boundsCache.getOrPut(layer.polylines) {
            Bounds.ofOrNull(layer.polylines.flatMap { it.points }) ?: EMPTY_BOUNDS
        }

    /** Drop cached bounds whose geometry is no longer on any layer, so the cache can't grow forever. */
    private fun pruneBoundsCache() {
        val keep = layers.map { it.polylines }
        boundsCache.keys.removeAll { key -> keep.none { it === key } }
    }

    /** Place layers at their original relative positions with the whole design's top-left at (0,0). */
    private fun placeAtHome(ls: List<Layer>): List<Layer> {
        val allPts = ls.flatMap { it.polylines }.flatMap { it.points }
        if (allPts.isEmpty()) return ls
        val gb = Bounds.of(allPts)
        return ls.map { l ->
            val b = layerBounds(l)
            val lcx = (b.minX + b.maxX) / 2
            val lcy = (b.minY + b.maxY) / 2
            l.copy(centerMm = Pt(lcx - gb.minX, lcy - gb.minY), scaleX = 1.0, scaleY = 1.0, rotationDeg = 0.0)
        }
    }

    fun selectMaterial(m: Material) {
        material = m
        force = m.force
        settings.materialId = m.id
        settings.force = m.force
    }

    // User-defined materials, merged with the built-in presets.
    var customMaterials by mutableStateOf(settings.customMaterials); private set

    fun allMaterials(): List<Material> = (Materials.presets + customMaterials).sortedBy { it.display().lowercase() }

    // Materials used in an actual plot, most recent first (max 5). Only updated after a real plot.
    var recentMaterialIds by mutableStateOf(settings.recentMaterialIds); private set

    /** Recently plotted materials, most recent first, resolved to their current definitions. */
    fun recentMaterials(): List<Material> {
        val all = allMaterials()
        return recentMaterialIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
    }

    private fun recordMaterialUse(id: String) {
        val updated = (listOf(id) + recentMaterialIds.filter { it != id }).take(5)
        recentMaterialIds = updated
        settings.recentMaterialIds = updated
    }

    fun isCustomMaterial(m: Material): Boolean = m.id.startsWith("custom-")

    fun addMaterial(name: String, forceValue: Int): Material {
        val m = Material(
            id = "custom-" + java.util.UUID.randomUUID(),
            name = name.trim().ifBlank { "Material" },
            force = forceValue.coerceIn(Materials.FORCE_MIN, Materials.FORCE_MAX),
        )
        customMaterials = customMaterials + m
        settings.customMaterials = customMaterials
        selectMaterial(m)
        return m
    }

    fun updateMaterial(id: String, name: String, forceValue: Int) {
        val f = forceValue.coerceIn(Materials.FORCE_MIN, Materials.FORCE_MAX)
        customMaterials = customMaterials.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }, force = f) else it }
        settings.customMaterials = customMaterials
        customMaterials.firstOrNull { it.id == id }?.let { if (material.id == id) selectMaterial(it) }
    }

    fun deleteMaterial(id: String) {
        customMaterials = customMaterials.filter { it.id != id }
        settings.customMaterials = customMaterials
        if (material.id == id) selectMaterial(Materials.default)
    }

    /**
     * Tool of the currently selected layer, falling back to the global default. Drives which
     * pressure (pen vs. knife) the Material & Druck sheet shows, so the value always matches the
     * tool the selected layer will actually cut with.
     */
    val selectedTool: Tool get() = layers.getOrNull(selectedLayer)?.tool ?: tool

    /** Tool the Material sheet edits: the selected layer's, or the global default when the mat is selected. */
    val activeTool: Tool get() = if (matSelected) tool else selectedTool

    /** Set the tool the Material sheet edits — the selected layer when one is chosen, else all layers. */
    fun setActiveTool(t: Tool) {
        if (matSelected) {
            setAllTool(t)
        } else {
            tool = t
            settings.toolSp = t.sp
            setLayerTool(selectedLayer, t)
        }
    }

    /** Set the tool for all layers (the bottom quick-select). */
    fun setAllTool(t: Tool) {
        if (tool == t && layers.all { it.tool == t }) return
        pushHistory()
        tool = t
        settings.toolSp = t.sp
        layers = layers.map { it.copy(tool = t) }
    }

    fun setLayerTool(index: Int, t: Tool) {
        if (layers.getOrNull(index)?.tool == t) return
        pushHistory()
        layers = layers.mapIndexed { i, l -> if (i == index) l.copy(tool = t) else l }
    }

    fun toggleLayerVisible(index: Int) {
        pushHistory()
        layers = layers.mapIndexed { i, l -> if (i == index) l.copy(visible = !l.visible) else l }
    }

    /**
     * Apply [spec] to layer [index].  The first call preserves the layer's current geometry as the
     * deform source; subsequent calls always re-warp from that original source, so changing the
     * spec (e.g. a different radius) doesn't accumulate distortion.
     *
     * Pass [pushHistory] = false when calling repeatedly during a live drag preview; pass true (the
     * default) to record an undoable snapshot on commit.
     */
    fun setLayerDeform(index: Int, spec: DeformSpec, pushHistory: Boolean = true) {
        if (index !in layers.indices) return
        val layer = layers[index]
        val source = layer.deformSource ?: layer.polylines
        val warped = DeformEngine.apply(spec, source)
        if (pushHistory) pushHistory()
        layers = layers.mapIndexed { i, l ->
            // Deform and node-editing are mutually exclusive: clear editPath (and its frozen pivot)
            // when a deform is set.
            if (i == index) l.copy(polylines = warped, deform = spec, deformSource = source, editPath = null, editOriginMm = null) else l
        }
        pruneBoundsCache()
    }

    /** Convenience wrapper that targets the currently selected layer. */
    fun setSelectedDeform(spec: DeformSpec, pushHistory: Boolean = true) =
        setLayerDeform(selectedLayer, spec, pushHistory)

    /**
     * Remove the deformation from layer [index], restoring its original geometry.
     * No-op when the layer has no active deformation.
     */
    fun clearLayerDeform(index: Int) {
        if (index !in layers.indices) return
        val layer = layers[index]
        val src = layer.deformSource ?: return   // nothing to clear
        pushHistory()
        layers = layers.mapIndexed { i, l ->
            if (i == index) l.copy(polylines = src, deform = null, deformSource = null) else l
        }
        pruneBoundsCache()
    }

    /** Convenience wrapper that targets the currently selected layer. */
    fun clearSelectedDeform() = clearLayerDeform(selectedLayer)

    /**
     * Bounds of the selected layer's deform source geometry (original, pre-warp), or its current
     * polylines when no deform is active. Returns null when no layer is selected or it has no points.
     */
    fun selectedDeformSourceBounds(): Bounds? {
        val layer = layers.getOrNull(selectedLayer) ?: return null
        val src = layer.deformSource ?: layer.polylines
        return Bounds.ofOrNull(src.flatMap { it.points })
    }

    /** Bake a layer's placement into its geometry, so its coordinates already sit where it is drawn.
     *  Also clears deform, deformSource, and editPath: the baked coordinates are the new source of
     *  truth, and keeping stale pre-bake data would cause double-warping if a deform is reapplied. */
    private fun bake(layer: Layer): Layer {
        val m = layerMatrix(layer)
        val baked = layer.polylines.map { pl -> Polyline(pl.points.map { m.apply(it) }, pl.closed) }
        return layer.copy(
            polylines = baked, centerMm = centerOf(baked.flatMap { it.points }),
            scaleX = 1.0, scaleY = 1.0, rotationDeg = 0.0, flipX = false, flipY = false,
            deform = null, deformSource = null, editPath = null, editOriginMm = null,
        )
    }

    private fun centerOf(points: List<Pt>): Pt {
        val b = Bounds.ofOrNull(points) ?: return Pt(0.0, 0.0)
        return Pt((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2)
    }

    /** Break every layer into one layer per contour, keeping each where it currently sits. */
    fun splitLayers() {
        pushHistory()
        var n = 0
        layers = layers.flatMap { layer ->
            val b = bake(layer)
            val cols = b.colorList()
            b.polylines.mapIndexed { i, pl ->
                Layer("Form ${++n}", listOf(pl), layer.tool, layer.visible, centerMm = centerOf(pl.points), colorArgb = cols.getOrNull(i))
            }
        }
        selectedLayer = 0
        markedLayers = emptySet()
        pruneBoundsCache()
    }

    /** Tile the selected layer into a cols×rows grid, each copy offset by its footprint plus a gap. */
    fun tileSelected(cols: Int, rows: Int, gapMm: Double = 5.0) {
        val i = selectedLayer
        if (i !in layers.indices || cols < 1 || rows < 1 || (cols == 1 && rows == 1)) return
        val src = layers[i]
        val b = layerBounds(src)
        val stepX = b.widthMm * src.scaleX + gapMm
        val stepY = b.heightMm * src.scaleY + gapMm
        pushHistory()
        val copies = ArrayList<Layer>()
        for (r in 0 until rows) for (c in 0 until cols) {
            if (r == 0 && c == 0) continue
            copies.add(src.copy(
                name = "${src.name} ${r * cols + c + 1}",
                centerMm = Pt(src.centerMm.xMm + c * stepX, src.centerMm.yMm + r * stepY),
            ))
        }
        layers = layers.toMutableList().also { it.addAll(i + 1, copies) }
        pruneBoundsCache()
    }

    /** Merge all layers into one, keeping the current arrangement and each shape's colour. */
    fun mergeLayers() {
        if (layers.isEmpty()) return
        pushHistory()
        val baked = layers.map { bake(it) }
        val polys = baked.flatMap { it.polylines }
        val cols = baked.flatMap { it.colorList() }
        layers = listOf(Layer("Alle Formen", polys, layers.first().tool, visible = true,
            centerMm = centerOf(polys.flatMap { it.points }), colorArgb = layers.first().colorArgb, polylineColors = cols))
        selectedLayer = 0
        markedLayers = emptySet()
        pruneBoundsCache()
    }

    /**
     * Split all layers to individual polylines, then re-merge them grouped by colour.
     * Each unique colour becomes one layer; polylines without a colour form a separate layer.
     * Layer names use the hex colour value for easy identification.
     */
    fun mergeByColor() {
        if (layers.isEmpty()) return
        pushHistory()
        val firstTool = layers.first().tool
        // Flatten to (polyline, colour) pairs, baking transforms so positions are preserved.
        val flat = layers.flatMap { layer ->
            val b = bake(layer)
            val cols = b.colorList()
            b.polylines.mapIndexed { i, pl -> pl to cols.getOrNull(i) }
        }
        // Group by RGB (ignoring alpha) in document order, so the same colour at different opacities
        // lands in one layer rather than splitting into look-alike groups.
        val byColor = LinkedHashMap<Int?, MutableList<Polyline>>()
        flat.forEach { (pl, c) -> byColor.getOrPut(c?.and(0xFFFFFF)) { mutableListOf() }.add(pl) }
        layers = byColor.map { (rgb, polys) ->
            val argb = rgb?.let { it or 0xFF000000.toInt() }
            val name = if (rgb != null) "#%06X".format(rgb) else "Ohne Farbe"
            Layer(name, polys, firstTool, visible = true,
                centerMm = centerOf(polys.flatMap { it.points }), colorArgb = argb)
        }
        selectedLayer = 0
        markedLayers = emptySet()
        pruneBoundsCache()
    }

    // --- Multi-select (in the layers list) ---
    fun toggleMarked(i: Int) { markedLayers = if (i in markedLayers) markedLayers - i else markedLayers + i }
    fun clearMarks() { markedLayers = emptySet() }

    /** Merge the marked layers into one (keeping arrangement), placed where the first marked layer was. */
    fun mergeMarked() {
        val marks = markedLayers.filter { it in layers.indices }.toSortedSet()
        if (marks.size < 2) return
        pushHistory()
        val firstTool = layers[marks.first()].tool
        val firstColor = layers[marks.first()].colorArgb
        val bakedMarks = marks.map { bake(layers[it]) }
        val polys = bakedMarks.flatMap { it.polylines }
        val cols = bakedMarks.flatMap { it.colorList() }
        val merged = Layer("Zusammengeführt", polys, firstTool, visible = true,
            centerMm = centerOf(polys.flatMap { it.points }), colorArgb = firstColor, polylineColors = cols)
        val out = ArrayList<Layer>()
        var inserted = false
        layers.forEachIndexed { idx, l ->
            if (idx in marks) { if (!inserted) { out.add(merged); inserted = true } } else out.add(l)
        }
        layers = out
        selectedLayer = out.indexOf(merged).coerceAtLeast(0)
        markedLayers = emptySet()
        pruneBoundsCache()
    }

    /** Reset the cutting settings (compensation + blade offset) to their defaults. */
    fun resetCutSettings() {
        changeDragKnifeComp(true)
        changeBladeOffset(DragKnife.DEFAULT_OFFSET.toInt())
    }

    fun changeForce(v: Int) { force = v; settings.force = v }

    fun changePenForce(v: Int) { penForce = v; settings.penForce = v }

    fun changeCutSelectedOnly(v: Boolean) { cutSelectedOnly = v }

    fun changeCutCopies(n: Int) { cutCopies = n.coerceIn(1, 10) }

    /** Pressure for a tool: the material's knife pressure for the knife, the lighter pen pressure for the pen. */
    fun forceFor(t: Tool): Int = if (t == Tool.PEN) penForce else force

    fun selectMat(m: Mat) {
        mat = m
        settings.matName = m.name
        if (hasDesign) resetPlacement()
    }

    /** Rotate the selected layer by 90°. */
    fun rotate90() { pushHistory(); rotationDeg = (rotationDeg + 90.0) % 360.0 }

    fun resetPlacement() {
        pushHistory()
        layers = placeAtHome(layers)
        selectedLayer = selectedLayer.coerceIn(0, (layers.size - 1).coerceAtLeast(0))
        camScale = 1f
        camOffset = Offset.Zero
    }

    /** Reset every layer's placement and the mat view, keeping the mat (no layer) selected. */
    fun resetAll() {
        resetPlacement()
        selectedLayer = -1
    }

    /** Reset only the selected layer's size, aspect ratio, rotation, mirroring and position to home. */
    fun resetSelectedPlacement() {
        val i = selectedLayer
        val layer = layers.getOrNull(i) ?: return
        pushHistory()
        val b = layerBounds(layer)
        layers = layers.mapIndexed { idx, l ->
            if (idx == i) l.copy(
                scaleX = 1.0, scaleY = 1.0, rotationDeg = 0.0, flipX = false, flipY = false,
                centerMm = Pt((b.maxX - b.minX) / 2, (b.maxY - b.minY) / 2),
            ) else l
        }
    }

    /** Size of the selected layer in millimetres (bounding box after its scale). */
    fun designSizeMm(): Pair<Double, Double>? {
        val b = bounds ?: return null
        return b.widthMm * scaleX to b.heightMm * scaleY
    }

    /**
     * Move the selected layer so its centre is [center], snapping the placed box's top-left to the
     * snap grid when one is set. The offset between top-left and centre is invariant under the move,
     * so passing the running (un-snapped) centre each frame keeps small drags from being swallowed.
     */
    fun moveSelectedTo(center: Pt, guideTolMm: Double = 0.0) {
        var c = center
        // Grid snap: round the placed top-left to the snap step.
        val corners = placedCorners()
        if (snapMm > 0f && corners.size == 4) {
            val cur = centerMm
            val tlOffset = Pt(corners.minOf { it.xMm } - cur.xMm, corners.minOf { it.yMm } - cur.yMm)
            c = Snap.gridCenter(c, tlOffset, snapMm.toDouble())
        }
        // Alignment guides: snap the centre to another layer's centre or the mat centre when close.
        if (alignGuides && guideTolMm > 0.0) {
            val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
            layers.forEachIndexed { i, l -> if (i != selectedLayer) { xs.add(l.centerMm.xMm); ys.add(l.centerMm.yMm) } }
            xs.add(mat.widthMm / 2); ys.add(mat.heightMm / 2)
            alignGuideX = Snap.nearestWithin(c.xMm, xs, guideTolMm)
            alignGuideY = Snap.nearestWithin(c.yMm, ys, guideTolMm)
            c = Pt(alignGuideX ?: c.xMm, alignGuideY ?: c.yMm)
        } else {
            alignGuideX = null; alignGuideY = null
        }
        centerMm = c
    }

    fun clearGuides() { alignGuideX = null; alignGuideY = null }

    /** Compact "X · Y   W × H" readout for the selected layer (top-left + size), or null for the mat. */
    fun selectionReadout(): String? {
        val size = designSizeMm() ?: return null
        val corners = placedCorners()
        if (corners.size != 4) return null
        val u = displayUnit
        fun v(mm: Double) = String.format(java.util.Locale.GERMAN, "%.1f", u.fromMm(mm))
        val tlx = corners.minOf { it.xMm }
        val tly = corners.minOf { it.yMm }
        return "X ${v(tlx)} · Y ${v(tly)}   ${v(size.first)} × ${v(size.second)} ${u.label}"
    }

    /** Bounding box (in mm) over the placed points of [ls], or null if there's nothing to measure. */
    private fun placedExtent(ls: List<Layer>): Bounds? {
        val pts = ls.flatMap { l ->
            val m = layerMatrix(l)
            l.polylines.flatMap { pl -> pl.points.map { m.apply(it) } }
        }
        return Bounds.ofOrNull(pts)
    }

    /** Overall placed size of [ls] as "W × H unit" (how much material it spans), or null. */
    fun extentReadout(ls: List<Layer>): String? {
        val b = placedExtent(ls) ?: return null
        val u = displayUnit
        fun v(mm: Double) = String.format(java.util.Locale.GERMAN, "%.1f", u.fromMm(mm))
        return "${v(b.widthMm)} × ${v(b.heightMm)} ${u.label}"
    }

    // Memoise the overall readout: it's read on every editor recomposition (incl. camera pan/zoom)
    // while the mat is selected, but only changes when the layers or the display unit change.
    private var overallLayers: List<Layer>? = null
    private var overallUnit: DisplayUnit? = null
    private var overallText: String? = null

    /** Total size of all visible layers, shown bottom-left when the mat itself is selected. */
    fun overallReadout(): String? {
        if (overallLayers !== layers || overallUnit != displayUnit) {
            overallLayers = layers
            overallUnit = displayUnit
            overallText = extentReadout(layers.filter { it.visible })?.let { "Gesamt  $it" }
        }
        return overallText
    }

    fun connect(dev: BluetoothDevice, silent: Boolean = false) {
        if (connecting || connected) return
        connecting = true
        if (!silent) status = s(R.string.st_connecting, dev.name)
        viewModelScope.launch {
            try {
                val l = withContext(Dispatchers.IO) { BluetoothPlotter.connect(getApplication(), dev) }
                link?.close()
                link = l
                device = dev
                connected = true
                // Reflect an unexpected connection drop in the app state instead of staying "connected"
                // until the next command fails.
                l.onClosed = {
                    viewModelScope.launch {
                        if (link === l) {
                            connected = false
                            if (!cutting) status = s(R.string.st_connection_lost)
                        }
                    }
                }
                // The model (and thus the load/start gates) is the user's pick, not guessed from the
                // generic BT name — so we keep [model] as selected.
                settings.deviceAddress = dev.address
                settings.deviceTransport = "SPP"
                // Don't toast on the silent auto-reconnect at launch; the Settings sheet already shows
                // the connected state. Only confirm when the user connected on purpose — and name the
                // actual device, since it may be an explicitly-chosen non-plotter.
                if (!silent) status =
                    if (Devices.isCompatible(dev.name)) "Verbunden mit ${model.displayName}."
                    else "Verbunden mit ${dev.name} (kein unterstützter Plotter)."
            } catch (e: Exception) {
                connected = false
                if (!silent) status = s(R.string.st_connect_failed, e.message)
            } finally {
                connecting = false
            }
        }
    }

    /**
     * Connect to a Silhouette cutter over BLE GATT. Mirrors [connect] but uses [BluetoothLePlotter]
     * for the blocking GATT handshake. The selected [model] must be a BLE_SILHOUETTE model before
     * calling this, so the status message and subsequent [cut] dispatch are correct.
     */
    fun connectLe(dev: BluetoothDevice, silent: Boolean = false) {
        if (connecting || connected) return
        if (model.family != PlotterFamily.BLE_SILHOUETTE) {
            if (!silent) status = s(R.string.st_select_silhouette_first)
            return
        }
        connecting = true
        if (!silent) status = s(R.string.st_connecting_ble, dev.name)
        viewModelScope.launch {
            try {
                val l = withContext(Dispatchers.IO) { BluetoothLePlotter.connect(getApplication(), dev) }
                link?.close()
                link = l
                device = dev
                connected = true
                l.onClosed = {
                    viewModelScope.launch {
                        if (link === l) {
                            connected = false
                            if (!cutting) status = s(R.string.st_ble_connection_lost)
                        }
                    }
                }
                settings.deviceAddress = dev.address
                settings.deviceTransport = "BLE"
                if (!silent) status = s(R.string.st_connected_ble, model.displayName)
            } catch (e: Exception) {
                connected = false
                if (!silent) status = s(R.string.st_ble_connect_failed, e.message)
            } finally {
                connecting = false
            }
        }
    }

    /** On launch (or when permission arrives): reconnect to the last device if Bluetooth is on. */
    fun autoConnect() {
        if (connected || connecting) return
        val addr = settings.deviceAddress ?: return
        val ctx = getApplication<Application>()
        if (!hasConnectPermission(ctx) || !BluetoothPlotter.isEnabled(ctx)) return
        val dev = runCatching { BluetoothPlotter.adapter(ctx)?.getRemoteDevice(addr) }.getOrNull() ?: return
        // A Silhouette was last paired over BLE — reconnect on that path, not classic SPP.
        if (settings.deviceTransport == "BLE") { connectLe(dev, silent = true); return }
        // Only ever silently reconnect to a compatible plotter — never to some other paired device
        // (e.g. a laptop) that was connected explicitly once.
        if (!Devices.isCompatible(dev.name)) return
        connect(dev, silent = true)
    }

    private fun hasConnectPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    fun disconnect() {
        link?.close(); link = null; connected = false; device = null; status = s(R.string.st_disconnected)
    }

    /**
     * Test seam: attach a fake [ManagedLink] and mark the connection live, bypassing the Bluetooth
     * stack so the cut() transport guard can be exercised on the JVM. Not called by production code.
     */
    @VisibleForTesting
    internal fun attachLinkForTest(l: ManagedLink) { link = l; connected = true }

    private fun placedPolylines(ls: List<Layer>): List<Pair<Tool, List<Polyline>>> =
        ls.map { layer ->
            val m = layerMatrix(layer)
            layer.tool to layer.polylines.map { pl -> Polyline(pl.points.map { m.apply(it) }, pl.closed) }
        }

    /** One entry from [placedLayers]: the tool, the placed polylines, and each polyline's SVG colour. */
    data class PlacedLayer(val tool: Tool, val polylines: List<Polyline>, val colors: List<Int?>)

    /** Visible layers placed on the mat (mm, y-down) — for drawing in the editor. */
    fun placedLayers(): List<PlacedLayer> =
        layers.filter { it.visible }.map { layer ->
            val m = layerMatrix(layer)
            PlacedLayer(layer.tool, layer.polylines.map { pl -> Polyline(pl.points.map { m.apply(it) }, pl.closed) }, layer.colorList())
        }

    /** The layers a cut will send: just the selected one when [cutSelectedOnly], else all visible. */
    fun cutLayers(): List<Layer> =
        if (cutSelectedOnly) listOfNotNull(layers.getOrNull(selectedLayer)?.takeIf { it.visible })
        else layers.filter { it.visible }

    /** The single tool all cut layers share, or null if they mix tools (or there are none). */
    fun cutSingleTool(): Tool? = cutLayers().map { it.tool }.distinct().singleOrNull()

    /** Verb for the action button: cut, draw, or the neutral "plot" when the layers mix tools. */
    fun cutActionLabel(): String = when (cutSingleTool()) {
        Tool.PEN -> "Zeichnen"
        Tool.KNIFE -> "Schneiden"
        else -> "Plotten"
    }

    /** Switch the tool of the layers that will be cut (the pre-cut draw/cut toggle). */
    fun setCutTool(t: Tool) {
        if (tool == t && cutLayers().all { it.tool == t }) return
        pushHistory()
        tool = t
        settings.toolSp = t.sp
        if (cutSelectedOnly) {
            val i = selectedLayer
            if (i in layers.indices) layers = layers.mapIndexed { idx, l -> if (idx == i) l.copy(tool = t) else l }
        } else {
            layers = layers.map { it.copy(tool = t) }
        }
    }

    /** Placement matrix for one layer: rotate/scale/mirror about its own pivot, then move to [Layer.centerMm].
     *  Editable layers pivot about a frozen [Layer.editOriginMm] so node edits don't shift the frame. */
    private fun layerMatrix(layer: Layer): Matrix =
        Placement.matrix(
            layerBounds(layer), layer.centerMm, layer.scaleX, layer.scaleY, layer.rotationDeg,
            layer.flipX, layer.flipY, localCenter = layer.editOriginMm,
        )

    /** The local pivot the matrix turns/scales about: a frozen origin for editable layers, else the
     *  live bounds centre. Resize/rotate maths must use the same pivot as [layerMatrix]. */
    private fun layerPivot(layer: Layer): Pt =
        layer.editOriginMm ?: layerBounds(layer).let { Pt((it.minX + it.maxX) / 2, (it.minY + it.maxY) / 2) }

    /** The four corners of a layer's placed box, in mm (TL, TR, BR, BL; rotated with the layer). */
    fun layerCorners(index: Int): List<Pt> {
        val layer = layers.getOrNull(index) ?: return emptyList()
        val b = layerBounds(layer)
        val m = layerMatrix(layer)
        return listOf(Pt(b.minX, b.minY), Pt(b.maxX, b.minY), Pt(b.maxX, b.maxY), Pt(b.minX, b.maxY))
            .map { m.apply(it) }
    }

    /** Corners of the selected layer's box — the editor draws and manipulates these. */
    fun placedCorners(): List<Pt> = layerCorners(selectedLayer)

    /** True if a placed point lies outside the mat (with a small tolerance), so the editor can flag it. */
    fun isOutsideMat(p: Pt, tolMm: Double = 1.0): Boolean =
        p.xMm < -tolMm || p.yMm < -tolMm || p.xMm > mat.widthMm + tolMm || p.yMm > mat.heightMm + tolMm

    /** True if everything that will be cut fits on the mat (the full 12×12" area is usable). */
    fun designWithinMat(): Boolean {
        val tol = 1.0
        return placedPolylines(cutLayers()).all { (_, pls) ->
            pls.all { pl -> pl.points.none { it.xMm < -tol || it.xMm > mat.widthMm + tol || it.yMm < -tol || it.yMm > mat.heightMm + tol } }
        }
    }

    /** The topmost visible layer whose placed box contains [ptMm], or -1 if none. */
    fun layerAt(ptMm: Pt): Int {
        for (i in layers.indices.reversed()) {
            if (!layers[i].visible) continue
            val c = layerCorners(i)
            if (c.size == 4 && pointInQuad(ptMm, c)) return i
        }
        return -1
    }

    private fun pointInQuad(p: Pt, q: List<Pt>): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val a = q[i]; val b = q[(i + 1) % 4]
            val cross = (b.xMm - a.xMm) * (p.yMm - a.yMm) - (b.yMm - a.yMm) * (p.xMm - a.xMm)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s != 0) { if (sign == 0) sign = s else if (sign != s) return false }
        }
        return true
    }

    /**
     * Plotter-space polylines for one tool's layers being cut. The machine shares the editor's
     * coordinate system (origin top-left, x right, y down; verified by device plots), so no flip is
     * applied. [originOffsetMm] shifts Y down when the user wants extra space above the grid start
     * (0 by default — the plotter handles the gripper margin internally).
     */
    private fun plotterPolylinesFor(t: Tool): List<Polyline> {
        val dy = originOffsetMm.toDouble()
        val polys = cutLayers().filter { it.tool == t }.flatMap { layer ->
            val m = layerMatrix(layer)
            layer.polylines.map { pl -> Polyline(pl.points.map { val p = m.apply(it); Pt(p.xMm, p.yMm + dy) }, pl.closed) }
        }
        return if (optimizeCutOrder) CutOrder.optimize(polys) else polys
    }

    private var cutJob: Job? = null

    /**
     * Mirrors the stock app's workflow so the plotter never cuts into the air. After the handshake it
     * sets the material, then the work-area scale (JS, the step the machine needs before it will load
     * or accept a path), waits for the media to be fed in (Laden-Taste) and the start key on machines
     * that have them, and only then streams the path.
     */
    fun cut() {
        val l = link
        if (!connected || l == null) { status = s(R.string.st_connect_plotter_first); return }
        // The selected model decides the protocol; the link knows its transport. They must agree, or
        // we'd stream VEVOR JSON+HPGL into a Silhouette (or GPGL into a VEVOR). Refuse the mismatch up
        // front — before touching the design — instead of sending the wrong language down the wire.
        if (!model.family.matches(l.transport)) {
            status = if (model.family == PlotterFamily.BLE_SILHOUETTE) s(R.string.st_needs_ble, model.displayName)
                     else s(R.string.st_needs_spp, model.displayName)
            return
        }
        if (!hasDesign) { status = s(R.string.st_no_design); return }
        if (!designWithinMat()) { status = s(R.string.st_design_off_mat); return }
        if (cutting) return
        cutting = true
        progress = 0f
        cutJob = viewModelScope.launch {
            if (model.family == PlotterFamily.BLE_SILHOUETTE) runCutGpgl(l) else runCutVevor(l)
        }
    }

    /** GPGL cut path for Silhouette cutters over BLE. */
    private suspend fun runCutGpgl(l: ManagedLink) {
        val silDev = model.silhouetteDevice
        if (silDev == null) { finishCut(s(R.string.st_no_silhouette_profile)); return }
        val placed = cutLayers().flatMap { layer ->
            val m = layerMatrix(layer)
            layer.polylines.map { pl -> Polyline(pl.points.map { m.apply(it) }, pl.closed) }
        }
        val ordered = if (optimizeCutOrder) CutOrder.optimize(placed) else placed
        val polylines = if (cutCopies > 1) (1..cutCopies).flatMap { ordered } else ordered
        if (polylines.isEmpty()) { finishCut(s(R.string.st_no_visible_layers)); return }
        // The editor mat and the machine's cuttable area can differ (e.g. a 203 mm Portrait), so check
        // against the device's own bounds before streaming.
        val pts = polylines.flatMap { it.points }
        val minX = pts.minOfOrNull { it.xMm } ?: 0.0
        val minY = pts.minOfOrNull { it.yMm } ?: 0.0
        val maxX = pts.maxOfOrNull { it.xMm } ?: 0.0
        val maxY = pts.maxOfOrNull { it.yMm } ?: 0.0
        // Reject anything off the machine's cuttable area — too big, or pushed past the top/left origin
        // into negative units (which would otherwise stream as out-of-range coordinates).
        if (minX < 0 || minY < 0 || maxX > silDev.widthMm || maxY > silDev.lengthMm) {
            finishCut(s(R.string.st_design_off_area, model.displayName, silDev.widthMm.toInt(), silDev.lengthMm.toInt())); return
        }
        // User-chosen speed, clamped to what this family allows (GpglProtocol clamps again defensively).
        val speed = silhouetteSpeed.coerceIn(1, silhouetteSpeedMax)
        // Use the predominant tool's force (knife if any knife layer, else pen), not always the knife.
        val tool = if (cutLayers().any { it.tool == Tool.KNIFE }) Tool.KNIFE else Tool.PEN
        val cutSettings = GpglCutSettings(speed = speed, pressure = de.knutwurst.knutcut.data.Pressure.silhouette(forceFor(tool)))
        status = s(R.string.st_cutting_silhouette)
        try {
            val ok = withContext(Dispatchers.IO) {
                GpglSession(l).cut(silDev, cutSettings, polylines, shouldContinue = { isActive }) { p -> progress = p }
            }
            finishCut(if (ok) "Fertig an Silhouette gesendet." else "Silhouette hat nicht geantwortet – abgebrochen.")
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { l.write(GpglProtocol.INIT) } }
            finishCut(s(R.string.st_cancelled))
            throw e
        }
    }


    private suspend fun runCutVevor(l: ManagedLink) {
        val session = PlotterSession(l)
        try {
            status = s(R.string.st_connecting_plotter)
            val hs = withContext(Dispatchers.IO) { session.send(Handshake) }
            Log.d(TAG, "handshake -> $hs")
            if (hs == null) { finishCut(s(R.string.st_no_response)); return }

            val toolsUsed = cutLayers().map { it.tool }.distinct()
            if (toolsUsed.isEmpty()) { finishCut(s(R.string.st_no_visible_layers)); return }
            val primaryTool = if (Tool.KNIFE in toolsUsed) Tool.KNIFE else toolsUsed.first()

            // Setup, in the stock app's order: material, pressure (SP/FS), then the work-area scale.
            // Width is the grid width (12" = 12192 units); the second value is the work-area length the
            // machine may travel. The physical mat has margins above and below the printed grid, and the
            // plot is shifted down by the origin offset so the grid starts below the top margin — so the
            // declared length must be offset + grid, or the bottom of the grid gets clipped. The machine
            // needs this JS scale before it will feed material or accept a path (stock setPosition page).
            val widthUnits = (mat.widthMm * UNITS_PER_MM).toInt()
            val lengthUnits = ((mat.heightMm + originOffsetMm) * UNITS_PER_MM).toInt()
            Log.d(TAG, "mat=${mat.name} ${mat.widthMm}x${mat.heightMm}mm setScale=JS$widthUnits,$lengthUnits designSizeMm=${designSizeMm()}")
            // setmat may be rejected for a custom material id (non-fatal), but pressure and the work-area
            // scale MUST be acknowledged — otherwise the machine isn't in position mode and streaming the
            // path would cut into the air. Abort instead.
            val setmatResp = withContext(Dispatchers.IO) { session.send(PltCommand("setmat:${material.id};")) }
            Log.d(TAG, "setmat -> $setmatResp")
            val pressureResp = withContext(Dispatchers.IO) { session.send(PltCommand("SP${primaryTool.sp};FS${forceFor(primaryTool)};")) }
            Log.d(TAG, "setPressure -> $pressureResp")
            val scaleResp = withContext(Dispatchers.IO) { session.send(PltCommand("JS$widthUnits,$lengthUnits;")) }
            Log.d(TAG, "setScale -> $scaleResp")
            if (pressureResp == null || scaleResp == null) {
                finishCut(s(R.string.st_setup_not_confirmed)); return
            }

            // Load gate (machines with a paper key): wait until the media is fed in (queryMaterial
            // state 3), not just sitting at the sensor (state 1).
            if (model.hasPaperKey) {
                status = s(R.string.st_insert_material)
                if (!pollMaterialLoaded(session)) { finishCut(s(R.string.st_material_not_loaded)); return }
            }
            // Start gate (machines with a start key): wait for the physical Start button.
            if (model.hasStartKey) {
                status = s(R.string.st_press_start)
                if (!pollState(session, Query("queryStartKey"))) { finishCut(s(R.string.st_start_not_pressed)); return }
            }

            status = if (Tool.KNIFE in toolsUsed) s(R.string.st_cutting) else s(R.string.st_drawing)
            // Build one continuous command stream (the stock app's "plt"). Each tool's block starts
            // with its own SP/FS *inside* the stream — the machine takes the tool (SP1 = right/knife,
            // SP2 = left/pen) from the path data, not from a standalone setup command.
            val plt = ArrayList<String>()
            toolsUsed.forEach { t ->
                val base = plotterPolylinesFor(t)
                // Multiple copies = repeat every contour as extra passes (deeper cut / repeat draw).
                val polys = if (cutCopies > 1) (1..cutCopies).flatMap { base } else base
                var cmds = HpglEncoder.encode(polys)
                if (cmds.isEmpty()) return@forEach
                val raw = cmds.size
                // Drag-knife compensation is for the blade only — never apply it to the pen.
                if (dragKnifeComp && t == Tool.KNIFE) cmds = DragKnife.process(cmds, bladeOffset.toDouble())
                val xs = polys.flatMap { it.points }.map { mmToUnits(it.xMm) }
                val ys = polys.flatMap { it.points }.map { mmToUnits(it.yMm) }
                Log.d(TAG, "tool=${t.sp} force=${forceFor(t)} polylines=${polys.size} cmds=$raw->${cmds.size} (comp=$dragKnifeComp) " +
                    "x=[${xs.minOrNull()}..${xs.maxOrNull()}] y=[${ys.minOrNull()}..${ys.maxOrNull()}]")
                plt += "SP${t.sp}"; plt += "FS${forceFor(t)}"
                plt.addAll(cmds)
            }
            if (plt.isEmpty()) { finishCut(s(R.string.st_no_visible_layers)); return }
            plt += "PU"   // lift the tool at the end so the pen/knife isn't left pressed down

            // Stream as one continuous pltFile sequence (index 0..total-1, 30 commands per chunk),
            // exactly like the stock sendFile.
            val fileMsgs = Protocol.pathFile(plt, PLT_CHUNK)
            Log.d(TAG, "sendFile: ${plt.size} cmds in ${fileMsgs.size} chunks")
            fileMsgs.forEachIndexed { i, m ->
                val resp = withContext(Dispatchers.IO) { session.send(m) }
                if (resp == null) {
                    Log.d(TAG, "pltFile chunk ${i + 1}/${fileMsgs.size} not acknowledged")
                    finishCut(s(R.string.st_transfer_aborted, i + 1)); return
                }
                progress = (i + 1).toFloat() / fileMsgs.size
            }
            // Finish: end the file so the machine lifts the tool and returns (stock sends TB66 here).
            withContext(Dispatchers.IO) { Log.d(TAG, "finish TB66 -> ${session.send(PltCommand("TB66;"))}") }
            recordMaterialUse(material.id)   // remember it under "recently used" now that it actually plotted
            finishCut(s(R.string.st_sent_to_plotter))
        } catch (e: CancellationException) {
            cutting = false
            status = s(R.string.st_cancelled)
            // Stop streaming and tell the machine to finish the current file (the stock app's stop).
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { session.send(PltCommand("TB66;"), timeoutMs = 1500, maxAttempts = 1) }
            }
            throw e
        } catch (e: Exception) {
            finishCut(s(R.string.st_error_generic, e.message))
        } finally {
            // One place that always tidies up, however the cut ended (success, error, cancel).
            cutJob = null
            cutting = false
        }
    }

    private fun finishCut(msg: String) {
        cutting = false
        progress = 0f
        status = msg
    }

    /**
     * Smart-series load gate: wait until the material is fed in (queryMaterial state 3), not just
     * sitting at the sensor (state 1, which only means "detected").
     */
    private suspend fun pollMaterialLoaded(session: PlotterSession, attempts: Int = MATERIAL_POLL_ATTEMPTS, intervalMs: Long = MATERIAL_POLL_MS): Boolean {
        repeat(attempts) {
            val resp = withContext(Dispatchers.IO) { session.send(Query("queryMaterial")) }
            when (responseState(resp)) {
                3 -> return true
                1 -> status = s(R.string.st_material_detected)
                else -> status = s(R.string.st_insert_material)
            }
            delay(intervalMs)
        }
        return false
    }

    private suspend fun pollState(session: PlotterSession, query: Query, attempts: Int = START_POLL_ATTEMPTS, intervalMs: Long = START_POLL_MS): Boolean {
        repeat(attempts) {
            val resp = withContext(Dispatchers.IO) { session.send(query) }
            Log.d(TAG, "${query.action} -> $resp")
            if (responseStateReady(resp)) return true
            delay(intervalMs)
        }
        return false
    }

    fun cancelCut() {
        val job = cutJob
        if (job == null) { cutting = false; progress = 0f; return }
        // Cancel the running job; runCut's CancellationException handler resets state and signals the
        // machine (TB66). Keeping it in one place avoids the two paths fighting over the status.
        status = s(R.string.st_cancelling)
        cutJob = null
        job.cancel()
    }

    override fun onCleared() { shutdown() }

    private companion object {
        const val TAG = "Knutcut"
        const val MAX_HISTORY = 40          // undo/redo depth
        const val MAX_IMPORT_MB = 16        // per-file import cap
        const val PLT_CHUNK = 30            // commands per pltFile chunk (the stock sendFile value)
        const val MAT_GAP_MM = 5.0          // gap between pieces when appending / auto-arranging
        const val MATERIAL_POLL_ATTEMPTS = 240
        const val MATERIAL_POLL_MS = 700L   // load-gate polling: ~168 s total
        const val START_POLL_ATTEMPTS = 150
        const val START_POLL_MS = 800L      // start-gate polling: ~120 s total
        // RDP simplification tolerance for freehand strokes (mm).
        const val RDP_TOLERANCE_MM = 0.6
    }
}
