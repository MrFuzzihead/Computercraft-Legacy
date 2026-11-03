# Batch K — in-game validation pack

`/computercraft dump | turn-on | shutdown`.

## Why a pack and not just unit tests

`CommandComputerCraftTest` covers the whole command surface headlessly — argument parsing, the
listing, the per-computer detail, the power actions and the ordering — because the command works
entirely off `ComputerCraft.serverComputerRegistry` and a `ServerComputer` can be mocked. 28 tests.

What it **cannot** cover:

| Check | Why |
|---|---|
| that the command is registered at all | it is registered from `FMLServerStartingEvent`, which no unit test fires |
| that the permission level is enforced | `ICommandSender.canCommandSenderUseCommand` is the server's business |
| real computer output | a turned-on computer has to actually boot |
| the printed table on a real terminal | formatting, colours and wrapping |

## Run

This is a **server-side** command, so it needs a world, not a computer. Two ways:

**From the chat bar** (single player, cheats on, or an op on a server):

```
/computercraft dump
/computercraft dump <instanceID>
/computercraft turn-on <instanceID>
/computercraft shutdown <instanceID>
```

**From a command computer** (no cheats needed, if you are an op):

```bat
copy docs\ingame-tests\batch-k\k1_admin_command.lua "run\client\saves\<World>\computer\<id>\"
```

Then run `k1_admin_command` on a **command computer**. It drives the command through `commands.exec`
and prints what came back:

```
readlog_manual /batch-k-results.txt
```

## What the script checks

| Check | Expected |
|---|---|
| `dump` with computers present | a header line, a column header, and one row per loaded computer |
| `dump <instanceID>` | the detail block: id, instance id, label, on/off, position |
| `dump banana` | fails, and the error mentions `dump` |
| `dump 999999` | fails naming the id |
| `turn-on <instanceID>` | reports how many it turned on |
| `shutdown <instanceID>` | reports how many it shut down |
| no subcommand | usage text, listing the three subcommands |
| `explode` | usage text (not a stack trace) |

The script reads the instance id out of its own `dump` output, so it does not need one hard-coded.

## Deliberately absent subcommands

Upstream CC:Tweaked has more, and they are **not** ported. Each omission is a decision, not an
oversight:

| Upstream | Why not here |
|---|---|
| `track start\|stop\|dump` | built on the Micrometer metrics library and per-task aggregation. 1.7.10 has no equivalent, and a version that printed invented numbers would be worse than none. |
| `computer-folder` | opens a desktop folder; 1.20 only. |
| `tp`, `view`, `queue` | depend on 1.20's command-computer queue and spectator-style view. |

If you want a simple "which computers are busy, and for how long" view, the runtime already tracks
per-lane start times in `ComputerThread`, so a `track` substitute is possible — say the word and I
will add it as a separate item rather than pretending the upstream one exists.
