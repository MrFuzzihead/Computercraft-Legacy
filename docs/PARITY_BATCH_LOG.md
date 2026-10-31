# Parity batch log

> Companion to [`CC_TWEAKED_1.120_PARITY_FEASIBILITY.md`](CC_TWEAKED_1.120_PARITY_FEASIBILITY.md).
>
> One section per implemented batch, recording: what changed, the **public API impact**,
> deliberate deviations from CC:Tweaked, what the automated tests cover, and the residue that
> only an in-game check can settle.

Legend for API impact:

| Mark | Meaning |
|---|---|
| 🟢 | Purely additive — existing behaviour and signatures unchanged. |
| 🟡 | Behaviour change — a Lua-visible result or message differs; source compatibility kept. |
| 🔴 | Breaking — an existing call site, signature or value changes meaning. |

---

## Source of truth used for porting

Ports are diffed against the real upstream source, never written from memory.

**Authoritative reference:** `migrate/CC-Tweaked-1.20.1-1.120.2` (gitignored). Its
`gradle.properties` says `modVersion=1.120.2`, `mcVersion=1.20.1`, `isUnstable=false` — the
released build this audit targets.

### Beware: a branch head is not a release

The GitHub `mc-1.20.x` **branch head** also reports `version=1.120.2`, because the version is
only bumped at release time — but it is *ahead* of the 1.120.2 release. Concretely: branch head
splits the JSON and stringified-NBT parsers out into `cc/internal/json.lua` and
`cc/internal/snbt.lua`, shrinking `textutils.lua` to 874 lines. That split is **unreleased**;
the 1.120.2 release *and* the 1.120.3 release both keep the parsers **inline** in
`textutils.lua` (1057 lines) and ship no `cc/internal/json.lua` / `cc/internal/snbt.lua`.

Verified against `migrate/CC-Tweaked-1.20.1-1.120.2`:

| File | vs released 1.120.3 | vs `mc-1.20.x` branch head |
|---|---|---|
| `bios.lua` (757 lines) | identical | whitespace only (two `type = "boolean"` indents) |
| `rom/apis/keys.lua` (156 lines) | identical | identical |
| `rom/modules/main/cc/base64.lua` (182 lines) | identical | identical |
| `rom/apis/textutils.lua` (1057 lines, parsers inline) | identical | **differs** (branch head delegates to `cc.internal.*`) |

Consequences:

- Batch A's ports of `bios.lua`, `keys`, `textutils` and `base64` match the released 1.120.2
  source.
- `cc/internal/json.lua` / `cc/internal/snbt.lua` are **not** parity items — they are unreleased
  upstream refactoring. Do not add them to Batch C unless upstream ships the split.
- `bios.lua` differs from 1.120.3 only in indentation, so all four files can be treated as a
  single reference version.

---

## Batch A — Core BIOS / Lua runtime

**Status:** implemented and complete (see the end of this section for sign-off).

**Files touched**

| File | Change |
|---|---|
| `assets/computercraft/lua/bios.lua` | `print`/`printError`/`write`, `loadfile`/`dofile`, `os.run`, `os.loadAPI`, `os.version`, `fs.complete`, `settings.define` defaults, `_CC_DEFAULT_SETTINGS` parsing |
| `assets/computercraft/lua/rom/apis/textutils` | `formatTime`, `urlEncode`, inf/NaN `serialize`, JSON sentinels, `serializeJSON`/`unserializeJSON` rewritten |
| `assets/computercraft/lua/rom/apis/keys` | modern aliases, the omitted LWJGL2 key codes, two corrected legacy values |
| `assets/computercraft/lua/rom/modules/main/cc/base64.lua` | **new** (MPL-2.0, ported from CC:Tweaked) |

### Public API impact

**No Java/API-package changes.** Nothing under `dan200.computercraft.api` was touched, so
companion mods that compile against the mod are unaffected.

Lua-visible surface:

| Change | Impact | Note |
|---|---|---|
| `os.version()` → `"CraftOS 1.9"` | 🟡 | Was `"CraftOS 1.7"`. Programs that compare the string see a new value; that is the point of the change. |
| `print(...)` separates arguments with `\t` and prints `nil` holes | 🟡 | Was concatenation with no separator, and `{...}` truncated at the first `nil`. No bundled ROM program is affected (checked). |
| `printError(...)` restores the previous text colour | 🟡 | Was hard-coded back to white. |
| `write(x)` accepts non-strings | 🟢 | `tostring` coercion; previously raised. |
| `loadfile(path, mode, env)`; `loadfile(path, env)` | 🟢 | The old single-argument form still works. Chunk names are now `@/path` instead of a bare file name, so tracebacks change text. |
| `dofile(path)` now always defined | 🟢 | It was **undefined** on Lua 5.2, i.e. a straight bug fix. |
| `os.run` returns `false, err` on failure (was `false`) | 🟢 | Extra return value; callers checking only the first value are unaffected. |
| `os.run` honours `bios.strict_globals` | 🟢 | Off by default. |
| `os.loadAPI("foo.lua")` exposes `foo` | 🟡 | Was registered as `foo.lua`. Matches CC:Tweaked. |
| `fs.complete(..., { include_hidden = false })` | 🟢 | Default is `true`, so boolean-argument callers keep the old behaviour. |
| `settings.get(...)` for the new BIOS defaults | 🟢 | New names only, e.g. `shell.autocomplete`, `edit.default_extension`, `motd.path`. |
| `_CC_DEFAULT_SETTINGS` parsing | 🟡 | Now hand-parsed like upstream: `true`/`false`/number/string, and `nil` **unsets** (previously stored the string `"nil"`). Table/string literals still fall back to `textutils.unserialize`. |
| `textutils.formatTime(0)` → `"12:00 AM"` | 🟡 | Was `"0:00 AM"`. |
| `textutils.urlEncode` leaves `-_.` unescaped | 🟡 | Output differs for those three characters only. |
| `textutils.serialize` emits `1/0` / `-1/0` / `0/0` | 🟡 | Was `inf` / `-inf` / `nan`, which did not round-trip. |
| `textutils.json_null` / `empty_json_array` are immutable | 🟡 | Mutating them now raises. Comparison by identity still works. |
| `textutils.serializeJSON(..., { nbt_style = true })` | 🟢 | New option. |
| `textutils.unserializeJSON(..., { parse_null, nbt_style, parse_empty_array })` | 🟢 | New options; the pre-existing local spelling `{ null = value }` still works. |
| `textutils.serializeJSON` array holes → `null` | 🟡 | A `nil` in the middle of a sequence used to truncate the array. |
| `textutils.serializeJSON` reports "repeated" vs "recursive" tables distinctly | 🟡 | Error text only. |
| `textutils.serializeJSON` non-ASCII handling | 🟡 | Now matches upstream: `unicode_strings` escapes **code points** (`\u00E9`), and the default path escapes **bytes** as uppercase `\u00XX`. Previously the default path passed raw UTF-8 through and `unicode_strings` produced lowercase byte escapes. |
| `keys.semicolon`, `.scrollLock`, `.circumflex`, `.numPadMultiply`, `.numPadEqual` | 🟢 | Aliases; the legacy spellings keep their values. |
| `keys.f16`–`f19`, `.printScreen`, `.leftSuper`, `.rightSuper`, `.menu` | 🟢 | New codes, added at the real LWJGL2 values. |
| `keys.numPadEnter` 152 → **156**, `keys.rightCtrl` 153 → **157** | 🟡 | Value change that makes two previously dead constants work. See below. |
| `require "cc.base64"` | 🟢 | New module. |

### Corrected legacy key codes

The original ComputerCraft 1.75 `keys` table put the block after `KEY_AX` (150) four slots early,
so it defined `numPadEnter = 152` and `rightCtrl = 153`. LWJGL2 defines **nothing** at 152/153;
the real values are `KEY_NUMPADENTER = 156` and `KEY_RCONTROL = 157`, so both constants could
never match a `key` event.

The values were corrected, and the whole table was then machine-checked against every
`org.lwjgl.input.Keyboard.KEY_*` constant from `lwjgl-2.9.4-nightly-20150209.jar`. Result: no
remaining name maps to a code LWJGL2 does not define, and no modelled key sits at the wrong code.

Independent confirmation that 157 is right: this mod's own `WidgetTerminal.update()` checks
`Keyboard.isKeyDown(157)` for right-ctrl (pairing it with 29 for left-ctrl).

Logged as 🟡 rather than 🟢 only because a constant changed value. No working program can depend
on the old numbers, because those keys never fired.

### Deliberate deviations from CC:Tweaked

1. **`keys` numeric values are LWJGL2, not GLFW.** 1.7.10 delivers
   `org.lwjgl.input.Keyboard.KEY_*` codes, so only the *names* could be brought to parity.
   `f20`–`f25` and `world1`/`world2` are absent rather than fabricated — 1.7.10 cannot produce
   them.
2. **`print` writes a tab, but newlines still move the cursor.** `write()` implements `\n` by
   repositioning rather than emitting a character (unchanged legacy behaviour).
3. **`textutils` has no `cc.internal.snbt` / `cc.internal.json` module, and should not.** See
   "Beware: a branch head is not a release" above. The 1.120.2 release keeps `nbt_style` inline
   in `textutils.lua`, which is what this project has.
4. **Unicode escapes combine UTF-16 surrogate pairs; upstream does not.** The 1.120.2 release
   does `utf8.char(tonumber(num_str, 16))` per escape, which turns a `\uD83D\uDE00` pair into
   two invalid 3-byte sequences. This ROM combines the pair into one code point (U+1F600).
   Strictly better, still accepts ordinary escapes; pinned by `TextUtilsBatchATest`.
5. **Invalid UTF-8 is tolerated rather than rejected.** Upstream's `unicode_strings` path uses
   Lua's `utf8.codes`, which raises on malformed input. This ROM's self-contained iterator falls
   back to emitting the offending byte as `\u00XX`, so serialising arbitrary binary-ish strings
   never throws.
6. **`utf8`, `bit32`, `debug` and `errorInfo` are absent from this runtime.** Upstream's
   `CobaltLuaMachine` installs `CoreLibraries.debugGlobals(state)` (which includes `Utf8Lib`)
   plus `Bit32Lib` and `ErrorInfoLib`; this mod installs only Base/Table/String/Math/Coroutine.
   Lua-side ports therefore cannot use `utf8.codes` / `bit32.extract` verbatim and carry local
   arithmetic instead. Closing that gap means adding globals, so it is tracked separately and is
   **not** part of Batch A.
