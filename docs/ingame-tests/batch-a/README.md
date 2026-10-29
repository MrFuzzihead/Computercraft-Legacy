# Batch A — in-game validation pack

Automated tests cover the code paths; these cover what needs a real client, a real
keyboard, or a server restart. Everything here is self-checking where possible and
prints a `RESULT BATCH-A-<n> ...` summary last, so a scrolled-off failure still shows up.

## Install

Copy the `.lua` files into the computer's save directory. For the dev instance that is:

```
run/client/saves/<WorldName>/computer/<computerId>/
```

e.g. on Windows, from the repo root:

```bat
copy docs\ingame-tests\batch-a\*.lua "run\client\saves\Test World\computer\0\"
```

Then, on that computer in game, run each file by name (no `.lua`):

```
a4_print
a5_settings
a6_strict_globals
a8_json
a9_fs_complete
a3_multishell
```

Each prints `RESULT ... passed N failed M skipped S` and then a `FAIL` line per failure.
Every script also **appends** the same summary to `/batch-a-results.txt`, so after running
several you can review them all at once with `type /batch-a-results.txt`. That log is the
reliable record: the manual drills printed after the summary can scroll a 19-row screen.

Send me the `RESULT`/`FAIL` block of anything that does not come out clean.

## What each file does

| File | Item | What it proves | Needs |
|---|---|---|---|
| `a3_multishell.lua` | 3 | Tab creation/count/title/focus, then opens one real shell tab for the drill. Skips cleanly without multishell. | Colour computer |
| `a4_print.lua` | 4 | `print` emits `a\tb` and keeps nil holes; `write` coerces numbers; `printError` restores colour; a tab is exactly **one column** wide. | — |
| `a5_settings.lua` | 5 | Every BIOS-registered default, `getNames`, type enforcement, and persistence across a reboot (uses a throwaway `batcha.test` key, cleaned up after). | Two runs, one reboot |
| `a6_strict_globals.lua` | 6 | `bios.strict_globals` blocks new globals via `os.run`, still allows reads, and is restored before exit. | — |
| `a7_cc_default_settings.md` | 7 | Server-side `cc_default_settings` applies to new computers, including `nil` unsetting. | Server restart |
| `a8_json.lua` | 8 | JSON escape output, code-point vs byte escaping, round-trips, malformed UTF-8, `parse_null`/`parse_empty_array`, SNBT (`nbt_style`) parsing. | — |
| `a9_fs_complete.lua` | 9 | `include_hidden` behaviour for table and boolean arguments, `include_dirs`, prefix filtering; then a Tab-completion drill. | — |

## Items that cannot be scripted

### 3 — multishell

`a3_multishell` checks what can be scripted (tab creation via `shell.openTab`, tab count, title,
focus switching) and then opens **one extra shell tab on purpose** for the manual drill. Run the
drill, then close it with a held Ctrl+T.

The extra tab is a real shell, so it echoes what you type. An earlier version of this script left
a background program that only slept, which looked identical to a broken computer: typing did
nothing and the only way out was Ctrl+T. `BiosMultishellInputTest` now asserts that an opened tab
actually runs typed commands, so that mistake cannot recur.

**There are no keyboard shortcuts for tabs in this build, and there never were.** Verified
against the source:

| Expected | Actual |
|---|---|
| Ctrl+T opens a tab | **No.** `WidgetTerminal.update()` uses Ctrl+T as *terminate* (hold ~1s). |
| Ctrl+W closes a tab | **No handler at all.** |
| Ctrl+Left/Right switches tabs | **No handler.** `multishell` only switches on `mouse_click`. |
| Clicking a tab switches | **Yes.** |
| `bg` / `fg` open tabs | **Yes** — they call `shell.openTab` / `shell.switchTab`. |

CC:Tweaked 1.120.2 is the same: its `TerminalWidget` still reserves Ctrl+T/S/R, and its
`multishell.lua` also only handles `mouse_click`. So this is not a regression and not a
missing feature — the shortcut just does not exist in either version.

