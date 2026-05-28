# Adding a New Pocket Computer Upgrade to ComputerCraft-Legacy

This guide covers adding a new pocket computer upgrade (like the wireless modem or speaker). Use `PocketModemPeripheral` (location-aware modem) and `PocketSpeakerPeripheral` (location-aware speaker) as references.

> **Important architectural difference from turtle upgrades:** Pocket upgrades are **not** backed by a formal interface or registry. The active upgrade is stored as an integer ID in the item's NBT (`"upgrade"` key), and the peripheral is instantiated via a hardcoded `if/else if` chain inside [`ItemPocketComputer.java`](../src/main/java/dan200/computercraft/shared/pocket/items/ItemPocketComputer.java). Every new upgrade requires edits in several places within that file.

---

## Existing Upgrade IDs

| ID | Upgrade        | Peripheral class             |
|----|----------------|------------------------------|
| 1  | Wireless Modem | `PocketModemPeripheral`      |
| 2  | Ender Modem    | `PocketEnderModemPeripheral` |
| 3  | Speaker        | `PocketSpeakerPeripheral`    |
| 4  | Chat Box       | `PocketChatBoxPeripheral`    |

**Next available ID: 5.**

The upgrade peripheral is always attached to **side 2** (`"back"`), which is the only supported slot for pocket upgrades. Lua programs access it as `peripheral.wrap("back")`.

---

## Step 1 — Create the Peripheral Class

Create a new class in `dan200.computercraft.shared.pocket.peripherals`. The key requirement is a `setLocation(World, double, double, double)` method so `ItemPocketComputer.onUpdate` can push the player's current position to the peripheral every tick.

```java
package dan200.computercraft.shared.pocket.peripherals;

import net.minecraft.world.World;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.mything.MyThingPeripheral;

/**
 * Pocket-computer variant of the MyThing peripheral.
 * Location is updated every tick by ItemPocketComputer.onUpdate.
 */
public class PocketMyThingPeripheral extends MyThingPeripheral {

    private World m_world = null;
    private double m_x = 0.0;
    private double m_y = 0.0;
    private double m_z = 0.0;

    /** Called once per tick from ItemPocketComputer.onUpdate. */
    public void setLocation(World world, double x, double y, double z) {
        m_world = world;
        m_x = x;
        m_y = y;
        m_z = z;
    }

    /**
     * If your peripheral needs per-tick processing (e.g. flushing audio),
     * add a tick() method and call it from onUpdate after setLocation.
     */
    public void tick() {
        if (m_world != null && !m_world.isRemote) {
            // ... periodic work using m_world, m_x, m_y, m_z ...
        }
    }

    @Override
    protected World getWorld() {
        return m_world;
    }

    @Override
    protected Vec3 getPosition() {
        return m_world != null
            ? Vec3.createVectorHelper(m_x, m_y, m_z)
            : null;
    }

    @Override
    public boolean equals(IPeripheral other) {
        // Pocket peripherals use class identity — one instance per computer.
        return other instanceof PocketMyThingPeripheral;
    }
}
```

References:
- `PocketModemPeripheral` — holds a mutable `Vec3` position, calls `switchNetwork()` when the world changes.
- `PocketSpeakerPeripheral` — holds x/y/z fields, exposes a `tick()` method to flush pending audio.

---

## Step 2 — Add the Upgrade ID and Getter to `ItemPocketComputer`

Open [`ItemPocketComputer.java`](../src/main/java/dan200/computercraft/shared/pocket/items/ItemPocketComputer.java).

### 2a. Add a getter

```java
public boolean getHasMyThing(ItemStack stack) {
    NBTTagCompound compound = stack.getTagCompound();
    return compound != null && compound.hasKey("upgrade") && compound.getInteger("upgrade") == 4;
}
```

Follow the same pattern as the existing `getHasModem`, `getHasEnderModem`, and `getHasSpeaker` methods.

### 2b. Add a factory method

```java
public ItemStack createWithMyThing(int id, String label, ComputerFamily family) {
    if (family != ComputerFamily.Normal && family != ComputerFamily.Advanced) return null;
    int damage = family == ComputerFamily.Advanced ? 1 : 0;
    ItemStack result = new ItemStack(this, 1, damage);
    NBTTagCompound compound = new NBTTagCompound();
    if (id >= 0) compound.setInteger("computerID", id);
    compound.setInteger("upgrade", 4);
    result.setTagCompound(compound);
    if (label != null) result.setStackDisplayName(label);
    return result;
}
```

---

## Step 3 — Wire into `createServerComputer`

In `createServerComputer`, find the `if/else if` block that calls `computer.setPeripheral(2, ...)` and add a new branch:

```java
if (this.getHasModem(stack)) {
    computer.setPeripheral(2, new PocketModemPeripheral());
} else if (this.getHasEnderModem(stack)) {
    computer.setPeripheral(2, new PocketEnderModemPeripheral());
} else if (this.getHasSpeaker(stack)) {
    computer.setPeripheral(2, new PocketSpeakerPeripheral());
} else if (this.getHasMyThing(stack)) {              // ← add this
    computer.setPeripheral(2, new PocketMyThingPeripheral());
}
```

---

## Step 4 — Wire into `onUpdate`

In `onUpdate`, find the `if/else if` block that dispatches to `instanceof` checks on the peripheral and add a branch:

