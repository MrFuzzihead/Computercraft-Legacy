package dan200.computercraft.api.pocket;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * A peripheral that can be fitted to the back of a pocket computer.
 *
 * <p>
 * This is the pocket computer's counterpart to {@link dan200.computercraft.api.turtle.ITurtleUpgrade}: implement it,
 * register it with {@link dan200.computercraft.api.ComputerCraftAPI#registerPocketUpgrade(IPocketUpgrade)}, and an
 * item stack whose item is {@link #getCraftingItem()} can then be installed by the player with
 * {@code pocket.equipBack()}, survives being carried between worlds in NBT, and appears in the item's name.
 * </p>
 *
 * <p>
 * Ported from CC: Tweaked 1.120.2 ({@code dan200.computercraft.api.pocket.IPocketUpgrade}, MPL-2.0) — see
 * {@code NOTICE} section 2. The differences are deliberate and are all consequences of 1.7.10's upgrade model:
 * </p>
 *
 * <ul>
 * <li>{@link #getUpgradeID()} is an {@code int}, not a {@code ResourceLocation}, matching
 * {@link dan200.computercraft.api.turtle.ITurtleUpgrade}. Ids 1–{@value #FIRST_MOD_UPGRADE_ID}-1 are ComputerCraft's;
 * a mod's id must be at least {@value #FIRST_MOD_UPGRADE_ID}.</li>
 * <li>Registration is code-driven. Upstream also has a {@code PocketUpgradeSerialiser} and JSON in {@code data/};
 * 1.7.10 has no data-pack registry to hook, so {@code registerPocketUpgrade} is the whole story.</li>
 * <li>There is no {@code onRightClick} hook yet. Upstream's suppresses the pocket computer's GUI, which 1.7.10's
 * {@code ItemPocketComputer} opens on both sides without a packet of its own; a half-working version would be worse
 * than none, so it is deferred until there is something to suppress the client GUI with.</li>
 * </ul>
 *
 * <p>
 * Only one upgrade can be installed at a time, on the back side (side 2) of the computer.
 * </p>
 */
public interface IPocketUpgrade {

    /**
     * The smallest upgrade id a mod may use. Ids below this are ComputerCraft's own and are stored in saved items, so
     * they must not be reused.
     */
    int FIRST_MOD_UPGRADE_ID = 7;

    /**
     * A unique id for this upgrade, stored in the pocket computer's NBT.
     *
     * <p>
     * Changing this on an existing upgrade turns every pocket computer already carrying it into one with no upgrade
     * (and, since {@code pocket.equipBack()} refuses to overwrite anything, one that cannot be re-equipped without
     * being unequipped first). Pick it once.
     * </p>
     *
     * @return the upgrade id; at least {@value #FIRST_MOD_UPGRADE_ID}
     */
    int getUpgradeID();

    /**
     * The unlocalised adjective used to describe this upgrade in item names, for example
     * {@code "upgrade.computercraft:wireless_modem.adjective"}.
     *
     * @return the localisation key of the adjective
     */
    String getUnlocalisedAdjective();

    /**
     * The item that installs this upgrade.
     *
     * <p>
     * Also the item {@code pocket.unequipBack()} hands back, unless {@link #getUpgradeItem(NBTTagCompound)} returns
     * something else. It is matched against the player's inventory by item, damage and NBT (see
     * {@link #getUpgradeData(ItemStack)}).
     * </p>
     *
     * @return the crafting item, or {@code null} if this upgrade cannot be crafted
     */
    ItemStack getCraftingItem();

    /**
     * Whether an item stack would install this upgrade, and with what data.
     *
     * <p>
     * Called for every item in the player's inventory by {@code pocket.equipBack()}, and by the crafting recipe that
     * fits an upgrade to a pocket computer. Override it when the installing item carries data that should follow the
     * upgrade (upstream's rule, kept here): copy that data into the returned compound, and reload it in
     * {@link #getUpgradeItem(NBTTagCompound)} when the upgrade is removed.
     * </p>
     *
     * <p>
     * The default compares against {@link #getCraftingItem()} as a plain item, ignoring NBT, so a stack whose data is
     * irrelevant to the upgrade still installs.
     * </p>
     *
     * @param stack the candidate stack, never {@code null}
     * @return the data to store with the upgrade, or {@code null} if this stack does not install it
     */
    default NBTTagCompound getUpgradeData(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;

        ItemStack crafting = this.getCraftingItem();
        if (crafting == null || crafting.getItem() == null) return null;
        if (stack.getItem() != crafting.getItem()) return null;
        if (stack.getItemDamage() != crafting.getItemDamage()) return null;
        return new NBTTagCompound();
    }

    /**
     * The item handed back by {@code pocket.unequipBack()}.
     *
     * <p>
     * The default returns {@link #getCraftingItem()}, which loses any data captured by
     * {@link #getUpgradeData(ItemStack)}. Override both together if the upgrade's data should survive a round trip.
     * </p>
     *
     * @param upgradeData the data the upgrade was installed with
     * @return the item to give back
     */
    default ItemStack getUpgradeItem(NBTTagCompound upgradeData) {
        return this.getCraftingItem();
    }

    /**
     * Builds the peripheral this upgrade attaches.
     *
     * <p>
     * Called once when the pocket computer's server computer is created, and again whenever
     * {@link IPocketAccess#invalidatePeripheral()} is called. The result is attached to the computer's back side and
     * is passed back to {@link #update(IPocketAccess, IPeripheral)} on every tick.
     * </p>
     *
     * @param access the pocket computer being upgraded
     * @return the peripheral, or {@code null} to attach nothing
     */
    IPeripheral createPeripheral(IPocketAccess access);

    /**
     * Called once per tick while the pocket computer exists, on the server.
     *
     * <p>
     * This is where an upgrade whose peripheral has a position (a modem, a speaker) follows its holder, and where
     * per-tick state such as a light is refreshed. It runs before the peripheral's own {@code update()} would be
     * called by the computer.
     * </p>
     *
     * @param access     the pocket computer being upgraded
     * @param peripheral the peripheral from {@link #createPeripheral(IPocketAccess)}, or {@code null} if this upgrade
     *                   attached none
     */
    default void update(IPocketAccess access, IPeripheral peripheral) {}
}
