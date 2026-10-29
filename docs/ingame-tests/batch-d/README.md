# Batch D — in-game validation pack

Small ROM programs and option additions. Everything here runs unattended; `d1_programs.lua` is
also executed in CI by the auto-discovering `IngameTestScriptsTest`.

## Install

```bat
copy docs\ingame-tests\batch-d\*.lua "run\client\saves\<World>\computer\<id>\"
```

| File | Covers |
|---|---|
| `d1_programs.lua` | `about` (version + host), `motd` (single line, several lines, missing file), `peripherals`, `set` (list / show / set / unset / wrong type), `list.show_hidden`, `clear` (screen / palette / all / usage), and `monitor scale` (skipped when no monitor is attached) |

It appends to `/batch-d-results.txt`. Where a monitor is required it reports `SKIP` rather than
failing, so the same script is meaningful on any computer.

## Still needs eyes

1. **`about`** — `about` should print something like `CraftOS 1.9 on ComputerCraft`.
2. **`set`** with no arguments pages through every setting; check it does not run off the screen.
3. **`set motd.enable false`** then reboot: no message of the day. Turn it back on and reboot:
   one line from `/rom/motd.txt` (or `/motd.txt`).
4. **`clear palette`** after `term.setPaletteColour(colours.red, 0.5, 0.25, 0.125)` — red returns
   to its normal shade. `clear all` also clears the screen.
5. **`monitor scale <name> 2`** with a monitor attached — the text doubles. `monitor scale <name> 9`
   should be rejected.
6. **`list`** with a dot-file present, then `set list.show_hidden true` and `list` again.

## Notes on this batch

- `rom/motd.txt` is **not** upstream's file: the CC:Tweaked one advertises tweaked.cc and
  forums.computercraft.cc, and mentions drag-and-drop upload (Batch J, unavailable on vanilla
  LWJGL2). The replacement lists tips that are true of this mod.
- `about` and `motd` are MPL-2.0 upstream, so they keep their SPDX headers and are listed in
  `NOTICE`; the rest of the ROM programs remain CCPL.
