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
```

Needs **a turtle** (for `turtle.getItemDetail`/`inspect`) and benefits from a **chest** above it.
`commands.getBlockInfo` and the stamina of the checks are skipped on machines without them.

Put a stack of stone in turtle slot 1 before starting; slots 2+ are used opportunistically.

Every check is written to `/batch-f-results.txt`, not just the failures, so a run can be read in
full afterwards:

    readlog_manual                     -- pages /batch-f-results.txt a screen at a time
    readlog_manual /batch-d-results.txt
    edit /batch-f-results.txt          -- scrolls with the arrow keys

Copy `docsingame-testseadlog_manual.lua` alongside the batch scripts.

The verdict is the last line on screen (`RESULT BATCH-F item detail: passed N failed M`), with any
`FAIL`/`SKIP` lines above it. Results also land in `/batch-f-results.txt`.

## Manual checks the script cannot make

0. **`getItemDetail(slot, true)`** — the second argument is required for the full shape. Upstream
   defaults `detailed` to **false**, so `turtle.getItemDetail(1)` returns only name/count/damage
   (and the nbt hash). The script checks both forms.
1. **`displayName`** — name a sword on an anvil, put it in slot 1, and confirm the custom name.
2. **`potionEffects`** — a speed potion should report `name = "moveSpeed"`, a `displayName`, and a
   `duration` in seconds.
3. **`lore`** — an item with anvil lore should list the lines as plain strings (1.7.10 has no JSON
   text components, so this is the raw text).
4. **`unbreakable`** — creative-only items should set `unbreakable = true`.
5. **`nbt` hash stability** — two identical stacks must share a hash; rename one and it must change.
6. **GTNHLib** — with GTNHLib installed, `turtle.inspectDown().state` should carry named properties
   (e.g. `facing`, `half`) instead of only `metadata`. Without GTNHLib it must still work and report
   `state.metadata`.

## Notes

- `nbt` hashes are stable but **not byte-identical to a 1.20 server's**: 1.7.10 cannot serialise NBT
  canonically (`NBTBase.write` is package-private, `NBTTagList` has no index accessor), so the
  digest is taken over a canonical text encoding instead. Same NBT still means same hash.
- `durability` is reconstructed from the damage ratio because 1.7.10 has no `getBarWidth`.
- `mapColor` is only present for block items.
- `itemGroups` is always an empty table (deprecated upstream, kept for shape).
- `enchantments` moved into the shared builder, so `inventory.getItemDetail` now reports them too.
