# Computercraft-Legacy

[![](https://jitpack.io/v/MrFuzzihead/Computercraft-Legacy.svg)](https://jitpack.io/#MrFuzzihead/Computercraft-Legacy)
[![Build status](https://github.com/MrFuzzihead/Computercraft-Legacy/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/MrFuzzihead/Computercraft-Legacy/actions/workflows/build-and-test.yml)
[![Latest release](https://img.shields.io/github/v/release/MrFuzzihead/Computercraft-Legacy?include_prereleases&sort=semver)](https://github.com/MrFuzzihead/Computercraft-Legacy/releases/latest)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.7.10-62a34a)](https://minecraft.wiki/w/Java_Edition_1.7.10)
[![Forge](https://img.shields.io/badge/Forge-10.13.4.1614-1e2b4f)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.7.10.html)

ComputerCraft for Minecraft 1.7.10, maintained for modern Forge, GTNH and custom servers.

## What this is

This repository is a **decompilation of ComputerCraft 1.75 for Minecraft 1.7.10**,
the last ComputerCraft release for 1.7.10, plus a substantial backport of the
maintenance work done by [SquidDev](https://github.com/SquidDev) on the 1.8 line.

It is a hybrid, and the layering matters:

- **The base is decompiled, not upstream source.** ComputerCraft's source was
  closed for 1.7.10; public ComputerCraft source only begins with the Minecraft
  1.8 line (the oldest tag in `dan200/ComputerCraft` is `1.79`, which is the 1.8.9
  build). The ComputerCraft code here was therefore recovered by decompiling the
  1.7.10 binary, which the ComputerCraft Public License expressly permits.
- **On top of that sit backports from CC: Tweaked**, the SquidDev-maintained
  fork: the Cobalt Lua runtime, the extended Lua API surface, and the computer
  and peripheral registry architecture. The 1.7.10-era `ComputerPeripheral`,
  `TileWirelessModem` and `WirelessModemPeripheral` code coexists with the 1.8.9
  registry classes rather than replacing them.
- **Plus original work**: the peripherals and Lua modules listed below, and the
  bugfixes.

So this is not "decompiled ComputerCraft with a patch" — it is decompiled
ComputerCraft with a 1.8-line maintenance backport. See [NOTICE](NOTICE) for the
full provenance record, including exactly which code came from where.

## Features

Bugfixes:

- Computers no longer time out when out of the 64-block `TileEntity` tick range
  and shut down after a while. Originally logged as
  [CCTweaks#136](https://github.com/SquidDev-CC/CCTweaks/issues/136) by SquidDev;
  the underlying issue traces to a
  [SpongeCommon change](https://github.com/SpongePowered/SpongeCommon/commit/6a15cf8f9114efacc818edd7bbd0e9ef9d2405b2).
- Reflection has been removed from `ComputerCraftAPI` and the peripheral
  registration path, in favor of direct calls.

Backported from the 1.8 line:

- The **Cobalt** Lua runtime replaces LuaJ, fixing re-entrance and bringing the
  runtime closer to reference Lua 5.1/5.2 behavior.
- The extended Lua API surface (`IArguments`, `ILuaAPI`, `ArgumentDelegator`,
  `ILuaEnvironment`, and friends) and the peripheral argument model.
- The computer and peripheral registry architecture.
- Terminal color palette support, persisted as the packed `int[16]` NBT key
  `term_palette`.

Original additions:

- **`websocket` API** — WebSocket client support for Computers.
- **Redstone relay** peripheral, including bundled-color handling.
- **ChatBox** peripheral integration.
- **Speaker** peripheral with a `cc.audio.tts` text-to-speech helper.
- **CustomNPCs** peripherals: NPC interface, NPC detector and trader role.
- Energy/RF and generic peripheral helpers.
- ~1,390 unit tests covering the runtime, filesystem, terminal, networking,
  turtle and peripheral layers.

## Compiling

Everything is handled by the `com.gtnewhorizons.gtnhconvention` plugin. Load the
project in IntelliJ and run `./gradlew build`, or use the wrapper from the command
line:

```
./gradlew build          # compile, test, format check
./gradlew test           # tests only
./gradlew runServer      # dev server
./gradlew spotlessApply  # apply formatting
```

Java 17 is required to build; the mod itself targets Java 8 bytecode. Cobalt is
resolved from `https://maven.squiddev.cc` (see `repositories.gradle`).

## License

**ComputerCraft Public License 1.0.0 (CCPL)** — see [LICENSE](LICENSE).

This is not a permissive license, and the CCPL's terms constrain what you can do
with this mod:

- The ComputerCraft code and assets remain the property of their original
  authors. This project is distributed under the CCPL because CCPL section 5
  requires every distribution of the mod to remain CCPL; it is **not** offered
  under MIT or any other permissive license.
- The CCPL permits reuse only in **other non-commercial Minecraft mods**. Use in
  commercial mods, mods for other games, other games, and any commercial or
  non-game project is not permitted, and the source of anything reused must be
  made available at no cost and remain CCPL licensed.
- Parts of this project come from CC: Tweaked and are under the **MPL 2.0**
  ([LICENSE-MPL-2.0](LICENSE-MPL-2.0)); the shaded runtime libraries are MIT.

Read [LICENSE](LICENSE) rather than relying on this summary. Attribution,
copyright holders and a per-component breakdown are in [NOTICE](NOTICE), and
`NOTICE`, `LICENSE*` are shipped inside the mod jar.

## Credit

ComputerCraft was created by [Daniel "dan200" Ratcliffe](https://github.com/dan200),
with additional code by Aaron "Cloudy" Mills, and program contributions from
nitrogenfingers, GopherAtl and RamiLego among others.

Shout out to [SquidDev](https://github.com/SquidDev) for keeping ComputerCraft
alive, and for CC: Tweaked and Cobalt, both of which this project depends on.
