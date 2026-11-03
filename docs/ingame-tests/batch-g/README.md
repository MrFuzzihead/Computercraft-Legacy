# Batch G — in-game validation pack

Command-computer parity: `getDimension`, dimension *names* as arguments, `getBlockInfos` traversal
order, and entity selectors.

## Why an in-game pack is needed

The unit tests (`CommandDimensionTest`) cover the two pure helpers — `dimensionName(id, providerName)`
and `resolveDimension(arg, fallback)` — because both take their inputs explicitly. Everything else in
this batch resolves against a live server:

| Item | Why it cannot be unit-tested |
|---|---|
| `getDimension()` | needs a `World` with a provider |
| dimension string arguments | name resolution scans the loaded worlds on the server |
| `getBlockInfos` ordering | needs real blocks to compare ranged against single lookups |
| `getEntities` | needs `PlayerSelector` and a populated `loadedEntityList` |

## Run

```bat
copy docs\ingame-tests\batch-g\*.lua "run\client\saves\<World>\computer\<id>\"
copy docs\ingame-tests\readlog_manual.lua "run\client\saves\<World>\computer\<id>\"
```

Then type **`g1_commands`** at the shell **on a command computer**.

The run has more output than a terminal can scroll, so it writes `/batch-g-results.txt` as well:

```
readlog_manual /batch-g-results.txt
```

On a non-command computer the script reports one SKIP and stops, so running it in the wrong place
cannot corrupt anything.

## What is checked

| Section | Checks |
|---|---|
| `getDimension` | returns a namespaced string; prints it |
| dimension arguments | numeric id still works, name form agrees with it, another dimension is reachable, bare aliases (`nether`, `end`) and case-insensitivity work, an unknown name raises |
| `getBlockInfos` ordering | a 2×2×2 range compared against eight single lookups using `x + z*width + y*width*depth + 1`; reversed corners give the same result; >4096 blocks raises |
| `commands.list` | non-empty, prefix filtering narrows the set and every entry matches |
| `getEntities` | `@a`/`@p`/`@r`, a player by name, `@e[type=…]`, `limit=`, an unsupported filter raising |

## Expected results, and what is honestly *not* modern

`commands.list` is **not** a subcommand walk. 1.7.10 has no Brigadier tree — `ICommandManager`
exposes flat single-word names — so `commands.list(prefix)` stays a prefix filter. The script asserts
that behaviour rather than a child-node listing, because a child listing cannot be derived from a flat
command namespace without parsing usage strings.

`getEntities` is a **documented subset**:

| Supported | Not supported (no 1.7.10 equivalent) |
|---|---|
| `@p`, `@r`, `@a`, player names, with Forge's own `x`/`y`/`z`/`r`/`rm`/`m`/`c`/`l`/`team` filters | `@s` |
| `@e` with `type=`, `distance=`/`r=`, `rm=`, `x=`/`y=`/`z=`, `limit=` | `tag=`, `nbt=`, `scores=`, `predicate=` |

An unsupported filter raises `Unsupported selector filter: <key>` rather than silently matching
everything — a silent match would be worse than an error.

`@e[type=…]` names come from `EntityList.getEntityString` lowercased (`zombie`, `item`). They are
**not** namespaced, because 1.7.10 has no entity registry id and inventing `minecraft:` would
mislabel every mod entity.
