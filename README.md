# Knutcut

Android app for cutting my own SVGs on a VEVOR Smart plotter (and Silhouette cutters over Bluetooth LE).

Share an SVG to Knutcut (for example from Cricut Export), place and scale it on the mat, send it to
the plotter over Bluetooth. German and English UI, no account, no login.

The app that ships with the plotter ("Superb Cut") has a broken German translation, forces a login,
and can't take a shared SVG. Knutcut does just the part I need, in code I control.

## Features

- Mat editor: place, scale, rotate, mirror, duplicate; snapping and alignment guides; undo/redo.
- Layers: rename, reorder, group by colour, merge/split, tile into a grid, auto-arrange to save material.
- Text tool with outline and single-stroke (Hershey) fonts.
- Open or share SVG/PLT; save and reload a project.
- Cut over classic Bluetooth (VEVOR Smart, JSON + HPGL) or Bluetooth LE (Silhouette, GPGL); material
  presets, pressure, drag-knife compensation, optional cut-order optimisation, multiple passes.
- Self-update from a GitHub releases repo with SHA-256 verification.

See [CHANGELOG.md](CHANGELOG.md) for the per-release history.

## Build

Uses the same local toolchain as cricut-export (Android SDK and Gradle under `tools/`, JDK 17).

    cd android
    ./gradlew :app:assembleRelease     # installable APK
    ./gradlew test                     # unit tests

## Layout

- `svgcore` — pure Kotlin, no Android. Turns an SVG into mm geometry and then into the plotter
  command stream (HPGL for VEVOR, GPGL for Silhouette). No Android dependency, so cricut-export can
  reuse it. Unit-tested on the JVM.
- `app` — Compose UI (share target, mat editor, material picker, cut) plus the Bluetooth transports:
  classic Serial (SPP) for VEVOR and Bluetooth LE (GATT) for Silhouette.

## Notes

Personal use with my own hardware. Contains only my own code. The plotter protocol is
reimplemented from watching my own device; no third-party app code or assets are included.
See `docs/` for the design and the protocol notes.
