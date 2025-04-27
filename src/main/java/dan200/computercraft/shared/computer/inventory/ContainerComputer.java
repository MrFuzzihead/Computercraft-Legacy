package dan200.computercraft.shared.computer.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import dan200.computercraft.shared.computer.blocks.TileComputer;

public class ContainerComputer extends Container {

    private TileComputer m_computer;

    public ContainerComputer(TileComputer computer) {
        this.m_computer = computer;
    }

    public boolean canInteractWith(EntityPlayer player) {
        return this.m_computer.isUseableByPlayer(player);
    }
}
