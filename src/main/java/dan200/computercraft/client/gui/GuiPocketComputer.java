package dan200.computercraft.client.gui;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.media.inventory.ContainerHeldItem;

public class GuiPocketComputer extends GuiComputer {

    public GuiPocketComputer(ContainerHeldItem container) {
        super(
            container,
            ComputerCraft.Items.pocketComputer.getFamily(container.getStack()),
            ComputerCraft.Items.pocketComputer.createClientComputer(container.getStack()),
            26,
            20);
    }
}
