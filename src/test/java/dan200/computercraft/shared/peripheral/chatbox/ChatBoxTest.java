package dan200.computercraft.shared.peripheral.chatbox;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IComputerAccess;

/**
 * Unit tests for {@link ChatBoxManager} event fan-out and
 * {@link ChatBoxPeripheral} argument validation.
 *
 * <p>
 * These tests do NOT spin up a Minecraft server; they exercise the
 * pure-Java logic only (argument parsing, fan-out dispatch).
 * The {@code say} / {@code tell} Minecraft I/O paths are integration-tested
 * manually in-game.
 * </p>
 */
class ChatBoxTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Creates a minimal TileChatBox stub — no world attached. */
    private TileChatBox makeTile() {
        return new TileChatBox();
    }

    /**
     * Attaches a mock computer to a tile and registers the tile in ChatBoxManager,
     * mirroring what happens when the chunk loads and a computer connects.
     */
    private IComputerAccess attachMock(TileChatBox tile) {
        IComputerAccess mock = mock(IComputerAccess.class);
        ChatBoxManager.register(tile);
        tile.attachComputer(mock);
        return mock;
    }

    @BeforeEach
    void resetConfig() {
        // Ensure config default (infinite) for argument-parsing tests.
        ComputerCraft.chatbox_max_range = -1;
    }

    // -------------------------------------------------------------------------
    // ChatBoxManager fan-out tests
    // -------------------------------------------------------------------------

    @Test
    void dispatchChat_queuesEventOnAttachedComputer() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);

        ChatBoxManager.dispatchChat("Steve", "Hello world");

        verify(computer).queueEvent("chat", new Object[] { "Steve", "Hello world" });

        ChatBoxManager.unregister(tile);
    }

    @Test
    void dispatchChat_doesNotQueueOnDetachedComputer() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);
        tile.detachComputer(computer);

        ChatBoxManager.dispatchChat("Steve", "Hello world");

        verify(computer, never()).queueEvent(any(), any());

        ChatBoxManager.unregister(tile);
    }

    @Test
    void dispatchDeath_withKillerQueuesCorrectParams() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);

        ChatBoxManager.dispatchDeath("Steve", "Alex", "player");

        verify(computer).queueEvent("death", new Object[] { "Steve", "Alex", "player" });

        ChatBoxManager.unregister(tile);
    }

    @Test
    void dispatchDeath_withoutKillerQueuesEmptyKiller() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);

        ChatBoxManager.dispatchDeath("Steve", "", "fall");

        verify(computer).queueEvent("death", new Object[] { "Steve", "", "fall" });

        ChatBoxManager.unregister(tile);
    }

    @Test
    void dispatchCommand_queuesOneBased1TableOfTokens() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);

        Map<Integer, String> args = new java.util.LinkedHashMap<>();
        args.put(1, "teleport");
        args.put(2, "Steve");
        args.put(3, "0");
        args.put(4, "64");
        args.put(5, "0");

        ChatBoxManager.dispatchCommand("Alex", args);

        verify(computer).queueEvent("command", new Object[] { "Alex", args });

        ChatBoxManager.unregister(tile);
    }

    @Test
    void dispatchChat_fanOutToMultipleTiles() {
        TileChatBox tileA = makeTile();
        TileChatBox tileB = makeTile();
        IComputerAccess compA = attachMock(tileA);
        IComputerAccess compB = attachMock(tileB);

        ChatBoxManager.dispatchChat("Player", "hi");

        verify(compA).queueEvent("chat", new Object[] { "Player", "hi" });
        verify(compB).queueEvent("chat", new Object[] { "Player", "hi" });

        ChatBoxManager.unregister(tileA);
        ChatBoxManager.unregister(tileB);
    }

    @Test
    void unregisteredTile_receivesNoEvents() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);
        ChatBoxManager.unregister(tile);

        ChatBoxManager.dispatchChat("X", "msg");

        verify(computer, never()).queueEvent(any(), any());
    }

    // -------------------------------------------------------------------------
    // ChatBoxPeripheral argument-parsing tests (via package-private helpers)
    // -------------------------------------------------------------------------

    /** Exercises the static parseRange logic directly (package-private). */
    @Test
    void parseRange_defaultsToConfigValue() throws Exception {
        ComputerCraft.chatbox_max_range = 100;
        double range = ChatBoxPeripheral.parseRange(new Object[0], 0);
        assertEquals(100.0, range);
    }

    @Test
    void parseRange_negativeOnePassedThrough_whenConfigIsInfinite() throws Exception {
        ComputerCraft.chatbox_max_range = -1;
        double range = ChatBoxPeripheral.parseRange(new Object[] { -1.0 }, 0);
        assertEquals(-1.0, range);
    }

    @Test
    void parseRange_cappedToConfigMax() throws Exception {
        ComputerCraft.chatbox_max_range = 50;
        double range = ChatBoxPeripheral.parseRange(new Object[] { 200.0 }, 0);
        assertEquals(50.0, range);
    }

    @Test
    void parseRange_throwsOnNonNumber() {
        assertThrows(
            dan200.computercraft.api.lua.LuaException.class,
            () -> ChatBoxPeripheral.parseRange(new Object[] { "bad" }, 0));
    }

    @Test
    void requireText_throwsOnEmpty() {
        assertThrows(
            dan200.computercraft.api.lua.LuaException.class,
            () -> ChatBoxPeripheral.requireText(new Object[] { "" }, 0));
    }

    @Test
    void requireText_throwsOnTooLong() {
        char[] chars = new char[257];
        java.util.Arrays.fill(chars, 'A');
        String longText = new String(chars);
        assertThrows(
            dan200.computercraft.api.lua.LuaException.class,
            () -> ChatBoxPeripheral.requireText(new Object[] { longText }, 0));
    }

    @Test
    void requireText_throwsOnMissing() {
        assertThrows(
            dan200.computercraft.api.lua.LuaException.class,
            () -> ChatBoxPeripheral.requireText(new Object[0], 0));
    }

    @Test
    void requireText_acceptsValidText() throws Exception {
        String result = ChatBoxPeripheral.requireText(new Object[] { "Hello!" }, 0);
        assertEquals("Hello!", result);
    }

    @Test
    void parseLabel_throwsOnTooLong() {
        char[] chars = new char[33];
        java.util.Arrays.fill(chars, 'A');
        String longLabel = new String(chars);
        assertThrows(
            dan200.computercraft.api.lua.LuaException.class,
            () -> ChatBoxPeripheral.parseLabel(new Object[] { longLabel }, 0));
    }

    @Test
    void parseLabel_acceptsLabelAtMaxLength() throws Exception {
        char[] chars = new char[32];
        java.util.Arrays.fill(chars, 'A');
        String maxLabel = new String(chars);
        assertEquals(maxLabel, ChatBoxPeripheral.parseLabel(new Object[] { maxLabel }, 0));
    }

    @Test
    void parseLabel_defaultsToHash() throws Exception {
        String label = ChatBoxPeripheral.parseLabel(new Object[0], 0);
        assertEquals("#", label);
    }

    @Test
    void parseLabel_usesProvidedValue() throws Exception {
        String label = ChatBoxPeripheral.parseLabel(new Object[] { "MyBox" }, 0);
        assertEquals("MyBox", label);
    }

    // -------------------------------------------------------------------------
    // CustomNPCs NPC event dispatch tests
    // -------------------------------------------------------------------------

    @Test
    void dispatchNpcInteract_queuesEventOnAttachedComputer() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);

        ChatBoxManager.dispatchNpcInteract("Steve", "Bob");

        verify(computer).queueEvent("npc_interact", new Object[] { "Steve", "Bob" });

        ChatBoxManager.unregister(tile);
    }

    @Test
    void dispatchNpcDialog_queuesEventWithDialogAndOptionIds() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);

        ChatBoxManager.dispatchNpcDialog("Steve", "ShopKeeper", 42, 3);

        verify(computer).queueEvent("npc_dialog", new Object[] { "Steve", "ShopKeeper", 42, 3 });

        ChatBoxManager.unregister(tile);
    }

    @Test
    void dispatchNpcDialogClosed_queuesEventWithDialogAndOptionIds() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);

        ChatBoxManager.dispatchNpcDialogClosed("Steve", "ShopKeeper", 42, 3);

        verify(computer).queueEvent("npc_dialog_closed", new Object[] { "Steve", "ShopKeeper", 42, 3 });

        ChatBoxManager.unregister(tile);
    }

    @Test
    void dispatchNpcDied_queuesEventWithKillerAndDamageType() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);

        ChatBoxManager.dispatchNpcDied("Bob", "Steve", "player");

        verify(computer).queueEvent("npc_died", new Object[] { "Bob", "Steve", "player" });

        ChatBoxManager.unregister(tile);
    }

    @Test
    void dispatchNpcInteract_doesNotFireOnUnregisteredTile() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);
        ChatBoxManager.unregister(tile);

        ChatBoxManager.dispatchNpcInteract("Steve", "Bob");

        verify(computer, never()).queueEvent(any(), any());
    }

    @Test
    void dispatchNpcInteract_fansOutToMultipleTiles() {
        TileChatBox tileA = makeTile();
        TileChatBox tileB = makeTile();
        IComputerAccess compA = attachMock(tileA);
        IComputerAccess compB = attachMock(tileB);

        ChatBoxManager.dispatchNpcInteract("Player", "Guard");

        verify(compA).queueEvent("npc_interact", new Object[] { "Player", "Guard" });
        verify(compB).queueEvent("npc_interact", new Object[] { "Player", "Guard" });

        ChatBoxManager.unregister(tileA);
        ChatBoxManager.unregister(tileB);
    }

    @Test
    void dispatchNpcSpawned_queuesEventOnAttachedComputer() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);

        ChatBoxManager.dispatchNpcSpawned("Guard");

        verify(computer).queueEvent("npc_spawned", new Object[] { "Guard" });

        ChatBoxManager.unregister(tile);
    }

    @Test
    void dispatchNpcDamaged_queuesEventWithAllParams() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);

        ChatBoxManager.dispatchNpcDamaged("Guard", "Steve", 4.5f, "player");

        verify(computer).queueEvent("npc_damaged", new Object[] { "Guard", "Steve", 4.5f, "player" });

        ChatBoxManager.unregister(tile);
    }

    @Test
    void dispatchNpcKilledEntity_queuesEventWithNpcAndEntityName() {
        TileChatBox tile = makeTile();
        IComputerAccess computer = attachMock(tile);

        ChatBoxManager.dispatchNpcKilledEntity("Guard", "Zombie", "zombie");

        verify(computer).queueEvent("npc_killed_entity", new Object[] { "Guard", "Zombie", "zombie" });

        ChatBoxManager.unregister(tile);
    }
}
