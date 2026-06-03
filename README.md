# Knutcut

A small, focused Android app that **receives an SVG via Android Share**, lets you **place & scale**
it on a virtual mat, and **cuts it on a VEVO Smart 1 plotter over Bluetooth LE** — German-native,
no login, no account.

It's the missing last link for the [`cricut-export`](https://github.com/knutwurst) workflow: export
your own design to a true-to-scale SVG, share it to Knutcut, cut it.

## Why

The VEVO Smart 1 ships with a Chinese cross-platform app ("Superb Cut") whose German translation is
broken, that requires a login, and that can't receive shared SVG files. Knutcut does only what's
needed — import an SVG, position it, send it to the plotter — in clean, testable, owned code.

## Status

🚧 **Design phase.** See the spec in [`docs/superpowers/specs/`](docs/superpowers/specs/).
Implementation starts with a protocol spike (confirm the BLE/HPGL command stream against a real cut).

## Architecture (planned)

- **`svgcore`** — pure-Kotlin, Android-free, reusable: SVG → mm geometry → HPGL/plotter command stream.
  Built so `cricut-export` can depend on it later. Fully host-unit-tested.
- **`ble`** — Android BLE transport to the plotter (scan, connect, chunked writes, ack handshake).
- **`app`** — Jetpack Compose UI: share-intent receiver, mat canvas (place/scale), material picker, cut.

## Legal / scope

For **personal use** with hardware you own. Knutcut contains **only original code**. No third-party
app binaries, decompiled sources, or proprietary assets are included or redistributed — the plotter
protocol is reimplemented for interoperability with a device you own, observed via your own traffic.
