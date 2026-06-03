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
- Place / scale (and likely rotate) on a mat that represents the VEVO Smart 1 work area; show real-world mm size.
- Material presets (cut force / speed per material), ported from the stock app's values.
- Bluetooth-LE connect to the plotter and send the cut; surface progress and the "media loaded?" check.

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
- **Plotter protocol is recoverable from that JS.** It uses a framed JSON message protocol over BLE:
  - `handshake` / `bye` — connect / disconnect
  - `pltCommand { data }` — control: `TB66;` (setup), `JS…`, `SP…`, `setmat:…` (set material)
  - `pltFile { index, total, data }` — the cut, **sent chunked**; payload is **HPGL-like** (`PU`/`PD` pen up/down)
  - `query { action }` — `queryMaterial`, `queryPulled` (media loaded?), `queryStartKey`
  - device registry: `name_lang:"Smart1"`, `knife_count:2`, models Smart1–4; scan matches an advertised `localName` prefix
- **Not in the JS:** the lowest BLE-GATT layer (service/characteristic UUIDs, byte serialization of the
  messages, MTU/chunk size, write-with-response vs. notify). That lives in a native UTS module / `.so` and
  is the one piece the Phase 0 spike must capture.

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
- **`:ble`** (or a package inside `:app`) — Android transport:
  - `PlotterTransport` interface — `connect(device)`, `sendWaitRet(message): Response`, notifications, `disconnect`.
  - `BlePlotterTransport` — scan by `localName` prefix, GATT connect, MTU-aware chunked writes, notify/ack.
  - `FakePlotterTransport` — records frames and scripts responses, for tests and offline development.
- **`:app`** — Jetpack Compose UI:
  - Share / `VIEW` intent receiver → load SVG.
  - **Mat canvas**: render the SVG on the VEVO Smart 1 work area; drag to place, pinch/handles to scale
    (and rotate); live mm dimensions.
  - Material picker (presets), connect/scan device, `queryPulled` "media loaded?" gate, **Cut** with progress.
  - Settings: device, calibration offset, units. **German-only UI.**

## 4. Data flow

```
shared SVG
  → SvgParser            (→ mm geometry)
  → user place/scale     (mat transform)
  → PathFlattener        (→ absolute mm polylines)
  → HpglEncoder          (→ PU/PD command stream)
  → Protocol             (handshake → setmat → chunked pltFile → bye)
  → BlePlotterTransport  (BLE GATT writes, ack-gated)
  → VEVO Smart 1
```

Each step is gated on the device acknowledgement (`sendWaitRet`), matching the stock app's flow control.

## 5. Plotter protocol — known vs. spike

**Known (from JS):** the JSON message layer and the HPGL path payload described in §2.

**Unknown → Phase 0 spike output:** GATT service/characteristic UUIDs; how a message serializes to bytes;
MTU / chunk size; write-with-response vs. notify; exact handshake bytes; the material preset values.

**Phase 0 (de-risk before building the app):**
1. `jadx`-decompile the native UTS/BLE module (secondary dex + `lib39285EFA.so`) for UUIDs + serialization.
2. Capture a real cut: enable the Bluetooth HCI snoop log, perform one small cut in Superb Cut, `adb pull`
   `btsnoop_hci.log`, analyze in Wireshark.
3. Deliverable: a **written protocol spec** + a **golden byte capture** for a known shape — used as the
   test oracle in §6.

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
| BLE-GATT framing unknown | Phase 0 spike (decompile + HCI capture) + golden capture before building the app |
| Coordinate / scale correctness | byte-for-byte golden test, then a real cut |
| SVG feature coverage | first target the flattened-mm SVG subset Cricut Export emits; widen later |
| Device variants (Smart1–4) | parameterize device model + presets; only Smart 1 targeted now |
| Re-sign / DCloud license (modify path) | avoided — we build our own app |

## 9. Implementation phases (overview)

0. **Protocol spike** — capture & document the BLE/HPGL command stream; produce the golden fixture.
1. **`:svgcore`** — SVG → mm → HPGL → Protocol, TDD against the golden fixture.
2. **`:ble`** — BLE transport + `FakePlotterTransport`; connect to the real plotter.
3. **`:app`** — share-intent receiver, mat canvas (place/scale), material picker, cut flow (German UI).
4. **Hardening** — real-cut verification checklist, presets, calibration, packaging.

(The detailed plan is produced by the writing-plans step after this spec is approved.)

## 10. Defaults / open questions

- Target device: **VEVO Smart 1 only** (architecture parameterized for Smart2–4 later). _(default)_
- Distribution: **debug-signed APK**, on the user's + wife's phones, no Play Store. _(default)_
- Reusable core: **in-repo `:svgcore` Gradle module**, wired into `cricut-export` later. _(default)_
- UI language: **German only.** _(default)_
- Rotate support in the mat editor: assumed **yes** (cheap, useful) — confirm.
