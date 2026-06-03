# VEVO Smart 1 protocol

My own notes for talking to a plotter I own. No third-party code is copied here; this is a
description of how the device speaks, worked out from the stock app's readable JS and its decompiled
Bluetooth plugin. Raw artifacts stay out of git under `research/`.

## Transport: classic Bluetooth Serial (SPP)

Not BLE. The cutter is a classic Bluetooth Serial device.

- Connect: `createRfcommSocketToServiceRecord(00001101-0000-1000-8000-00805F9B34FB)`, then `connect()`.
- Discovery: Android `startDiscovery()`; the cutter shows up by name (Smart1...). Pair with `createBond()`.
- Send: each message is a small JSON object, framed as `JSON + CRC + CRLF` (see Frame format), written
  to the socket output stream as text.
- Receive: a background reader splits the input stream on CRLF and hands back each line. Every message
  is answered by one CRLF-terminated JSON line, so it is a send-and-wait loop.
- MTU: classic Bluetooth negotiates this itself; the stock app's setMTU is effectively a no-op.

## Frame format

Every message is a JSON object with a `cseq` counter that starts at 0 and increments per send. On the
wire:

    JSON(message) + CRC + "\r\n"

- JSON is compact, keys in insertion order, `cseq` appended last, e.g.
  `{"type":"pltCommand","data":"TB66;","cseq":0}`.
- CRC is CRC-16/X25 over the JSON bytes (init 0xFFFF, reflected poly 0x8408, final xor 0xFFFF), uppercase
  hex. Quirk: a three-digit result is zero-padded to four; one/two/four-digit results are left as is. So
  the example frames as `{"type":"pltCommand","data":"TB66;","cseq":0}C05B\r\n`.
- Responses are CRLF-terminated JSON lines. A response of type `crc` means the checksum was rejected and
  the message is resent; sends retry up to five times.

## Commands

The command text (HPGL etc.) lives in the `data` field; `type` and `action` select the message.

Status queries (reply `{data:{state}}`):
- `queryMaterial` — feed state: **3 = loaded/fed in**, **1 = only at the sensor** (prompt "press the feed
  button"), 0 = none. Poll until 3 before cutting.
- `queryStartKey` — `true` once the physical Start button is pressed.
- `queryPulled` — the sensor sees material (true while it merely touches the sensor; NOT the same as fed in).

Commands:
- `{type:"pltCommand",data:"TB66;"}` — init
- `{type:"pltCommand",data:"setmat:<materialId>;"}` — select material
- `{type:"pltCommand",data:"SP<tool>;FS<force>;"}` — `SP` = Select Pen (tool: 1 = right/knife, 2 = left/pen),
  `FS` = force. No speed is sent; the machine handles it.
- `{type:"pltCommand",data:"JS<a>,<b>;"}` — setScale
- `{type:"pltFile",index,total,data}` — HPGL `PU<x>,<y>` (pen up, travel) / `PD<x>,<y>` (pen down, cut),
  joined by `;`, sent in chunks (later chunks prefixed with `;`), wrapped with `TB66;`
- `{type:"handshake"}` / `{type:"bye"}` — open / close the session

Cut workflow: handshake → `setmat` → `SP`/`FS` → poll `queryMaterial` until **3** (while 1, ask to press
the feed button) → if the model has a start key, poll `queryStartKey` until pressed → stream the `pltFile`
(the device cuts as it arrives) → `bye`.

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
