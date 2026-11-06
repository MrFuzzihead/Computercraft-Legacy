# Batch M — in-game validation pack

Pocket upgrades, the wired modem's `peripheral_hub` type, and remote multi-type propagation.

## What changed

| Item | Before | After |
|---|---|---|
| `pocket.equipBack` | accepted **only a plain wireless modem**; anything else returned `false, "No upgrade to equip"` | accepts every upgrade the pocket computer supports: wireless modem, ender modem, speaker, chat box, NPC detector, NPC interface |
| `pocket.unequipBack` | always returned a **wireless modem**, whatever was installed | returns the item for the upgrade that is actually installed |
| `pocket.isEquipped` | true only for upgrade id 1 | true for any supported upgrade |
| wired modem type | `"modem"` | `"modem"` **and** `"peripheral_hub"` |
| `getTypeRemote` | one type | every type, primary first |
| `hasTypeRemote` | matched the primary type (or a `;`-joined string) | matches any of the remote's types |

The upgrade mapping now lives in one place (`PocketUpgrades`), shared by `equipBack` and by the code
that rebuilds a computer from its NBT. Those two paths used to be written separately, which is
exactly how they drift: a pocket computer could be created with a speaker but refuse to *equip* one.

The NBT ids are on-disk values (1 modem, 2 ender modem, 3 speaker, 4 chat box, 5 NPC detector,
6 NPC interface) and `PocketUpgradesTest` pins them so a future refactor cannot silently turn every
saved upgrade into a different one.

## Run

```bat
copy docs\ingame-tests\batch-m\*.lua "run\client\saves\<World>\computer\<id>\"
copy docs\ingame-tests\readlog_manual.lua "run\client\saves\<World>\computer\<id>\"
```

Then run `m1_pocket_types`. It checks what a computer can see without a player inventory or a cable:

| Check | Detail |
|---|---|
| wired modem types | `hasType('modem')` and `hasType('peripheral_hub')` both true; a wired modem is not wireless |
| hub surface | `getNamesRemote`, `getTypeRemote`, `hasTypeRemote` are all present, and `getNamesRemote` returns a table |
| pocket API | `pocket.isEquipped()` returns a boolean (on a pocket computer) |

Everything else is staged at the end of the run, because it needs a player inventory or two
computers on a cable.

## The staged checks, and why each one exists

1. **Cable a wired modem to another peripheral.** `hasType(side, 'peripheral_hub')` must be true — the
   point of the new type — and `getType(side)` must return `modem` then `peripheral_hub`, primary
   first, because code that reads only the first value must keep working.
2. **`getTypeRemote` / `hasTypeRemote`** on a remote wired modem: both types, primary first, and
   `hasTypeRemote(name, 'peripheral_hub')` true.
3. **`pocket.equipBack` with a speaker** — this is the headline fix. Before, only a modem worked.
4. **`pocket.unequipBack` returns the same item**, not always a modem.
5. **Persistence**: equip a speaker, leave and re-enter the pocket computer, and
   `peripheral.getType('back')` must still be `speaker`. That exercises the NBT-rebuild path through
   the shared table.
6. **Wireless still works** (`rednet.open('back')`, `rednet.broadcast`) — the modem path through the
   same table must not regress.
7. **A foreign upgrade id is not overwritten.** Give yourself a pocket computer carrying an upgrade
   id ComputerCraft does not own, hold it, and call `pocket.equipBack()`:

   ```
   /give @p computercraft:pocket_computer 1 0 {upgrade:7}
   ```

   `equipBack()` must return `false, "Upgrade already equipped"`, and the tag must still read
   `upgrade:7` afterwards (`/data get entity @p SelectedItem.tag`). Before this change it returned
   `true` and replaced the id with ComputerCraft's own, orphaning whatever peripheral id 7 had
   attached — and `unequipBack()` could not give it back. Ids 1–6 behave as they always did.

## Covered by JVM tests instead

* `PocketAPIEquipGuardTest` (18) — the `upgrade` guard end to end through `callMethod`: ids 1–6 still
  refuse, ids 0/7/8/100/32766/-1 now refuse too, a non-integer value refuses (presence of the key is
  what "installed" means), the absent-tag and no-`upgrade`-key cases still report `"No upgrade to
  equip"`, and a refusal leaves both the inventory and the existing tag untouched. Staged check 7 is
  the in-game counterpart; the unit test is the authority because it can drive ids no in-game item
  has.
* `PocketUpgradesTest` (11) — every item type maps to its id and back, the on-disk ids are pinned,
  non-upgrade items are rejected, each upgrade builds its own peripheral, the ender modem stays a
  subclass of the modem (existing `instanceof` checks depend on it), and out-of-range NBT values are
  not upgrades.
* `WiredModemTypesTest` (6) — the published type list is `modem` then `peripheral_hub`, the primary
  type matches `getType()`, the peripheral class really implements `IMultiTypePeripheral`, and the
  remote path still has a string form and an array form.

`PeripheralAPITest` already covers `IMultiTypePeripheral` for a *directly attached* peripheral, which
is the code path `peripheral.getType`/`hasType` use.