Two things that look like bugs but are not:

- **A tab you cannot type in.** If the tab is running a program that never calls `os.pullEvent`
  (a `while true do sleep(1) end` loop, for example) it ignores input entirely and Ctrl+T is the
  only way out. That is the program's behaviour, not multishell's. `a3_multishell` opens a real
  shell for this reason.
- **`SKIP getFocus before the menu is first selected`.** The shell's process is resumed inside
  `launchProcess()` before `selectProcess()` runs, so `getFocus()` is `nil` for anything run from
  `/startup`. Run `a3_multishell` from the prompt and it asserts the real value.

Note also that `multishell.launch(env, path)` exposes only what you put in `env`: `launch({},
"/prog")` leaves `multishell` nil inside the tab, so `multishell.getCurrent()` reads as nil. Pass
`{ multishell = multishell }`, or use `shell.openTab`/`bg`/`fg`, which pass the shell env for you.

Opening and closing tabs manually: `bg shell` opens one, `fg shell` opens and switches to one, and
a **held** Ctrl+T inside a tab terminates its program (which ends the tab).

### 5 — the reboot

`a5_settings` is deliberately two-phase. The first run writes `batcha.test = 42`,
saves, and tells you to reboot. After the reboot, run it again: it asserts the value
survived and then removes it. Do not skip the second run, or the key stays in
`.settings` (harmless, but untidy).

### 7 — `cc_default_settings` (server config)

This one lives in `config/ComputerCraft.cfg` on the server, so it needs a restart.

1. Stop the server. Edit `config/ComputerCraft.cfg` and find `cc_default_settings`.
2. Set it to:

   ```
   cc_default_settings=shell.autocomplete=false,edit.default_extension=txt,cc_test.number=7
   ```

3. Start the server and place a **new** computer (a fresh ID, not an existing one).
4. On that computer run:

   ```lua
   print(settings.get("shell.autocomplete"))      -- false  (boolean)
   print(settings.get("edit.default_extension"))  -- txt    (string)
   print(settings.get("cc_test.number"))          -- 7      (number)
   ```

   The diagnostic to watch for: `shell.autocomplete` must print `false`, **not** `"false"`.
   A quoted value means the parser regressed to storing raw strings.
5. Now check `nil` unsets rather than storing `"nil"`:

   ```
   cc_default_settings=bios.strict_globals=nil
   ```

   Restart, place a new computer, and run `print(settings.get("bios.strict_globals"))`.
   It must print `false` (the registered default). `nil` as a *string* is the failure.
6. Optionally check the table fallback still works (single, comma-free literal):

   ```
   cc_default_settings=cc_test.table={a=1}
   ```

   On a new computer: `local t = settings.get("cc_test.table") print(type(t), t and t.a)`
   must print `table  1`. Note that entries are split on `,` first, so multi-entry table
   literals were never supported and still are not — that limitation is inherited, not a
   regression.
7. Tidy up: restore `cc_default_settings=` (empty), restart, and delete the throwaway
   computers.

Also worth a look while you are there: put a deliberately invalid value in the config,
e.g. `cc_default_settings=motd.path=true`, restart, and place a computer. It must **boot
normally** with the default `motd.path`. A computer that fails to start is the failure
case this guard exists to prevent.

## Optional extras worth doing once

- **`.settings` round-trip by hand.** `settings.set("edit.default_extension","txt")`,
  `settings.save()`, `reboot`, then `print(settings.get("edit.default_extension"))` → `txt`.
  `settings.unset` + save + reboot returns `lua`.
- **`print` on a monitor.** Run `monitor right` (or whatever side) and print a
  multi-argument line; the tab must be one column there too, and `blit` output must be
  unaffected.
- **Two computers, one disk.** Batch A does not touch disk handling, but `os.loadAPI`
  now strips a `.lua` suffix, so if you have a disk with `myapi.lua` on it,
  `os.loadAPI("/disk/myapi.lua")` should expose `myapi`, not `myapi.lua`.
