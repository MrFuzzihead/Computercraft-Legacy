package dan200.computercraft.shared.pocket.upgrades;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.pocket.IPocketUpgrade;

/**
 * Shared plumbing for ComputerCraft's own pocket upgrades.
 *
 * <p>
 * These six used to be a {@code switch} in {@code PocketUpgrades}, a chain of {@code instanceof} checks in
 * {@code ItemPocketComputer.onUpdate} and another chain in {@code getItemStackDisplayName}. They are upgrades like any
 * mod's now; this class only carries what they have in common, and deliberately no more.
 * </p>
 *
 * <p>
 * The API default for {@link IPocketUpgrade#getUpgradeData(ItemStack)} — item and damage against
 * {@link IPocketUpgrade#getCraftingItem()}, ignoring NBT — is the right rule for all six, so it is not overridden.
 * </p>
 */
public abstract class BuiltinPocketUpgrade implements IPocketUpgrade {

    private final int m_id;
    private final String m_adjective;

    protected BuiltinPocketUpgrade(int id, String adjective) {
        this.m_id = id;
        this.m_adjective = adjective;
    }

    @Override
    public int getUpgradeID() {
        return this.m_id;
    }

    @Override
    public String getUnlocalisedAdjective() {
        return this.m_adjective;
    }

    /**
     * Where the holder is, with the eye height applied for living entities.
     *
     * <p>
     * This reproduces what {@code ItemPocketComputer.onUpdate} used to do inline once per upgrade: modems, speakers,
     * chat boxes and the NPC peripherals all sit at the holder's eyes, and at the raw entity position for a dropped
     * item.
     * </p>
     *
     * @param access the pocket computer
     * @return {@code {x, y, z}}, or {@code null} if nothing holds it
     */
    protected static double[] holderPosition(IPocketAccess access) {
        Entity entity = access.getEntity();
        if (entity == null) return null;

        double y = entity.posY;
        if (entity instanceof EntityLivingBase) {
            y += ((EntityLivingBase) entity).getEyeHeight();
        }

        return new double[] { entity.posX, y, entity.posZ };
    }

    /**
     * The peripheral handed back by the computer, if it is the class this upgrade attaches.
     *
     * <p>
     * The peripheral is fetched from the computer by side, so it is {@code null} when this upgrade attached nothing,
     * and it can be an instance of another upgrade's class for the tick in which the upgrade changes.
     * </p>
     *
     * @param peripheral the peripheral from the computer
     * @param type       the class this upgrade attaches
     * @param <T>        the peripheral type
     * @return the peripheral if it is of that class, else {@code null}
     */
    protected static <T extends IPeripheral> T expect(IPeripheral peripheral, Class<T> type) {
        return type.isInstance(peripheral) ? type.cast(peripheral) : null;
    }
}
