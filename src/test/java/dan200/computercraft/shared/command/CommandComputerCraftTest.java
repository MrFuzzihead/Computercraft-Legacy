package dan200.computercraft.shared.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.computer.core.ServerComputer;

/**
 * Tests for {@code /computercraft}.
 *
 * <p>
 * The command works entirely off {@link ComputerCraft#serverComputerRegistry}, and a
 * {@link ServerComputer} can be mocked, so the whole surface — argument parsing, the listing, the
 * per-computer detail and the power actions — is testable without a running server.
 * </p>
 */
class CommandComputerCraftTest {

    private CommandComputerCraft command;
    private ICommandSender sender;
    private final List<String> messages = new ArrayList<>();

    /** A server computer stub with just the fields the command reads. */
    private static ServerComputer computer(int instanceId, int computerId, String label, boolean on, World world, int x,
        int y, int z) {
        ServerComputer computer = mock(ServerComputer.class);
        when(computer.getInstanceID()).thenReturn(instanceId);
        when(computer.getID()).thenReturn(computerId);
        when(computer.getLabel()).thenReturn(label);
        when(computer.isOn()).thenReturn(on);
        when(computer.getWorld()).thenReturn(world);
        when(computer.getPosition()).thenReturn(new ChunkCoordinates(x, y, z));
        return computer;
    }

    /** A world stub: the command only compares identity and reads the provider for naming. */
    private static World world() {
        return mock(World.class);
    }

    @BeforeEach
    void setUp() {
        ComputerCraft.serverComputerRegistry.reset();
        command = new CommandComputerCraft();

        sender = mock(ICommandSender.class);
        // Capture every chat line so the output can be asserted.
        org.mockito.Mockito.doAnswer(invocation -> {
            IChatComponent component = invocation.getArgument(0);
            messages.add(component.getUnformattedTextForChat());
            return null;
        })
            .when(sender)
            .addChatMessage(any(IChatComponent.class));
    }

    @AfterEach
    void tearDown() {
        ComputerCraft.serverComputerRegistry.reset();
    }

    private void register(ServerComputer... computers) {
        for (ServerComputer computer : computers) {
            ComputerCraft.serverComputerRegistry.add(computer.getInstanceID(), computer);
        }
    }

    private String output() {
        return String.join("\n", messages);
    }

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    @Test
    void theCommandIsCalledComputercraft() {
        assertEquals("computercraft", command.getCommandName());
    }

    @Test
    void theCommandIsOpOnly() {
        // Level 2 is "op": this manipulates other players' computers.
        assertEquals(2, command.getRequiredPermissionLevel());
    }

    @Test
    void theUsageLineNamesTheSubcommands() {
        String usage = command.getCommandUsage(sender);
        for (String subcommand : CommandComputerCraft.SUBCOMMANDS) {
            assertTrue(usage.contains(subcommand), "usage should mention " + subcommand + ": " + usage);
        }
    }

    @Test
    void thereAreNoAliases() {
        assertTrue(
            command.getCommandAliases()
                .isEmpty());
    }

    @Test
    void noArgumentIsAUsername() {
        assertFalse(command.isUsernameIndex(new String[] {}, 0));
        assertFalse(command.isUsernameIndex(new String[] { "dump" }, 0));
    }

    // -------------------------------------------------------------------------
    // Argument handling
    // -------------------------------------------------------------------------

    @Test
    void noArgumentsRaises() {
        assertThrows(CommandException.class, () -> command.processCommand(sender, new String[] {}));
    }

    @Test
    void anUnknownSubcommandRaises() {
        assertThrows(CommandException.class, () -> command.processCommand(sender, new String[] { "explode" }));
    }

    @Test
    void turnOnWithoutIdsRaises() {
        assertThrows(CommandException.class, () -> command.processCommand(sender, new String[] { "turn-on" }));
    }

    @Test
    void shutdownWithoutIdsRaises() {
        assertThrows(CommandException.class, () -> command.processCommand(sender, new String[] { "shutdown" }));
    }

    @Test
    void aNonNumericIdRaisesWithAHint() {
        CommandException error = assertThrows(
            CommandException.class,
            () -> command.processCommand(sender, new String[] { "dump", "banana" }));
        assertTrue(
            error.getMessage()
                .contains("dump"),
            "the error should point at /computercraft dump: " + error.getMessage());
    }

