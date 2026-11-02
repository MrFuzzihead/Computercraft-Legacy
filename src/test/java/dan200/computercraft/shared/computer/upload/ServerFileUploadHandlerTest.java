package dan200.computercraft.shared.computer.upload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import org.junit.jupiter.api.Test;

import dan200.computercraft.shared.computer.inventory.IContainerComputer;

/**
 * Tests for the rule that decides whether an upload may target a computer.
 *
 * <p>
 * This is the whole security model of the feature, and it is one predicate, so it is worth pinning
 * exhaustively: a client may only upload to a computer it currently has open. Without it a modified
 * client could push files onto any computer whose instance id it can guess, and the shell's prompt
 * would then have them written to disk.
 * </p>
 */
class ServerFileUploadHandlerTest {

    /** A stand-in for {@code ContainerComputer} and friends: a container that knows its computer. */
    private static final class ComputerContainer extends Container implements IContainerComputer {

        private final int instanceID;

        ComputerContainer(int instanceID) {
            this.instanceID = instanceID;
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }

        @Override
        public int getInstanceID() {
            return this.instanceID;
        }
    }

    private static EntityPlayer playerWithContainer(Container container) {
        EntityPlayer player = mock(EntityPlayer.class);
        player.openContainer = container;
        return player;
    }

    @Test
    void allowsTheComputerThePlayerHasOpen() {
        assertTrue(ServerFileUploadHandler.canUpload(playerWithContainer(new ComputerContainer(7)), 7));
    }

    @Test
    void refusesAnotherComputer() {
        assertFalse(ServerFileUploadHandler.canUpload(playerWithContainer(new ComputerContainer(7)), 8));
    }

    @Test
    void refusesAContainerThatIsNotAComputer() {
        assertFalse(ServerFileUploadHandler.canUpload(playerWithContainer(mock(Container.class)), 7));
    }

    @Test
    void refusesAContainerWhoseComputerIsUnknown() {
        assertFalse(
            ServerFileUploadHandler.canUpload(playerWithContainer(new ComputerContainer(-1)), 7),
            "a container that cannot name its computer (the client-side layout copy) authorises nothing");
    }

    @Test
    void refusesAnImpossibleInstanceId() {
        assertFalse(
            ServerFileUploadHandler.canUpload(playerWithContainer(new ComputerContainer(-1)), -1),
            "an upload must not be able to target 'no computer'");
    }
}
