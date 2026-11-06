# In-game test packs (Batches A–O)

Every script here is self-checking: it prints a `RESULT ...` block and, on a real terminal, repeats
the verdict as the **last line on screen** (CraftOS has no scrollback). Scripts whose name contains
`manual` are skipped by the CI runner, which executes all the others headlessly.

## Running them

```bat
copy docs\ingame-tests\batch-*\*.lua "run\client\saves\<World>\computer\<id>\"
copy docs\ingame-tests\readlog_manual.lua "run\client\saves\<World>\computer\<id>\"
```

Then run each by name (no `.lua`), e.g. `a4_print`, `d1_programs`, `f1_item_detail`.

## Reading results afterwards

Each run *appends* to a per-batch log — `/batch-a-results.txt`, `/batch-b-results.txt`, … — which
now holds **every check**, not just the failures:

```
    readlog_manual /batch-f-results.txt     -- pages it a screen at a time
    edit /batch-f-results.txt               -- opens it; arrows scroll
    type /batch-f-results.txt               -- dumps it (scrolls past fast)
```

`readlog_manual` with no argument reads `/batch-f-results.txt`.

## What needs a human

Each batch's `README.md` lists the items the scripts cannot assert — custom item names, audible
speaker output, GTNHLib block properties, `/startup` across a reboot, and so on.
