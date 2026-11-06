package dan200.computercraft.shared.pocket.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.core.computer.ComputerThread;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.core.ServerComputerRegistry;

/**
 * Tests for {@link ItemPocketComputer#createServerComputer}, which is public so addons that ship their own
 * pocket-computer items can reuse ComputerCraft's instance bookkeeping instead of reimplementing it.
 *
 * <p>
 * The real registry is swapped for a mock: {@code ServerComputerRegistry.add} broadcasts over the mod's network
 * channel, and that channel only exists after {@code preInit}. Mocking the registry keeps the test on the code path
 * being widened — the instance id bookkeeping and the registry round trip — without needing a network.
 * </p>
 *
 * <p>
 * The stack is pre-seeded with a computer id so the "no id yet" branch, which allocates a save directory on disk, is
 * not exercised here. That branch is unchanged and covered in game.
 * </p>
 */
class ItemPocketComputerServerComputerTest {

    /** Instance id the mocked registry hands out. */
    private static final int INSTANCE_ID = 0;

    private final ItemPocketComputer item = new ItemPocketComputer();
    private ServerComputerRegistry realRegistry;
    private ServerComputerRegistry registry;

    @BeforeEach
    void installMockRegistry() {
        realRegistry = ComputerCraft.serverComputerRegistry;
        registry = mock(ServerComputerRegistry.class);
        ComputerCraft.serverComputerRegistry = registry;
        when(registry.getUnusedInstanceID()).thenReturn(INSTANCE_ID);
    }

    @AfterEach
    void restoreRegistry() {
        ComputerCraft.serverComputerRegistry = realRegistry;
        ComputerThread.stop();
    }

    /** A stack carrying {@code computerID = 42} and no instance id yet. */
    private ItemStack stack() {
        ItemStack stack = new ItemStack(item);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("computerID", 42);
        stack.setTagCompound(tag);
        return stack;
    }

    @Test
    void createsTheServerComputerForTheStacksComputerId() {
        World world = mock(World.class);

        ServerComputer computer = item.createServerComputer(world, null, stack());

        assertNotNull(computer);
        assertEquals(42, computer.getID());
        assertSame(world, computer.getWorld());
    }

    @Test
    void recordsAnInstanceIdOnTheStackAndRegistersIt() {
        ItemStack stack = stack();

        ServerComputer computer = item.createServerComputer(mock(World.class), null, stack);

        assertEquals(computer.getInstanceID(), item.getInstanceID(stack), "the stack must record the instance id");
        assertEquals(INSTANCE_ID, item.getInstanceID(stack));
        verify(registry).add(INSTANCE_ID, computer);
    }

    @Test
    void secondCallReturnsTheSameComputerFromTheRegistry() {
        ItemStack stack = stack();
        World world = mock(World.class);

        ServerComputer first = item.createServerComputer(world, null, stack);
        // The registry now holds it, and the stack's instance id and session id both match.
        when(registry.contains(INSTANCE_ID)).thenReturn(true);
        when(registry.get(INSTANCE_ID)).thenReturn(first);

        ServerComputer second = item.createServerComputer(world, null, stack);

        assertNotNull(first);
        assertSame(first, second);
    }
}
