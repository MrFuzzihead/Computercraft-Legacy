# Codebase Analysis Report

> **Scope:** Full review of `src/main/java` (368 Java files), `src/main/resources` (ROM Lua), and the build/test setup of this decompiled-and-patched ComputerCraft 1.7.10 fork (Cobalt runtime, CustomNPCs compat, ChatBox/Speaker/NPC peripherals, Redstone Relay, WebSocket/TTS additions).
> **Method:** Static review of core runtime (`core/`), shared Minecraft-coupled code (`shared/`), client code, compat layer, ROM `bios.lua`, plus selected comparison against the original dan200/ComputerCraft 1.7.10 source (commit `bbe7a4c`) to distinguish inherited legacy issues from fork regressions.
> Severity legend: 🔴 High (crash / exploitable), 🟠 Medium (functional bug / real DoS risk), 🟡 Low (hardening / quality), 🔵 Info.

---

## Executive summary

The codebase is in good shape overall. The **custom additions** (Speaker + DFPWM/PCM audio, ChatBox, NPC Detector/Interface/Trader, Redstone Relay, WebSocket API, seekable `fs` handles, `os.date`, palette support) are consistently well-written: main-thread execution via `executeMainThreadTask`, snapshot-based event dispatch, careful argument validation, and a solid 49-file unit-test suite. Most severe problems are **inherited from upstream CC 1.7.10** and remain unfixed — notable because this fork's stated purpose is bug-fixing. The most impactful items:

1. 🔴 ~~Unvalidated packet lengths in `ComputerCraftPacket.fromBytes` → a modified client can OOM-crash a server with one tiny packet.~~ **FIXED** (see S1 — validation + caps + tests added)
2. 🔴 ~~**Unbounded HTTP**: no request cap, no download-size cap, default whitelist `*` → any player can exhaust server memory/threads.~~ **FIXED** (see S2 — `http_max_requests`/`http_max_websockets`/`http_max_download`/`http_blacklist` + tests)
3. 🔴 ~~**`NBTUtil.toNBTTag` encodes the map *key* as the *value*** → table-valued event arguments sent client→server are silently corrupted; plus unbounded `new Object[len]` from client NBT.~~ **FIXED** (see S3 — key/value fix, `len` derives from written entries, `byte[]` support, hostile-length clamps + tests)
4. 🟠 **Redstone Relay mutates the world from the computer thread** (off-main-thread neighbor notifications).
5. 🟠 ~~**`buffer` API is dead code** (never registered) and contains two genuine bugs.~~ **RESOLVED** (see B1 — deleted as not-a-CCTweaks-feature; `TextBuffer.fill` empty-pattern div-by-zero fixed + tested)
6. 🟠 `ComputerThread` concurrency hazards (unsynchronized `WeakHashMap`, silently dropped tasks) and a globally serialized, thread-per-task execution model.

---

## 1. Security & crash findings

### S1 🔴 Unvalidated lengths in `ComputerCraftPacket.fromBytes` → server OOM — ✅ **FIXED**
`shared/network/ComputerCraftPacket.java` lines 111–157 (original):

```java
int len = buffer.readInt();
byte[] b = new byte[len];          // line 116 — no bound vs buffer.readableBytes()
...
this.m_dataByte = new byte[nByte][];   // line 141
int length = buffer.readInt();
this.m_dataByte[kx] = new byte[length];
...
byte[] bytes = new byte[byteLength];   // line 157
```

Every length field is attacker-controlled and allocated **before** any readability check. A malicious client can send a ~20-byte packet declaring `len = 0x7FFFFFFF`, causing a multi-GB allocation → `OutOfMemoryError`. Because `PacketHandler` catches `Exception` (not `Throwable`), the `Error` escapes and can take down the netty thread / server. This is a known legacy-CC 1.7.10 attack vector; it is inherited from upstream but is the single most important fix for any public server.

