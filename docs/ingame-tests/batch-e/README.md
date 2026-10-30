# Batch E — in-game validation pack

Terminal/API polish: packed palette colours, case-insensitive `blit`, `colours.toBlit`
validation, `paintutils.drawLine` arguments.

## Install and run

```bat
copy docs\ingame-tests\batch-e\*.lua "run\client\saves\<World>\computer\<id>\"
```

Then in the shell: **`e1_terminal`**. It also runs in CI via the auto-discovering runner.

It ends with the verdict as the last line (`RESULT BATCH-E terminal: passed N failed M`), plus any
`FAIL`/`SKIP` lines immediately above it, so nothing needs scrolling. Results are also appended to
`/batch-e-results.txt`.

## What it checks

- `term.setPaletteColour(colour, 0xRRGGBB)` (the packed form) — red/green/blue land correctly
- the three-channel form still works, and `colours.packRGB` feeds the packed form directly
- `term.blit` accepts uppercase hex, and still accepts lowercase
- `colours.toBlit` maps the palette, rejects `0` / `65536` / non-numbers, floors in-between values
- `paintutils.drawLine` rejects non-numeric arguments, still draws, and no longer leaves
  `maxX`/`minY`/`maxY` in the global table

## Still needs eyes

1. `term.setPaletteColour(colours.red, 0xFF8000)` — red should turn orange immediately, and
   `clear palette` should restore it.
2. `term.blit('hi', 'FF', '00')` and `term.blit('hi', 'ff', '00')` — both draw, neither errors.
3. `paintutils.drawLine(1, 1, 10, 10, colours.blue)` — a clean diagonal, and `print(maxX)` still
   prints `nil`.
4. On a monitor: `monitor right`, then in that shell the same three checks — the monitor forwards
   palette calls and validates `blit` colours the same way.

## Notes

`Terminal`'s cursor coordinates are 0-based internally while the Lua API is 1-based; the Java tests
account for that. `colors.toBlit` now matches upstream by rejecting values outside `1..0xffff`
instead of guessing, which is a behaviour change for programs that passed nonsense.
