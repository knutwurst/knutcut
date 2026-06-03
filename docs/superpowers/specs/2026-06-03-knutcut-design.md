# Knutcut — Design Spec

- **Date:** 2026-06-03
- **Author:** Knutwurst
- **Status:** Draft (awaiting review before implementation)
- **Related project:** `cricut-export` (produces the true-to-scale SVGs Knutcut consumes)

## 1. Purpose & scope

A standalone Android app that **receives an SVG via Android Share / Open-with** (primarily from the
Cricut Export app, but from any source), lets the user **place and scale** it on a virtual mat, and
**cuts it on a VEVO Smart 1 plotter over Bluetooth LE**. German-native, no login.

**In scope**
- Receive SVG via `ACTION_SEND` (`image/svg+xml`, `text/xml`, `application/octet-stream` fallback) and `ACTION_VIEW`.
- Place, scale and **rotate** on a mat that represents the plotter work area; show real-world mm size.
- **Select among multiple plotters** (the Smart1–4 device family); remember the chosen device.
- Material presets (cut force / speed per material), ported from the stock app's values.
- Bluetooth **classic Serial (SPP)** connect to the plotter and send the cut; surface progress and the "media loaded?" check.

**Non-goals (YAGNI)**
- Design/asset library, camera trace / auto-trace, account/login, cloud sync, multi-vendor support.
- Editing beyond place/scale/rotate. No new SVG authoring.

**Legal / framing.** Personal use with hardware the user owns. Knutcut ships **only original code**.
No third-party app binary, decompiled source, or proprietary asset is committed or redistributed
(`research/` is git-ignored). The plotter protocol is reimplemented for interoperability with a device
the user owns, informed by observing the user's own Bluetooth traffic — analogous to how `cricut-export`
documents Cricut's formats while keeping their binaries local.

## 2. Background — what the stock app is (findings)

The VEVO Smart 1's stock app is **"Superb Cut"** (`com.cn.huiwo.vevor`, v2.5.55), a **DCloud uni-app**
(Vue/JS). Relevant findings that shaped this design:

- The app logic is **readable, un-encrypted minified JS** (`app-service.js`). German, login, SVG handling
  and the plotter command logic all live there — not in Android resources or smali. (That's why the APK
  has no `de` resource: German is a vue-i18n locale inside the JS.)
- **Plotter protocol fully recovered (Phase 0 spike done — see [`protocol-findings.md`](protocol-findings.md)).**
  - **Transport = classic Bluetooth Serial (SPP/RFCOMM), _not_ BLE.** The native plugin
    `com.cn.wudao.uniplugin` (`WuDaoBTModule`/`BluetoothUtil`) connects via
    `createRfcommSocketToServiceRecord(00001101-…)` and writes command strings **raw** to the socket;
    responses are **`\r\n`-terminated lines** (send-and-wait). No GATT, no binary framing.
  - **Command sequence (each a raw string):** `handshake` → `queryMaterial`/`queryStartKey`/`queryPulled` →
    `TB66;` (init) → `setmat:<id>;` → `SP<speed>;FS<force>;` (speed + force) → `JS<a>,<b>;` →
    chunked HPGL path (`PU`/`PD`, wrapped in `TB66;`) → `bye`.
  - device registry: `name_lang:"Smart1"`, `knife_count:2`, models Smart1–4; matched by Bluetooth name/prefix.
- **No third-party capture needed.** Samsung's bug-report omitted the HCI snoop log, but it's moot: the wire
  bytes *are* the command strings (recovered from the JS), and the validation oracle is a real cut produced
  by **our own** app (see §6).

**Build-vs-modify decision.** We **build our own app** rather than patch the stock APK because (a) the hard
requirement "everything plotter-related must keep working, covered by tests" is only satisfiable on code we
own, (b) re-signing a modified uni-app risks the DCloud license check (`<lia>` bound to package+cert), and
(c) building also delivers goals "no login" and "good German" for free. The stock app stays untouched on the
phone and remains available for its asset library.

## 3. Architecture & modules

Mirrors `cricut-export`'s proven layering: a **pure-Kotlin, host-unit-tested core** plus a **thin Compose UI**.

- **`:svgcore`** — pure Kotlin, **no Android dependencies**, the reusable module (so `cricut-export` can
  depend on it later):
  - `SvgParser` — SVG → normalized geometry in **mm** (paths, basic shapes, transforms, `viewBox`, units).
  - `PathFlattener` — flatten beziers/arcs to polylines at a tolerance; apply transforms; absolute mm coords.
  - `HpglEncoder` — geometry → plotter command stream (`PU`/`PD` + control: `TB66;`, `JS`, `SP`, `setmat:`).
    Pure function → golden-testable.
  - `Protocol` — the JSON message model + chunking sequence (`handshake → setmat → pltFile chunks → bye`),
    **transport-agnostic** (emits messages/byte frames; knows nothing about BLE).