**Fixed (2026-06):** every declared length/count is now validated via a `checkLength()` helper before any allocation — it must be non-negative, within an absolute cap, and no larger than `buffer.readableBytes()` — otherwise a `DecoderException` (a `RuntimeException`, so it is caught by `PacketHandler`) is thrown without allocating. Caps: 64 strings / 64 ints / 64 byte arrays (array length ≤ 1 MiB each), strings ≤ 1 MiB, NBT ≤ 4 MiB. These are far above any legitimate payload (speaker audio ≤ 128 KiB, terminal NBT ≤ ~50 KiB). The count reads were also switched from signed `readByte()` to `readUnsignedByte()` so counts can no longer go negative. Covered by `src/test/java/dan200/computercraft/shared/network/ComputerCraftPacketTest.java` (round-trips for empty/full/speaker-audio/terminal-NBT packets plus rejection tests for every malicious-length variant).

### S2 🔴 HTTP API is unbounded (memory + thread DoS) — ✅ **FIXED**
* `core/apis/HTTPAPI.java:210` — `m_httpRequests` grows without limit; there is no `http_max_requests` equivalent. Every `http.request` spawns a **new thread** (`HTTPRequest` constructor), so a Lua program can spawn thousands of concurrent threads.
* `core/apis/HTTPRequest.java:198` — the download loop reads the response into memory with **no size cap** (`http_max_download` equivalent missing). A malicious/Lua-initiated download of a huge file (or a server that never closes the stream) OOMs the server; with `timeout = 0` (the default when no timeout arg is given) there is not even a read timeout.
* `ComputerCraft.java:106` — `http_whitelist = "*"` by default: every computer can talk to the entire internet. There is also **no blacklist** (`http_blacklist` equivalent) — not possible to deny specific hosts while allowing the rest.

**Fixed (2026-06):** four new config options (all `general` section, in `ComputerCraft.java`):

| Option | Default | Meaning |
|---|---|---|
| `http_max_requests` | 16 | Max in-flight HTTP requests per computer; `0` = unlimited |
| `http_max_websockets` | 4 | Max open/pending websockets per computer; `0` = unlimited |
| `http_max_download` | 16 MiB | Max response body; oversized bodies are aborted (early via `Content-Length`, and during streaming) and surface as `http_failure(url, "Download limit exceeded", nil)` |
| `http_blacklist` | (empty) | Semicolon-separated wildcard domains blocked even if whitelisted; applied to both `http` and websocket URLs |

Caps are enforced under the tracking list's lock in `HTTPAPI.callMethod`, before the worker thread is started; an over-cap `http.get/post` returns `nil, "Too many ongoing HTTP requests"` synchronously (via `bios.lua`), and `http.request` queues an `http_failure` event consistently with the pre-existing URL-rejection path (no double events — the locked re-check cancels before adding to the list). The whitelist/blacklist matching is a shared `matchesDomain(host, list)` helper with identical regex semantics to the pre-existing whitelist check. This closes the thread/memory DoS vectors (item 3 in the priority list); the optional shared `ExecutorService` and non-zero default timeout remain as a follow-up (P2). Covered by `src/test/java/dan200/computercraft/core/apis/HTTPLimitsTest.java` (12 tests: blacklist rules for http + websockets, request cap incl. `0`-means-unlimited, and download-limit abort/success against a local in-process `HttpServer`).

### S3 🔴 `NBTUtil` event encoding bugs — ✅ **FIXED**
`shared/util/NBTUtil.java`:

* **Line 47 (was):** `NBTBase value = toNBTTag(entry.getKey());` — the *key* was encoded as the *value*. Any table passed through `ClientComputer.queueEvent` → packet → `ServerComputer.handlePacket` (e.g. `os.queueEvent("x", {{a=1}})` triggered from a client GUI path) arrived with all values replaced by their keys. Verified identical in the original dan200 source — an upstream bug.
* **Line 215 (was, `decodeObjects`):** `Object[] objects = new Object[len];` where `len` is read from client-supplied NBT → a modified client could trigger `new Object[Integer.MAX_VALUE]` → OOM. Same class of bug as S1.
* **Line 55 (was):** `nbt.setInteger("len", m.size())` counted entries that may have been skipped (unencodable key/value), desynchronizing encode/decode.
* `byte[]` (Lua binary strings) were not encodable and were silently dropped from client→server event arguments.

