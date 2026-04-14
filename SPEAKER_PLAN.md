# Speaker Peripheral — Implementation Plan

> Created: 2026-04-14
> Tracking issue: TWEAKEDCC_COVERAGE.md § "Speaker peripheral"
> Reference upstream: [SpeakerPeripheral.java (CC:Tweaked db32ddf)](https://github.com/cc-tweaked/CC-Tweaked/blob/db32ddfec5e8c2bdefb3232b471328a3e92cc43f/projects/common/src/main/java/dan200/computercraft/shared/peripheral/speaker/SpeakerPeripheral.java)

---

## Lua API surface

| Method | Signature | Notes |
|---|---|---|
| `playNote` | `playNote(instrument [, volume [, pitch]])` | Plays a noteblock note |
| `playSound` | `playSound(name [, volume [, pitch]])` | Plays any Minecraft sound by ResourceLocation |
| `playAudio` | `playAudio(audio [, volume])` | Plays raw 48 kHz signed-8-bit PCM audio |
| `stop` | `stop()` | Stops any currently-playing audio |

---

## Architecture overview

The speaker is implemented as a **standalone block** (`BlockSpeaker`) with its own `TileSpeaker`
and a `SpeakerPeripheral` that holds the four Lua methods.  It is *not* added to the existing
`BlockPeripheral` metadata system so that no existing peripheral routing code is touched.

```
shared/peripheral/speaker/
  BlockSpeaker.java        – BlockGeneric subclass; 3-face texture set
  TileSpeaker.java         – TileGeneric + IPeripheralTile; owns rate-limit state
  SpeakerPeripheral.java   – IPeripheral; 4 Lua methods

client/audio/
  SpeakerManager.java      – @SideOnly(CLIENT); javax.sound.sampled playback
```

---

## Implementation steps

### Step 1 — `PeripheralType` enum

Add `Speaker` to `shared/peripheral/PeripheralType.java` so `TileSpeaker` can satisfy the
`IPeripheralTile.getPeripheralType()` contract without modifying any existing enum consumers.

```java
// PeripheralType.java
Speaker,   // add after WiredModemWithCable
```

---

### Step 2 — `BlockSpeaker`

**File**: `shared/peripheral/speaker/BlockSpeaker.java`

- Extends `BlockGeneric` (Material.rock, hardness 2.0).
- **Three textures**: `speakerTop`, `speakerFront`, `speakerSide`
  - Top/bottom face → `computercraft:speakerTop`
  - Front face (direction the block is "facing") → `computercraft:speakerFront`
  - All other side faces → `computercraft:speakerSide`
- `createTile(metadata)` → `new TileSpeaker()`
- `getDefaultMetadata(damage, placedSide)` → stores facing direction in metadata (sides 2–5)
  so the front face always faces the placing player.
- Drops one `ItemBlock` of itself on break.

---

### Step 3 — `TileSpeaker`

**File**: `shared/peripheral/speaker/TileSpeaker.java`

- Extends `TileGeneric`, implements `IPeripheralTile`.
- Fields:
  - `private int m_direction = 2` — stored facing direction (NBT-persisted).
  - `private final List<PendingNote> m_pendingNotes` — notes queued this tick by `playNote`.
  - `private PendingSound m_pendingSound` — sound queued this tick by `playSound`.
  - `private SpeakerAudioState m_audioState` — DFPWM buffer state for `playAudio` streaming.
  - `private boolean m_shouldStop` — set by `stop()`, consumed in `updateEntity()`.
- `getPeripheral(int side)` → `new SpeakerPeripheral(this)` (accessible from every side).
- `getPeripheralType()` → `PeripheralType.Speaker`.
- `getLabel()` → `null`.
- `getDirection()` / `setDirection()` delegate to `m_direction`; persisted in NBT.
- `getTexture(int side)` delegates to `BlockSpeaker.getIcon(side, metadata)`.

**`updateEntity()`** — called every server tick on the main thread; dispatches all pending
sound/note/audio work accumulated by the Lua thread since the last tick:

```
1. Handle stop:  if m_shouldStop, clear m_audioState and m_pendingSound; send
   SpeakerStop packet to ALL players; return.
2. Flush pending notes: for each PendingNote in m_pendingNotes, call
   world.playSoundEffect(x+0.5, y+0.5, z+0.5, soundName, volume, pitchF).
   Clear the list.
3. Flush pending sound: if m_pendingSound != null, call
   sendToAllAround(SpeakerPlay packet, volume * 16).  Clear m_pendingSound.
4. Flush audio: if m_audioState.shouldSendPending(System.nanoTime()), send
   SpeakerAudio packet via sendToAllAround (approximated chunk-tracking range),
   call audioState.pullPending(); queue speaker_audio_empty on all computers.
```

This removes the need for `executeMainThreadTask` in the peripheral — the Lua methods only
write to the shared fields and return immediately; all Minecraft I/O happens in this method.

---

### Step 4 — `SpeakerPeripheral`

**File**: `shared/peripheral/speaker/SpeakerPeripheral.java`

Implements `IPeripheral`. All four Lua methods are **non-blocking**: they write to shared
fields on `TileSpeaker` and return immediately; actual Minecraft I/O happens in
`TileSpeaker.updateEntity()` on the main server thread.  No `executeMainThreadTask` is used.

#### Method index table

| Index | Name |
|---|---|
| 0 | `playNote` |
| 1 | `playSound` |
| 2 | `playAudio` |
| 3 | `stop` |

#### `playNote(instrument, volume, pitch)`

1. Validate `instrument` (arg 0) is a string and a member of `INSTRUMENTS` map (throws
   `LuaException("Invalid instrument …")` for unknown names).
2. Validate optional `volume` (arg 1): number in `[0, 3]`, default `1.0`.
3. Validate optional `pitch` (arg 2): number in `[0, 24]`, default `12.0`.
4. Compute `float pitchF = (float) Math.pow(2.0, (pitch - 12.0) / 12.0)`.
5. Synchronized on `m_tile.m_pendingNotes`:
   - If `pendingNotes.size() >= ComputerCraft.speaker_max_notes_per_tick` → return `false`.
   - Add `new PendingNote(soundEvent, volume, pitchF)` to the list.
   - Return `true`.

> **No `executeMainThreadTask`**: the list is flushed to `world.playSoundEffect()` in
> `TileSpeaker.updateEntity()`.  The Lua thread never blocks on a sound call.

**Rate limit**: `ComputerCraft.speaker_max_notes_per_tick` (config key, default `8`) notes
per tick, matching upstream `Config.maxNotesPerTick`.

**`INSTRUMENTS` map** (static `HashMap<String,String>` initialized in a static block):

| CC:Tweaked name | 1.7.10 sound event | Vanilla in 1.7.10? |
|---|---|---|
| `harp` | `note.harp` | ✅ |
| `basedrum` | `note.bd` | ✅ |
| `snare` | `note.snare` | ✅ |
| `hat` | `note.hat` | ✅ |
| `bass` | `note.bass` | ✅ |
| `flute` | `note.flute` | ❌ (silent unless resource pack) |
| `bell` | `note.bell` | ❌ |
| `guitar` | `note.guitar` | ❌ |
| `chime` | `note.chime` | ❌ |
| `xylophone` | `note.xylophone` | ❌ |
| `iron_xylophone` | `note.iron_xylophone` | ❌ |
| `cow_bell` | `note.cow_bell` | ❌ |
| `didgeridoo` | `note.didgeridoo` | ❌ |
| `bit` | `note.bit` | ❌ |
| `banjo` | `note.banjo` | ❌ |
| `pling` | `note.pling` | ❌ |

All 16 CC:Tweaked names are accepted (API-compatible). The 11 post-1.7.10 ones are silently
inaudible in vanilla 1.7.10 but will work if a resource pack provides the sound events.

#### `playSound(name, volume, pitch)`

1. Validate `name` (arg 0) is a non-empty string, length ≤ 512 chars.
2. Validate optional `volume` (arg 1): number in `[0, 3]`, default `1.0`.
3. Validate optional `pitch` (arg 2): number `≥ 0`, default `1.0`.
4. Synchronized on the tile lock:
   - If `m_tile.m_pendingSound != null` or `m_tile.m_audioState != null &&
     m_audioState.isPlaying()` → return `false`.
   - Clear `m_audioState`.
   - Set `m_tile.m_pendingSound = new PendingSound(name, volume, pitch)`.
   - Return `true`.

> Sound is broadcast from `updateEntity()` via
> `world.playSoundEffect(x+0.5, y+0.5, z+0.5, name, volume, pitch)`.
> The effective range is Minecraft's native `max(volume, 1.0) * 16` blocks.

#### `playAudio(audio, volume)`

1. Validate `audio` (arg 0) is a table with `1 ≤ length ≤ 128 * 1024` entries.
2. Validate optional `volume` (arg 1): number in `[0, 3]`, default = previous volume (or `1.0`
   on first call), matching upstream semantics.
3. Delegate to `m_tile.m_audioState.pushBuffer(audio, length, volume)`:
   - This method encodes the PCM samples to DFPWM byte-by-byte (matching `DfpwmState.pushBuffer`
     exactly), stores the result in `pendingAudio`, and returns `true`.
   - If `pendingAudio != null` (previous batch not yet dispatched) → return `false` immediately
     without touching the DFPWM encoder state.
4. Return the boolean result.

> `speaker_audio_empty` is queued only when the pending audio bytes are actually dispatched to
> clients from `updateEntity()`, **not** when `playAudio` is called.  This is the upstream
> back-pressure model: the Lua program loops `while not speaker.playAudio(buffer) do
> os.pullEvent("speaker_audio_empty") end`.

**Encoding note**: upstream re-encodes PCM → DFPWM on the server before sending, which
significantly reduces packet size (8× compression).  The Java encoder in `SpeakerAudioState`
mirrors `DfpwmState.pushBuffer` exactly — same predictor, same strength adaption.

#### `stop()`

1. No arguments.
2. Set `m_tile.m_shouldStop = true` (synchronized).
3. Return `null` immediately.

> `updateEntity()` processes the flag: clears `m_audioState`, clears `m_pendingSound`, sends
> `SpeakerStop` to **all players on the server** via `ComputerCraft.sendToAllPlayers()`, then
> returns.  `speaker_audio_empty` is **not** queued on stop (matching upstream behaviour).

---

### Step 5 — Network layer

#### `ComputerCraftPacket` changes

```java
public static final byte SpeakerAudio = 10;
public static final byte SpeakerStop  = 11;
```

No changes to the serialization format — `m_dataInt` (4 ints for audio, 3 for stop),
`m_dataByte[0]` (DFPWM payload) map directly onto the existing field layout.

#### `ComputerCraft` new helper

```java
public static void sendToAllAround(ComputerCraftPacket packet,
                                    World world, double x, double y, double z,
                                    double range) {
    networkEventChannel.sendToAllAround(
        encode(packet),
        new NetworkRegistry.TargetPoint(world.provider.dimensionId, x, y, z, range));
}
```

#### Packet destinations (matching upstream exactly)

| Packet | Upstream method | 1.7.10 equivalent |
|---|---|---|
| `SpeakerAudio` | `sendToAllTracking(packet, chunk)` — all players with the chunk loaded | `sendToAllAround` with range = `ComputerCraft.speaker_audio_range` (config, default `256` blocks ≈ 16 chunks, covers vanilla max view distance) |
| `SpeakerPlay` (from `playSound`) | `sendToAllAround(packet, level, pos, volume * 16)` | `sendToAllAround` with range = `volume * 16` |
| `SpeakerStop` | `sendToAllPlayers(packet, server)` — **entire server** | `ComputerCraft.sendToAllPlayers(packet)` (already exists) |

> Upstream `sendToAllTracking` has no fixed distance — it is purely chunk-load-based.  In
> 1.7.10 the closest practical equivalent is a large-radius `sendToAllAround`.  256 blocks covers
> the maximum supported view distance (16 chunks × 16 = 256 blocks) and is therefore functionally
> equivalent on any standard server.

---

### Step 6 — Proxy layer

#### `IComputerCraftProxy` additions

```java
void playSpeakerAudio(int x, int y, int z, byte[] pcm, float volume);
void stopSpeaker(int x, int y, int z);
```

#### `ComputerCraftProxyCommon.handlePacket` — new cases

```java
case ComputerCraftPacket.SpeakerAudio: {
    int x = packet.m_dataInt[0], y = packet.m_dataInt[1], z = packet.m_dataInt[2];
    float vol = packet.m_dataInt[3] / 1000.0f;
    // m_dataByte[0] contains DFPWM-encoded bytes (not raw PCM)
    byte[] dfpwm = packet.m_dataByte != null && packet.m_dataByte.length > 0
                   ? packet.m_dataByte[0] : new byte[0];
    proxy.playSpeakerAudio(x, y, z, dfpwm, vol);
    break;
}
case ComputerCraftPacket.SpeakerStop: {
    proxy.stopSpeaker(packet.m_dataInt[0], packet.m_dataInt[1], packet.m_dataInt[2]);
    break;
}
```

#### `ComputerCraftProxyServer` stubs

```java
@Override public void playSpeakerAudio(int x, int y, int z, byte[] pcm, float volume) {}
@Override public void stopSpeaker(int x, int y, int z) {}
```

#### `ComputerCraftProxyClient` overrides

```java
@Override
public void playSpeakerAudio(int x, int y, int z, byte[] pcm, float volume) {
    SpeakerManager.INSTANCE.playAudio(x, y, z, pcm, volume);
}

@Override
public void stopSpeaker(int x, int y, int z) {
    SpeakerManager.INSTANCE.stop(x, y, z);
}
```

---

### Step 7 — `SpeakerManager` (client-only)

**File**: `client/audio/SpeakerManager.java`
**Annotation**: `@SideOnly(Side.CLIENT)`

Receives DFPWM-encoded audio bytes from the network, decodes them to PCM, and plays via
`javax.sound.sampled.SourceDataLine` at 48 kHz / 8-bit signed mono.

```
public static final SpeakerManager INSTANCE = new SpeakerManager();

// Keyed by ChunkCoordinates so multiple speakers in different positions each get
// their own audio line.
private final Map<ChunkCoordinates, SourceDataLine> m_lines = new HashMap<>();

public void playAudio(int x, int y, int z, byte[] dfpwm, float volume) {
    // 1. Decode DFPWM → PCM using a per-speaker DfpwmDecoder instance
    //    (each speaker position has its own decoder so state is not shared between speakers)
    // 2. Scale PCM samples by volume, clamping to [-128, 127]
    // 3. Open or reuse a SourceDataLine:
    //      AudioFormat(48000, 8, 1, true, false)
    // 4. Write decoded bytes to the line (non-blocking: drop excess if buffer full)
}

public void stop(int x, int y, int z) {
    SourceDataLine line = m_lines.remove(new ChunkCoordinates(x, y, z));
    if (line != null) { line.stop(); line.flush(); line.close(); }
}
```

The DFPWM decoder (`DfpwmDecoder`) mirrors `cc.audio.dfpwm`'s `make_decoder()` exactly — the
same `PREC_SHIFT = 10`, `PREC_CEIL = 1024`, `LPF_STRENGTH = 140` constants — so audio round-
trips cleanly through the server-side encoder.

> **Volume note**: `javax.sound.sampled` does not integrate with Minecraft's master volume slider.
> Volume is applied by linearly scaling the PCM sample values before writing to the line.
> See § "Further considerations / OpenAL alternative".

---

### Step 8 — Registration and resources

#### `ComputerCraft.Blocks`

```java
public static BlockSpeaker speaker;
```

#### `ComputerCraftProxyCommon.registerItems`

```java
ComputerCraft.Blocks.speaker = new BlockSpeaker();
GameRegistry.registerBlock(ComputerCraft.Blocks.speaker, ItemBlock.class, "speaker");
```

Craft recipe — **note block** in the centre, **redstone dust** in the bottom-centre, **stone**
in the seven remaining slots:

```
S S S
S N S
S R S
```

```java
ItemStack speaker = new ItemStack(ComputerCraft.Blocks.speaker);
GameRegistry.addRecipe(speaker,
    "SSS", "SNS", "SRS",
    'S', Blocks.stone,
    'N', Blocks.noteblock,
    'R', Items.redstone);
```

#### `ComputerCraftProxyCommon.registerTileEntities`

```java
GameRegistry.registerTileEntity(TileSpeaker.class, "ccspeaker");
```

#### Config keys added to `ComputerCraft.java` / `preInit`

```java
public static int speaker_max_notes_per_tick = 8;     // matches upstream Config.maxNotesPerTick
public static int speaker_audio_range = 256;          // approx. sendToAllTracking (16-chunk view distance)
```

Both loaded from `ComputerCraft.cfg` under `[general]`.

#### Texture assets

Three PNG files required under `assets/computercraft/textures/blocks/`:

| File | Used on |
|---|---|
| `speakerTop.png` | Top and bottom faces |
| `speakerFront.png` | Front (facing) face |
| `speakerSide.png` | The four remaining side faces |

#### `en_US.lang`

```
tile.computercraft:speaker.name=Speaker
```

---

### Step 9 — Tests

**File**: `src/test/java/dan200/computercraft/shared/peripheral/speaker/SpeakerPeripheralTest.java`

All cases below are runnable without a live Minecraft world.  Argument validation and list-size
checks happen before any Minecraft I/O, so no world reference is needed.

| # | Description |
|---|---|
| 1 | `getType()` returns `"speaker"` |
| 2 | `getMethodNames()` returns exactly `["playNote","playSound","playAudio","stop"]` |
| 3 | `playNote` — missing instrument arg → `LuaException` |
| 4 | `playNote` — non-string instrument → `LuaException` |
| 5 | `playNote` — unknown instrument name → `LuaException` |
| 6 | `playNote` — volume below 0 → `LuaException` |
| 7 | `playNote` — volume above 3 → `LuaException` |
| 8 | `playNote` — pitch below 0 → `LuaException` |
| 9 | `playNote` — pitch above 24 → `LuaException` |
| 10 | `playNote` — all valid args, returns `true`, note added to pending list |
| 11 | `playNote` — returns `false` (not throws) when pending list is at `speaker_max_notes_per_tick` |
| 12 | `playSound` — missing name arg → `LuaException` |
| 13 | `playSound` — non-string name → `LuaException` |
| 14 | `playSound` — name length > 512 → `LuaException` |
| 15 | `playSound` — negative volume → `LuaException` |
| 16 | `playSound` — valid args, returns `true` |
| 17 | `playSound` — returns `false` when `pendingSound` already set |
| 18 | `playAudio` — missing audio arg → `LuaException` |
| 19 | `playAudio` — non-table audio arg → `LuaException` |
| 20 | `playAudio` — empty table (length 0) → `LuaException` |
| 21 | `playAudio` — table length > 128 * 1024 → `LuaException` |
| 22 | `playAudio` — sample value < −128 → `LuaException` |
| 23 | `playAudio` — sample value > 127 → `LuaException` |
| 24 | `playAudio` — valid 128-element table, returns `true` |
| 25 | `playAudio` — returns `false` when `pendingAudio` already set (buffer full) |
| 26 | `stop` — no args, no throw, sets `shouldStop` flag |

---

### Step 10 — `TWEAKEDCC_COVERAGE.md`

Move the speaker row in the roadmap table from `Deferred` to `✅ Done` and add a `### 21. Speaker peripheral` section documenting the implementation decisions.

---

## Further considerations

### 0 — Speaker range

Range is handled differently by each method, matching upstream exactly:

| Method | Range mechanism | Effective range |
|---|---|---|
| `playNote` | MC native `world.playSoundEffect` → `S29PacketSoundEffect` | `max(volume, 1.0) × 16` blocks (e.g. 48 blocks at max volume 3.0) |
| `playSound` | `sendToAllAround(packet, volume * 16)` | `max(volume, 1.0) × 16` blocks |
| `playAudio` | Upstream: `sendToAllTracking(packet, chunk)` (all players with chunk loaded) | 1.7.10 approximation: `sendToAllAround` with `speaker_audio_range` (config, default **256** blocks ≈ 16-chunk max view distance) |
| `stop` | Upstream: `sendToAllPlayers` — **entire server** | `ComputerCraft.sendToAllPlayers(packet)` (already exists) |

There is no single `speaker_range` config.  The audio range is a **separate** config key
(`speaker_audio_range`) because it approximates chunk-tracking, not a sound radius.

---

### 1 — `playAudio` back-pressure

Upstream uses a **nanosecond-timer** model inside `DfpwmState` — no client→server feedback
needed:

- After `pullPending()` dispatches a batch, `clientEndTime` is advanced by
  `(dfpwmBytes × 8 / 48000)` seconds worth of nanoseconds.
- `shouldSendPending(now)` returns `true` when `now >= clientEndTime - CLIENT_BUFFER`
  where `CLIENT_BUFFER = 500 ms`.  This means the server starts sending the next batch when
  the client has less than 500 ms of audio left in its buffer.
- `speaker_audio_empty` is queued from `updateEntity()` immediately after each batch is
  dispatched — not from `playAudio` itself.

This model provides natural back-pressure with no network round-trip.  It is the model this
implementation follows (`SpeakerAudioState` mirrors `DfpwmState` using `System.nanoTime()`).

---

### 2 — Post-1.7.10 instruments (silent sounds)

The 11 post-1.7.10 instrument names (`flute`, `bell`, `guitar`, `chime`, `xylophone`,
`iron_xylophone`, `cow_bell`, `didgeridoo`, `bit`, `banjo`, `pling`) are accepted by the API
without error, matching CC:Tweaked's validation contract.  In vanilla 1.7.10 they are inaudible
because Minecraft has no `note.<name>` sound event defined for them.  A resource pack that adds
those events will make them work automatically, with no code change needed.

---

### 3 — OpenAL alternative for `playAudio`

`javax.sound.sampled` does not respect Minecraft's in-game volume slider.  Replacing
`SourceDataLine` with LWJGL's OpenAL API (`AL10.alSourceQueueBuffers`) would let the speaker
honour the "master volume" setting at the cost of more complex buffer lifecycle management.  This
is deferred; the `SpeakerManager` abstraction isolates the change to a single class.

---

### 4 — Advanced speaker

Deferred.  Design notes will be captured in a separate plan when development is ready to begin.

