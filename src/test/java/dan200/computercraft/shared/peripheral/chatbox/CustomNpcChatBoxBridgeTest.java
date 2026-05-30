package dan200.computercraft.shared.peripheral.chatbox;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.compat.customnpcs.chatbox.CustomNpcChatBoxBridge;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.scripted.event.NpcEvent;

/**
 * Unit tests for {@link CustomNpcChatBoxBridge}.
 *
 * <p>
 * Handlers are registered against the concrete {@code NpcEvent.*} classes
 * (not the {@code INpcEvent.*} interfaces) because FML's EventBus dispatches
 * by concrete class hierarchy. Mockito mocks the concrete classes directly,
 * bypassing their constructors.
 * </p>
 */
class CustomNpcChatBoxBridgeTest {

    private CustomNpcChatBoxBridge bridge;
    private TileChatBox tile;
    private IComputerAccess computer;

    @BeforeEach
    void setUp() {
        bridge = new CustomNpcChatBoxBridge();
        tile = new TileChatBox();
        computer = mock(IComputerAccess.class);
        ChatBoxManager.register(tile);
        tile.attachComputer(computer);
    }

    @AfterEach
    void tearDown() {
        ChatBoxManager.unregister(tile);
    }

    // -------------------------------------------------------------------------
    // InteractEvent
    // -------------------------------------------------------------------------

    @Test
    void onNpcInteract_queuesNpcInteractEvent() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        IPlayer<?> player = mock(IPlayer.class);
        NpcEvent.InteractEvent event = mock(NpcEvent.InteractEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getPlayer()).thenReturn(player);
        when(npc.getName()).thenReturn("Guard");
        when(player.getName()).thenReturn("Steve");

        bridge.onNpcInteract(event);