7. **`os.run` does not overwrite an existing env metatable.** Kept from the previous local
   implementation: the shell builds a `tRunEnv → tEnv → _G` chain and overwriting it would hide
   `shell`.
8. **A bad `_CC_DEFAULT_SETTINGS` entry cannot brick a computer.** Each `settings.set` is
   wrapped in `pcall`, so a value that violates a registered `type` is dropped instead of
   aborting BIOS startup. Upstream would raise.

### Automated coverage

Unit (Lua loaded into a `CobaltMachine`):

| Test class | Covers |
|---|---|
| `KeysAPITest` | every legacy key value pinned, aliases, added codes, absence of `f20`–`f25`, corrected `numPadEnter`/`rightCtrl`, `getName` |
| `CcBase64Test` | RFC 4648 vectors, full 0–255 byte round-trip, base64url, padding/validation errors |
| `TextUtilsBatchATest` | `formatTime` (incl. midnight/24h), `urlEncode`, inf/NaN serialise + round-trip, sentinel immutability, `nbt_style` both directions, `parse_null`, `parse_empty_array`, unicode escapes and surrogate pairs, error paths, aliases |
| `LuaRomTestSupport` | shared machine/capture plumbing for the above |

Headless harness (`BiosBatchAHeadlessTest`) — boots the **real BIOS against the real ROM**, drops
a `/startup` program into the save directory and lets the shell run it. Covers what per-file unit
tests structurally cannot: `os.loadAPI`'s table copy, `require` resolving `cc/base64.lua` through
the real `package.path`, `settings.define` running before `settings.load`, `os.run`'s per-program
env, and `bios.strict_globals` end to end. Each check must report exactly once, so a check that
stops running fails the build.

The in-game scripts in [`ingame-tests/batch-a/`](ingame-tests/batch-a/) are also run by
`BatchAIngameScriptsTest`, which boots the real BIOS and executes each one, so a broken in-game
script fails the build instead of wasting a manual pass.

Full suite: **1492 tests, 0 failures**, including `spotlessCheck`, `checkstyleMain` and
`checkstyleTest`.

### Needs in-game validation

The harness cannot reproduce a real client, so these need a human:

1. **`print` separators on screen.** Confirmed unaffected in the bundled ROM by inspection, but
   third-party programs relying on `print(a, b)` concatenating will now show a tab.
2. **`keys` on a real keyboard** — details in the sign-off list at the end of this document.
3. **`settings` defaults from a program, before and after a reboot** with a persisted
   `.settings` file.
4. **`bios.strict_globals`** end to end via `settings.save()` + reboot.
5. **`fs.complete` inside `read()`/shell completion.** Hidden files must still complete by
   default when typing `.`; `shell.autocomplete_hidden` is only *defined* here, not yet
   *consumed* (that is Batch C).

---

## Batch A.1 — follow-up fixes

**Status:** implemented.

Two defects were found while reviewing Batch A and fixed here rather than left as "open
questions", because both are bugs rather than taste:

### A.1.1 `textutils.serializeJSON` non-ASCII handling

Ported `serializeJSONString` faithfully from the 1.120.2 release:

- `unicode_strings = true` walks **code points** and emits `\uXXXX` (with a UTF-16 surrogate pair
  above U+FFFF), matching upstream.
- The default path escapes bytes ≥ 0x7F as uppercase `\u00XX`, matching upstream. It previously
  passed raw UTF-8 through, which produced JSON that was not ASCII-safe.
- Hex digits are now uppercase in both paths, matching upstream (`\u00E9`, not `\u00e9`).

Implemented with a self-contained UTF-8 iterator because this runtime has no `utf8` library (see
deviation 6). Invalid sequences fall back to byte-wise escaping instead of raising.

Note the asymmetry this inherits from upstream: the **default** path escapes bytes while
`unserializeJSON` decodes code points, so `serializeJSON` → `unserializeJSON` only round-trips
when `unicode_strings = true` is used. That is upstream's behaviour, not a local defect.

Impact: 🟡. `serializeJSON("é")` now yields `"\u00C3\u00A9"`, and with `unicode_strings` it yields
`"\u00E9"`. Use the option when a round-trip is needed.

### A.1.2 `_CC_DEFAULT_SETTINGS` parsing

Adopted upstream's hand-parser. Previously the whole value was fed to
`textutils.unserialize`, which meant `...=nil` stored the **string** `"nil"` instead of unsetting
the setting, and bare words were only accepted by accident.

- `true` / `false` → booleans; `nil` → `settings.unset`; numeric text → number; anything else →
  the literal string.
- Local extension kept for compatibility: values starting with `{`, `[`, `"` or `'` are still run
  through `textutils.unserialize`, so table/string defaults in `cc_default_settings` keep working.
  Inherited limitation: entries are split on `,` before parsing, so only comma-free literals (e.g.
  `{a=1}`) survive. The previous implementation had the same limitation.
- Each `settings.set` is `pcall`-guarded, so a value that violates a registered `type` (for
  example `motd.path={}`) is dropped instead of aborting BIOS startup and bricking every computer
  with that config.

Impact: 🟡, and only for servers that actually set `cc_default_settings`.

### Coverage added for A.1

| Test | Covers |
|---|---|
| `TextUtilsBatchATest.unicodeStringsEscapeCodePoints` | 2-, 3- and 4-byte code points; surrogate pair for U+1F600; uppercase hex |
| `TextUtilsBatchATest.unicodeStringsRoundTrips` | serialise → unserialise returns the original bytes for ASCII and multi-byte UTF-8 |
| `TextUtilsBatchATest.defaultJsonStringIsAsciiSafe` | raw UTF-8 is escaped even without `unicode_strings`, and the output is pure ASCII |
| `TextUtilsBatchATest.invalidUtf8IsEscapedRatherThanRaising` | malformed UTF-8 degrades to byte escaping instead of throwing |
| `BiosBatchAHeadlessTest.defaultSettingsParseBooleansNumbersAndNil` | real BIOS: `false`, number, `nil`→unset, plain word |
| `BiosBatchAHeadlessTest.defaultSettingsTableFallbackStillWorks` | local table-literal extension still accepted |
| `BiosBatchAHeadlessTest.badDefaultSettingDoesNotBrickBoot` | type-mismatched default is dropped, later entries still apply, computer boots |

---

## Batch A sign-off

Batch A (including A.1) is complete when the in-game checklist below passes. Nothing under
`dan200.computercraft.api` changed, so no companion mod recompilation is needed.

### In-game validation checklist

Everything below is behaviour the automated suite structurally cannot check, because it needs a
real client, a real keyboard, or a reboot.

**1 — Keyboard (`keys`), in a computer, running `pastebin`-free local script:**

```lua
while true do
  local _, k = os.pullEvent("key")
  print(k, keys.getName(k))
end
```

Press each of these and confirm the number matches the second column:

| Key | Expected code |
|---|---|
| Print Screen | 183 |
| Menu / Apps | 221 |
| Left Win/Super | 219 |
| Right Win/Super | 220 |
| F16 / F17 / F18 / F19 | 103 / 104 / 105 / 113 |
| Numpad Enter | **156** (was 152 — must now fire) |
| Right Ctrl | **157** (was 153 — must now fire) |
| Numpad `*` | 55 |
| Numpad `=` | 141 |

If a key produces no event at all, Minecraft/Forge never forwards it — that is a platform limit,
not a regression. Record which ones are silent.

**2 — `print` layout.** `print("a", "b", nil, "c")` must render as `a<TAB>b<TAB>nil<TAB>c` on one
line. Check one bundled program with multi-column output (e.g. `help`, then a topic index) still
looks right.

**3 — Settings defaults and persistence.**
```lua
print(settings.get("edit.default_extension"))      -- lua
print(settings.get("shell.autocomplete"))          -- true
print(settings.get("shell.autocomplete_hidden"))   -- false
print(settings.get("motd.path"))                   -- /rom/motd.txt:/motd.txt
settings.set("edit.default_extension", "txt")
settings.save()
```
then reboot and confirm the value survives, and `settings.unset` + reboot returns the default.

**4 — `bios.strict_globals` end to end.**
```lua
settings.set("bios.strict_globals", true); settings.save()
```
Reboot, then run a program containing a bare `x = 1`. It must fail with
`Attempt to create global x` and the shell must survive. Set it back to `false`, save, reboot, and
confirm the same program runs.

**5 — `cc_default_settings` (server config).** In `config/ComputerCraft.cfg` set
`cc_default_settings=shell.autocomplete=false,edit.default_extension=txt`. Restart the server, open
a **fresh** computer, and confirm the shell no longer autocompletes and the editor defaults to
`.txt`. Also try `...=nil` and confirm it reverts to the built-in default rather than showing
`"nil"`.

**6 — `textutils.serializeJSON` non-ASCII.** No HTTP needed:
```lua
print(textutils.serializeJSON("café"))
print(textutils.serializeJSON("café", { unicode_strings = true }))
print(textutils.serializeJSON({ ["café"] = "naïve" }, { nbt_style = true }))
print(textutils.serializeJSON(string.char(255), { unicode_strings = true }))
```
Expect `"\u00C3\u00A9"` on the first line (pure ASCII), `\u00E9` on the second, an unquoted key
on the third, and `"\u00FF"` rather than an error on the fourth.