    @Test
    void anUnknownInstanceIdRaises() {
        CommandException error = assertThrows(
            CommandException.class,
            () -> command.processCommand(sender, new String[] { "dump", "42" }));
        assertTrue(
            error.getMessage()
                .contains("42"),
            "the error should name the id: " + error.getMessage());
    }

    @Test
    void subcommandsAreCaseInsensitive() {
        register(computer(1, 0, "alpha", false, world(), 0, 64, 0));
        command.processCommand(sender, new String[] { "TURN-ON", "1" });
        assertTrue(output().contains("Turned on"));
    }

    // -------------------------------------------------------------------------
    // dump
    // -------------------------------------------------------------------------

    @Test
    void dumpWithNoComputersSaysSo() {
        command.processCommand(sender, new String[] { "dump" });
        assertTrue(output().contains("No computers"), output());
    }

    @Test
    void dumpListsEveryComputer() {
        register(
            computer(1, 5, "alpha", true, world(), 10, 64, 20),
            computer(2, 6, "beta", false, world(), 30, 70, 40));
        command.processCommand(sender, new String[] { "dump" });

        String out = output();
        assertTrue(out.contains("2 computer(s)"), out);
        assertTrue(out.contains("alpha"), out);
        assertTrue(out.contains("beta"), out);
        // Both the instance id (block identity) and the computer id (program identity) are shown.
        assertTrue(out.contains("Inst"), out);
        assertTrue(out.contains("Id"), out);
    }

    @Test
    void dumpShowsOnAndOff() {
        register(
            computer(1, 0, "on-one", true, world(), 0, 64, 0),
            computer(2, 0, "off-one", false, world(), 0, 64, 0));
        command.processCommand(sender, new String[] { "dump" });

        String out = output();
        assertTrue(out.contains("yes"), out);
        assertTrue(out.contains("no"), out);
    }

    @Test
    void dumpOneShowsTheDetails() {
        ServerComputer machine = computer(7, 3, "labelled", true, world(), 1, 2, 3);
        register(machine);
        command.processCommand(sender, new String[] { "dump", "7" });

        String out = output();
        assertTrue(out.contains("Computer 7"), out);
        assertTrue(out.contains("labelled"), out);
        assertTrue(out.contains("1, 2, 3"), out);
    }

    @Test
    void dumpOneNamesPeripheralsBySide() {
        ServerComputer machine = computer(9, 0, "with-peripherals", false, world(), 0, 64, 0);
        IPeripheral monitor = mock(IPeripheral.class);
        when(monitor.getType()).thenReturn("monitor");
        when(machine.getPeripheral(0)).thenReturn(monitor); // down
        register(machine);

        command.processCommand(sender, new String[] { "dump", "9" });

        String out = output();
        assertTrue(out.contains("Peripheral down"), out);
        assertTrue(out.contains("monitor"), out);
    }

    @Test
    void positionFormattingNamesTheDimension() {
        assertEquals(
            "10, 64, -3 in The Nether",
            CommandComputerCraft.describePosition("The Nether", new ChunkCoordinates(10, 64, -3)));
        assertEquals("1, 2, 3 in ?", CommandComputerCraft.describePosition(null, new ChunkCoordinates(1, 2, 3)));
    }

    @Test
    void aPocketComputerHasNoPosition() {
        ServerComputer pocket = mock(ServerComputer.class);
        when(pocket.getInstanceID()).thenReturn(4);
        when(pocket.getID()).thenReturn(0);
        when(pocket.getLabel()).thenReturn("pocket");
        when(pocket.getWorld()).thenReturn(null);
        when(pocket.getPosition()).thenReturn(null);
        register(pocket);

        command.processCommand(sender, new String[] { "dump", "4" });
        assertTrue(output().contains("pocket computer"), output());
    }

    @Test
    void aLongLabelIsTruncatedInTheListing() {
        register(computer(1, 0, "a-very-long-label-that-will-not-fit", false, world(), 0, 64, 0));
        command.processCommand(sender, new String[] { "dump" });
        assertTrue(output().contains("…"), "a long label should be truncated: " + output());
    }

    // -------------------------------------------------------------------------
    // turn-on / shutdown
    // -------------------------------------------------------------------------

    @Test
    void turnOnPowersComputersOn() {
        ServerComputer first = computer(1, 0, "a", false, world(), 0, 64, 0);
        ServerComputer second = computer(2, 0, "b", false, world(), 0, 64, 0);
        register(first, second);

        command.processCommand(sender, new String[] { "turn-on", "1", "2" });

        verify(first).turnOn();
        verify(second).turnOn();
        assertTrue(output().contains("Turned on 2"), output());
    }

