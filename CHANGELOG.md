# Changelog

Notable changes per release, newest first. Versions match `versionName` in the app and the
published `latest.json` in the releases repo.

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

## up to 0.38.x
- Mat editor (place, scale, rotate), per-layer tools, snapping and alignment guides.
- Text tool with outline and single-stroke (Hershey) fonts.
- Undo/redo, recently-used materials, auto-arrange.
- VEVOR Smart cut path over classic Bluetooth (JSON + HPGL), drag-knife compensation.
