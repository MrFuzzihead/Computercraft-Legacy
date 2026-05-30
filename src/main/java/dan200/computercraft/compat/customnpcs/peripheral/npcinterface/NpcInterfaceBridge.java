package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import java.util.concurrent.ConcurrentHashMap;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dan200.computercraft.compat.customnpcs.chatbox.CustomNpcChatBoxBridge;
import noppes.npcs.scripted.event.NpcEvent;

/**
 * Forge event listener that forwards CustomNPCs NPC events to
 * {@link NpcInterfaceManager} for routing to linked {@link TileNpcInterface} tiles.
 *
 * <p>
 * <b>Important:</b> handlers must declare concrete {@code NpcEvent.*} parameter types,
 * not the {@code INpcEvent.*} interfaces. FML's EventBus walks the concrete class
 * hierarchy only and will never invoke a handler declared on an interface.
 * </p>
 *
 * <p>
 * Registered alongside {@link CustomNpcChatBoxBridge} in {@code ComputerCraft#registerCustomNpcCompat}.
 * </p>
 */
public class NpcInterfaceBridge {

    /** Per-NPC game-tick counter used to throttle {@code npc_tick} events to every 20 ticks. */
    private final ConcurrentHashMap<String, Integer> m_tickCounters = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onInteract(NpcEvent.InteractEvent e) {
        NpcInterfaceManager.dispatchInteract(
            e.getNpc()
                .getUniqueID(),
            e.getPlayer()
                .getName());
    }

    @SubscribeEvent
    public void onDialog(NpcEvent.DialogEvent e) {
        NpcInterfaceManager.dispatchDialog(
            e.getNpc()
                .getUniqueID(),
            e.getPlayer()
                .getName(),
            e.getDialogId(),
            e.getOptionId());
    }

    @SubscribeEvent
    public void onDialogClosed(NpcEvent.DialogClosedEvent e) {
        NpcInterfaceManager.dispatchDialogClosed(
            e.getNpc()
                .getUniqueID(),
            e.getPlayer()
                .getName(),
            e.getDialogId(),
            e.getOptionId());
    }

    @SubscribeEvent
    public void onDamaged(NpcEvent.DamagedEvent e) {
        String src = e.getSource() != null ? CustomNpcChatBoxBridge.resolveKillerType(e.getSource(), e.getType()) : "";
        NpcInterfaceManager.dispatchDamaged(
            e.getNpc()
                .getUniqueID(),
            src,
            e.getDamage(),
            e.getType());
    }

    @SubscribeEvent
    public void onDied(NpcEvent.DiedEvent e) {
        String killer = e.getSource() != null ? CustomNpcChatBoxBridge.resolveKillerType(e.getSource(), e.getType())
            : "";
        NpcInterfaceManager.dispatchDied(
            e.getNpc()
                .getUniqueID(),
            killer,
            e.getType());
    }

    @SubscribeEvent
    public void onTarget(NpcEvent.TargetEvent e) {
        String name = e.getTarget() != null ? e.getTarget()
            .getTypeName() : "";
        NpcInterfaceManager.dispatchTarget(
            e.getNpc()
                .getUniqueID(),
            name);
    }

    @SubscribeEvent
    public void onTargetLost(NpcEvent.TargetLostEvent e) {
        NpcInterfaceManager.dispatchTargetLost(
            e.getNpc()
                .getUniqueID());
    }

    @SubscribeEvent
    public void onTick(NpcEvent.UpdateEvent e) {
        String uuid = e.getNpc()
            .getUniqueID();
        int count = m_tickCounters.merge(uuid, 1, Integer::sum);
        if (count % 20 == 0) {
            NpcInterfaceManager.dispatchTick(uuid);
        }
    }
}