        verify(computer).queueEvent("cnpc_interact", new Object[] { "Steve", "Guard" });
    }

    // -------------------------------------------------------------------------
    // DialogEvent
    // -------------------------------------------------------------------------

    @Test
    void onNpcDialog_queuesNpcDialogEventWithIds() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        IPlayer<?> player = mock(IPlayer.class);
        NpcEvent.DialogEvent event = mock(NpcEvent.DialogEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getPlayer()).thenReturn(player);
        when(npc.getName()).thenReturn("ShopKeeper");
        when(player.getName()).thenReturn("Steve");
        when(event.getDialogId()).thenReturn(42);
        when(event.getOptionId()).thenReturn(3);

        bridge.onNpcDialog(event);

        verify(computer).queueEvent("cnpc_dialog", new Object[] { "Steve", "ShopKeeper", 42, 3 });
    }

    // -------------------------------------------------------------------------
    // DialogClosedEvent
    // -------------------------------------------------------------------------

    @Test
    void onNpcDialogClosed_queuesNpcDialogClosedEvent() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        IPlayer<?> player = mock(IPlayer.class);
        NpcEvent.DialogClosedEvent event = mock(NpcEvent.DialogClosedEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getPlayer()).thenReturn(player);
        when(npc.getName()).thenReturn("ShopKeeper");
        when(player.getName()).thenReturn("Steve");
        when(event.getDialogId()).thenReturn(42);
        when(event.getOptionId()).thenReturn(3);

        bridge.onNpcDialogClosed(event);

        verify(computer).queueEvent("cnpc_dialog_closed", new Object[] { "Steve", "ShopKeeper", 42, 3 });
    }

    // -------------------------------------------------------------------------
    // DiedEvent
    // -------------------------------------------------------------------------

    @Test
    void onNpcDied_killedByPlayer_usesPlayerUsername() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        IPlayer<?> player = mock(IPlayer.class);
        NpcEvent.DiedEvent event = mock(NpcEvent.DiedEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getSource()).thenReturn(player);
        when(event.getType()).thenReturn("player");
        when(npc.getName()).thenReturn("Guard");
        when(player.getName()).thenReturn("Steve");

        bridge.onNpcDied(event);

        verify(computer).queueEvent("cnpc_died", new Object[] { "Guard", "Steve", "player" });
    }

    @Test
    void onNpcDied_killedByCustomNpc_usesNpcDisplayName() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        ICustomNpc<?> killerNpc = mock(ICustomNpc.class);
        NpcEvent.DiedEvent event = mock(NpcEvent.DiedEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getSource()).thenReturn(killerNpc);
        when(event.getType()).thenReturn("mob");
        when(npc.getName()).thenReturn("Villager");
        when(killerNpc.getName()).thenReturn("Bandit");
        when(killerNpc.getTypeName()).thenReturn("CustomNPC");

        bridge.onNpcDied(event);

        verify(computer).queueEvent("cnpc_died", new Object[] { "Villager", "Bandit", "customnpc" });
    }

    @Test
    void onNpcDied_killedByNameTaggedMob_usesCustomName() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        IEntity<?> mob = mock(IEntity.class);
        net.minecraft.entity.Entity mcEntity = mock(net.minecraft.entity.Entity.class);
        NpcEvent.DiedEvent event = mock(NpcEvent.DiedEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getSource()).thenReturn(mob);
        when(event.getType()).thenReturn("mob");
        when(npc.getName()).thenReturn("Guard");
        when(mob.getMCEntity()).thenReturn(mcEntity);
        when(mcEntity.getCommandSenderName()).thenReturn("Rex");
        when(mob.getTypeName()).thenReturn("Wolf");

        bridge.onNpcDied(event);

        verify(computer).queueEvent("cnpc_died", new Object[] { "Guard", "Rex", "wolf" });
    }

    @Test
    void onNpcDied_killedByMob_usesCommandSenderName() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        IEntity<?> mob = mock(IEntity.class);
        net.minecraft.entity.Entity mcEntity = mock(net.minecraft.entity.Entity.class);
        NpcEvent.DiedEvent event = mock(NpcEvent.DiedEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getSource()).thenReturn(mob);
        when(event.getType()).thenReturn("mob");
        when(npc.getName()).thenReturn("Guard");
        when(mob.getMCEntity()).thenReturn(mcEntity);
        when(mcEntity.getCommandSenderName()).thenReturn("Zombie");
        when(mob.getTypeName()).thenReturn("Zombie");

        bridge.onNpcDied(event);

        verify(computer).queueEvent("cnpc_died", new Object[] { "Guard", "Zombie", "zombie" });
    }

    @Test
    void onNpcDied_withNullSource_usesEmptyKillerName() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        NpcEvent.DiedEvent event = mock(NpcEvent.DiedEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getSource()).thenReturn(null);
        when(event.getType()).thenReturn("fall");
        when(npc.getName()).thenReturn("Guard");

        bridge.onNpcDied(event);

        verify(computer).queueEvent("cnpc_died", new Object[] { "Guard", "", "fall" });
    }

    // -------------------------------------------------------------------------
    // InitEvent
    // -------------------------------------------------------------------------

    @Test
    void onNpcSpawned_queuesNpcSpawnedEvent() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        NpcEvent.InitEvent event = mock(NpcEvent.InitEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(npc.getName()).thenReturn("Guard");

        bridge.onNpcSpawned(event);

        verify(computer).queueEvent("cnpc_spawned", new Object[] { "Guard" });
    }

    // -------------------------------------------------------------------------
    // DamagedEvent
    // -------------------------------------------------------------------------

    @Test
    void onNpcDamaged_attackedByPlayer_usesPlayerUsername() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        IPlayer<?> player = mock(IPlayer.class);
        NpcEvent.DamagedEvent event = mock(NpcEvent.DamagedEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getSource()).thenReturn(player);
        when(event.getDamage()).thenReturn(4.5f);
        when(event.getType()).thenReturn("player");
        when(npc.getName()).thenReturn("Guard");
        when(player.getName()).thenReturn("Steve");

        bridge.onNpcDamaged(event);

        verify(computer).queueEvent("cnpc_damaged", new Object[] { "Guard", "Steve", 4.5f, "player" });
    }

    @Test
    void onNpcDamaged_attackedByCustomNpc_usesNpcDisplayName() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        ICustomNpc<?> attackerNpc = mock(ICustomNpc.class);
        NpcEvent.DamagedEvent event = mock(NpcEvent.DamagedEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getSource()).thenReturn(attackerNpc);
        when(event.getDamage()).thenReturn(3.0f);
        when(event.getType()).thenReturn("mob");
        when(npc.getName()).thenReturn("Villager");
        when(attackerNpc.getName()).thenReturn("Bandit");
        when(attackerNpc.getTypeName()).thenReturn("CustomNPC");

        bridge.onNpcDamaged(event);

        verify(computer).queueEvent("cnpc_damaged", new Object[] { "Villager", "Bandit", 3.0f, "customnpc" });
    }

    @Test
    void onNpcDamaged_attackedByMob_usesCommandSenderName() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        IEntity<?> mob = mock(IEntity.class);
        net.minecraft.entity.Entity mcEntity = mock(net.minecraft.entity.Entity.class);
        NpcEvent.DamagedEvent event = mock(NpcEvent.DamagedEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getSource()).thenReturn(mob);
        when(event.getDamage()).thenReturn(2.0f);
        when(event.getType()).thenReturn("mob");
        when(npc.getName()).thenReturn("Guard");
        when(mob.getMCEntity()).thenReturn(mcEntity);
        when(mcEntity.getCommandSenderName()).thenReturn("Zombie");
        when(mob.getTypeName()).thenReturn("Zombie");

        bridge.onNpcDamaged(event);

        verify(computer).queueEvent("cnpc_damaged", new Object[] { "Guard", "Zombie", 2.0f, "zombie" });
    }

    @Test
    void onNpcDamaged_withNullSource_usesEmptyAttackerName() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        NpcEvent.DamagedEvent event = mock(NpcEvent.DamagedEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getSource()).thenReturn(null);
        when(event.getDamage()).thenReturn(2.0f);
        when(event.getType()).thenReturn("fall");
        when(npc.getName()).thenReturn("Guard");

        bridge.onNpcDamaged(event);

        verify(computer).queueEvent("cnpc_damaged", new Object[] { "Guard", "", 2.0f, "fall" });
    }

    // -------------------------------------------------------------------------
    // KilledEntityEvent
    // -------------------------------------------------------------------------

    @Test
    void onNpcKilledEntity_queuesNpcKilledEntityEvent() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        noppes.npcs.api.entity.IEntityLivingBase<?> entity = mock(noppes.npcs.api.entity.IEntityLivingBase.class);
        net.minecraft.entity.EntityLivingBase mcEntity = mock(net.minecraft.entity.EntityLivingBase.class);
        NpcEvent.KilledEntityEvent event = mock(NpcEvent.KilledEntityEvent.class);

        when(event.getNpc()).thenReturn(npc);
        when(event.getEntity()).thenReturn(entity);
        when(npc.getName()).thenReturn("Guard");
        when(entity.getMCEntity()).thenReturn(mcEntity);
        when(mcEntity.getCommandSenderName()).thenReturn("Zombie");
        when(entity.getTypeName()).thenReturn("Zombie");

        bridge.onNpcKilledEntity(event);

        verify(computer).queueEvent("cnpc_killed_entity", new Object[] { "Guard", "Zombie", "zombie" });
    }
}
