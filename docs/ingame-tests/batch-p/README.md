# Batch P — ROM parity fixes

The fixes for the gaps recorded in [`docs/ROM_PARITY_GAPS.md`](../../ROM_PARITY_GAPS.md): the three
dead settings, the missing menu module, `arg[0]`, `rename`'s validation, `id`'s audio branch, and the
editor's quality-of-life deltas.

## Run

```bat
copy docs\ingame-tests\batch-p\*.lua "run\client\saves\<World>\computer\<id>\"
```

| Script | What it does |
|---|---|
| `p1_rom_parity` | Automated: the settings exist with the right defaults, `rename` refuses every case it should, `arg[0]` and `shell.execute`'s validation, the tab title, and `id` on whatever disk is present. Appends to `/batch-p-results.txt`. |
| `p2_editor_manual` | **Manual.** Skipped by CI. Sets up two fixture files and prints a 21-step checklist; every step needs a real mouse, a real keyboard or human eyes. |

`p2_editor_manual` is deliberately not automated: the harness can send key code 156, but whether a
real client *delivers* it for numpad Enter is exactly the kind of thing that has been wrong before
(`PageUp`/`PageDown` never arrive on this platform at all — see batch I).

## What the JVM tests already cover

Do not repeat these by hand unless the drill is the only way to see them.

| Check | Covered by |
|---|---|
| The editor's Tab = 4 spaces, backspace dedent, numpad Enter, caret movement | `EditProgramInputTest` (real BIOS, simulated keys) |
| Clicking the footer opens the menu; clicking "Save" saves | `EditProgramInputTest` |
| A read-only mount shows "File is read only"; a save reports success | `EditProgramInputTest` |
| `paint.default_extension`, including an existing extension and a changed setting | `PaintProgramInputTest` |
| `paint`'s footer click opens the menu | `PaintProgramInputTest` |
| `bios.use_multishell` picks the shell; `lua.warn_against_use_of_local` fires and can be turned off | `BiosShellSelectionTest` (real BIOS) |
| Tab title drops `.lua`; an alias keeps its name in `arg[0]`; `shell.execute` type checks | `ShellAPITest` + `BiosShellSelectionTest` |
| Menu module: keyboard, letters, mouse, numpad Enter, Ctrl/Alt cancel | `CcInternalMenuTest` |
| `rename`'s five guards | `RenameProgramTest` |
| `id`'s audio and non-disk branches | `IdProgramTest` |
| Every ROM program and module compiles | `RomProgramCompileTest` (walks the ROM) |

## What only a human can check

Grouped by what a script cannot see.

### 1. Keys that may never reach the ROM

* **Numpad Enter.** The harness proves the ROM handles key 156; it cannot prove the client produces
  it. Press the keypad Enter in `edit` and expect a newline. If nothing happens, check
  Options → Controls: if the game cannot see the key either, it is the platform, as with
  PageUp/PageDown.
* **Ctrl, Alt, mouse.** `edit` and `paint` open the menu on Ctrl (and Alt in `edit`). A real client
  is the only place the modifier reaches the ROM unfiltered.

### 2. Colour

CraftOS cannot read a character's colour back, so no script can assert these:

* A failed save and "File is read only" must be **red**; a successful save and the hint are yellow.
* The REPL's `local` warning is yellow.
* In `paint`, "Access denied" is red.

### 3. Timing and redraw paths

* **`term_resize` while the menu is open.** Drag the window size around in both `edit` and `paint`
  with the menu open: the menu must be redrawn in the right place and the buffer reflowed.
* **Scrolling a long file with the wheel**, then opening the menu: the footer must not be left
  half-drawn.

### 4. Real peripherals

* **A printer.** `edit`'s Print item: pages, page titles, and the "out of ink/paper" messages. The
  menu must be off the screen while the printer is redirected — this is the path where a missing
  `term.setCursorBlink` would error, so it is worth doing with the menu open on purpose.
* **A disk drive.** `id` on an audio disc (expect `Has audio track "..."`), on a data disk
  (`The disk is #N`), and on an empty drive (`No disk in drive`). `rename /disk /x` must say
  `Can't rename mounts` and leave the drive usable.
* **A full disk.** Fill the computer until `edit` reports `Disk is low on space` on open.

### 5. Things that need a reboot

* `settings.set('bios.use_multishell', false)` + reboot: no tab bar, `bg`/`fg` report
  `No such program`, and the shell prompt still works.
* Set it back to `true` + reboot: the tab bar returns.
* `settings.set('lua.warn_against_use_of_local', false)` + reboot: no warning in `lua`.

### 6. Visual identity of the tab bar

* `bg hello.lua` must label the tab **`hello`**, not `hello.lua`. The suffix-stripping is asserted
  in `ShellAPITest`, but a client can be checked in five seconds and the tab bar is what users see.
