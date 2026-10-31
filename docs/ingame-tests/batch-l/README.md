# Batch L — in-game validation pack

GPS accuracy: the fixed request/reply channel and the dedupe.

## What changed

| Before | After |
|---|---|
| `locate` opened the **computer's own id** as its channel and pinged with `reply = computerId` | opens **`CHANNEL_GPS` (65534)** and pings with `reply = CHANNEL_GPS` |
| replies matched on `sChannel == computerId` | matched on `sChannel == CHANNEL_GPS and sReplyChannel == CHANNEL_GPS` |
| every reply became a new fix, so one chatty host could fill all three slots | a fix within **1 block** of one already held replaces it, so one host can only ever hold one slot |
| replies with no `nDistance`, or non-numeric coordinates, went on to crash trilateration | rejected before use |
| the host answered pings with no `nDistance` | ignored, because the client cannot measure from them |

The dedupe is the safety property: three replies from a single host leave **one** slot filled, so
trilateration never runs and a lone host cannot fake a position.

## Run

```bat
copy docs\ingame-tests\batch-l\*.lua "run\client\saves\<World>\computer\<id>\"
copy docs\ingame-tests\readlog_manual.lua "run\client\saves\<World>\computer\<id>\"
```

Then run `l1_gps` on any computer. It performs **no blocking calls**, so it is safe everywhere:

| Check | Detail |
|---|---|
| constants | `gps.CHANNEL_GPS == 65534`, and it is not the computer id |
| no modem | `gps.locate` returns nothing immediately when no wireless modem is attached |
| geometry | the ranges the ROM's maths will see for a known setup (22.36 / 92.2 / 80.62), and that a host at our own position reports 0 |

Everything that waits for a real modem reply is in the script's manual list: a headless run has no
timers, so `gps.locate(timeout)` would hang there.

## The manual checks, in order of what they prove

1. **One host proves the channel.** `gps host <x> <y> <z>` in one tab, `gps locate` in another. A
   reply at distance ~0 is returned immediately — and it can only arrive if the ping and the reply
   both use 65534.
2. **No host.** `gps locate` returns `nil` after about two seconds and leaves no channel open:
   `peripheral.call('<side>', 'isOpen', 65534)` must be `false`.
3. **Dedupe, positive.** Three hosts at non-collinear positions → exact position.
4. **Dedupe, negative.** Shut two down → `nil`. This is the regression this batch fixes.
5. **Dedupe, coincident hosts.** Two hosts within one block plus a third → `nil`, because the two
   count as one.

## Covered by JVM tests instead

`GpsLocateTest` (15 tests) loads the **real** `rom/apis/gps` chunk with a stubbed `os`/`peripheral`
and a scripted event queue, so it can assert what a live test cannot arrange:

* the ping goes out on 65534 with reply channel 65534, the channel is opened and then closed, and a
  channel that was already open is **not** closed;
* three distinct hosts give the exact position; a zero-distance reply is the answer immediately;
* **three replies from one host give `nil`**, two hosts plus a repeat give `nil`;
* replies on the wrong channel, from another modem, without a distance, or with malformed payloads
  are all ignored;
* the timeout ends the search and still closes the channel;
* the chunk transmits exactly once (a guard against a stray second ping).

One geometry note worth knowing: with **three** ranges the solution is a pair of points mirrored in
the hosts' plane, so a listener out of that plane legitimately gets "ambiguous position" from
`gps.locate`. The tests use coplanar geometry, where the two solutions coincide.
