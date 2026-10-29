# Batch B — in-game validation pack (`io`)

The `io` library is almost entirely testable headlessly, so this pack is smaller than Batch A's.
`b1_io.lua` runs automatically in CI; `b2_io_manual.lua` needs a keyboard.

## Install

```bat
copy docs\ingame-tests\batch-b\*.lua "run\client\saves\<World>\computer\<id>\"
```

Then on that computer:

| File | What it proves | Needs |
|---|---|---|
| `b1_io.lua` | API surface, read formats (`l`/`L`/`a`/`n`/counts/multi), `io.lines` autoclose, `seek`, `io.output`/`io.input` redirection, the `wb`-write fix, `stderr`, closed-handle errors. Cleans up after itself. | — |
| `b2_io_manual.lua` | Typed input through `io.stdin:read()`, `io.read()`, `stdin:lines()`, `term.redirect` interaction, `paintutils.loadImage` on a bad file. | Keyboard |

Both append their summary to `/batch-b-results.txt`, so several runs can be reviewed at once with
`type /batch-b-results.txt`.

## Still needs eyes on a real client

1. **Typing.** CI injects synthetic `char`/`key` events. Run `b2_io_manual.lua` and answer the
   prompts with a real keyboard.
2. **`edit /batchb-manual.txt`** — write two lines, then save and exit. This build has no
   Ctrl+S chord: press **Ctrl once** to open the menu, choose **Save**, press Ctrl once more and
   choose **Exit**. Then `type /batchb-manual.txt` and check both lines print. `edit` reads with
   `io.open(...):read()` in a loop, and writes through `io.open(path, "w")`.
3. **`help intro`** — must page normally; it is the other bundled `io` reader.
4. **`paint`** — `paintutils.loadImage` uses `io.open(...):read("*a")`. Load a real `.nfp` image.
5. **A monitor** — `monitor right`, then `io.write("hi")` in the monitor's shell. Output must
   follow the redirect rather than appearing on the computer.
6. **A disk drive** (optional) — copy a text file to a floppy and read it with `io.lines`. hdd and
   rom are covered in CI; a disk is the remaining mount type.

## Known, accepted limitations

- `io.stdin:read("*a")` returns `nil` (the standard streams have no `readAll`). Same as
  CC:Tweaked.
- `io.open(path, "q")` raises instead of returning `nil, err`. Same as CC:Tweaked, but not Lua 5.1.
- `fs.open(path, "w"):write(1)` writes `"1.0"`, because this codebase's text handles stringify a
  Java double. `io.write`/`file:write` are unaffected (they convert first). Pre-existing, tracked
  separately from Batch B.