**Fixed (2026-06):** `toNBTTag` now encodes `entry.getValue()` (the key/value bug); the map `len` is derived from the entries actually written so encode→decode is lossless even when entries are skipped; `byte[]` values are encoded as `NBTTagByteArray` (and decoded back) in both the event path and inside nested maps; `fromNBTTag` gains byte array decoding; and hostile lengths are clamped — `decodeObjects` rejects `len` outside `1..256` (returning null) and nested maps reject `len` outside `0..4096` (returning null) instead of allocating attacker-sized structures. Covered by `src/test/java/dan200/computercraft/shared/util/NBTUtilTest.java` (16 tests: scalars, map key/value correctness, nested maps, skipped unencodable entries, byte[] in args and in maps, hostile top-level and nested lengths, plus `toObject` tile-entity sanity).

### S4 🟠 Malformed packet → unvalidated array access (server noise) — ✅ **FIXED**
`shared/proxy/ComputerCraftProxyCommon.handlePacket` (cases 1–6: `packet.m_dataInt[0]`) and `ServerComputer.handlePacket` (case 4: `packet.m_dataString[0]`) index packet arrays without length/null checks. A modified client sending a type-4 packet with no strings causes NPE/AIOOBE. `PacketHandler` catches `Exception`, so it's log spam rather than a crash — but it should be validated.

**Fixed (2026-09):** `ComputerCraftPacket` gains two payload-shape predicates — `hasInts(count)` and `hasStrings(count)` (the latter requires non-null elements) — and every indexed access in the packet handlers is now guarded behind them: `ComputerCraftProxyCommon.handlePacket` cases 1–8 (instance lookup), case 9 (`RequestTileEntityUpdate`, 3 ints), `SpeakerAudio` (4 ints) and `SpeakerStop` (3 ints), plus `ServerComputer.handlePacket` case 4, which now also refuses to queue an event whose name string is null (network-decoded packets can carry null string elements). Malformed packets are dropped silently — consistent with the pre-existing `computer != null` drop — so a hostile client produces no exception or log spam. Legitimate flows are preserved (e.g. `SetLabel` still tolerates a null string to clear the label). Covered by `src/test/java/dan200/computercraft/shared/computer/core/ServerComputerPacketGuardTest.java` (5 tests: malformed/valid `QueueEvent` packets against a real `ServerComputer`, payload-less control packets, and the `SetLabel` set/clear flow) and 4 new predicate/decode tests in `ComputerCraftPacketTest`.

### S5 🟠 ChatBox impersonation & CNPC command execution (admin-facing)
* `ChatBoxPeripheral.say/tell` format messages as `[<label>] <text>` where `<label>` is a free-form ≤32-char string — a computer can convincingly impersonate `[Server]` or another player, and with the default `chatbox_max_range = -1` broadcast to every player in every dimension. No permission gate exists. Consider a config option to force a fixed prefix or to require the label to differ from player names.
* `NpcInterfacePeripheral.executeCommand` / `TraderRolePeripheral` rely on CustomNPCs' `npc.executeCommand`, which executes with NPC (op-level) permissions. Any player who can place an `npc_interface` can run op-level commands through a computer. This mirrors what CustomNPCs itself allows, but it deserves a loud note in `docs/CUSTOMNPCS_INTEGRATION.md` and possibly an opt-in config.
* `SpeakerPeripheral.playSound` accepts arbitrary sound names (≤512 chars) — harmless in vanilla but a nuisance vector; volume is properly capped at 3.

---

## 2. Functional bugs

### B1 🟠 `buffer` API is dead code **and** broken — ✅ **FIXED (deleted)**
* `core/apis/BufferAPI.java` is never registered — no `new BufferAPI(...)` exists outside the class itself, and `Computer.createAPIs()` doesn't add it. The `buffer` row in `docs/TWEAKEDCC_COVERAGE.md` therefore overstates coverage: the API is not reachable from Lua.
* If it were registered:
  * **Line 88 (`read`):** `startxx = ((Number) arguments[1]).intValue() - 1;` — the guard validates `arguments[0]` but reads `arguments[1]`. `buf:read(5)` throws AIOOBE (`Java Exception Thrown`); `buf:read(1, 5)` silently uses the *end* index as the start.
  * **Line 148 (`fill`) + `TextBuffer.fill(String,...)`:** `fill("")` on a non-empty buffer reaches `text.charAt((i - pos) % textLength)` with `textLength == 0` → `ArithmeticException: / by zero`.