- **`:transport`** (Android) — classic Bluetooth Serial (SPP/RFCOMM):
  - `PlotterTransport` interface — `connect(device)`, `sendAndWait(command): ResponseLine`, `disconnect`.
  - `SppPlotterTransport` — discovery by device name/prefix, `createRfcommSocketToServiceRecord(00001101-…)`,
    write command strings to the `OutputStream`, read `\r\n`-terminated response lines (send-and-wait loop).
  - `FakePlotterTransport` — records writes and scripts response lines, for tests and offline development.
- **`:app`** — Jetpack Compose UI:
  - Share / `VIEW` intent receiver → load SVG.
  - **Mat canvas**: render the SVG on the VEVO Smart 1 work area; drag to place, pinch/handles to scale
    (and rotate); live mm dimensions.
  - **Device picker** (choose among paired Smart1–4 plotters), material picker (presets),
    `queryPulled` "media loaded?" gate, **Cut** with progress.
  - Settings: device, calibration offset, units. **German-only UI.**

## 4. Data flow

```
shared SVG
  → SvgParser            (→ mm geometry)
  → user place/scale     (mat transform)
  → PathFlattener        (→ absolute mm polylines)
  → HpglEncoder          (→ PU/PD command stream)
  → Protocol             (handshake → queries → TB66; → setmat → SP/FS → chunked HPGL → bye)
  → SppPlotterTransport  (raw RFCOMM socket writes, line-ack-gated)
  → VEVO Smart 1
```

Each command is gated on the device's `\r\n` response line (send-and-wait), matching the stock app's flow control.

## 5. Plotter protocol — spike complete

**Done.** Full protocol recovered from the stock app's readable JS + decompiled native plugin and written up
in [`protocol-findings.md`](protocol-findings.md): classic Bluetooth Serial (SPP/RFCOMM) transport, raw
string commands, `\r\n` response lines, and the `handshake → queries → TB66; → setmat → SP/FS → chunked
HPGL → bye` command sequence.

**Remaining details to pin during Phase 1** (cheap, confirmed via a real cut from our own app):
- exact coordinate unit (likely 40 units/mm) and origin orientation (Y-down vs Y-up),
- exact meaning of `JS<a>,<b>;`,
- the material-id table + per-material `SP`/`FS` values (extract from the JS material table),
- exact handshake/query request+response strings.

**Validation reference:** a 40 × 40 mm square at origin — re-cut from Knutcut, the generated command stream
becomes the golden fixture (§6).

## 6. Testing strategy (hard requirement)

- **Host-JVM unit tests over `:svgcore`** (no device): `SvgParser`, `PathFlattener`, `HpglEncoder`, `Protocol`.
  The critical test asserts *a known SVG → the exact pltFile/HPGL byte stream*, **byte-for-byte against the
  Phase 0 golden capture** — i.e. we prove we reproduce what Superb Cut sends. Chunking and the handshake
  sequence are verified through `FakePlotterTransport`.
- **Regression lock:** any change to the encoder must keep emitting the golden bytes → "the plotter keeps
  working" is enforced by tests on every build.
- **Manual verification:** real cuts of a few reference shapes on the VEVO Smart 1 (the device is the final
  oracle), against a documented checklist.

## 7. Build & toolchain

Reuse `cricut-export`'s local, git-ignored toolchain: `tools/android-sdk` (platform-34, build-tools 34,
platform-tools), `tools/gradle-8.7`, JDK 17 (`/opt/homebrew/opt/openjdk@17`). Package `de.knutwurst.knutcut`.
Debug-signed release APK (installable directly on the user's and his wife's phones; no Play Store).
Commands: `:app:assembleRelease`, `:app:testDebugUnitTest`, `:svgcore:test`.

## 8. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| ~~BLE-GATT framing unknown~~ | ✅ Resolved by the spike: classic SPP, recovered from code — no capture needed |
| Coordinate / scale correctness | byte-for-byte golden test, then a real cut |
| SVG feature coverage | first target the flattened-mm SVG subset Cricut Export emits; widen later |
| Device variants (Smart1–4) | device picker + per-model parameters/presets; Smart 1 verified first, others enabled as confirmed |
| Re-sign / DCloud license (modify path) | avoided — we build our own app |

## 9. Implementation phases (overview)

0. **Protocol spike** — ✅ **done**: SPP transport + command sequence documented in `protocol-findings.md`.
1. **`:svgcore`** — SVG → mm → HPGL → Protocol, TDD against the golden fixture.
2. **`:transport`** — SPP/RFCOMM transport + `FakePlotterTransport`; connect to the real plotter.
3. **`:app`** — share-intent receiver, mat canvas (place/scale), material picker, cut flow (German UI).
4. **Hardening** — real-cut verification checklist, presets, calibration, packaging.

(The detailed plan is produced by the writing-plans step after this spec is approved.)

## 10. Defaults / open questions

- Target devices: **the Smart1–4 family with in-app selection**; **VEVO Smart 1 verified first** (others
  follow once their parameters are confirmed). _(confirmed)_
- Mat editor: **place, scale and rotate.** _(confirmed)_
- Distribution: **debug-signed APK**, on the user's + wife's phones, no Play Store. _(default)_
- Reusable core: **in-repo `:svgcore` Gradle module**, wired into `cricut-export` later. _(default)_
- UI language: **German only.** _(default)_
