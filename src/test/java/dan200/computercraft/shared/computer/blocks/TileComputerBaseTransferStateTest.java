package dan200.computercraft.shared.computer.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import dan200.computercraft.shared.computer.core.ServerComputer;

/**
 * Tests for {@link TileComputerBase#transferStateFrom(TileComputerBase)}, which addons call directly when they swap a
 * computer block in place to keep the running computer alive.
 *
 * <p>
 * The method is public for that reason, so the tests exercise it from outside the class rather than widening anything
 * further. A {@link World} mock is only needed for the branch that re-syncs the block; the identical-id branch must
 * not touch the world at all, which is what the {@code never()} verification pins down.
 * </p>
 */
class TileComputerBaseTransferStateTest {

    /** Minimal concrete tile: the abstract members are only reachable through block interaction. */
    private static final class TestComputerTile extends TileComputerBase {

        @Override
        public void openGUI(EntityPlayer player) {}

        @Override
        protected ServerComputer createComputer(int instanceID, int id) {
            return null;
        }

        @Override
        public IIcon getTexture(int side) {
            return null;
        }

        @Override
        public void setDirection(int direction) {}

        @Override
        public int getDirection() {
            return 0;
        }

        void setWorld(World world) {
            this.worldObj = world;
        }

        World getWorld() {
            return this.worldObj;
        }
    }

    private static TestComputerTile tile(int instanceID, int computerID, String label, boolean on) {
        TestComputerTile tile = new TestComputerTile();
        tile.m_instanceID = instanceID;
        tile.m_computerID = computerID;
        tile.m_label = label;
        tile.m_on = on;
        tile.setWorld(mock(World.class));
        return tile;
    }

    @Test
    void copiesIdentityAndDefaultsFromTheSourceTile() {
        TestComputerTile source = tile(7, 100, "turtle", true);
        TestComputerTile target = tile(1, 200, null, false);

        target.transferStateFrom(source);

        assertEquals(100, target.m_computerID, "the computer id must follow the block");
        assertEquals(7, target.m_instanceID, "the instance id must follow the block");
        assertEquals("turtle", target.m_label);
        assertEquals(true, target.m_on);
    }

    @Test
    void invalidatesTheSourceTileSoItCannotBeUsedTwice() {
        TestComputerTile source = tile(7, 100, null, false);
        TestComputerTile target = tile(1, 200, null, false);

        target.transferStateFrom(source);

        assertEquals(-1, source.m_instanceID, "the source tile must give up its instance id");
    }

    @Test
    void resyncsTheBlockWhenTheIdentityChanges() {
        TestComputerTile source = tile(7, 100, null, false);
        TestComputerTile target = tile(1, 200, null, false);

        target.transferStateFrom(source);

        verify(target.getWorld()).markBlockForUpdate(anyInt(), anyInt(), anyInt());
        verify(target.getWorld()).markTileEntityChunkModified(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void doesNothingWhenBothTilesAlreadyShareAnIdentity() {
        TestComputerTile source = tile(7, 100, "old", false);
        TestComputerTile target = tile(7, 100, "new", true);

        target.transferStateFrom(source);

        assertEquals("new", target.m_label, "an identity-equal transfer must not copy state");
        assertEquals(true, target.m_on);
        verify(target.getWorld(), never()).markBlockForUpdate(anyInt(), anyInt(), anyInt());
        assertEquals(-1, source.m_instanceID, "the source is invalidated either way");
    }

    @Test
    void doesNotRequireAWorldWhenNothingChanges() {
        TestComputerTile source = tile(7, 100, null, false);
        TestComputerTile target = tile(7, 100, null, false);
        target.setWorld(null);

        target.transferStateFrom(source);

        assertNull(target.getWorld());
        assertEquals(-1, source.m_instanceID);
        assertFalse(source.m_on);
    }
}