**Resolved (2026-09): deleted.** Investigation showed the `buffer` API is *not* a CCTweaks feature — it appears nowhere in the vendored `migrate/CCTweaksSourceCode` tree, CCTweaks' README only advertises the `socket` and `data` APIs, and CC:Tweaked has no `buffer` global either. It was an unfinished hand-written idea from the migration that was never wired up, so `BufferAPI.java` was deleted and the bogus `buffer` row removed from `docs/TWEAKEDCC_COVERAGE.md` (no Lua-visible change: the API was unreachable). The `fill("")` div-by-zero lives in `TextBuffer` itself, which *is* live code (used by `Terminal` for colour rows), so both `fill(String,...)` and `fill(TextBuffer,...)` overloads now treat an empty pattern as a no-op instead of dividing by zero. Today's `Terminal` callers can't trigger it (they always fill single-char strings with `end == start + 1`), but it is now impossible to hit from any future caller. Covered by the new `src/test/java/dan200/computercraft/core/terminal/TextBufferTest.java` (9 tests: empty-pattern fills via both overloads and the one-arg overload, tiling/bounds/clamping of `fill`, `write`/`read` basics, and the repeating constructor).

### B2 🟠 `OSAPI.Alarm.compareTo` compares `this` with `this`
`core/apis/OSAPI.java:549`: `double ot = this.m_day * 24.0 + this.m_time;` — should be `o.m_day * 24.0 + o.m_time`. Currently, always returns 0. Latent only (alarms live in a `HashMap` and are never sorted), but the class implements `Comparable` for no reason — fix or delete `compareTo`.

### B3 🟡 `TurtleBrain.setOverlay` compares the wrong field
`shared/turtle/core/TurtleBrain.java:613`: `!Objects.equal(this.m_hatOverlay, overlay)` — second comparison should use `hatOverlay`, not `overlay`. Effect is only a spurious `updateBlock()` (state itself is written correctly), but it's a real copy-paste bug.

### B4 🟡 `fs.open` error contract is inconsistent
`core/apis/FSAPI.java` case 11: modes `r`/`w`/`a`/`rb`/`wb`/`ab` return bare `nil` on `FileSystemException`, while `r+`/`w+` return `nil, message`. CC:T returns `nil, "message"` for all modes. Programs that surface the second return value get no diagnostic for the common modes.

### B5 🟡 `http.get`/`http.post`/`http.websocket` in `bios.lua` can steal each other's events
`bios.lua` `wrapRequest` matches events by URL only (`param1 == _url`). Two concurrent requests to the *same* URL (or two websockets to the same URL) will cross-deliver success/failure events. CC:T fixed this with request IDs. Also, `wrapRequest` loops on **unfiltered** `os.pullEvent()` and discards every non-matching event — consistent with the legacy machine's filter semantics (see C5), but worth documenting for program authors; timer/redstone events are lost while blocked in `http.get`.

