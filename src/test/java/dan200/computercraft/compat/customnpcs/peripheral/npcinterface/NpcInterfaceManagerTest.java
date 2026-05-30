package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NpcInterfaceManager}.
 *
 * <p>
 * Verifies event routing to registered tiles without a running Minecraft world.
 * </p>
 */
class NpcInterfaceManagerTest {

    private TileNpcInterface tileAbc;
    private TileNpcInterface tileXyz;

    @BeforeEach
    void setUp() {
        tileAbc = mock(TileNpcInterface.class);
        tileXyz = mock(TileNpcInterface.class);
    }

    @Test
    void dispatch_noRegistrations_doesNothing() {
        // Should not throw; no tiles registered for "unknown"
        NpcInterfaceManager.dispatchInteract("unknown", "Steve");
    }

    @Test
    void dispatchInteract_routesToRegisteredTile() {
        NpcInterfaceManager.register("abc", tileAbc);
        try {
            NpcInterfaceManager.dispatchInteract("abc", "Steve");
            verify(tileAbc).queueNpcEvent("npc_interact", "Steve");
        } finally {
            NpcInterfaceManager.unregister("abc", tileAbc);
        }
    }

    @Test
    void dispatch_afterUnregister_doesNotFire() {
        NpcInterfaceManager.register("abc", tileAbc);
        NpcInterfaceManager.unregister("abc", tileAbc);
        NpcInterfaceManager.dispatchInteract("abc", "Steve");
        verify(tileAbc, never()).queueNpcEvent(any(), any());
    }

    @Test
    void dispatch_twoTilesSameUUID_bothReceive() {
        NpcInterfaceManager.register("abc", tileAbc);
        NpcInterfaceManager.register("abc", tileXyz);
        try {
            NpcInterfaceManager.dispatchInteract("abc", "Steve");
            verify(tileAbc).queueNpcEvent("npc_interact", "Steve");
            verify(tileXyz).queueNpcEvent("npc_interact", "Steve");
        } finally {
            NpcInterfaceManager.unregister("abc", tileAbc);
            NpcInterfaceManager.unregister("abc", tileXyz);
        }
    }

    @Test
    void dispatch_differentUUID_onlyMatchingTileFires() {
        NpcInterfaceManager.register("abc", tileAbc);
        NpcInterfaceManager.register("xyz", tileXyz);
        try {
            NpcInterfaceManager.dispatchInteract("abc", "Steve");
            verify(tileAbc).queueNpcEvent("npc_interact", "Steve");
            verify(tileXyz, never()).queueNpcEvent(any(), any());
        } finally {
            NpcInterfaceManager.unregister("abc", tileAbc);
            NpcInterfaceManager.unregister("xyz", tileXyz);
        }
    }

    @Test
    void dispatchDialog_routesCorrectArgs() {
        NpcInterfaceManager.register("abc", tileAbc);
        try {
            NpcInterfaceManager.dispatchDialog("abc", "Steve", 5, 2);
            verify(tileAbc).queueNpcEvent("npc_dialog", "Steve", 5, 2);
        } finally {
            NpcInterfaceManager.unregister("abc", tileAbc);
        }
    }

    @Test
    void dispatchDialogClosed_routesCorrectArgs() {
        NpcInterfaceManager.register("abc", tileAbc);
        try {
            NpcInterfaceManager.dispatchDialogClosed("abc", "Steve", 5, 2);
            verify(tileAbc).queueNpcEvent("npc_dialog_closed", "Steve", 5, 2);
        } finally {
            NpcInterfaceManager.unregister("abc", tileAbc);
        }
    }

    @Test
    void dispatchDamaged_routesCorrectArgs() {
        NpcInterfaceManager.register("abc", tileAbc);
        try {
            NpcInterfaceManager.dispatchDamaged("abc", "zombie", 4.0, "mob");
            verify(tileAbc).queueNpcEvent("npc_damaged", "zombie", 4.0, "mob");
        } finally {
            NpcInterfaceManager.unregister("abc", tileAbc);
        }
    }

    @Test
    void dispatchDied_routesCorrectArgs() {
        NpcInterfaceManager.register("abc", tileAbc);
        try {
            NpcInterfaceManager.dispatchDied("abc", "player", "player");
            verify(tileAbc).queueNpcEvent("npc_died", "player", "player");
        } finally {
            NpcInterfaceManager.unregister("abc", tileAbc);
        }
    }

    @Test
    void dispatchTarget_routesCorrectArgs() {
        NpcInterfaceManager.register("abc", tileAbc);
        try {
            NpcInterfaceManager.dispatchTarget("abc", "Creeper");
            verify(tileAbc).queueNpcEvent("npc_target", "Creeper");
        } finally {
            NpcInterfaceManager.unregister("abc", tileAbc);
        }
    }

    @Test
    void dispatchTargetLost_routesNoParams() {
        NpcInterfaceManager.register("abc", tileAbc);
        try {
            NpcInterfaceManager.dispatchTargetLost("abc");
            verify(tileAbc).queueNpcEvent("npc_target_lost");
        } finally {
            NpcInterfaceManager.unregister("abc", tileAbc);
        }
    }

    @Test
    void dispatchTick_routesNoParams() {
        NpcInterfaceManager.register("abc", tileAbc);
        try {
            NpcInterfaceManager.dispatchTick("abc");
            verify(tileAbc).queueNpcEvent("npc_tick");
        } finally {
            NpcInterfaceManager.unregister("abc", tileAbc);
        }
    }
}
