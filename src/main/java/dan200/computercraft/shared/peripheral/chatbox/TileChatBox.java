package dan200.computercraft.shared.peripheral.chatbox;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.IPeripheralTile;

public class TileChatBox extends TileGeneric implements IPeripheralTile, IChatBoxReceiver {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Facing direction (2–5, matching MC side indices). Stored in NBT. */
    int m_direction = 2;

    /** Computers currently attached to this peripheral. Guarded by {@code this}. */
    private final Set<IComputerAccess> m_computers = new HashSet<>();

    // -------------------------------------------------------------------------
    // IPeripheralTile
    // -------------------------------------------------------------------------

    @Override
    public PeripheralType getPeripheralType() {
        return PeripheralType.ChatBox;
    }

    @Override
    public IPeripheral getPeripheral(int side) {
        return new ChatBoxPeripheral(this);
    }

    @Override
    public String getLabel() {
        return null;
    }

    @Override
    public int getDirection() {
        return m_direction;
    }

    @Override
    public void setDirection(int dir) {
        if (dir < 2 || dir > 5) dir = 2;
        m_direction = dir;
    }

    // -------------------------------------------------------------------------
    // Computer tracking (called by ChatBoxPeripheral.attach / .detach)
    // -------------------------------------------------------------------------

    synchronized void attachComputer(IComputerAccess computer) {
        m_computers.add(computer);
    }

    synchronized void detachComputer(IComputerAccess computer) {
        m_computers.remove(computer);
    }

    /**
     * Queues a Lua event on all currently attached computers.
     */
    private void queueEvent(String eventName, Object... params) {
        Set<IComputerAccess> snapshot;
        synchronized (this) {
            snapshot = new HashSet<>(m_computers);
        }
        for (IComputerAccess computer : snapshot) {
            computer.queueEvent(eventName, params);
        }
    }

    // -------------------------------------------------------------------------
    // IChatBoxReceiver
    // -------------------------------------------------------------------------

    @Override
    public void onChatEvent(String playerName, String message) {
        queueEvent("chat", playerName, message);
    }

    @Override
    public void onDeathEvent(String player, String killer, String damageType) {
        queueEvent("death", player, killer, damageType);
    }

    @Override
    public void onCommandEvent(String player, Map<Integer, String> arguments) {
        queueEvent("command", player, arguments);
    }

    // -------------------------------------------------------------------------
    // IChatBoxReceiver — CustomNPCs NPC events
    // -------------------------------------------------------------------------

    @Override
    public void onNpcInteractEvent(String playerName, String npcName) {
        queueEvent("npc_interact", playerName, npcName);
    }

    @Override
    public void onNpcDialogEvent(String playerName, String npcName, int dialogId, int optionId) {
        queueEvent("npc_dialog", playerName, npcName, dialogId, optionId);
    }

    @Override
    public void onNpcDialogClosedEvent(String playerName, String npcName, int dialogId, int optionId) {
        queueEvent("npc_dialog_closed", playerName, npcName, dialogId, optionId);
    }

    @Override
    public void onNpcDiedEvent(String npcName, String killerName, String damageType) {
        queueEvent("npc_died", npcName, killerName, damageType);
    }

    @Override
    public void onNpcSpawnedEvent(String npcName) {
        queueEvent("npc_spawned", npcName);
    }

    @Override
    public void onNpcDamagedEvent(String npcName, String attackerName, float damage, String damageType) {
        queueEvent("npc_damaged", npcName, attackerName, damage, damageType);
    }

    @Override
    public void onNpcKilledEntityEvent(String npcName, String entityName, String entityType) {
        queueEvent("npc_killed_entity", npcName, entityName, entityType);
    }

    // -------------------------------------------------------------------------
    // TileEntity lifecycle — register/unregister with ChatBoxManager
    // -------------------------------------------------------------------------

    @Override
    public void validate() {
        super.validate();
        ChatBoxManager.register(this);
    }

    @Override
    public void invalidate() {
        ChatBoxManager.unregister(this);
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        ChatBoxManager.unregister(this);
        super.onChunkUnload();
    }

    // -------------------------------------------------------------------------
    // TileGeneric — drops / pick block
    // -------------------------------------------------------------------------

    @Override
    public IIcon getTexture(int side) {
        return BlockChatBox.getChatBoxIcon(side, m_direction);
    }

    @Override
    public void getDroppedItems(java.util.List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
        if (!creative) {
            drops.add(new ItemStack(ComputerCraft.Blocks.chatBox));
        }
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(ComputerCraft.Blocks.chatBox);
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("dir")) {
            m_direction = nbt.getInteger("dir");
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("dir", m_direction);
    }

    @Override
    protected void writeDescription(NBTTagCompound nbt) {
        nbt.setInteger("dir", m_direction);
    }

    @Override
    protected void readDescription(NBTTagCompound nbt) {
        m_direction = nbt.getInteger("dir");
        updateBlock();
    }
}
