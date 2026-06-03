# VEVO Smart 1 — protocol findings

_Our own description, for interoperability with a device we own. No third-party source code is reproduced
here. Reverse-engineered from the stock app's readable uni-app JS and its decompiled native Bluetooth
plugin; raw artifacts stay local under the git-ignored `research/`._

## Transport — classic Bluetooth Serial (SPP / RFCOMM)

Not BLE. The cutter is a classic Bluetooth Serial device.

- **Connect:** `createRfcommSocketToServiceRecord(00001101-0000-1000-8000-00805F9B34FB)` then `connect()`
  (the standard Serial Port Profile UUID).
- **Discovery:** Android `startDiscovery()`; the cutter shows up by name (e.g. `Smart1…`) — match by
  name / prefix. **Pairing:** `createBond()`.
- **Send:** each command is a string written **raw** to the socket `OutputStream`
  (`value.getBytes()`; an `isHex` mode writes hex-decoded bytes). **No length prefix, no binary framing** —
  the command string *is* the payload.
- **Receive:** device responses are **`\r\n`-terminated ASCII lines**. A background reader splits the stream
  on `\r\n` and treats each line as the response/ack to the last command → a classic send-and-wait loop.
- **"MTU":** classic BT auto-negotiates; the stock app's `setMTU` is effectively a no-op (default 250).

## Command layer (the strings on the wire)

A cut is a sequence of commands, each written to the socket and each answered by a CRLF line:

1. `handshake` — establish the session
2. status queries — `queryMaterial`, `queryStartKey`, `queryPulled` (is media loaded?)
3. `TB66;` — init / setup
4. `setmat:<materialId>;` — select material
5. `SP<speed>;FS<force>;` — set **speed** (`SP`) and **force/pressure** (`FS`); values from the material preset
6. `JS<a>,<b>;` — offset/scale (exact meaning to confirm)
7. **path data** — HPGL pen moves `PU<x>,<y>;` (pen up / travel) and `PD<x>,<y>;` (pen down / cut),
   sent as a **chunked "file"** (`index`/`total`) wrapped with `TB66;` … `;TB66;`
8. `bye` — end the session

(The JSON `{type, data, index, total, action}` objects in the JS are just the JS↔native bridge envelope;
only the `data` string reaches the wire.)

## Coordinate model

- HPGL-style pen-up / pen-down with absolute coordinates.
- Standard HPGL plotter unit is **1/40 mm** (40 units/mm = 1016/inch) — **to be confirmed** against a real cut.
- **Reference cut for validation:** a **40 × 40 mm square at origin (0,0), top-left**. If 40 u/mm, that's a
  closed square spanning 0…1600 units per side.

## To confirm during implementation (Phase 1)

- Exact meaning/value of `JS<a>,<b>;`.
- Exact coordinate unit and origin orientation (Y-down vs Y-up) — pin via a real cut of the 40 mm reference
  square produced by **our own** app.
- Material id table + per-material `SP`/`FS` values (present in the stock JS material table — to extract).
- Exact handshake / query request + response strings.

## Validation oracle

We do **not** need the stock app's captured bytes. Our app generates the command stream; the **physical cut
of the 40 mm reference square** is ground truth. The generated stream for that square becomes the **golden
fixture** for `:svgcore` unit tests, locking the encoder against regressions.
