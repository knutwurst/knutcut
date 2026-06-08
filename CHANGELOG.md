# Changelog

Notable changes per release, newest first. Versions match `versionName` in the app and the
published `latest.json` in the releases repo.

## 0.54.6
- Expanded the SVG library to more than 7,600 offline motifs.
- Replaced the multi-row category chips with a compact category picker.
- Added a visible scrollbar to the library list.

## 0.54.5
- Expanded the SVG library to nearly one thousand offline motifs from Material Design Icons via
  Iconify, with additional categories.
- Start screen: short, equal-width buttons for file, library, and shape.
- Library opens as a tall sheet with more visible content instead of a small half-height panel.

## 0.54.4
- SVG library: searchable motif picker with categories, previews, and direct insertion into the
  editor. Motifs are available offline and are placed as one grouped layer.

## 0.54.3
- Fix: sharing a design from another app a second time (after cancelling the first) no longer silently
  replaces the loaded project. Back press now backgrounds the app instead of finishing it, so the
  ViewModel stays alive and the import dialog always appears when a design is already loaded.

## 0.54.2
- Removed the double-back-to-exit gesture.
- About section: exit button (bottom-right) closes the app cleanly — handy when something hangs.
- Tap the logo in the header to open the project on GitHub.
- Tap the version line ("v… · Knutwurst") to open the changelog directly from the main screen.

## 0.54.1
- DXF import fixes: entities keep their file order; polylines defined in block/table sections no
  longer leak into the drawing; closed splines close properly; old-style POLYLINE arcs (bulges)
  are honoured; fit-point-only splines no longer vanish silently. DXF layers now map to separate
  editor layers with their colour (ACI), and file detection is stricter.

## 0.54.0
- DXF import: open .dxf files from the file picker or share from any app. Supports LINE,
  LWPOLYLINE (including bulge arcs), CIRCLE, ARC, ELLIPSE, and SPLINE. Units are read from
  the file header ($INSUNITS) and converted to mm automatically.

## 0.53.4
- Release signing with v3 key-rotation lineage (debug -> release); existing installs update
  over the air without a manual reinstall on Android 9+.

## 0.53.3
- Start screen: one "Open file/project" button (handles SVG/PLT/.kcp), and "Add shape" is back.

## 0.53.2
- Opening a .kcp via the normal file button loads it as a project (no more "not an SVG" error);
  the file is recognised by content, so the extension doesn't matter.

## 0.53.1
- "Changelog" entry in the About section, shown from the bundled file (offline).

## 0.53.0
- Donate section in settings: a PayPal button (official logo) opening paypal.me/oliverkoester.

## 0.52.0
- Settings reordered: Project on top (expanded), Plotter moved down, Language its own section.
- Save/Load swapped (Save on the right) and the current project file name is shown.
- Projects use the `.kcp` extension; "Open project" button on the empty start screen ("Add shape" removed — use +).

## 0.51.0–0.51.2
- Settings grouped into collapsible cards; sheet opens at full height and stays there.

## 0.50.2–0.50.3
- Localised the transport-mismatch messages; fixed the i18n-broken guard tests.
- Layout fixes: tiles label and the selected layer row no longer overflow.

## 0.50.1
- Language: picking "System" restores the real system locale; no more global-locale side effects
  from status messages. Real vector flags instead of emoji; settings reopen after a language change.
- Reordering a layer no longer leaves stale "marked" selections.
- Closes the Bluetooth link cleanly on the double-back exit.
- Launch cleanup keeps a downloaded update until it's actually installed.
- Layer row no longer overflows on small screens (edit/reorder show on the selected row).

## 0.50.0
- Rename a layer and move it up or down in the list (reorder also changes the plot order).
- "View" action recentres the camera on the mat.
- Cut sheet: copies stepper to plot several passes (deeper cut or repeated draw).
- Update dialog shows the release notes from `latest.json`.
- Settings: About section with the GitHub link and font licences.

## 0.49.1
- The "checking for a new version" toast now only shows when you tap the button, not on launch.

## 0.49.0
- Snap defaults to 5 mm.
- Language picker in settings (System / Deutsch / English); switching reloads the UI.

## 0.48.6
- Double-press back on the main screen to fully close the app (finishes the task and exits the process).
- Clear the cut job and link when the view model is cleared.

