# Knutcut

Android app for cutting my own SVGs on a VEVO Smart 1 plotter.

Share an SVG to Knutcut (for example from Cricut Export), place and scale it on the mat, send it to
the plotter over Bluetooth. German UI, no account, no login.

The app that ships with the plotter ("Superb Cut") has a broken German translation, forces a login,
and can't take a shared SVG. Knutcut does just the part I need, in code I control.

## Build

Uses the same local toolchain as cricut-export (Android SDK and Gradle under `tools/`, JDK 17).

    cd android
    ./gradlew :app:assembleRelease     # installable APK
    ./gradlew test                     # unit tests

## Layout

- `svgcore` — pure Kotlin, no Android. Turns an SVG into mm geometry and then into the plotter
  command stream. No Android dependency, so cricut-export can reuse it. Unit-tested on the JVM.
- `transport` — classic Bluetooth Serial (SPP) link to the plotter.
- `app` — Compose UI: share target, mat editor (place, scale, rotate), material picker, cut.

## Notes

Personal use with my own hardware. Contains only my own code. The plotter protocol is
reimplemented from watching my own device; no third-party app code or assets are included.
See `docs/` for the design and the protocol notes.
