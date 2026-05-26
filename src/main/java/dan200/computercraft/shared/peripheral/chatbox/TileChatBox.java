package dan200.computercraft.shared.peripheral.chatbox;

import java.util.HashSet;
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

public class TileChatBox extends TileGeneric implements IPeripheralTile {

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
     * The first element of {@code args} must be the event name string.
     */
    void queueEvent(Object[] args) {
        Set<IComputerAccess> snapshot;
        synchronized (this) {
            snapshot = new HashSet<>(m_computers);
        }
        String eventName = (String) args[0];
        // Build parameter array (everything after the event name)
        Object[] params = new Object[args.length - 1];
        System.arraycopy(args, 1, params, 0, params.length);
        for (IComputerAccess computer : snapshot) {
            computer.queueEvent(eventName, params);
        }
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
