# Batch F — in-game validation pack

Item detail fields, per-item `getItemLimit`, and GTNHLib named block states.

## Why this is the layer that matters

`NBTHashTest` and `BlockStateBridgeTest` cover the pure logic (hash determinism, key-order
independence, type distinction, metadata fallback), but the item fields themselves resolve names
through Minecraft's registries, which a bare JVM test does not have. So this script is the only
place the real fields are checked.

## Run

```bat
copy docs\ingame-tests\batch-f\*.lua "run\client\saves\<World>\computer\<id>\"
copy docs\ingame-tests\readlog_manual.lua "run\client\saves\<World>\computer\<id>\"
```

Then type **`f1_item_detail`** at the shell.

It adapts to whatever the computer is:

| Present | What runs | Otherwise |
|---|---|---|
| An **inventory peripheral** (`peripheral.find("inventory")`) | item detail and `getItemLimit` | SKIP, with a reason |
| A **turtle** | `turtle.getItemDetail` (basic *and* the `(1, true)` form) plus `inspectDown` | SKIP |
| A **command computer** | `commands.getBlockInfo` state | SKIP |

A *vanilla* chest is **not** an inventory peripheral, so a plain computer with just a chest will
report only skips. That is expected, not a failure. Run it on a turtle with something in slot 1, or
next to a modded inventory.

Each group of checks runs inside `pcall`, so a crash becomes a `FAIL <group> crashed [...]` line and
the log is still written — an earlier version could die before writing anything.

## Reading the results

Every check is appended to `/batch-f-results.txt`, not just the failures:

```
readlog_manual                        -- pages /batch-f-results.txt a screen at a time
readlog_manual /batch-d-results.txt   -- any other batch's log
edit /batch-f-results.txt             -- opens it; arrow keys scroll
type /batch-f-results.txt             -- dumps it (scrolls past quickly)
```

The log holds the `RESULT` block followed by one line per check (`OK` / `FAIL` / `SKIP`), so a run
can be reviewed line by line afterwards.

## Manual checks the script cannot make

1. **`getItemDetail(slot, true)`** — the second argument is required for the full shape. Upstream
   defaults `detailed` to **false**, so `turtle.getItemDetail(1)` returns only `name`/`count`/`damage`
   (and the nbt hash). The script checks both forms.
2. **`displayName`** — name a sword on an anvil, put it in slot 1, and confirm the custom name.
3. **`potionEffects`** — a speed potion should report `name = "moveSpeed"`, a `displayName`, and a
   `duration` in seconds.
4. **`lore`** — an item with anvil lore should list the lines as plain strings (1.7.10 has no JSON
   text components, so this is the raw text).
5. **`unbreakable`** — creative-only items should set `unbreakable = true`.
6. **`nbt` hash stability** — two identical stacks must share a hash; rename one and it must change.
7. **GTNHLib** — with GTNHLib installed, `turtle.inspectDown().state` should carry named properties
   (e.g. `facing`, `half`) instead of only `metadata`. The script prints
   `GTNHLib named properties present: ...` when it sees them, and otherwise asserts the
   `state.metadata` fallback, so either outcome produces a definite line in the log.

## Notes

- `nbt` hashes are stable but **not byte-identical to a 1.20 server's**: 1.7.10 cannot serialise NBT
  canonically (`NBTBase.write` is package-private, `NBTTagList` has no index accessor), so the
  digest is taken over a canonical text encoding instead. Same NBT still means same hash.
- `durability` is reconstructed from the damage ratio because 1.7.10 has no `getBarWidth`.
- `mapColor` is only present for block items.
- `itemGroups` is always an empty table (deprecated upstream, kept for shape).
- `enchantments` moved into the shared builder, so `inventory.getItemDetail` now reports them too.
