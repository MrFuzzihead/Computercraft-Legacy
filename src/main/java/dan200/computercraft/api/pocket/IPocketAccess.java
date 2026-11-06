package dan200.computercraft.api.pocket;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * The view of a pocket computer that an {@link IPocketUpgrade} is handed.
 *
 * <p>
 * An instance is created for each callback, so do not keep a reference to one across ticks: the stack and the entity
 * it reports change as the item moves between inventories, is dropped and is picked up again. Everything reachable
 * through this interface is on the server thread.
 * </p>
 *
 * <p>
 * Ported from CC: Tweaked 1.120.2 ({@code dan200.computercraft.api.pocket.IPocketAccess}, MPL-2.0). That version is
 * written against a {@code PocketBrain} that owns the computer; this one is the 1.7.10 equivalent over
 * {@code ItemPocketComputer}'s existing instance bookkeeping. See {@code NOTICE} section 2.
 * </p>
 *
 * <p>
 * <strong>Divergences from upstream, both deliberate:</strong> there is no {@code getColour()}/{@code setColour()}
 * (1.7.10 pocket computers have no dye colour at all) and no {@code getLight()}/{@code setLight(int)} (the only light
 * a 1.7.10 pocket computer has is the modem's on/off flag, which the in-tree modem upgrade drives through the internal
 * access object). Both are additive later.
 * </p>
 */
public interface IPocketAccess {

    /**
     * The world the pocket computer is in.
     *
     * @return the world
     */
    World getWorld();

    /**
     * The entity holding the pocket computer.
     *
     * <p>
     * This is {@code null} when the item is not held by an entity, for example while it is being crafted or is lying
     * in a chest. Upgrades that report a position should fall back to their last known one rather than assume a
     * holder.
     * </p>
     *
     * @return the holding entity, or {@code null}
     */
    Entity getEntity();

    /**
     * The pocket computer's item stack.
     *
     * <p>
     * This is a live reference to the stack in the inventory, so writing to it is how an upgrade persists its own
     * settings (see {@link #getUpgradeNBTData()}). Do not store it.
     * </p>
     *
     * @return the pocket computer stack
     */
    ItemStack getStack();

    /**
     * NBT owned by the installed upgrade, persisted on the item stack across reboots, chunk loads and computers being
     * recreated.
     *
     * <p>
     * The returned compound is live: mutate it and then call {@link #updateUpgradeNBTData()} so the containing
     * inventory is marked dirty.
     * </p>
     *
     * @return the upgrade's NBT, created on demand
     */
    NBTTagCompound getUpgradeNBTData();

    /**
     * Marks the upgrade's NBT as changed.
     *
     * @see #getUpgradeNBTData()
     */
    void updateUpgradeNBTData();

    /**
     * Throws away the current peripheral and attaches a freshly created one.
     *
     * <p>
     * Call this when the peripheral's method list changes, for instance when the owning entity changes, rather than
     * expecting the computer to notice on its own.
     * </p>
     */
    void invalidatePeripheral();
}
