package dan200.computercraft.compat.customnpcs.peripheral.npcdetector;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.IPeripheralTile;

/**
 * Tile entity for the NPC Detector peripheral block.
 *
 * <p>
 * Stateless — no computer tracking, no NBT state beyond facing direction.
 * All scanning is pull-based via {@link NpcDetectorPeripheral}.
 * </p>
 */
public class TileNpcDetector extends TileGeneric implements IPeripheralTile {

    /** Facing direction (2–5). Stored in NBT for consistent orientation. */
    int m_direction = 2;

    // -------------------------------------------------------------------------
    // IPeripheralTile
    // -------------------------------------------------------------------------

    @Override
    public PeripheralType getPeripheralType() {
        return PeripheralType.NpcDetector;
    }

    @Override
    public IPeripheral getPeripheral(int side) {
        return NpcDetectorPeripheral.forTile(this);
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
    // TileGeneric — rendering / drops / pick block
    // -------------------------------------------------------------------------

    @Override
    public IIcon getTexture(int side) {
        return BlockNpcDetector.getNpcDetectorIcon(side, m_direction);
    }

    @Override
    public void getDroppedItems(List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
        if (!creative) {
            drops.add(new ItemStack(ComputerCraft.Blocks.npcDetector));
        }
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(ComputerCraft.Blocks.npcDetector);
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
