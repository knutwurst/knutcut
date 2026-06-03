# VEVO Smart 1 protocol

My own notes for talking to a plotter I own. No third-party code is copied here; this is a
description of how the device speaks, worked out from the stock app's readable JS and its decompiled
Bluetooth plugin. Raw artifacts stay out of git under `research/`.

## Transport: classic Bluetooth Serial (SPP)

Not BLE. The cutter is a classic Bluetooth Serial device.

- Connect: `createRfcommSocketToServiceRecord(00001101-0000-1000-8000-00805F9B34FB)`, then `connect()`.
- Discovery: Android `startDiscovery()`; the cutter shows up by name (Smart1...). Pair with `createBond()`.
- Send: each command is a string written straight to the socket output stream. No length prefix and no
  binary framing. There is also a hex mode that writes hex-decoded bytes.
- Receive: a background reader splits the input stream on CRLF and hands back each line. Every command
  is answered by one line, so it is a send-and-wait loop.
- MTU: classic Bluetooth negotiates this itself; the stock app's setMTU is effectively a no-op.

## Commands

A cut is a sequence of strings, each answered by a CRLF line:

1. `handshake`
2. `queryMaterial`, `queryStartKey`, `queryPulled` (is media loaded)
3. `TB66;` (init)
4. `setmat:<materialId>;`
5. `SP<speed>;FS<force>;` (speed and force, from the material preset)
6. `JS<a>,<b>;` (offset or scale, exact meaning still to confirm)
7. path data: HPGL `PU<x>,<y>;` (pen up, travel) and `PD<x>,<y>;` (pen down, cut), sent as a chunked
   file wrapped in `TB66;`
8. `bye`

## Coordinates

HPGL pen up / pen down, absolute coordinates. Standard HPGL is 1/40 mm per unit (40 units/mm), to be
confirmed against a real cut. Reference: a 40x40 mm square at origin, top left. At 40 units/mm that is
a closed square from 0 to 1600 per side.

## To confirm while building

- Coordinate unit and Y direction (up vs down).
- Meaning of `JS<a>,<b>;`.
- Material id table and per-material speed/force values.
- Exact handshake and query strings, request and response.

## Validation

I don't need the stock app's captured bytes. Knutcut generates the command stream, and a real cut of
the 40 mm square is the ground truth. That generated stream becomes the golden fixture for the
`svgcore` tests.