```java
IPeripheral peripheral = computer.getPeripheral(2);
if (peripheral instanceof PocketModemPeripheral modem) {
    // ...existing modem location update...
} else if (peripheral instanceof PocketSpeakerPeripheral speaker) {
    // ...existing speaker location update...
} else if (peripheral instanceof PocketMyThingPeripheral myThing) {  // ← add this
    if (entity instanceof EntityLivingBase living) {
        myThing.setLocation(world, living.posX, living.posY + living.getEyeHeight(), living.posZ);
    } else {
        myThing.setLocation(world, entity.posX, entity.posY, entity.posZ);
    }
    myThing.tick(); // omit if your peripheral has no per-tick work
}
```

Use `EntityLivingBase.getEyeHeight()` for the Y offset so the peripheral's effective position matches what a held item would sense (consistent with modem and speaker).

---

## Step 5 — Update `getItemStackDisplayName`

Add an `else if` branch so the item name reflects the installed upgrade:

```java
public String getItemStackDisplayName(ItemStack stack) {
    String baseString = this.getUnlocalizedName(stack);
    if (this.getHasEnderModem(stack)) {
        // ...existing...
    } else if (this.getHasModem(stack)) {
        // ...existing...
    } else if (this.getHasSpeaker(stack)) {
        // ...existing...
    } else if (this.getHasMyThing(stack)) {           // ← add this
        return StatCollector.translateToLocalFormatted(
            baseString + ".upgraded.name",
            new Object[] { StatCollector.translateToLocal("upgrade.computercraft:my_thing.adjective") });
    } else {
        return StatCollector.translateToLocal(baseString + ".name");
    }
}
```

---

## Step 6 — Add to `getSubItems` (Creative Tab)

In `getSubItems`, add entries for both computer families:

```java
list.add(PocketComputerItemFactory.createWithMyThing(-1, null, ComputerFamily.Normal));
list.add(PocketComputerItemFactory.createWithMyThing(-1, null, ComputerFamily.Advanced));
```

Also add the corresponding factory delegates to [`PocketComputerItemFactory.java`](../src/main/java/dan200/computercraft/shared/pocket/items/PocketComputerItemFactory.java):

```java
public static ItemStack createWithMyThing(int id, String label, ComputerFamily family) {
    return ComputerCraft.Items.pocketComputer.createWithMyThing(id, label, family);
}
```

---

## Step 7 — Add a Crafting Recipe

In `CCTurtleProxyCommon` or `ComputerCraftProxyCommon`, register a shaped recipe:
- **Input:** Pocket Computer + your upgrade item (e.g. placed below or beside it).
- **Output:** `PocketComputerItemFactory.createWithMyThing(-1, null, family)`.

```java
// Example: pocket computer + my thing item → upgraded pocket computer
GameRegistry.addRecipe(new ImpostorRecipe(
    1, 2,
    new ItemStack[] {
        PocketComputerItemFactory.create(-1, null, ComputerFamily.Normal, false),
        new ItemStack(ComputerCraft.Blocks.myThing, 1, 0)
    },
    PocketComputerItemFactory.createWithMyThing(-1, null, ComputerFamily.Normal)
));
```

---

## Step 8 — Add the Lang Entry

Add the adjective key to `assets/computercraft/lang/en_US.lang`:

```properties
upgrade.computercraft:my_thing.adjective=Gadget
```

This produces the item name **"Gadget Pocket Computer"** in-game (via the `*.upgraded.name` format string).

---

## Optional: Item Icon Animation

The modem upgrade uses `computer.getUserData()` to store a `"modemLight"` boolean that selects between icon animation frames (e.g. icon indices 3/4 for normal, 8/9 for advanced). If your upgrade needs a visual indicator on the item icon:

1. Register additional `IIcon` entries in `registerIcons` (following the existing 0–9 index scheme).
2. In `onUpdate`, read your peripheral's state and write it to `computer.getUserData()`, then call `computer.updateUserData()` if it changed.
3. In `getAnimation`, read the extra NBT key and return the appropriate icon index.

Reference: the `"modemLight"` pattern in `ItemPocketComputer.onUpdate` and `getAnimation`.

---

## Checklist for Adding a New Pocket Upgrade

1. **Pick an ID** — must be unique; IDs 1–3 are taken. Use the next free ID (currently 4).
2. **Create `PocketMyThingPeripheral`** in `dan200.computercraft.shared.pocket.peripherals` with `setLocation` and optionally `tick`.
3. **Add `getHasMyThing(ItemStack)`** to `ItemPocketComputer`.
4. **Add `createWithMyThing(id, label, family)`** to `ItemPocketComputer` and delegate from `PocketComputerItemFactory`.
5. **Wire `setPeripheral(2, ...)`** in `createServerComputer`.
6. **Wire `setLocation` + `tick`** in `onUpdate`.
7. **Update `getItemStackDisplayName`** with the adjective lang key.
8. **Add to `getSubItems`** for both `Normal` and `Advanced` families.
9. **Add a crafting recipe**.
10. **Add the lang entry** in `en_US.lang`.
11. **Add tests** — verify `getHasMyThing` reads the correct NBT ID, and that `createServerComputer` attaches the right peripheral type.

---

*Reference implementations: `PocketModemPeripheral` (mutable position, modem LED state via `getUserData`), `PocketSpeakerPeripheral` (x/y/z fields, `tick()` for audio flushing), `PocketAPI` (example of a Lua API added specifically for pocket computers).*

