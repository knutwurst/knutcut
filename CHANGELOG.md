# Changelog

Notable changes per release, newest first. Versions match `versionName` in the app and the
published `latest.json` in the releases repo.

## 0.57.1
- Fixed colour fill on a merged, uniformly-coloured layer leaving gaps. The "Farbig" fill grouped
  every same-colour contour into one even-odd path, so once a whole layer shared one colour,
  overlapping shapes cancelled in their overlap. Filling now groups by colour and containment:
  nested contours still carve real holes (letter counters, donut holes), but shapes that merely
  overlap fill solidly.

## 0.57.0
- New: set a layer's colour. The toolbar's duplicate button is now a colour button that opens a
  picker — a curated swatch palette for quick choices, plus an expandable custom picker with a
  hue bar, a saturation/brightness field and a hex field, and a "no colour" reset. The colour shows
  as the fill in "Farbig" mode and as the dot in the layer list. (Duplicate moved into the ⋯ menu.)
- Tiling moved out of the layer sheet into its own dialog: the sheet now has a compact "Kacheln…"
  button that opens a focused window with the grid, a live columns × rows preview and a spacing
  field, so the layer sheet stays tidy.

## 0.56.7
- Fixed resizing a mirrored (flipped) layer: the corner/side handles no longer collapse the layer to
  a sliver because the resize maths now accounts for the flip.
- Tiling now steps by the layer's rotated footprint, so a turned shape tiles without overlaps or gaps.
- The tile grid goes up to 10 × 10 again (it had been capped at 6 × 6) and the cells shrink to fit
  narrow screens.
- Switching tool, adding/importing a shape, arranging, splitting or merging now always leaves the
  node/bend editor cleanly, so an editing mode can't linger on a layer it no longer fits.
- The text field is single-line: curved text lays each letter on one arc, so multi-line input would
  have silently collapsed on the first bend.
- Removed the unreachable "Verformen" (warp) plumbing that was left disconnected from the editor; the
  curved-text feature is unaffected.

## 0.56.6
- Spiegeln (flip) is back as its own toolbar button. The toolbar buttons now shrink on narrow screens
  so the whole row stays on screen instead of one button getting pushed into the ⋯ menu.
- Removed the long-press context menu on a layer (it never fired reliably); use the toolbar buttons.

## 0.56.5
- Fixed the editor toolbar spilling off the right edge: Spiegeln (flip) moved into the ⋯ menu so the
  whole toolbar fits one row.
- Removed the button next to the gesture hint (it was redundant — bend/shape are toggled from the
  toolbar, and the mat exits those modes); the hint now uses the full width.
- Long-press a layer reliably opens the context menu (now uses the proper long-press detector).

## 0.56.4
- Reworked editor toolbar: Select and Draw are round toggle buttons, a context-aware Magic button
  bends a text layer or edits a shape's points (disabled when neither fits), the active tool glows,
  and Delete moved into the ⋯ menu.
- Long-press a layer on the mat for a context menu with the same actions (including delete).
- In the shape editor you can now drag the shape to move it (not just pan); tapping the mat while
  bending returns to Select.
- Tiling uses a tap-to-pick mini grid with a live columns × rows preview, instead of two steppers.
- Loading a project closes the settings sheet so you see it right away.

## 0.56.3
- The gesture hint under the mat sits in a fixed slot and wraps to two lines: it's no longer cut
  off, and the mode buttons no longer jump up when a hint appears. The hint shows directly below the
  mat for every mode (including a Select-mode tip).

## 0.56.2
- Drawn shapes close less eagerly, so an open line isn't turned into a cut contour by accident (the
  node editor's Open/Close button still closes one on demand).
- Node editor: smooth/corner now toggles on a real double-tap (not on any re-tap); the resize/rotate
  and node handles are larger and easier to hit on high-resolution screens; bend a text layer by
  dragging its handle, while dragging elsewhere just pans.
- Back leaves the active tool (draw/shape/bend) before it backgrounds the app.
- SVG export and freehand drawing handle large or self-intersecting input more robustly.

## 0.56.1
- Settings → Projekt: export the visible design as an SVG (stroked outlines at real millimetre
  size, placement baked in), so the design opens in other vector tools and re-imports here.

## 0.56.0
- Freehand drawing: sketch a shape on the mat with your finger. A stroke whose ends roughly meet
  closes into a cuttable shape; sharp turns stay crisp corners while curves stay round. Auto-close
  can be switched off in the settings.
- Shape editor ("Formen"): turn any layer into editable points. Drag the anchor or its two
  (always equal-length) handles; double-tap or long-press the line to add a point; double-tap a
  point to toggle between a smooth and a corner node; open or close the whole path. The node dots
  are larger and easier to hit, and a dragged point stays on the mat instead of flying off.
- Curved text: bend a text layer along an arc by dragging a handle directly on the mat, with a
  live preview circle. Each letter is placed on its own, so the text stays readable.
- Text tool: more fonts to choose from, and the input field and the font list now render in the
  selected font as a preview.

## 0.55.3
- Self-update now downloads the APK from a GitHub release asset (with the raw repo copy as a
  fallback), so the releases repo no longer grows with every version. Older installs keep updating
  via the existing raw URL during the transition.

## 0.55.2
- Library previews follow the display setting: "Farbig" shows a filled silhouette, "Nur Outline"
  shows the outline (the toolpath). The motifs have no colour of their own, so the fill uses a
  neutral tint.

## 0.55.1
- Fix: library motifs now cut as closed contours. Filled icons (Material Design Icons) often omit
  the closing command on inner shapes because the fill closes them implicitly; those contours were
  being cut as open lines (e.g. the counters in "Ab Testing"). Every motif contour is now closed.
- Library previews are back to outlines (the path the plotter traces) instead of filled silhouettes,
  which also makes open contours visible at a glance.

## 0.55.0
- New SVG motif library: 7,600+ offline motifs (Material Design Icons, Apache-2.0, via Iconify),
  searchable with categories and previews; a chosen motif is inserted as one grouped layer.
- Search matches motif names and tags only, so common letters or words no longer return the whole
  library.
- The library list is built off the main thread (no freeze on first open), and previews are parsed
  in the background and cached, so scrolling stays smooth.
- Previews are filled silhouettes that match what the plotter actually cuts, instead of thin
  outlines.
- Compact category picker and a slim scrollbar for the list.
- Removed the "test cut 20 mm" entry from the add menu; add a square from the same menu if you need one.

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
