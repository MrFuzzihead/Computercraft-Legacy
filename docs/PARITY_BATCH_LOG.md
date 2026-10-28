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

Full suite: **1488 tests, 0 failures**, including `spotlessCheck`, `checkstyleMain` and
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

**10 — Multishell smoke test.** With the above changes in place, open a second tab (`Ctrl+T`),
run something in each, and verify `Ctrl+T`/`Ctrl+W` and program switching still work — Batch A
touches `print`, `write`, `os.run` and the program env chain, all of which multishell depends on.