**7 — `textutils.unserializeJSON` with real SNBT.** 1.7.10 has no `/data get`, so paste literals
directly (`"` is the Loremaster's problem, so use long strings):
```lua
local nbt = [[{id:"minecraft:stone",Count:1b,Damage:0s,tag:{display:{Name:"Test"}}}]]
local t = textutils.unserializeJSON(nbt, { nbt_style = true })
print(t.id, t.Count, t.Damage, t.tag.display.Name)

local inv = [[{Inventory:[{Slot:0b,id:"minecraft:dirt",Count:3b}],Level:5}]]
local t2 = textutils.unserializeJSON(inv, { nbt_style = true })
print(#t2.Inventory, t2.Inventory[1].Count, t2.Level)

print(textutils.unserializeJSON("[I;1,2,3]", { nbt_style = true })[2])
print(textutils.unserializeJSON("{a:'quoted'}", { nbt_style = true }).a)
```
Report the first string it rejects, if any.

**8 — `fs.complete` / hidden files.** `mkdir /t` then `touch /t/.hidden` (via `fs.open`), then in
the shell type `ls t/` and `t/` + <kbd>Tab</kbd>. Hidden entries must complete by default. Also
check `read(nil, nil, function(s) return fs.complete(s, "/t", { include_files = true,
include_hidden = false }) end)` omits `.hidden`.

**9 — `os.version()`.** `print(os.version())` must print `CraftOS 1.9`, and the shell banner on
boot must show the same.

**10 — Multishell smoke test.** `bg shell` to open a second tab, click between the tab labels, and
run something in each. Batch A touches `print`, `write`, `os.run` and the program env chain, all
of which multishell depends on.

Note for whoever runs this: **there are no keyboard shortcuts for tabs in this build.**
`WidgetTerminal` reserves Ctrl+T / Ctrl+R / Ctrl+S as hold-to-terminate / reboot / shutdown, and
`multishell.lua` only switches tabs on `mouse_click`. Ctrl+W and Ctrl+Left/Right have no handler
at all. CC:Tweaked 1.120.2 behaves identically, so this is neither a regression nor a gap. Also
note that `multishell.launch({}, path)` does not put `multishell` in the tab's env — a tab printed
`multishell.getCurrent()` as `nil` for exactly that reason. `bg`/`fg` pass the shell env for you.

---

## Batch B — `io` library

**Status:** implemented.

**Files touched**

| File | Change |
|---|---|
| `assets/computercraft/lua/rom/apis/io` | rewritten as a port of CC:Tweaked 1.120.2's `io.lua` |
| `core/apis/FSAPI.java` | binary (`wb`/`ab`) handles now accept strings as well as single bytes |

### Public API impact

**No changes under `dan200.computercraft.api`.**

Lua-visible surface:

| Change | Impact | Note |
|---|---|---|
| Handles gain `seek`, `setvbuf`, `flush`, `writeLine` pass-through, and `__tostring` | 🟢 | `io.type(f)` still distinguishes `"file"` / `"closed file"`. |
| `file:read` accepts Lua 5.1 formats: `l`, `L`, `a`, `n`, a `*` prefix, a byte count, and several formats per call | 🟢 | Previously only `*l`/`*a` on read handles. `n` is accepted but yields nil (unimplemented in CC). |
| `io.stdin`, `io.stdout`, `io.stderr` | 🟢 | New. `stderr` writes in red and restores the previous colour. |
| `io.input()` / `io.output()` return the current handle | 🟡 | Previously returned nothing; now returns the handle, matching Lua 5.1. |
| `io.close()` / `io.flush()` return values | 🟡 | `close` returns `true`; closing a standard stream returns `nil, "attempt to close standard stream"` instead of silently doing nothing. |
| `io.write` rejects non-string/number values | 🟡 | Was `tostring`-ing everything, so `io.write(true)` worked. Lua 5.1 also rejects it. |
| `io.open` with an unsupported mode raises | 🟡 | Was `nil, "Unsupported mode"`. `fs.open` rejects the mode, and CC:Tweaked propagates that, so this is parity — but it is a Lua 5.1 incompatibility. |
| `io.lines(path)` closes its own handle at EOF | 🟢 | `handle:lines()` still leaves the handle open. |
| `io.output(path)` opens in `"wb"` | 🟡 | Upstream behaviour; see the FSAPI fix below. |
| A closed handle raises on further use | 🟡 | Was silently ignored. |
| **`fs.open(path, "wb"/"ab"):write("text")` now writes the text** | 🟢 | It only accepted a single byte number before and silently discarded strings. |

### Deviations from CC:Tweaked

1. **`require("cc.expect")` instead of `dofile(...)`.** Upstream's `io.lua` uses `dofile` because
   CC:T's `require` belongs to the shell, not the BIOS. This BIOS defines `require` before any API
   loads, and every other module here uses it, so the port does too.
2. **`io.stdin:read("*L")` works.** Upstream hands `true` straight into `_G.read` as the
   replace-character argument and raises. Terminal input has no trailing newline to preserve, so
   the port wraps `readLine` and drops the flag.
3. **`stdin:read("*a")` is nil** — inherited: the standard streams only provide `readLine`/`write`,
   so there is no `readAll` to call. Same as upstream.
4. **`io.stdin:lines()` and `io.lines()` work.** Upstream's `lines` requires `handle.read`, which
   the standard streams do not have, so both returned `nil, "file is not readable"` even though
   `read()` accepts `readLine`. The port checks for either, matching `read`.
   (Renumbering: the entries formerly numbered 4 and 5 follow.)
4. **`file:write(1)` writes `"1"`, not `"1.0"`.** `io` converts values with Lua's `tostring`
   before handing them to the fs handle, because this codebase's handles stringify a Java double.
   Note that calling `fs.open(..., "w"):write(1)` directly still writes `1.0`: that is a
   pre-existing `fs`-level discrepancy with CC:Tweaked, not something `io` can fix, and is left
   out of this batch.
5. Upstream's `"bad fileument #1"` typo in `io.input` is fixed.

### Automated coverage

| Test | Covers |
|---|---|
| `IOAPITest` (47 tests) | open/modes/argument checks, `l`/`L`/`a`/numeric/multi-format reads, `lines` (with and without autoclose, format pass-through), `seek` against a real file, write/append/chaining, standard streams, `io.input`/`io.output` getters and setters, `io.type`, closed-handle errors, `setvbuf`, error messages, `io.stdin:lines()`/`io.lines()` over stubbed input |
| `FSAPITest.binaryWriteHandlesAcceptStringsAsWellAsBytes` | the `wb` fix, including that a single byte number still works |
| `BiosBatchBHeadlessTest` (3 tests) | real BIOS: `io` loaded as an API table, `io.write`/`io.stderr` reaching the terminal, `/rom` file reads, `io.lines`, `io.output`/`io.input` redirection, `io.stdin` driven by simulated keystrokes, and `help` still printing through `io` |
| `IngameTestScriptsTest` | now discovers every `docs/ingame-tests/**/*.lua` (skipping `*manual*`), so `b1_io` runs in CI too |

Full suite: **1522 tests, 0 failures**.

### Found while testing

A unit harness that loads `io.lua` into the *shared* environment hangs forever: `io` defines a
global `read`, and the stdin handle resolves `_G.read` at call time, so `stdin:read()` recurses
until the JUnit timeout. In game this cannot happen because `os.loadAPI` gives each API a private
environment. `IOAPITest` now emulates that (source loaded into a table with `__index = _G`, exposed
as `io`), and `BiosBatchBHeadlessTest` covers the real `os.loadAPI` path.

### Needs in-game validation

The unattended half is `docs/ingame-tests/batch-b/b1_io.lua` (also run in CI). The interactive
half is `b2_io_manual.lua`, plus these by hand:

1. **Typing into `io.stdin`** — the harness injects synthetic `char`/`key` events; confirm on a
   real keyboard that `io.stdin:read()`, `io.read()` and `io.stdin:lines()` behave the same.
2. **`edit`** — `edit /tmp.txt`, write two lines, save, reopen. `edit` reads with
   `io.open(...):read()` in a loop, so it exercises the ported `read` path with real input.
3. **`help`** — `help intro` must page normally (it is the other bundled `io` reader).
4. **`paint`** — `paint` and `paintutils.loadImage` use `io.open(...):read("*a")`.
5. **A monitor** — `monitor right`, then `io.write("hi")` in the monitor shell; output must follow
   the redirect.
6. **A disk drive** (optional) — copy a file to a floppy and read it with `io.lines`; the hdd and
   rom mounts are already covered, a disk is the remaining mount type.

---

## Batch C — `cc.require`, shell environment, hashbangs (slice 1)

**Status:** slice implemented; error-reporting half deferred pending a decision on `debug`.

**Files touched**

| File | Change |
|---|---|
| `rom/modules/main/cc/require.lua` | **new** — `cc.require` ported from 1.120.2 |
| `rom/modules/main/cc/internal/tiny_require.lua` | **new** — verbatim upstream |
| `rom/programs/shell` | per-program `require`/`package`, `#!` hashbangs, `.lua` stripping in `shell.programs` |

### Public API impact

No `dan200.computercraft.api` changes.

| Change | Impact | Note |
|---|---|---|
| Programs get their own `require`/`package` | 🟢 | Rooted at the program's directory. Nothing was injected before, so this is additive. |
| `#!` on the first line of a program | 🟢 | The named interpreter runs with the program path appended, then the original arguments. Depth-limited to 8. |
| `shell.programs()` strips a `.lua` suffix | 🟡 | `list.lua` is now listed as `list`, and no longer twice. |
| Modules see the program's env, not `_G` | 🟢 | Standard Lua behaviour; a module's globals land in the program env. |

### Deviations from CC:Tweaked

1. **`debug.getregistry()` is guarded.** Upstream copies the global `package.loaded` registry via
   `debug.getregistry()._LOADED`. This runtime installs no `debug` library, so the lookup is
   skipped when `debug` is absent instead of raising.
2. **Hashbang programs still run through `os.run`.** Upstream's shell loads the chunk itself so it
   can read the file once; here the first line is peeked for `#!` and the normal path is left to
   `os.run`, which owns the strict-globals handling and error printing. One extra file open per
   program run, in exchange for a much smaller diff.
3. **Error reporting is not ported yet.** Upstream's shell uses
   `cc.internal.exception.try`/`report`, and on a syntax error runs the source through
   `cc.internal.syntax` to decide whether to show the compiler's message. Both need the `debug`
   library — see the decision below.

### The `debug` decision (blocks the rest of Batch C)

`cc.internal.exception` and `cc.internal.error_hints` call `debug.getinfo(thread, offset, "Sl")`,
`debug.getlocal`, `debug.getupvalue`, `debug.getregistry` and `debug.getmetatable`. Cobalt has
`DebugLib`, and CC:Tweaked installs it (`CoreLibraries.debugGlobals` plus `Bit32Lib`), but this mod
deliberately installs only Base/Table/String/Math/Coroutine, and `bios.lua` goes out of its way to
block `getmetatable("")` so programs cannot reach the shared string metatable.

Options:

1. **Install `DebugLib`** (one line, matches CC:Tweaked). Unlocks `cc.internal.exception`,
   `error_printer`, `error_hints`, `cc.internal.syntax` and the upstream shell's error paths
   verbatim. Cost: every program can now call `debug.*`, including `debug.getmetatable("")` and
   `debug.sethook`. Whether that is safe depends on whether Cobalt 0.9 keeps the string metatable
   per-state; worth checking before enabling.
2. **Port only the non-debug parts** and keep the current `printError(err)` reporting. Batch C
   completes except for tracebacks/hints.
3. **Ship a restricted `debug` shim** exposing only `getinfo`/`getlocal`/`getupvalue`/`getregistry`,
   enough for the error modules and for `cc.require`'s registry lookup, without `sethook` or
   `setmetatable`.

Recommendation: option 3 if the goal is modern error output without widening the sandbox;
option 1 if matching CC:Tweaked's runtime exactly matters more.

### Automated coverage

| Test | Covers |
|---|---|
| `CcRequireTest` (18 tests) | `searchpath` template precedence, nested names, custom separators, failure messages; `require` relative to the package dir, extensionless and `init` modules, caching, `nil`→`true`, env isolation, `preload` precedence, missing modules, require cycles, sentinel-after-error; `package` surface |
| `BiosBatchCHeadlessTest` (2 tests, 15 checks) | real BIOS + shell: a program requiring its neighbour, nested modules, cache within a run vs isolation across runs, missing-module errors; hashbang interpreter resolution, body not executed, argument forwarding, `arg[0]`, missing interpreter, the shell-as-interpreter guard; `.lua` stripping |
| `ShellAPITest` (37 tests) | preamble extended for the new peek/lazy-load path; all existing shell behaviour unchanged |

Full suite: **1544 tests, 0 failures**.

### Still to do in Batch C

- `completion.build(..., many = true)` (our `cc/shell/completion.lua` has `build` without `many`).
- Shell startup settings, the `/startup` directory, and the MOTD hook (`rom/startup`).
- Hidden-file completion wired to `shell.autocomplete_hidden`.
- `cc.internal.exception` / `error_printer` / `error_hints` / `syntax` — blocked on `debug`.
- `cc.internal.import` / `event` / `menu` / `edit_runner` — needed only by later batches
  (`import` for `cc.pretty`-era programs, `menu`/`edit_runner` for Batch I).

### Batch C, slice 2 — `debug` + modern error reporting

Decided: install Cobalt's `DebugLib` (option 1), matching CC:Tweaked.

**Why option 1 won.** My earlier preference for a restricted shim rested on a claim that
`debug.getmetatable("")` reaches a metatable "global between all computers". That is not true for
this runtime: Cobalt 0.9.9 stores it per state (`LuaState.stringMetatable`, read via
`LuaString.getMetatable(state)`), and CC:Tweaked ships full `DebugLib` without even the
`getmetatable("")` guard our `bios.lua` carries. The remaining difference is that programs can
`setupvalue`/`setlocal` into the shell's own closures — self-tampering on one computer, not an
escape, since API objects are Java fields on `CobaltCallback` rather than Lua upvalues. A shim
would also have reimplemented `getinfo` frame semantics, i.e. risked breaking the one code path
(tracebacks) that is hardest to test. Bonus: with `debug` present, `cc/expect` can name the
calling function again (upstream behaviour) — not done yet.

**Files**

| File | Change |
|---|---|
| `core/lua/lib/cobalt/CobaltMachine.java` | `DebugLib.add(state)` alongside the five standard libraries |
| `rom/modules/main/cc/internal/exception.lua` | **new**, verbatim from 1.120.2 |
| `rom/modules/main/cc/internal/error_printer.lua` | **new**, verbatim |
| `rom/modules/main/cc/internal/error_hints.lua` | **new**, verbatim |
| `rom/modules/main/cc/internal/syntax/{init,lexer,parser,errors}.lua` | **new**, verbatim |
| `rom/programs/shell` | loads programs itself, runs them under `exception.try`, and calls `exception.report`; on a syntax error runs the source through `cc.internal.syntax` first |

### Public API impact

| Change | Impact | Note |
|---|---|---|
| Global `debug` library | 🟡 | New global. Programs that feature-detect `if debug then` change path. Matches CC:Tweaked. |
| Program runtime errors show the offending source line, its column and a hint | 🟡 | Was a one-line `printError(err)`. |
| A failing program no longer unwinds the shell | 🟢 | `exception.try` runs the program in a coroutine, so the shell survives and reports. |

### Deviations

1. **`ErrorInfoLib` is not installed.** `error_hints` `pcall`s `require "cc.internal.error_info"`
   and degrades when it is missing, which is what happens here.
2. **`cc.internal.syntax` is loaded with `dofile`** rather than `require`, so the shell's own
   `require` (a BIOS global here, unlike CC:T) is not needed for it.
3. `rom/startup`'s MOTD and the `/startup` directory, `completion.build(..., many)` and
   hidden-file completion are still outstanding (see below).

### Coverage added

`BiosBatchCHeadlessTest` gained three checks around a program that raises:

| Check | Proves |
|---|---|
| `errors.message_reported` | `shell.run` returns false and the message reaches the terminal |
| `errors.code_frame` | the failing source line is echoed — only possible if `debug.getinfo` frame/column lookup works |
| `errors.no_internal_error` | the reporting path itself does not raise (it did once, via a missing `term.blit` in the test recorder, which is exactly the kind of thing that would kill a shell tab in game) |

Full suite: **1544 tests, 0 failures**.

### Batch C, slice 3 — completion, startup, `cc.expect`

**Files**

| File | Change |
|---|---|
| `rom/modules/main/cc/shell/completion.lua` | `build(...)` gains the `many` flag, validates its specs, and `file`/`dir`/`dirOrFile` follow `shell.autocomplete_hidden` |
| `rom/startup` | `/startup` may be a **directory**, honours `shell.allow_startup` / `shell.allow_disk_startup`, and runs the MOTD hook |
| `rom/modules/main/cc/expect.lua` | reports a value's `__name`; the caller-name lookup is restored but is inert on this runtime |

### Public API impact

| Change | Impact | Note |
|---|---|---|
| `completion.build(..., { fn, many = true })` | 🟢 | New flag: the last spec keeps completing every later argument. `many` anywhere else raises. |
| `build` validates non-function specs | 🟡 | Previously a bad spec was silently ignored. |
| Hidden files in completion | 🟡 | Now controlled by `shell.autocomplete_hidden` (default `false`, so hidden files are no longer offered by default). Typing a leading `.` still completes them. |
| `/startup` as a directory | 🟢 | Every file in it runs, in sorted order; a `/startup` file and directory may coexist. |
| `shell.allow_startup` / `shell.allow_disk_startup` | 🟡 | Startup can now be disabled per computer. Defaults are `true` / `commands == nil`, as upstream. |
| MOTD hook | 🟢 | Runs `/rom/programs/motd` when `motd.enable` is set — guarded by an existence check, so it stays inert until Batch D ports that program. |
| `cc.expect` reports `__name` | 🟡 | A table with a `__name` metatable field (e.g. `FILE*`) is named in the message instead of `table`. |

### Deviations and limits

1. **`build`'s spec signature is unchanged.** Our completers take `(shell, index, text, previous)`;
   upstream's take `(shell, text, previous)` because `build` drops the index. Rather than change
   every completer and caller, `build` still normalises the index to 1. `many` behaves as upstream.
2. **`build` keeps the factory form.** `{ cc.completion.choice, { "yes", "no" } }` still calls the
   factory once at build time, which is this module's existing documented behaviour.
3. **`cc.expect` still cannot name the calling function.** The lookup is restored and guarded, but
   `Cobalt 0.9.9`'s `debug.getinfo` populates `what` (`"Lua"`/`"C"`/`"main"`) and never `name`, so
   the branch is unreachable here. Verified with a probe over five stack levels. `__name` display
   works, because that only needs `debug.getmetatable`.

### Coverage

| Test | Covers |
|---|---|
| `CcShellCompletionTest` (+6, 69 total) | `many` repeats for later indexes and stops without it, `many` in the middle raises, non-function specs raise, and `file`/`dir`/`dirOrFile` pass `shell.autocomplete_hidden` through |
| `BiosBatchCHeadlessTest` (+2, 4 total) | `/startup` as a directory runs every file in sorted order; `shell.allow_startup = false` skips it |
| `CcExpectDebugTest` (3) | `__name` is reported by `expect` and `field`; a plain value still reports its Lua type |

Full suite: **1555 tests, 0 failures**.

### Batch C status

Complete. Deferred to other batches: `motd` itself and the missing help topics (Batch D), and
`cc.internal.import`/`event`/`menu`/`edit_runner` (only needed by later batches).

### Batch C — in-game pack, and a bug it found

`docs/ingame-tests/batch-c/` (auto-discovered by `IngameTestScriptsTest`):

| File | Covers |
|---|---|
| `c1_require.lua` | require rooted at the program's directory (flat, nested, cached, isolated per run), missing-module messages, hashbangs (interpreter, argument order, `arg[0]`, body not executed, missing interpreter, shell guard), `.lua` stripping, error frames, completion `many` + `shell.autocomplete_hidden` |
| `c2_require_manual.lua` | `/startup` directory across a reboot (sorted order), `shell.allow_startup`, scripted hidden-completion checks, then a Tab drill |

**Bug found by `c1_require`: file-handle leak on every reported error.**

Upstream's `cc.internal.exception.report()` opens the failing program to echo the offending source
line and never closes it:

```lua
local handle = fs.open(frame.source:sub(3), "r")
for _ = 1, line - 1 do handle.readLine() end
line_contents = handle.readLine()
-- no close
```

Symptom in game: after a reported error the program's own file could not be deleted
(`Access denied`), and each reported error permanently consumed one of the computer's file handles
— eventually `Too many open files`. Two control checks in `c1_require` (a never-run file, and a
successfully-run file) both delete fine, which pinned it to the error path.

Fixed locally by closing the handle, with a comment marking the deviation. The test now asserts
that a program's file can be deleted after it errored, so a regression here fails the build.

---

## Batch D — ROM programs and resources (slice 1)

**Status:** slice implemented; the larger programs are still to do.

**Files**

| File | Change |
|---|---|
| `rom/programs/about` | **new** (MPL-2.0, upstream) |
| `rom/programs/motd` | **new** (MPL-2.0, upstream) |
| `rom/programs/peripherals` | **new** |
| `rom/programs/set` | **new** |
| `rom/motd.txt` | **new** — rewritten for this mod, see below |
| `rom/programs/clear` | now supports `screen` / `palette` / `all` |
| `rom/programs/monitor` | now supports `scale <name> <scale>` |
| `rom/programs/list` | honours `list.show_hidden` |
| `rom/help/{about,motd,set}` | **new** topics |
| `NOTICE` (+ `src/main/resources/NOTICE`) | records the two MPL programs |

### Public API impact

No `dan200.computercraft.api` changes. Lua-visible:

| Change | Impact | Note |
|---|---|---|
| `about`, `motd`, `peripherals`, `set` programs | 🟢 | New programs. `shell.programs` and completion pick them up automatically. |
| `clear palette` / `clear all` | 🟢 | Previously `clear` ignored its arguments and always cleared the screen. A bare `clear` behaves as before. |
| `monitor scale <name> <n>` | 🟢 | New subcommand; rejects values outside 0.5–5. |
| `list` hides dot files unless `list.show_hidden` | 🟡 | Dot files were already hidden; the setting now reveals them. |
| MOTD on boot | 🟢 | The Batch C hook now has a program to run. `motd.enable` defaults to true on normal computers. |

### Deviations

1. **`rom/motd.txt` is not upstream's.** The CC:Tweaked file advertises `tweaked.cc` and
   `forums.computercraft.cc`, and tells the user to drag files onto the computer — Batch J, and
   impossible on vanilla LWJGL2. Replaced with tips that hold for this mod (settings, `edit`,
   `programs`, `peripherals`, monitor/speaker, Ctrl+T/R/S, floppies, multi-path file programs).
2. **No 2-argument `term.setPaletteColour(colour, rgb24)`.** `clear palette` works the same way
   upstream's does — `nativePaletteColour` returns `r, g, b` and is passed straight through as the
   remaining arguments — so the packed form is not needed. The feasibility audit's Batch E item
   remains open, and this batch does not add it.
3. `set` uses `cc.pretty` via `require`, which works because Batch C gives each program its own
   `require`.

### Coverage

`docs/ingame-tests/batch-d/d1_programs.lua` (also run in CI):

| Check | Notes |
|---|---|
| `about` | prints the CraftOS version and `_HOST` |
| `motd` | one line, several lines (random pick), missing `motd.path` |
| `peripherals` | runs and prints a heading |
| `set` | no args lists settings, `<name>` shows one, `<name> <value>` sets, `nil` unsets, a wrong-typed value is rejected |
| `list.show_hidden` | dot files hidden by default, revealed when set |
| `clear` | `screen`/`palette`/`all`/usage; `clear palette` visibly restores a changed palette entry |
| `monitor scale` | applies 1.5, rejects 9 — `SKIP` when no monitor is attached |

Full suite: **1555 tests, 0 failures** (the in-game pack runs inside it).

### Still open in Batch D

- `http/wget`, `pocket/equip`, `pocket/unequip`, `fun/speaker`
- `copy`/`move`/`delete`/`mkdir`: multi-path support and read-only/mount-root guards
- `import` (upstream's `rom/programs/import`)
- `rom/modules/turtle` directory

### Batch D, slice 2 — file programs, wget, speaker, pocket equip

**Files**

| File | Change |
|---|---|
| `rom/programs/{copy,move,delete,mkdir}` | replaced with the 1.120.2 versions: wildcard expansion via `fs.find`, multi-path `delete`/`mkdir`, read-only / mount-root / free-space guards |
| `rom/programs/http/wget` | **new** — downloads a file, or `run`s it |
| `rom/programs/fun/speaker` | **new** — DFPWM and 8-bit WAV playback, plus `sound` and `stop` (MPL-2.0) |
| `rom/programs/pocket/{equip,unequip}` | **new** (MPL-2.0) |
| `rom/modules/turtle/.ignoreme` | **new** — the empty turtle module directory |
| `rom/help/{wget,speaker,speakers,pocket}` | **new** topics |
| `NOTICE` (+ mirrored copy) | records `fun/speaker` and `pocket/{equip,unequip}` as MPL-2.0 |

### Public API impact

No `dan200.computercraft.api` changes.

| Change | Impact | Note |
|---|---|---|
| `copy` / `move` understand wildcards | 🟢 | `copy /a/*.txt /b` now works; previously only exact paths. |
| `copy` refuses to clobber, and reports read-only destinations and free space | 🟡 | It used to overwrite silently. |
| `move` refuses to move a mount root or a read-only file | 🟡 | Previously it tried and failed with a raw fs error. |
| `delete` / `mkdir` accept several paths | 🟢 | Both used to take only the first. |
| `delete` explains a mount root instead of deleting inside it | 🟡 | It now prints `Cannot delete mount /x` and suggests `rm /x/*`. |
| `wget`, `speaker`, `pocket/equip`, `pocket/unequip` | 🟢 | New programs; `shell.programs` and completion pick them up. |

### Deviations

1. **`wget`'s "http disabled" message points at this mod's config.** Upstream says to set
   `http.enabled` in CC:T's server config; this mod's flag is `http_enable` in
   `ComputerCraft.cfg`.
2. **`rom/programs/import` is deliberately not ported.** It exists upstream but waits for a
   `file_transfer` event, which needs Batch J — and vanilla LWJGL2 has no file-drop callback, so
   the program would be dead on every 1.7.10 client. Revisit with Batch J if lwjgl3ify support is
   added.
3. `speaker` uses `string.unpack`, which Cobalt provides, and `cc.audio.dfpwm`, which this ROM
   already ships.

### Coverage

`docs/ingame-tests/batch-d/d2_files.lua` (also run in CI):

| Check | Notes |
|---|---|
| `mkdir` | several paths, nested creation, usage message |
| `copy` | single source to a new name, into a directory, wildcard into a directory, refusing to overwrite, refusing "several into one", no matches |
| `move` | renames, wildcard into a directory, refuses to overwrite (destination untouched) |
| `delete` | one path, several paths, refuses a read-only file (and the file survives), no matches |
| `wget` | usage message, invalid URL; `SKIP` when the http API is off |
| `speaker` | usage message, `sound`, `stop`; `SKIP` with no speaker attached |
| `pocket/equip` | `equip`/`unequip`; `SKIP` on a non-pocket computer |

Full suite: **1555 tests, 0 failures**.

### Batch D status

Complete. Deliberately excluded: `import` (see above). The `speaker play` WAV/DFPWM path and the
pocket-computer equip flow need real hardware, so they are in the pack's manual drill rather than
the automated checks.

---

## Batch E — Terminal/API polish

**Status:** implemented.

**Files**

| File | Change |
|---|---|
| `core/apis/TermAPI.java` | `setPaletteColour(colour, rgb24)` overload; `blit` accepts uppercase hex |
| `shared/peripheral/monitor/MonitorPeripheral.java` | the same packed-colour overload for monitors |
| `rom/apis/window` | palette setters forward any arity, so the packed form survives a `term.redirect` |
| `rom/apis/colors` | `toBlit` validates its argument and range |
| `rom/apis/paintutils` | `drawLine` validates `startY` (it checked `startX` twice) and no longer leaks `maxX`/`minY`/`maxY` as globals |

### Public API impact

No `dan200.computercraft.api` changes.

| Change | Impact | Note |
|---|---|---|
| `term.setPaletteColour(colour, 0xRRGGBB)` | 🟢 | Matches `colours.packRGB`; the 4-argument form is unchanged. Also on monitors and windows. |
| `term.blit` with `A`–`F` | 🟢 | Previously raised `Invalid colour`. Lowercase still works, and the string is stored as given (the renderer lower-cases per character). |
| `colors.toBlit(non-number)` | 🟡 | Now raises a type error. |
| `colors.toBlit(0)` / `toBlit(> 0xffff)` | 🟡 | Now raises `Colour out of range`. Values in between keep the old floor(log2) mapping. |
| `paintutils.drawLine` with a non-numeric `startY` | 🟡 | Now raises the documented error instead of failing inside `math.floor`. |
| `paintutils.drawLine` no longer defines `maxX`/`minY`/`maxY` | 🟢 | They are locals now; a program relying on those globals was relying on a leak. |

### Notes

- The feasibility audit listed "`term.setPaletteColour(colour, rgb8)`" as missing. It was, but the
  earlier investigation for `clear palette` showed `nativePaletteColour` returns `r, g, b` in both
  1.120 and this ROM, so upstream's `clear` never used the packed form. The overload is still worth
  having because `colors.packRGB` produces exactly that shape, and CC:Tweaked accepts both.
- Two existing tests pinned the old case-sensitive `blit` behaviour (`checkColour("0F")` throwing,
  and `"feA"` as an invalid colour). Both were updated, since the change is deliberate.

### Coverage

| Test | Covers |
|---|---|
| `TermAPITest` (+5) | uppercase `blit` (text and background colours), packed palette colour, three-channel form still working, too-few arguments, channel range validation |
| `LuaTerminalPolishTest` (11, new) | `toBlit` palette mapping, range rejection, type rejection, floor behaviour, top-of-range; `drawLine` rejecting `startY`/`endX`/others, not leaking globals, still drawing the right pixels, rejecting numeric strings |
| `MonitorPeripheralTest` (updated) | uppercase accepted in `checkColour`, and the invalid-colour set no longer contains `A` |
| `docs/ingame-tests/batch-e/e1_terminal.lua` | all of the above through the real terminal API, in CI |

Full suite: **1571 tests, 0 failures**.

---

## Batch F — Item/block detail

**Status:** implemented. HogUtils tags deliberately excluded (owner decision: not a widely used
mod); everything else from the audit's Batch F list is in.

**Files**

| File | Change |
|---|---|
| `shared/util/ItemDetail.java` | **new** — the shared detail builder used by both `turtle.getItemDetail` and `inventory.getItemDetail` |
| `shared/util/NBTHash.java` | **new** — stable `nbt` hash |
| `shared/util/BlockStateBridge.java` | **new** — GTNHLib block states by reflection, metadata fallback |
| `shared/peripheral/inventory/InventoryPeripheral.java` | uses the shared builder; `getItemLimit` is now per-item |
| `shared/turtle/apis/TurtleAPI.java` | `getItemDetail` delegates to the shared builder |
| `shared/turtle/core/TurtleInspectCommand.java` | `state` comes from the bridge |
| `shared/computer/apis/CommandAPI.java` | `getBlockInfo(s)` `state` comes from the bridge |

### Public API impact

No `dan200.computercraft.api` changes. Lua-visible:

| Change | Impact | Note |
|---|---|---|
| `getItemDetail` gains `nbt`, `maxDamage`, `durability`, `lore`, `unbreakable`, `potionEffects`, `mapColor`, `itemGroups`, `enchantments` | 🟢 | All additive. `damage` still appears for damageable items. |
| `inventory.list` entries gain `nbt` | 🟡 | Basic detail now matches CC:Tweaked's (name, count, hash), plus the existing `damage`. |
| `inventory.getItemLimit` reports `min(stackSize, inventoryLimit)` for occupied slots | 🟡 | Was always the inventory limit. Empty slots are unchanged. |
| `turtle.inspect` / `commands.getBlockInfo` `state` | 🟡 | Was always `{}`. Now `{ metadata = n }`, or named properties when GTNHLib is installed. |
| `inventory.getItemDetail` gains `enchantments` | 🟢 | It previously only had them on the turtle path. |

### Deviations and limits

1. **The `nbt` digest is not byte-identical to CC:Tweaked's.** Upstream hashes the NBT wire format
   via a canonical writer; 1.7.10 has none (`NBTBase.write` is package-private, `NBTTagList` has no
   generic index accessor). `NBTHash` hashes a canonical *text* encoding instead: sorted compound
   keys, type-tagged scalars, list order preserved. Same NBT → same hash; a 1.20 server would
   report a different digest for the same stack, which only matters for cross-version comparison.
2. **`lore` entries are plain strings.** 1.7.10 has no JSON text components, so nothing is parsed.
3. **`potionEffects[].name` is the unlocalised name minus `potion.`** (e.g. `moveSpeed`), because
   1.7.10 has no effect registry id.
4. **`durability` is reconstructed** as `round(13 - 13 * damage / maxDamage) / 13`, since there is
   no `getBarWidth`.
5. **`mapColor` is only present for block items**, matching upstream, and is the 24-bit
   `MapColor.colorValue`.
6. **GTNHLib is read by reflection only** — nothing links at compile time, GTNHLib is LGPL-3.0 and
   stays optional. `BlockStateBridge` resolves `BlockPropertyRegistry.getBlockState(IBlockAccess,
   int, int, int)` once, then `BlockState.forEachValue(BiConsumer)` for typed values. Enums are
   reported by lowercased name; any failure falls back to metadata rather than breaking `inspect`.
7. **HogUtils tags are not implemented** (owner decision). The `tags` field is therefore absent
   rather than empty, so a program can distinguish "no tags available" from "no tags".

### Coverage

| Test | Covers |
|---|---|
| `NBTHashTest` (12) | null → null, empty tag hashes, lowercase hex, key-order independence (including nested compounds), distinct values/keys/types, list order preserved, nested lists, and that hashing does not consume the list |
| `BlockStateBridgeTest` (5) | metadata fallback shape, zero/high metadata, null world falls back, and `hasGtnhLib()` is false on the test classpath |
| `docs/ingame-tests/batch-f/f1_item_detail.lua` | the fields themselves, on a real turtle: stone's name/count/maxCount/displayName/mapColor/itemGroups, chest `getItemDetail`/`getItemLimit`, damageable items' `damage`/`maxDamage`/`durability`, `commands.getBlockInfo` state, `turtle.inspectDown`, and whether named GTNHLib properties are present |

Full suite: **1588 tests, 0 failures**.

### Needs in-game validation

Everything in the automated pack, plus the six manual items in
`docs/ingame-tests/batch-f/README.md` — mainly the fields that need specially-crafted items
(custom name, lore, potion, unbreakable) and the GTNHLib success path.

### Batch F follow-up — log the whole transcript, and gate GTNHLib on the mod list

Two changes after the first in-game attempt.

**1. My test asserted the wrong shape.** Upstream's `turtle.getItemDetail(slot [, detailed])`
defaults `detailed` to **false**, so `turtle.getItemDetail(1)` returns only name/count/damage (plus
the nbt hash). `f1_item_detail.lua` now checks both forms explicitly — the basic one must *lack*
`maxCount`/`displayName`, the `(1, true)` one must have everything — which is a stronger parity test
than before.

**2. Every check is now logged, not just the failures.** Each script records `OK`/`FAIL`/`SKIP`
with its detail into a transcript that `finish()` appends to the results file. Since terminals have
no scrollback, that file is the only way to review a run:

```
    readlog_manual /batch-f-results.txt    -- pages it a screen at a time
    edit /batch-f-results.txt              -- arrows scroll
```

`docs/ingame-tests/readlog_manual.lua` is the new paged reader (`*_manual` keeps CI away from it),
and `docs/ingame-tests/README.md` is a top-level index for all six packs.

**3. GTNHLib detection: `Loader.isModLoaded` first, reflection second.**

`BlockStateBridge` now gates its reflective probe on `Loader.isModLoaded("gtnhlib")`, with a
class-presence fallback:

```java
if (!modLoaded() && !classPresent()) return ABSENT;
```

- `Loader.isModLoaded` reads Forge's mod list and **never loads a class**, so nothing touches
  GTNHLib when it is not installed — the cheapest possible check, and it is the idiomatic 1.7.10
  soft-dependency test.
- It is wrapped in `try/catch (Throwable)` because `Loader` is uninitialised in unit tests and in
  any non-FML entry point; there it simply reports false.
- The class-presence fallback means a wrong mod id cannot silently disable the bridge: absence of
  the class is the only thing that turns it off.
- The API itself is still reached reflectively. That is not a preference — `compileOnly` against
  GTNHLib would work, but GTNHLib is LGPL-3.0 and optional, and reflection keeps it out of the
  compile classpath entirely, which is stronger than `IsModLoaded` alone can be.

Full suite: **1588 tests, 0 failures**.

### Batch F follow-up 2 — the in-game script reported only skips and left no file

Reported: "I still see skips and I don't see an output file in Computer 1."

Two separate problems, both in the test rather than in the feature:

1. **Everything skipped on a plain computer.** Every check was gated on `turtle`, so a non-turtle
   computer produced nothing but skips. A *vanilla* chest is not an inventory peripheral, which is
   why a chest did not help. `f1_item_detail.lua` now adapts instead:
   - an inventory peripheral (`peripheral.find("inventory")`) drives the item-detail and
     `getItemLimit` checks;
   - a turtle adds `turtle.getItemDetail` (basic *and* the `(1, true)` form) and `inspectDown`;
   - a command computer adds `commands.getBlockInfo`.
   Each absent capability is a named SKIP, and the script prints which inventory peripheral it used.
2. **A crash lost the log entirely.** `finish()` was only reached if every check completed, so an
   exception anywhere meant no `/batch-f-results.txt` at all. Each group of checks now runs inside
   `pcall` via a `group(name, fn)` helper: a crash becomes a `FAIL <group> crashed [...]` line and
   the log is still written. The script also prints the log path at the end.

Both are visible in the log now, so a future "it did nothing" report can be answered from
`/batch-f-results.txt` alone.

Full suite: **1588 tests, 0 failures**.

### Batch F follow-up 3 — `inspectDown crashed`

Reported: `FAIL inspectDown crashed`.

The cause was the test, not the mod: **a failed turtle command returns `false` plus a message, not
`nil`** (e.g. `No block to inspect` when there is nothing beneath the turtle). The script checked
`below == nil`, so it fell through to `below.name` on a boolean and threw. It now indexes only when
`type(below) == "table"` and reports the reason otherwise:

```lua
local below, reason = turtle.inspectDown()
if type(below) ~= "table" then
  skip("turtle.inspectDown returned " .. tostring(below) .. " (" .. tostring(reason) .. ")")
  return
end
```

`BlockStateBridge` was hardened at the same time, because the same class of surprise would be worse
there: both the registry lookup and the reflective invoke now catch `Throwable` (not
`ReflectiveOperationException | LinkageError`), and log at warn rather than debug. An optional,
LGPL-3.0 integration must not be able to take a core call like `turtle.inspect` down with it — not
via a missing class, a signature change, or a broken static initialiser.

---

## Batch G — Commands

`CommandAPI.java`. Five items, of which three were exactly as the audit described and two needed
re-scoping after checking against the 1.20 reference (the same pattern as Batch C's oversized
verdicts).

### G1 — `getDimension()`

New method, index 6, appended so the pre-existing indices stay stable for any program that pokes at
`commands[6]` (nothing does, but appending is free).

1.7.10 has no dimension *registry*, so the name is derived:

| id | name |
|---|---|
| 0 | `minecraft:overworld` |
| -1 | `minecraft:the_nether` |
| 1 | `minecraft:the_end` |
| other | the provider's own `getDimensionName()`, lowercased with spaces → underscores |

The naming rule is split out as `dimensionName(int id, String providerName)` — pure, no world — with
`dimensionName(World)` as a one-line wrapper. That is what makes it unit-testable: a mod dimension
cannot be exercised from a bare JVM, but the *rule* can.

### G2 — dimension arguments

`getBlockInfo` and `getBlockInfos` accepted only a numeric id. They now accept a name too, via
`resolveDimension(arg, fallback)`:

* a `Number` → the id, unchanged (full back-compat);
* `minecraft:overworld` / `overworld` / `minecraft:the_nether` / `the_nether` / `nether` /
  `minecraft:the_end` / `the_end` / `end`, case-insensitively;
* otherwise the loaded worlds are scanned by `dimensionName` (so mod dimensions work by their own
  name), and a miss raises `Unknown dimension`.

Both call sites lost their duplicated `infoHasDim4`/`infoHasDim5` marshalling blocks in favour of one
`worldFor(arg, fallback)` helper.

### G3 — `getBlockInfos` traversal order

Was `x → y → z`. Now `y → z → x`, matching upstream, so a program can index with
`x + z*width + y*width*depth + 1`. This is a **breaking change for any stored index**, which is why
the in-game pack asserts the formula against eight single-block lookups rather than just counting
entries.

### G4 — `commands.list` — re-scoped

The audit asked for "prefix filter → child-command listing". Upstream walks Brigadier's literal
command tree. **1.7.10 has no command tree**: `ICommandManager.getCommands()` is a flat map of
single-word names, and subcommands are parsed inside each `ICommand.processCommand`. There is no
public child list to return, and deriving one from `getCommandUsage` strings would be guesswork.

So `commands.list` keeps prefix filtering, and the in-game pack asserts *that* — an honest limitation
recorded here instead of a plausible-looking implementation that would be wrong on mod commands.

### G5 — `getEntities` — implemented as a documented subset

Two paths:

* `@p`, `@r`, `@a` and player names → `PlayerSelector.matchPlayers`, i.e. **Forge's own parser**,
  including the `x`/`y`/`z`/`r`/`rm`/`m`/`c`/`l`/`team` filters. Reimplementing that would be a
  source of divergence with no benefit.
* `@e[…]` → a small scanner over `loadedEntityList` supporting `type=`, `distance=`/`r=`, `rm=`,
  `x=`/`y=`/`z=` and `limit=`, with `..N` / `N..M` range syntax.

Anything 1.7.10 has no equivalent for (`tag=`, `nbt=`, `scores=`, `predicate=`, `@s`) **raises**
`Unsupported selector filter: <key>`. Silently matching everything would be strictly worse than an
error: a script would get a plausible list of the wrong entities.

Result shape matches upstream's `entity_details`: `name`, `displayName`, and `health`/`maxHealth` for
living entities. `name` is `EntityList.getEntityString` lowercased and deliberately **not**
namespaced — 1.7.10 has no entity registry id, so prefixing `minecraft:` would mislabel every mod
entity.

### Verification

`CommandDimensionTest` — 13 tests over `dimensionName` and `resolveDimension`: vanilla ids,
provider-name normalisation, id fallback, numeric pass-through, modern/bare/legacy aliases,
case-insensitivity, the fallback argument, unknown names, and non-string arguments.

In-game: `docs/ingame-tests/batch-g/g1_commands.lua`, which covers the four items that need a live
server (dimension lookup, name arguments, range ordering, selectors) and writes
`/batch-g-results.txt` so the transcript survives a terminal that cannot scroll.

---

## Batch H — Networking and concurrency

Five items: the `redirect` option, websocket options/timeout, private-address blocking,
`rednet.lookup` timeout plus `MAX_ID_CHANNELS`, and `parallel.spawn` / the empty-argument hang /
error source info. Two were exactly as described, one was already done, and two needed real work.

### H1 — `http` `redirect` option

The option was absent, and adding it exposed a worse problem: **Java's own follow-redirects never
re-enter our validation**. With `HttpURLConnection` left to follow them, a public URL that redirects
to `http://192.168.1.1/` bypasses the domain patterns *and* the address rule — so the redirect
option would have been a hole rather than a feature.

`HTTPRequest` now follows redirects itself:

* `setInstanceFollowRedirects(false)`, then a loop over `Location` for 301/302/303/307/308;
* every hop is re-validated through `checkURL` (domain patterns) and `PrivateAddresses.check`;
* 303 switches the method to `GET`, other codes keep it;
* the hop budget is 16, as upstream, and a redirect to the same URL is ignored so the budget is not
  the only thing preventing a loop;
* only `http`/`https` targets are followed — a `file:` or `jar:` `Location` is not chased, and
  returning `null` keeps the 3xx response visible to Lua instead of failing the request.

`redirect` defaults to `true` and is plumbed through the table form of `http.get` / `http.post` /
`http.request` as argument 9 of the native call.

### H2 — Private / CGNAT / link-local blocking

New `PrivateAddresses`, mirroring CC:Tweaked's `$private` address predicate:

* the four `InetAddress` helpers (any-local, loopback, link-local, site-local) plus multicast;
* the IANA special-purpose ranges that those helpers miss, kept as `network/bits` **data** so the
  list can be read against the registry: CGNAT `100.64.0.0/10`, `192.0.0.0/24`, the three TEST-NET
  blocks, `192.88.99.0/24`, `198.18.0.0/15`, `64:ff9b::/96`, `64:ff9b:1::/48`, `2001::/23` and
  `fc00::/7`;
* the check is on the **resolved address**, not the host name, so `my-router.example.com` pointing
  at `192.168.1.1` is caught;
* it runs on the HTTP worker thread, where DNS already happens, so it never blocks a computer
  thread — this is why it is not in `checkURL`;
* **deny by default**, with `http_allow_local` to restore the old behaviour for LAN servers. That is
  a behaviour change for anyone with a local web server, and it is deliberate: a computer could
  otherwise read the server's own machine, its LAN, and a cloud metadata endpoint at
  `169.254.169.254`.

The three existing HTTP test classes serve from `127.0.0.1`, so they now set `http_allow_local`
around their runs and restore it afterwards.

### H3 — `http.websocket` options table and timeout

Both entry points accepted only `(url, headers)`. They now also accept
`{ url =, headers =, timeout = }`, with field validation before any connection is attempted.

The bug this uncovered: `http.websocket({ url = ... })` **hung forever**, because the event loop
compared `evUrl == _url` against the *table* while the event carries a string. The real URL is now
resolved first and compared instead. The handshake timeout is passed through to `WebSocketRequest`
and clamped to 120 s, so a huge value cannot pin a pool slot.

### H4 — `rednet.lookup` timeout — already present

`lookup` already bounds itself with a 2 second timer. No change beyond a comment recording it, so
the next audit does not re-open it.

### H5 — `rednet` `MAX_ID_CHANNELS`

Computer IDs are used directly as modem channels, but a channel is 16-bit: an ID above 65535 would
wrap or be rejected inside the modem. The ID is now mapped into 1..65535 (`((id-1) % 65535) + 1`) for
the computer's own channel, the reply channel, and `send`'s recipient. `MAX_ID_CHANNELS` is published
as a constant. `broadcast` still uses 65535, which the mapping reproduces exactly.

### H6 — `parallel.spawn`, empty arguments, error source

`rom/apis/parallel` is now upstream's implementation, adapted:

* **`spawn`**: `waitForAll` passes every function a `spawn` argument, so work can be fanned out
  dynamically; calling it outside `waitForAll` raises `Cannot spawn new functions outside of
  waitForAll`;
* **empty arguments**: `waitForAny()` and `waitForAll()` with no functions now return immediately
  instead of pulling events for ever. `waitForAny()` returns 0;
* **error source info**: errors are re-raised through `cc.internal.exception.wrap_error` with the
  failing coroutine, so the shell can point at the line that broke rather than at `parallel`.

### Verification

* `HTTPRedirectTest` — 17 tests: which statuses redirect, absolute/relative `Location` resolution,
  the same-URL guard, non-http targets, and end-to-end follow/no-follow/303/hop-budget against a
  real local server.
* `PrivateAddressesTest` — 18 tests: every added range with its boundary cases, literal-vs-name
  detection (so `127.example.com` is never mistaken for an address), and the config flag.
* `docs/ingame-tests/batch-h/h1_networking.lua` — the `parallel` and `rednet` halves, which need
  `os`/`peripheral` and so cannot run on the headless machine, plus the socket-level `http` checks
  in its manual drill.

Full suite: **1636 tests, 0 failures**.

### Batch H follow-up — four bugs found by re-reading the batch, not by a test

Reviewing H after it merged turned up four things the green suite had not caught. All four are now
fixed and pinned by `HTTPFailureReasonTest`.

1. **A refused request said `"Could not connect"`.** `PrivateAddresses.check` and the per-hop
   `checkURL` raise `LuaException`, which fell into the generic `catch (Exception)` in the worker:
   the reason was dropped, `HTTPAPI` substituted its fallback text, and every blocked request wrote
   a stack trace at ERROR level. A program could not tell "the server refused this address" from
   "the server is down", and a script could spam the log by asking twice. There is now a
   `catch (LuaException e)` that stores the message as the failure reason and logs nothing.

2. **The body was dropped for every verb except POST.** Writing the body only when
   `"POST".equals(method)` was my own regression from adding the redirect loop — the original code
   sent the body whenever one was supplied. `PUT` and `PATCH` with a body silently became
   bodiless requests. A `sendBody` flag now tracks it explicitly, and only a 303 (which switches
   to GET) clears it.

3. **`PATCH` was advertised but never worked.** `HTTPURLConnection`'s method whitelist predates
   PATCH and throws `ProtocolException`; the call surfaced as an IO failure. It now reports
   `Unsupported HTTP method: PATCH`. This is a real parity gap with CC:Tweaked, which uses an
   async HTTP client, and the honest options are a clear error or replacing the client — the clear
   error is what shipped, and the in-game drill now says so.

4. **An all-digit host was treated as a name.** `isLiteralShape` required a dot, so `http://1234/`
   (which `InetAddress` reads as `0.0.4.210`) went to DNS. It now treats any digits-and-dots host as
   a literal.

---

## Batch I — Editor / REPL / Help (part 1)

Batch I is the largest in the audit (1–2 weeks of pure Lua). Three of its four items are in; the
fourth, lexer-based syntax highlighting in `edit`, is still outstanding and is described at the end.

### I1 — `help` markdown rendering, sections and menu

Ported from upstream with one substantive change.

The renderer styles headings, bullet lists and inline code by building matching
foreground/background colour strings and drawing with `term.blit`, so the paragraph wrapping and the
colour strings stay aligned. A one-line menu bar shows `Help: <topic> (current section)` and the
keyboard moves through the document: up/down, page up/down, home/end, and **left/right to jump
between sections**. `term_resize` re-wraps at the new width and re-clamps the offset, and
`mouse_scroll` works.

The change: **upstream decides between the markdown and plain renderers by file extension**
(`.md`). Its build turns the documentation into `.md` topics; the topics that ship *here* are
extensionless plain text, and only some use headings at all (`speaker` does, `intro` does not). Ported
verbatim, the markdown renderer would have been unreachable — dead code that looks finished. The
choice now also inspects the content: a `.md` name *or* any heading line selects the rich renderer.

`cc.internal.event.discard_char()` was added as a dependency (MPL-2.0, recorded in `NOTICE`), so
"press Q to exit" does not leave its `char` event to land on the shell prompt as a stray command.

### I2 — `lua` REPL

* Results are pretty-printed through `cc.pretty`, honouring `lua.function_args` and
  `lua.function_source`, instead of one-line `textutils.serialise`.
* `require` is rooted at the working directory (`cc.require.make`), so `require "mylib"` finds the
  file being looked at rather than `/rom/programs`.
* `lua.autocomplete` gates completion, and `:` was added to the completion pattern so a dot-chain
  completes as a whole.
* **Multi-line input**: while a chunk cannot compile and the error mentions `<eof>`, the REPL shows a
  `   > ` continuation prompt, so `function f()` … `end` spans lines instead of failing.
* Runtime errors go through `cc.internal.exception`, so they carry their source line.

`cc.internal.syntax.parse_repl` is deliberately **not** used for the continuation test: it returns
"parsed successfully" and prints errors as a side effect, so it cannot answer "is this unfinished".
Checking for `<eof>` in the compiler error is the honest signal.

### I3 — `edit.default_extension` / `edit.autocomplete`

* A new path with no extension gets `edit.default_extension` (e.g. `lua`); an existing file is never
  renamed.
* `edit.autocomplete` (default on) gates completion, and `:` joins the completion pattern.

### Still outstanding — `edit` syntax highlighting

Not done. `edit` still colours by simple per-line heuristics, and has no notion of lexical state
across lines, so a multi-line string or `--[[ ]]` comment is mis-coloured on every line after the
first. Doing this properly means wiring `cc.internal.syntax` (lexer + parser, both already present)
into the editor: tokenising each line with the state left by the previous one, keeping the theme's
colour table, and re-tokenising only from the edited line. That is a substantial change to a 780-line
program and deserves its own pass rather than being rushed at the end of this one.

### Verification

`docs/ingame-tests/batch-i/i1_editor_repl_help.lua` — 24 checks over the parts that need a real
shell: help topics and lookup, a markdown topic actually containing headings, the `lua` settings and
`cc.pretty`, relative `require` through a directory-rooted package, and both `edit` settings. It
deliberately does **not** drive `multishell`, because that needs event pumping the headless harness
cannot do; the interactive rendering is in the script's manual drill.

Full suite: **1646 tests, 0 failures**.

### Batch I part 2 — `edit` syntax highlighting

`edit` now colours with the real lexer (`cc.internal.syntax.lexer.lex_one`) instead of per-line
pattern matching, and carries the lexer's **continuation state** from line to line. That is what
makes a `[[` long string or a `--[[ ]]` long comment stay coloured on every line it covers: each line
is lexed starting from the state the previous one left behind.

* `tLineLexStates` holds the state each line starts in (`false` = nothing outstanding). It is
  initialised on load, kept aligned when lines are inserted or removed, and reset on `load()`.
* `redrawLines(line, endLine)` draws from `line`, then keeps going while the state handed to the
  next line differs from the one recorded for it — so opening or closing a long comment on the
  edited line re-colours exactly the lines that changed, and stops at the bottom of the screen.
* `redrawText()` seeds the state for lines above the viewport once, because the first visible line
  can begin part-way through a long string.
* Token colours come from a table keyed by token id (strings red, comments green, numbers magenta,
  keywords the theme's keyword colour), with grey fallbacks on non-colour terminals.
* The old regex highlighter and its `tKeywords` table are gone.

The keyword list is written out explicitly rather than derived from `tokens`, because `tokens`
contains non-keyword entries and a derived list would colour identifiers.

### Bugs found by the new ROM compile test

`EditorHighlightingTest` compiles every ROM program, API and module. It immediately caught two
things that no test had covered, both introduced by the editor work above:

1. **`rom/programs/edit` did not compile**: a doc comment mentioned `` `--[[ ]]` `` *inside* a
   `--[[- ... ]]` block, and the inner `]]` closed the outer comment. Lua calls this "nesting of
   [[...]] is deprecated".
2. **`rom/programs/lua` did not compile**: the continuation join was written as
   `input .. "<newline>" .. more`, a real newline inside a string literal, instead of `"\n"`. The
   REPL would have failed to load at all.

Neither would have been caught by the in-game script (which cannot drive the editor) nor by any
existing test, because nothing compiled the ROM. That test stays.

### A date-dependent test, found by accident

The suite failed on 31 October: `motd` prints a fixed message on 1 January, 24 December,
**31 October** and 28 April, ignoring `motd.path`, so `d1_programs`' two motd assertions failed.
The script now checks the calendar message on those days and runs the file-based checks otherwise.

### Verification

* `EditorHighlightingTest` — 7 tests: every ROM Lua file compiles (with the compiler's message when
  it does not), the continuation protocol round-trips for long strings and long comments, a closed
  long comment/string leaves no state, a *short* string leaves no state across lines (Lua's real
  behaviour — short strings end at the newline), and the lexer/parser expose the names `edit` uses.
* Lexer modules need `cc.pretty` and `cc.internal.syntax.errors` loaded too, and that error table
  reads `colours` at load time, so the test prelude stubs `colours`/`term`. A missing module
  otherwise shows up as "never reached _capture", which is why the harness now wraps the body in
  `pcall` and captures the error.

Full suite: **1653 tests, 0 failures**.

Batch I is now complete against the audit's list.

### Batch I part 2 follow-up — two more bugs in my own highlighting

Re-reading the highlighting turned up two defects in what I had just written:

1. **Seeding the state for scrolled-off lines drew them.** `redrawText` walked lines `1..scrollY` to
   work out what state the first visible line starts in, by calling the drawing function — so those
   lines were written at the cursor position and corrupted the visible screen whenever the file was
   scrolled and a fresh state was needed. The lexer walk is now split out as `lexLine(nLine, draw)`,
   and seeding passes `draw = false`.
2. **The scrolled-past branch of `redrawLines` did nothing.** It incremented the line counter and
   claimed to update the state below, but never computed anything, so a change above the viewport
   could leave the first visible line coloured with a stale state. It now jumps to the first visible
   line and forces one full pass from there.

Both are the kind of thing that only shows up when a file is scrolled and a long comment is edited —
i.e. exactly the case the highlighting exists for, and exactly what a green test suite would not
notice. `aBlankLineInsideALongCommentKeepsItsState` now pins the related behaviour (an empty line
inside `--[[ ]]` must keep the state, or the comment stops being coloured from the first blank line).

### Batch I final double-check

Re-reading the finished highlighting found one more defect, and the in-game pack was split to match
what can actually be asserted.

**Bug: the state for the next line was only recorded when a previous value existed.** The condition
was `if sPrevious ~= nil and not sameState(...)`, so a line drawn for the first time never cached its
state — and the *next* time it was drawn it lexed from scratch, losing the comment or string it was
inside. It now always records, and only continues the downward redraw when a state that was already
known has moved.

**Coverage split.** CraftOS offers no way to read a character's colour back, so no script can assert
that a line was coloured. `i2_highlighting` therefore automates the thing that *determines* the
colours — the per-line token sequence from `cc.internal.syntax`, including the multi-line state and
the blank-line-inside-a-comment case — and stages the twelve visual checks (colours, section menu,
scrolling, continuation prompt). `docs/ingame-tests/batch-i/README.md` records exactly which check
lives where, so "the tests pass" is never mistaken for "the colours are right".

Full suite: **1654 tests, 0 failures.**

### PageUp / PageDown on the GTNH client — outside this repository

Reported in game: PageUp/PageDown do nothing in `edit`, while the wheel and Up/Down work.
`keyprobe_manual.lua` (added for this) shows they produce **no key event at all** — not an unknown
code, nothing — while Up, Down, Home and End arrive normally.

What that rules out, and in:

* `edit` handles them: `elseif param == keys.pageUp` / `keys.pageDown`.
* The ROM expects the LWJGL2 codes (`rom/apis/keys`: `pageUp` = 201, `pageDown` = 209).
* Nothing in this mod translates or filters key codes: no reference to `pageUp`/`KEY_PRIOR` anywhere
  in `src/main/java`, `WidgetTerminal.keyTyped` forwards every `key > 0`, and `GuiComputer` extends
  `GuiContainer` without intercepting them.

The client runs **lwjgl3ify 3.0.33** (confirmed from the launch classpath in
`run/client/logs/fml-client-latest.log`), whose input path is SDL-based
(`Keyboard.addSdlKeyEvent`) with LWJGL2 codes reconstructed by name. The key never reaches the game,
so it is lost in that layer, which is not part of this repository.

Consequences: page-scrolling in `edit` works on a Java 8/LWJGL2 client and not on this one; the wheel
and Up/Down paths are unaffected, and the scrolling check in `i2_highlighting` now uses the wheel —
which is also the path that exercises the state-seeding code, so nothing is unverified because of
this. Recorded in `docs/ingame-tests/batch-i/README.md` so it is not rediscovered as a mod bug.

---

## Batch K — Admin command

New `dan200.computercraft.shared.command.CommandComputerCraft`, registered from the existing
`FMLServerStartingEvent` handler. Three of the audit's four subcommands; the fourth is deliberately
not ported, with the reason recorded rather than faked.

| Subcommand | Behaviour |
|---|---|
| `dump` | every loaded computer, nearest-first for a player; instance id, computer id, on/off, label, position |
| `dump <instanceID>` | one computer's detail, including its peripherals by side |
| `turn-on <instanceID...>` | powers computers on, reporting how many actually changed state |
| `shutdown <instanceID...>` | the same, the other way |

Op-only (`getRequiredPermissionLevel()` = 2): every one of these manipulates somebody else's
computer.

Computers are addressed by **instance id** (the block's identity, stable across reboots), not by the
computer id a program can change. Both are printed by `dump` so the two are never confused — that is
the mistake this command exists to make impossible.

### Not ported, on purpose

* **`track`** — upstream's is built on Micrometer metrics and per-task aggregation, and 1.7.10 has no
  equivalent. A version that printed invented numbers would be worse than no command at all. The
  runtime does keep per-lane start times in `ComputerThread`, so a "which computers are busy, and for
  how long" substitute is possible as a separate item — but it would be a new feature, not parity.
* **`computer-folder`** — 1.20 only (opens a desktop folder).
* **`tp`, `view`, `queue`** — depend on 1.20's command-computer queue and spectator view.

### Testability, and one design change it forced

The command works entirely off `ComputerCraft.serverComputerRegistry`, and `ServerComputer` can be
mocked, so the whole surface is unit-testable: **28 tests** in `CommandComputerCraftTest` cover
metadata, argument parsing, the listing, the detail block, peripheral naming, the power actions and
the ordering.

One thing resisted testing: `World.provider` is a **final field** in 1.7.10, so a mocked world cannot
report a dimension name, which left the position formatting unreachable. Rather than reflect into a
final field, the formatting was split out as a pure
`describePosition(String dimension, ChunkCoordinates position)` — the caller supplies the name. That
is better code and it is now covered directly (`"10, 64, -3 in The Nether"`, and `?` when the name is
unknown).

### Verification

* `CommandComputerCraftTest` — 28 tests.
* `docs/ingame-tests/batch-k/k1_admin_command.lua` — the parts a unit test cannot reach: that the
  command is *registered* at all, that it runs through `commands.exec` on a real server, and a
  readable record of the output. It reads its own instance id out of `dump` rather than hard-coding
  one, and checks the error paths (`banana`, `999999`, `explode`, no subcommand).

Full suite: **1682 tests, 0 failures**.