## 0.48.5
- Update check compares against the installed versionCode at runtime, so it can't offer a version
  that is already installed after an in-place update.

## 0.48.4
- Open the "install unknown apps" settings when that permission is missing, instead of doing nothing.

## 0.48.3
- Bust the CDN cache when reading `latest.json` so the update check always sees the newest release.

## 0.48.2
- Full German/English localisation (English default, German `values-de`), including status messages
  and plurals.

## 0.48.0–0.48.1
- Self-update: the app reads `latest.json` from the releases repo, checks on launch and on demand,
  downloads the APK, verifies its SHA-256, and hands it to the installer. Auto-check can be turned off.

## 0.45.0–0.47.x
- Project save/load (layers and placement as JSON).
- Tile/array layout for the selected layer.
- Optional nearest-neighbour cut-order optimisation (off by default).
- 20 mm test-cut square in the add menu.

## 0.39.0–0.44.0
- Silhouette support over Bluetooth LE (GPGL): device picker, pressure/speed, bounds and cancel
  handling, alongside the existing VEVOR (SPP) path.
- SVG colour display and a colour mode; group layers by colour.
- Import confirmation dialog (replace or add).

## 0.38.1–0.38.9
- Resilient SVG parser; hardened XML reader, plotter session, and cut lifecycle.
- Custom materials stored as JSON; device address excluded from backup.
- Text vectorisation bounded to prevent runaway paths.
- Structural undo de-duplication: snapshot only taken after a validated change.
- AutoMirrored icon variants; updated origin-offset default.

## 0.38.0
- "New" button; automatic layer arrangement via shelf packing.

## 0.37.3
- Text tool: outline fonts (TTF) and single-stroke Hershey fonts.
- Plotter model shown in the device dialog; bounding-box shelf packer added to svgcore.

## 0.36.0
- Undo/redo history; recently-used materials list; total design size readout in the cut sheet.

## 0.34.0–0.34.3
- Editor alignment and snapping overhaul; pre-cut tool-switch directly in the cut sheet.
- Mat geometry modelled as a 12-inch grid with a gripper start offset.

## 0.29.0–0.29.4
- Proportional corner handles, side handles, and top-left-anchored free resize.
- Selected layer's tool pressure shown in the Material sheet.
- Snap and resize helpers extracted into svgcore; app icon redrawn as a clean vector pen.

## 0.28.0–0.28.5
- Fix tool selection, cut orientation, end-of-job lift/return, pressure range, and path flip.
- Abbrechen sends an abort signal to the machine.
- Adjustable top origin offset (default 25 mm).

## 0.22.0–0.27.1
- Canvas-first UI redesign: contextual action bar, bottom sheets, clear empty state.
- German material names; prettier chips; cleaner toolbar with a spatial alignment grid.
- Pre-cut confirmation sheet; editable numeric fields; multi-layer merge.
- Add several designs to one mat; delete the last layer.
- New K/C monogram app icon and in-app logo mark.

## 0.11.0–0.21.1
- Mirror (H/V), duplicate, delete the selected layer; enter exact size and rotation.
- Warn when the design runs off the mat; preserve arrangement on split/merge.
- Configurable drag-knife blade offset; option to cut only the selected layer.
- Insert basic shapes (rect, circle, triangle, pentagon, hexagon, star).
- Import `.plt` (HPGL) files; manage custom materials locally.
- Choose display unit (mm / cm / inch); align layer on the mat grid.

## 0.9.1–0.10.1
- Cut output matched to the stock app; per-layer placement (place, scale, rotate); drag-knife compensation.
- Separate pen pressure from knife pressure; hardened plotter link and response handling.

## 0.4.0
- Layer split/merge; mat size picker moved into the settings sheet; revised logo.

## 0.3.0
- SVG imported as multiple layers with per-layer tool selection and visibility toggle.

## 0.2.0–0.2.3
- App logo and bounded mat frame; version name shown in the header.
- Cut flow: auto-reconnect; connect/disconnect in settings; no toast spam on load.

## 0.1.0
- Initial build: svgcore module (SVG → Bézier flattening → HPGL encoding, framed SPP protocol).
- Compose UI with zoom/pan canvas, mm grid, mat selector, and drag handles.
- Material list, knife/pen tool selector, full cut flow, system/light/dark theme.
