package dan200.computercraft.shared.pocket.apis;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.IPeripheralItem;
import dan200.computercraft.shared.peripheral.common.PeripheralItemFactory;
import dan200.computercraft.shared.pocket.peripherals.PocketModemPeripheral;

/**
 * Implements the {@code pocket} Lua API for pocket computers.
 *
 * <p>
 * Exposes three methods:
 * <ul>
 * <li>{@code pocket.equipBack()} – consume a wireless modem from the player's
 * inventory and attach it as a peripheral.</li>
 * <li>{@code pocket.unequipBack()} – detach the modem and return it to the
 * player's inventory.</li>
 * <li>{@code pocket.isEquipped()} – return {@code true} if a modem is
 * currently equipped.</li>
 * </ul>
 *
 * <p>
 * {@link #equipBack} and {@link #unequipBack} execute their inventory
 * modifications on the Minecraft main thread via
 * {@link ILuaContext#executeMainThreadTask} so they are safe to call from the
 * computer thread. The carrier player and item-stack references are refreshed
 * once per game tick by {@link ItemPocketComputer#onUpdate} through
 * {@link #update}.
 */
public class PocketAPI implements ILuaAPI {

    private static final String[] METHOD_NAMES = { "equipBack", "unequipBack", "isEquipped" };
    private static final int EQUIP_BACK = 0;
    private static final int UNEQUIP_BACK = 1;
    private static final int IS_EQUIPPED = 2;

    /** The server-side computer this API is attached to. */
    private final ServerComputer m_computer;

    // Updated every tick on the main thread (ItemPocketComputer.onUpdate).
    // volatile ensures the computer thread always sees the latest written value.
    private volatile EntityPlayer m_player;
    private volatile ItemStack m_stack;
    private volatile IInventory m_inventory;

    public PocketAPI(ServerComputer computer) {
        this.m_computer = computer;
    }

    /**
     * Called from the Minecraft main thread once per game tick so that
     * {@link #doEquipBack} and {@link #doUnequipBack} have a current view of
     * the carrying player and item stack.
     *
     * @param player    the entity currently holding the pocket computer, or
     *                  {@code null} if the computer is not held by a player
     * @param stack     the pocket computer {@link ItemStack}
     * @param inventory the player's inventory, or {@code null}
     */
    public void update(EntityPlayer player, ItemStack stack, IInventory inventory) {
        m_player = player;
        m_stack = stack;
        m_inventory = inventory;
    }

    // -------------------------------------------------------------------------
    // ILuaAPI
    // -------------------------------------------------------------------------

    @Override
    public String[] getNames() {
        return new String[] { "pocket" };
    }

    @Override
    public void startup() {}

    @Override
    public void advance(double dt) {}

    @Override
    public void shutdown() {}

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES;
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        switch (method) {
            case EQUIP_BACK:
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        return doEquipBack();
                    }
                });
            case UNEQUIP_BACK:
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        return doUnequipBack();
                    }
                });
            case IS_EQUIPPED:
                return doIsEquipped();
            default:
                return null;
        }
    }

    // -------------------------------------------------------------------------
    // Main-thread implementations
    // -------------------------------------------------------------------------

    /**
     * Find a wireless modem in the player's inventory, consume one, attach it
     * as a peripheral, and mark the upgrade flag in the item's NBT.
     */
    private Object[] doEquipBack() {
        EntityPlayer player = m_player;
        ItemStack stack = m_stack;
        IInventory inventory = m_inventory;

        if (player == null || stack == null) {
            return new Object[] { false, "Cannot find player" };
        }

        // Refuse if a modem is already equipped.
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey("upgrade") && tag.getInteger("upgrade") == 1) {
            return new Object[] { false, "Upgrade already equipped" };
        }

        // Search the player's main inventory for a wireless modem item.
        InventoryPlayer inv = player.inventory;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot == null) continue;
            if (!(slot.getItem() instanceof IPeripheralItem)) continue;

            IPeripheralItem pi = (IPeripheralItem) slot.getItem();
            if (pi.getPeripheralType(slot) != PeripheralType.WirelessModem) continue;

            // Consume one modem from this slot.
            slot.stackSize--;
            if (slot.stackSize <= 0) {
                inv.setInventorySlotContents(i, null);
            }

            // Set the upgrade flag on the pocket computer stack.
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            tag.setInteger("upgrade", 1);

            // Attach the modem peripheral (location set on next onUpdate tick).
            m_computer.setPeripheral(2, new PocketModemPeripheral());

            if (inventory != null) {
                inventory.markDirty();
            }
            return new Object[] { true };
        }

        return new Object[] { false, "No upgrade to equip" };
    }

    /**
     * Detach the modem peripheral, clear the upgrade flag from the item's NBT,
     * and return a wireless modem item to the player's inventory (or drop it).
     */
    private Object[] doUnequipBack() {
        EntityPlayer player = m_player;
        ItemStack stack = m_stack;
        IInventory inventory = m_inventory;

        if (player == null || stack == null) {
            return new Object[] { false, "Cannot find player" };
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("upgrade") || tag.getInteger("upgrade") != 1) {
            return new Object[] { false, "Nothing equipped" };
        }

        // Return a wireless modem item to the player's inventory.
        // Guard first: if the item cannot be created, abort without mutating state
        // so the player does not lose the modem upgrade silently.
        ItemStack modemStack = PeripheralItemFactory.create(PeripheralType.WirelessModem, null, 1);
        if (modemStack == null) {
            return new Object[] { false, "Cannot create upgrade item" };
        }
        if (!player.inventory.addItemStackToInventory(modemStack)) {
            // Inventory full: drop the item at the player's feet.
            player.dropPlayerItemWithRandomChoice(modemStack, false);
        }

        // Clear the upgrade flag.
        tag.removeTag("upgrade");

        // Detach the peripheral.
        m_computer.setPeripheral(2, null);

        if (inventory != null) {
            inventory.markDirty();
        }

        return new Object[] { true };
    }

    /**
     * Read the upgrade flag directly from the volatile stack reference.
     * No main-thread dispatch is needed — this is a pure read.
     */
    private Object[] doIsEquipped() {
        ItemStack stack = m_stack;
        if (stack == null) return new Object[] { false };
        NBTTagCompound tag = stack.getTagCompound();
        return new Object[] { tag != null && tag.hasKey("upgrade") && tag.getInteger("upgrade") == 1 };
    }
}