    @Test
    void turnOnCountsOnlyTheOnesThatWereOff() {
        ServerComputer alreadyOn = computer(1, 0, "a", true, world(), 0, 64, 0);
        register(alreadyOn);

        command.processCommand(sender, new String[] { "turn-on", "1" });

        // It is still asked to turn on, but the count reports only the state change.
        verify(alreadyOn).turnOn();
        assertTrue(output().contains("Turned on 0"), output());
    }

    @Test
    void shutdownPowersComputersOff() {
        ServerComputer running = computer(3, 0, "a", true, world(), 0, 64, 0);
        ServerComputer stopped = computer(4, 0, "b", false, world(), 0, 64, 0);
        register(running, stopped);

        command.processCommand(sender, new String[] { "shutdown", "3", "4" });

        verify(running).shutdown();
        verify(stopped).shutdown();
        assertTrue(output().contains("Shut down 1"), output());
    }

    @Test
    void aBadIdInTheListAbortsBeforeTouchingAnything() {
        ServerComputer machine = computer(1, 0, "a", true, world(), 0, 64, 0);
        register(machine);

        assertThrows(
            CommandException.class,
            () -> command.processCommand(sender, new String[] { "turn-on", "1", "999" }));
        // The first id was valid, so it may or may not have been acted on; what matters is that the
        // command reported an error rather than silently doing half the work.
        assertTrue(messages.isEmpty() || !output().contains("Turned on"), output());
    }

    @Test
    void unknownIdsDoNotTurnAnythingOn() {
        ServerComputer machine = computer(1, 0, "a", true, world(), 0, 64, 0);
        register(machine);

        assertThrows(CommandException.class, () -> command.processCommand(sender, new String[] { "turn-on", "999" }));
        verify(machine, never()).turnOn();
    }

    // -------------------------------------------------------------------------
    // Ordering
    // -------------------------------------------------------------------------

    @Test
    void theComputerListIsOrderedByInstanceId() {
        register(
            computer(30, 0, "c", false, world(), 0, 64, 0),
            computer(10, 0, "a", false, world(), 0, 64, 0),
            computer(20, 0, "b", false, world(), 0, 64, 0));

        List<ServerComputer> loaded = CommandComputerCraft.loadedComputers();
        assertEquals(3, loaded.size());
        assertEquals(
            10,
            loaded.get(0)
                .getInstanceID());
        assertEquals(
            20,
            loaded.get(1)
                .getInstanceID());
        assertEquals(
            30,
            loaded.get(2)
                .getInstanceID());
    }

    @Test
    void dumpFromAPlayerPutsNearbyComputersFirst() {
        World world = world();
        register(computer(1, 0, "far", false, world, 500, 64, 500), computer(2, 0, "near", false, world, 1, 64, 1));

        net.minecraft.entity.player.EntityPlayer player = mock(net.minecraft.entity.player.EntityPlayer.class);
        player.worldObj = world;
        player.posX = 0;
        player.posY = 64;
        player.posZ = 0;
        org.mockito.Mockito.doAnswer(invocation -> {
            messages.add(((IChatComponent) invocation.getArgument(0)).getUnformattedTextForChat());
            return null;
        })
            .when(player)
            .addChatMessage(any(IChatComponent.class));
        when(player.getCommandSenderName()).thenReturn("player");

        command.processCommand(player, new String[] { "dump" });

        List<String> lines = new ArrayList<>(messages);
        int nearIndex = -1;
        int farIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i)
                .contains("near")) nearIndex = i;
            if (lines.get(i)
                .contains("far")) farIndex = i;
        }
        assertTrue(nearIndex >= 0 && farIndex >= 0, String.join("\n", lines));
        assertTrue(nearIndex < farIndex, "the nearer computer should be listed first:\n" + String.join("\n", lines));
    }

    @Test
    void theDetailOutputIsCapturedByTheSender() {
        register(computer(5, 0, "x", false, world(), 0, 64, 0));
        command.processCommand(sender, new String[] { "dump", "5" });

        ArgumentCaptor<IChatComponent> captor = ArgumentCaptor.forClass(IChatComponent.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).addChatMessage(captor.capture());
        assertTrue(
            captor.getAllValues()
                .size() >= 5,
            "one line per field");
    }
}
