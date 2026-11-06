package dan200.computercraft.shared.pocket.apis;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.IPeripheralItem;
import dan200.computercraft.shared.peripheral.common.PeripheralItemFactory;
import dan200.computercraft.shared.pocket.PocketUpgrades;

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
                return new Object[] { context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        return doEquipBack();
                    }
                }) };
            case UNEQUIP_BACK:
                return new Object[] { context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        return doUnequipBack();
                    }
                }) };
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

        // Refuse if any upgrade is already installed. "Installed" means the NBT key is present, not that its
        // value is one of ours: a third-party upgrade writes its own id into the same key, and treating those as
        // free would let equipBack() overwrite it (and leak the peripheral it had attached).
        NBTTagCompound tag = stack.getTagCompound();
        if (hasInstalledUpgrade(tag)) {
            return new Object[] { false, "Upgrade already equipped" };
        }

        // Search the player's main inventory for anything this pocket computer can use. The upgrade
        // table decides what counts, so this covers the speaker, the ender modem and the rest --
        // it used to accept only a plain wireless modem.
        InventoryPlayer inv = player.inventory;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot == null) continue;
            if (!(slot.getItem() instanceof IPeripheralItem)) continue;

            IPeripheralItem pi = (IPeripheralItem) slot.getItem();
            int upgrade = PocketUpgrades.upgradeFor(pi.getPeripheralType(slot));
            if (upgrade < 0) continue;

            IPeripheral peripheral = PocketUpgrades.createPeripheral(upgrade, stack);
            if (peripheral == null) continue;

            // Consume one item from this slot.
            slot.stackSize--;
            if (slot.stackSize <= 0) {
                inv.setInventorySlotContents(i, null);
            }

            // Set the upgrade flag on the pocket computer stack.
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            tag.setInteger("upgrade", upgrade);

            // Attach the peripheral (location set on the next onUpdate tick).
            m_computer.setPeripheral(2, peripheral);

            if (inventory != null) {
                inventory.markDirty();
            }
            return new Object[] { true };
        }

        return new Object[] { false, "No upgrade to equip" };
    }

    /**
     * Whether the stack's NBT records an installed upgrade.
     *
     * <p>
     * The key's value is deliberately not interpreted: ComputerCraft owns ids 1-6, but addons register their own ids
     * into the same {@code upgrade} key on the same item, so any present value means a peripheral is attached and
     * {@link #doEquipBack()} must not install a second one on top of it.
     * </p>
     *
     * @param tag the pocket computer stack's tag compound, may be {@code null}
     * @return whether an upgrade id is recorded
     */
    private static boolean hasInstalledUpgrade(NBTTagCompound tag) {
        return tag != null && tag.hasKey("upgrade");
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

        // Return the item for the upgrade that is actually installed, not always a modem.
        // Guard first: if the item cannot be created, abort without mutating state so the player
        // does not lose the upgrade silently.
        int upgrade = tag.getInteger("upgrade");
        PeripheralType type = peripheralTypeFor(upgrade);
        ItemStack upgradeStack = type != null ? PeripheralItemFactory.create(type, null, 1) : null;
        if (upgradeStack == null) {
            return new Object[] { false, "Cannot create upgrade item" };
        }
        if (!player.inventory.addItemStackToInventory(upgradeStack)) {
            // Inventory full: drop the item at the player's feet.
            player.dropPlayerItemWithRandomChoice(upgradeStack, false);
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
     * The item type that installs an upgrade, for returning it on unequip.
     *
     * @param upgrade the NBT upgrade id
     * @return the peripheral type of the item, or {@code null} if the id is unknown
     */
    private static PeripheralType peripheralTypeFor(int upgrade) {
        switch (upgrade) {
            case PocketUpgrades.WIRELESS_MODEM:
                return PeripheralType.WirelessModem;
            case PocketUpgrades.ENDER_MODEM:
                return PeripheralType.AdvancedWirelessModem;
            case PocketUpgrades.SPEAKER:
                return PeripheralType.Speaker;
            case PocketUpgrades.CHAT_BOX:
                return PeripheralType.ChatBox;
            case PocketUpgrades.NPC_DETECTOR:
                return PeripheralType.NpcDetector;
            case PocketUpgrades.NPC_INTERFACE:
                return PeripheralType.NpcInterface;
            default:
                return null;
        }
    }

    /**
     * Read the upgrade flag directly from the volatile stack reference.
     * No main-thread dispatch is needed — this is a pure read.
     */
    private Object[] doIsEquipped() {
        ItemStack stack = m_stack;
        if (stack == null) return new Object[] { false };
        NBTTagCompound tag = stack.getTagCompound();
        return new Object[] {
            tag != null && tag.hasKey("upgrade") && PocketUpgrades.isUpgrade(tag.getInteger("upgrade")) };
    }
}