### B6 🟡 `FileMount` can NPE on unreadable directories
`core/filesystem/FileMount.java` `list()` and `deleteRecursively()` iterate `file.list()`, which returns `null` on I/O error → NPE (uncaught, kills the computer's coroutine with a Java exception). Add a null guard → `IOException("Access denied")`.

### B7 🟡 `JarMount.m_root` can remain null → NPE on first use
`core/filesystem/JarMount.java` — if the zip contains the sub-path but never yields an exact root entry, `m_root` stays null and `exists()` NPEs. Guard with an `IOException("Zip does not contain path")`.

### B8 🟡 `FileSystem.getFreeSpace` is the only unsynchronized public FS method
`core/filesystem/FileSystem.java:822` — every other public method is `synchronized`; this one isn't. Benign in practice (reads a long), but inconsistent.

### B9 🟡 `TermAPI` colour getters read without the terminal lock
`core/apis/TermAPI.java` cases 14–17 (`getTextColour`/`getBackgroundColour`) read terminal state without `synchronized(m_terminal)` while every writer locks it. Benign today (single computer thread + client sync thread) but a latent race.

### B10 🟡 `term.blit` doesn't validate color characters
`core/apis/TermAPI.java` case 18 checks only that the three strings are equal-length. `term.blit("x", "g", "0")` writes an invalid color char into the buffers. `FixedWidthFontRenderer` clamps invalid indices to color 15/0 so there is **no crash**, but the data is garbage and `term.getPaletteColor`/NBT round-trips carry it. CC:T rejects with `Invalid colour`. Cheap hardening: validate `textColour`/`backgroundColour` against `[0-9a-f]` in `TermAPI.blit` (mirroring the renderer's `"0123456789abcdef".indexOf` logic).

### B11 🟡 `HTTPRequest` writes `responseCode`/`responseMessage` outside its lock
`core/apis/HTTPRequest.java` — the worker thread assigns `responseCode`/`responseMessage` without holding `lock` while `asResponse()` reads them under it. Practically safe because `complete` (volatile-ish via the lock) is set last, but the fields should be assigned inside the synchronized block for clarity/consistency (inherited from upstream).

### B12 🔵 Known legacy limitations (document, don't necessarily fix)
* Dropped file handles are only reclaimed on computer unload — a Lua program that discards handles without `close()` leaks them until shutdown (bounded by `maxFilesHandles`).
* `os.pullEvent(filter)` semantics: `CobaltMachine.handleEvent` drops events that don't match the coroutine's filter, matching CC 1.7.10. Modern CC:T queues them in the bios. Any ported CC:T program relying on non-consuming filtered pulls will lose events.
* `WebSocketHandle.receive` (core/apis/WebSocketHandle.java) loops on `pullEventRaw(null)` and discards all events that aren't for its URL — consistent with the above, but the strongest event-loss case; consider documenting or queueing non-matching events.
* `modem.transmit` payload size is unbounded (a Lua program can stuff a multi-MB table through rednet).

---

## 3. Concurrency & threading

### C1 🟠 `ComputerThread.queueTask`: unsynchronized `WeakHashMap` access
`core/computer/ComputerThread.java:136` — `m_computerTasks.get/put` (a `WeakHashMap`) is executed without any lock while being called from **multiple threads** (server thread, HTTP worker threads, WebSocket threads, CNPC event dispatch). `WeakHashMap` is not thread-safe: concurrent access can corrupt internal state or throw. Verified inherited from the original (the upstream code is identical). Wrap the get/put in `synchronized(m_lock)` (or use `ConcurrentHashMap` keyed by computer ID).

### C2 🟠 Silently dropped events when a computer's queue is full
`ComputerThread.java:142` — `queue.offer(_task)` on the 256-cap queue: when full, the task is **silently discarded** (upstream had a commented-out `// Event queue overflow`). A spammy computer (or fast event source) can drop `terminate`/`redstone`/`peripheral` events invisibly. Consider `put()` (blocking is fine — the producer is usually the main thread... verify) or at least log at debug level.

### C3 🟡 `Computer`'s `ITask.execute` bodies synchronize on the *task object*
`core/computer/Computer.java` (`startComputer`/`stopComputer`/`queueEvent` anonymous tasks) use `synchronized(this)` where `this` is the anonymous `ITask` — each task locks a unique object, so this provides **no mutual exclusion** against main-thread state transitions (`advance()`, `unload()` lock the `Computer`). A main thread can call `api.advance()` while the stop-task is mid-`api.shutdown()`. Verified identical in the original dan200 source, so it's an inherited hazard rather than a decompiler artifact; the practical races are narrow (state checks mostly happen under the computer lock before queueing). A correct fix is `synchronized(Computer.this)` inside the task bodies.

### C4 🟡 `ComputerThread.stop()` doesn't stop an idle dispatch thread
When the dispatcher is waiting on `m_monitor`, `interrupt()` wakes it, the `InterruptedException` is swallowed, and it re-enters `wait()` — `m_stopped` is never re-checked in the idle path. Harmless today (one thread, restarted flag semantics), but the stop contract is broken. Also `m_busy` (line 19) is written twice and never read — dead field; remove.

### C5 🔵 Event filter semantics (see B12) — the machine drops non-matching events; this is the *legacy* contract and is implemented faithfully by `CobaltMachine.handleEvent`.

### C6 🔵 Client packets mutate the terminal on the netty thread
`PacketHandler.onClientPacket` → `ClientComputer.handlePacket` → `readDescription`/terminal writes happen on the network thread while the render thread reads the same `Terminal`. Legacy behavior; no corruption observed in practice, but a `synchronized(terminal)` in `ClientTerminal.readDescription` would be cheap insurance.

---

## 4. Performance

### P1 🟠 Global serialization + thread-per-task computer execution
`ComputerThread`'s dispatch loop `take()`s one task, **spawns a new `Thread`**, and `join(timeout)`s it. Consequences:
* All computers on the server execute strictly one-at-a-time (single lane). A single busy computer delays every other computer's event handling by up to `computerThreadTimeout` (7 s) if it wedges.
* Thread creation per task is expensive at high event rates (timer-heavy programs, rednet storms).

CC:T's answer is a fixed worker pool with per-computer queues. Even a modest change (2–4 worker threads with per-computer task lanes, still serialized *per computer*) would materially improve multi-computer servers. This is the fork's biggest scalability lever.

### P2 🟠 Thread-per-HTTP-request
`HTTPRequest` and the (removed) per-request thread model: replace with one shared bounded `ExecutorService` (see S2). Also caps naturally fall out of a bounded pool.

### P3 🟡 Terminal sync broadcasts full state to *all* players
`ServerComputer.broadcastState()` → `ComputerCraft.sendToAllPlayers(packet)` with the **entire terminal NBT** (every line ×3 text buffers + palette) on every change. With many active computers/monitors this dominates bandwidth. Improvements (in order of value): (a) send only to players tracking the chunk, (b) diff-based terminal updates (CC:T-style), (c) skip the 3× per-line strings when only the cursor blinked.

### P4 🟡 CNPC entity lookups are O(all loaded entities) per call
`NpcInterfacePeripheral.resolveByUUID`, `TileNpcInterface.resolveByUUID/resolveNpcs`, and `NpcDetectorPeripheral` (name/UUID paths) iterate `api.getLoadedEntities()` linearly, **across all dimensions**, for every Lua call. On a busy server with thousands of entities, `getName()`-style polling loops (the documented pattern for `npc_trader`) become expensive. Options: cache a `Map<uuid, ICustomNpc>` refreshed per tick, or use `IWorld.getEntitiesNear` with the position (the detector's spatial methods already do this correctly).

### P5 🟡 URL whitelist patterns are recompiled per request
`HTTPRequest.checkURL`/`checkWebSocketURL` call `Pattern.compile` for every whitelist entry on every request. Parse the config once into a `Pattern[]` at startup.

### P6 🟡 Wired network BFS on every transmit
`TileCable.dispatchPacket` runs a full BFS (`searchNetwork`, 256-block radius) for **every modem message**, and `findPeripherals` repeats it on every network change. Legacy behavior; fine for small networks, scales poorly for big cable farms. A per-network receiver index (like `WirelessNetwork`'s channel map) would remove the BFS from the hot path.

### P7 🔵 Minor
* `FixedWidthFontRenderer.getIndex(char)` does a linear scan over a 256-char string per glyph — precompute a `char → index` lookup table at font load.
* `HTTPResponse.readLine` scans with `Arrays.copyOfRange` per line — fine, but a shared `ByteArrayInputStream`-style cursor would avoid copies.
* `FileSystem` contains four near-identical copies of the `readLine`/`seek` handle implementations (read, read-write, read-seekable RAF, read-seekable stream, write-seekable) — extract a shared base class; ~200 lines saved and one place to fix bugs.
* `FileSystem` tracks `m_openFiles` (HashSet) *and* `openFilesCount` (int) — redundant; use the set size.

---

## 5. Code quality & maintainability

* **Q1 🟡** 7 × `printStackTrace()` and 4 × `System.out.println` in production code (e.g. `ComputerThread`, `IDAssigner`, `JarMount`, `PacketHandler`). Route through `ComputerCraft.logger`.
* **Q2 🟡** 51 swallowed/ignored catches. Many are legitimately fine (optional CNPC paths, close-on-unload), but several hide real failures — e.g. `PeripheralAPI` mount failures ignore `FileSystemException` silently (peripheral mounts can fail invisibly), and `WirelessNetwork.get` uses an unsynchronized `WeakHashMap` keyed by `World` (called only from load paths, so low risk).
* **Q3 🟡** `HTTPAPI.wrapBufferedReader` (lines 92–140) is dead code — nothing references it since `HTTPResponse` replaced it. Delete.
* **Q4 🟡** Decompiler artifacts remain in places: `var6`/`var17`-style names (`TextBuffer.write`, `FileMount`), raw `List` types (`TurtleBrain.updateAnimation`), stray `assert` statements (`PeripheralAPI`, `FileSystem.toLocal`). `./gradlew spotlessApply` handles formatting but not naming.
* **Q5 🔵** AGENTS.md conventions are followed well: `m_` prefixes, American-first color method pairs (verified in `TermAPI`/`RedstoneAPI`/`RedstoneRelayPeripheral`), `window` API delegation for every new term method including `nativePaletteColor`, palette NBT as packed `int[16]` under `term_palette`, no `core`→`shared` imports beyond the pre-existing `Terminal` exception (the new `LuaExceptionStub` lives in `core/lua/binfs`, correctly).
* **Q6 🔵** The `LuaExceptionStub` trick (throwing a Lua-flavored exception from `FileSystem.addFile` across the API boundary) works but is fragile — it couples `core.filesystem` to `core.lua.binfs` and relies on `ArgumentDelegator`/Cobalt unwrapping it. A dedicated `FileSystemFullException` translated at the FSAPI boundary would be cleaner.
* **Q7 🔵** `ServerComputerRegistry.update()` still advances (and runs Lua for) computers whose chunk is unloaded for up to 100 ticks before timeout-unload. Since `onChunkUnload` normally removes them, this mostly affects edge cases (teleporting turtles mid-tick), but skipping `computer.update()` when `!isChunkLoaded` would be more precise.
* **Q8 🔵** Test coverage is strong for the custom peripherals (49 test files: chatbox, speaker, npc detector/interface/trader, redstone relay, FS/OS/Term APIs, ROM Lua modules) but the **core concurrency machinery** (`ComputerThread`, `Computer` state machine, `DelayedTasks`, `MainThread`) has no tests — precisely where the nastiest bugs live. `CobaltMachineTest` exists; adding deterministic two-thread tests for `queueTask` (C1) and task-drop behavior (C2) would guard the fixes.

---

## 6. Things that are notably *good* (worth preserving)

* **Main-thread discipline in new peripherals:** `ChatBox`, `NpcDetector`, `NpcInterface`, `TraderRole`, `PocketAPI` all use `executeMainThreadTask` for world/entity/inventory access — the single most common mistake in CC add-ons is absent here.
* **Event dispatch safety:** `ChatBoxManager` / `NpcInterfaceManager` snapshot before iterating; `NpcInterfaceManager` uses `ConcurrentHashMap.compute` for atomic remove-if-empty; `NpcInterfaceBridge` tick-throttling is bounded and self-cleaning.
* **Speaker pipeline:** queue-then-flush-on-main-thread in `TileSpeaker`, DFPWM encoder matches the `cc.audio.dfpwm` spec, back-pressure mirrors CC:T's `CLIENT_BUFFER` model, and stop-cleanup handles the client-drain race explicitly.
* **The keepAlive fix** (`ServerComputerRegistry.update` + `TileComputerBase.updateEntity`) correctly solves the original out-of-tick-range shutdown bug with a clean chunk-loaded predicate.
* **`fs` seekable handles** (`r`/`w`/`a` with `seek`, `r+`/`w+` modes, ROM-fallback seek) is a large, careful feature; the `PushbackInputStream` CRLF handling avoids the classic mark/reset bug.
* **Palette support** end-to-end: TermAPI ↔ window API ↔ NBT (`term_palette`) ↔ renderer (`applyColour` with live palette) is consistent, and `writeToNBT` round-trips correctly on resize.

---

## 7. Prioritized action list

| #  | Item                                                                                                                                                                            | Severity | Effort | Area                             |
|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|--------|----------------------------------|
| 1  | ~~Validate all packet lengths in `ComputerCraftPacket.fromBytes`~~ **DONE** — `checkLength` guard + caps + `ComputerCraftPacketTest` (13 tests) | 🔴 | Small | `shared/network` |
| 2  | ~~Cap `NBTUtil.decodeObjects` length; fix `toNBTTag` key/value bug; encode `byte[]`~~ **DONE** — `NBTUtilTest` (16 tests) | 🔴 | Small | `shared/util/NBTUtil` |
| 3  | ~~Add `http_max_requests` / `http_max_download` / `http_blacklist`; shared HTTP executor; non-zero default timeouts~~ **DONE (limits/blacklist); follow-up: shared executor + default timeout (P2)** | 🔴 | Medium | `core/apis`, `ComputerCraft`     |
| 4  | Move `TileRedstoneRelay.setOutput` propagation to `updateEntity` (dirty flag)                                                                                                   | 🟠       | Small  | `shared/peripheral/redstone`     |
| 5  | Synchronize `ComputerThread.queueTask` map access; stop dropping tasks silently                                                                                                 | 🟠       | Small  | `core/computer`                  |
| 6  | ~~Register-or-delete `BufferAPI` (fix `read` arg bug + `fill("")` div-by-zero); update coverage doc~~ **DONE (deleted)** — not a CCTweaks feature (absent from vendored source + README); `buffer` row removed from coverage doc; `TextBuffer.fill` empty-pattern guard + `TextBufferTest` (9 tests) | 🟠       | Small  | `core/apis`                      |
| 7  | ~~Bounds-check `m_dataInt`/`m_dataString` in `handlePacket` paths~~ **DONE** — `hasInts`/`hasStrings` guards + `ServerComputerPacketGuardTest` (5 tests) | 🟠       | Small  | `shared/proxy`, `ServerComputer` |
| 8  | Worker-pool `ComputerThread` (per-computer lanes)                                                                                                                               | 🟠       | Medium | `core/computer`                  |
| 9  | `fs.open` error messages for all modes; `term.blit` colour validation                                                                                                           | 🟡       | Small  | `core/apis`                      |
| 10 | Fix `Alarm.compareTo`, `TurtleBrain.setOverlay`, `FileMount.list` NPE, `JarMount.m_root` NPE, `getFreeSpace` sync, `TermAPI` getter locking, dead `m_busy`/`wrapBufferedReader` | 🟡       | Small  | various                          |
| 11 | Terminal broadcast: send to tracking players / diff sync                                                                                                                        | 🟡       | Medium | `shared/computer/core`           |
| 12 | Cache CNPC UUID→entity index; precompile HTTP whitelist patterns                                                                                                                | 🟡       | Small  | compat / HTTP                    |
| 13 | Document chatbox spoofing + `executeCommand` privilege implications; consider config gates                                                                                      | 🟡       | Small  | docs / config                    |
| 14 | Logger instead of `printStackTrace`/`System.out`; remove dead code                                                                                                              | 🟡       | Small  | various                          |

---

## Appendix: Inherited-vs-fork attribution (verified against dan200/ComputerCraft @ `bbe7a4c`)

| Finding                                           | In upstream 1.7.10?                                             | Notes                                                                 |
|---------------------------------------------------|-----------------------------------------------------------------|-----------------------------------------------------------------------|
| S1 packet length allocations                      | Yes                                                             | **Fixed here** (validation + caps + tests)                                  |
| S3 `toNBTTag` key/value bug                       | Yes (line 44 of original)                                       | **Fixed here** (key/value, len, byte[], length clamps + tests)               |
| S2 unbounded HTTP                                 | Yes (fork added timeouts/verbs/handles without adding caps)     | **Fixed here** (limits + blacklist + tests; executor follow-up)               |
| C1 unsynchronized `WeakHashMap`                   | Yes                                                             | Unfixed here                                                          |
| C2 silent `offer()` drop                          | Yes (with commented-out overflow log)                           | Unfixed here                                                          |
| C3 `synchronized(this)` in ITask                  | Yes                                                             | Unfixed here                                                          |
| B5 bios URL-matching event confusion              | Yes                                                             | Unfixed here                                                          |
| B7 `JarMount` root NPE                            | Yes                                                             | Unfixed here                                                          |
| B1 BufferAPI bugs                                 | **Fork/CCTweaks-derived** — never wired up                      | **Deleted** (not a CCTweaks feature); `TextBuffer.fill` empty-pattern guard added     |
| B2 `Alarm.compareTo`                              | Yes (decompiled identical)                                      | Latent                                                                |
| B3 `setOverlay` wrong field                       | Yes                                                             | Latent                                                                |
| S4 unvalidated packet array access                | Yes                                                             | Low risk, noisy                                                       |
| P1/P2/P3/P6 perf models                           | Yes (upstream architecture)                                     | Modernization opportunity                                             |
| Redstone Relay off-thread world writes (S-item 4) | **Fork-introduced**                                             | New block; needs fix                                                  |
| CustomNPCs/ChatBox/Speaker/pocket additions       | **Fork-introduced**                                             | Quality is high; see §6                                               |
