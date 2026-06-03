# Knutcut design

Android app that takes a shared SVG, lets me place and scale it on the plotter mat, and cuts it on a
VEVO Smart 1 over Bluetooth. German UI, no login.

## Scope

In:

- Receive an SVG via Android share / open-with (mainly from Cricut Export, but anything works).
- Place, scale and rotate on a mat sized to the plotter work area, with the real mm size shown.
- Pick from several plotters (the Smart1..Smart4 family) and remember the choice.
- Material presets (cut speed and force per material), taken from the stock app's values.
- Connect over Bluetooth Serial, run the cut, show progress and an "is media loaded" check.

Out, on purpose:

- Design library, auto-trace, accounts, cloud, other vendors.
- Any editing beyond place, scale, rotate.

## Why a new app instead of patching the stock one

The plotter ships with "Superb Cut" (`com.cn.huiwo.vevor`), a DCloud uni-app. Its logic is readable
JS, so patching looked possible, but:

- The hard rule is that the cutting side must stay correct and be covered by tests. That only works
  on code I own.
- Re-signing a modified uni-app risks the DCloud license check, which would refuse to start.
- A clean build also gives me proper German and no login without extra work.

So Knutcut is its own app. Superb Cut stays installed and untouched; I can still open it for its
design library.

## Modules

Same split as cricut-export: a pure-Kotlin core with JVM tests, plus a thin Compose UI.

- `svgcore` (pure Kotlin, no Android): `SvgParser` to mm geometry, `PathFlattener` (beziers and arcs
  to polylines, transforms applied), `HpglEncoder` (geometry to PU/PD plus the control commands),
  `Protocol` (the command sequence and chunking, transport-agnostic). No Android dependency so
  cricut-export can pull it in later.
- `transport` (Android): a `PlotterTransport` interface, an SPP implementation over
  `BluetoothSocket`, and a fake for tests.
- `app` (Compose): share target, mat editor, device picker, material picker, cut flow. German only.

## Flow

Shared SVG, parse to mm, place and scale on the mat, flatten, encode to HPGL, wrap in the command
sequence (handshake, queries, `TB66;`, setmat, SP/FS, chunked path, bye), write over the SPP socket
to the plotter. Each command waits for the device's CRLF response line before the next one, the same
way the stock app does it.

## Protocol

Worked out from the stock app's JS and its decompiled Bluetooth plugin. Full notes in
[protocol.md](protocol.md). Short version: classic Bluetooth Serial (SPP, UUID `00001101`), command
strings written raw, responses are CRLF lines, geometry is HPGL pen up/down. Not BLE.

## Testing

The cutting path is the thing that must not break, so it lives in `svgcore` and is unit-tested on the
JVM. The main test takes a known SVG (a 40x40 mm square at origin) to the exact command stream; that
stream is locked as a golden fixture once a real cut from Knutcut produces the square correctly. The
handshake and chunking sequence is tested through the fake transport. Manual check: a few real cuts
against a checklist.

## Build and toolchain

Reuses cricut-export's local toolchain (Android SDK and Gradle under `tools/`, JDK 17). Package
`de.knutwurst.knutcut`. Debug-signed release APK, installed directly, no store.

## Open points, settled while building

- Coordinate unit (likely 40 units/mm) and Y direction, confirmed by a real cut of the 40 mm square.
- Meaning of the `JS<a>,<b>;` command.
- Material id table and per-material speed/force values (pulled from the stock JS).
- Exact handshake and query strings, request and response.

## Decisions

- Smart1 first, picker built for the whole family.
- Place, scale, rotate in the editor.
- German only.
- `svgcore` as an in-repo module, reused by cricut-export later.
