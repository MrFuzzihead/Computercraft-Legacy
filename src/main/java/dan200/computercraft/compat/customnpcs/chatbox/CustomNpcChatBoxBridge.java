package dan200.computercraft.compat.customnpcs.chatbox;

import net.minecraft.entity.EntityList;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dan200.computercraft.shared.peripheral.chatbox.ChatBoxManager;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.scripted.event.NpcEvent;

/**
 * Forge event listener that bridges CustomNPCs NPC lifecycle events into the
 * {@link ChatBoxManager} dispatch pipeline, so that CC computers receive them as Lua events
 * ({@code npc_interact}, {@code npc_dialog}, {@code npc_dialog_closed}, {@code npc_died},
 * {@code npc_spawned}, {@code npc_damaged}, {@code npc_killed_entity}).
 *
 * <p>
 * This class is only ever instantiated after an
 * {@code AbstractNpcAPI.IsAvailable()} guard in {@code ComputerCraft.postInit()},
 * so it is never loaded when CustomNPCs is absent.
 * </p>
 *
 * <p>
 * Handlers listen on the concrete {@code noppes.npcs.scripted.event.NpcEvent.*}
 * classes (not the {@code INpcEvent.*} interfaces) because FML's EventBus
 * dispatches by concrete class hierarchy and does not walk interfaces.
 * </p>
 */
public class CustomNpcChatBoxBridge {

    /**
     * Returns a human-readable name for an entity source.
     * <ul>
     * <li>Players ({@code IPlayer}) → username via {@code IPlayer#getName()}</li>
     * <li>CustomNPCs ({@code ICustomNpc}) → display name via {@code ICustomNpc#getName()}</li>
     * <li>Name-tagged mobs → custom name tag via {@code Entity#getCustomNameTag()}</li>
     * <li>Other entities → type name via {@code IEntity#getTypeName()}, falling back to {@code ""}</li>
     * <li>{@code null} (environmental damage) → {@code ""}</li>
     * </ul>
     */
    public static String entityName(IEntity<?> source) {
        if (source == null) return "";
        if (source instanceof IPlayer) return ((IPlayer<?>) source).getName();
        if (source instanceof ICustomNpc) return ((ICustomNpc<?>) source).getName();
        return entityNameFromMc(source);
    }

    /** Captures the wildcard so {@code getMCEntity()} compiles cleanly. */
    private static <T extends net.minecraft.entity.Entity> String entityNameFromMc(IEntity<T> source) {
        T mcEntity = source.getMCEntity();
        if (mcEntity != null) return mcEntity.getCommandSenderName();
        String typeName = source.getTypeName();
        return typeName != null ? typeName : "";
    }

    /**
     * Resolves the killer/attacker type string for events.
     *
     * <p>
     * When the raw type from CustomNPCs is {@code "mob"}, the actual entity type is
     * derived from the Minecraft entity registry ({@link EntityList#getEntityString}),
     * falling back to {@link IEntity#getTypeName()}, so scripts receive a meaningful
     * identifier (e.g. {@code "zombie"}, {@code "customnpc"}) instead of the generic
     * {@code "mob"}.
     * </p>
     *
     * <p>
     * Non-mob types (e.g. {@code "player"}, {@code "fall"}, {@code "arrow"}) are
     * returned unchanged.
     * </p>
     */
    public static String resolveKillerType(IEntity<?> source, String rawType) {
        if (!"mob".equals(rawType)) return rawType;
        if (source == null) return rawType;
        String derived = entityTypeString(source);
        return derived != null ? derived.toLowerCase(java.util.Locale.ROOT) : rawType;
    }

    /** Captures the wildcard so {@code getMCEntity()} and {@code EntityList} compile cleanly. */
    private static <T extends net.minecraft.entity.Entity> String entityTypeString(IEntity<T> source) {
        T mcEntity = source.getMCEntity();
        if (mcEntity != null) {
            // Prefer the Minecraft entity registry name (e.g. "Zombie", "CustomNPC")
            String registered = EntityList.getEntityString(mcEntity);
            if (registered != null) return registered;
        }
        // Fall back to the CustomNPCs type name (set by the CustomNPCs API)
        return source.getTypeName();
    }

    @SubscribeEvent
    public void onNpcInteract(NpcEvent.InteractEvent event) {
        String npcName = event.getNpc()
            .getName();
        String player = event.getPlayer()
            .getName();
        ChatBoxManager.dispatchNpcInteract(player, npcName);
    }

    @SubscribeEvent
    public void onNpcDialog(NpcEvent.DialogEvent event) {
        ChatBoxManager.dispatchNpcDialog(
            event.getPlayer()
                .getName(),
            event.getNpc()
                .getName(),
            event.getDialogId(),
            event.getOptionId());
    }

    @SubscribeEvent
    public void onNpcDialogClosed(NpcEvent.DialogClosedEvent event) {
        ChatBoxManager.dispatchNpcDialogClosed(
            event.getPlayer()
                .getName(),
            event.getNpc()
                .getName(),
            event.getDialogId(),
            event.getOptionId());
    }

    @SubscribeEvent
    public void onNpcDied(NpcEvent.DiedEvent event) {
        ChatBoxManager.dispatchNpcDied(
            event.getNpc()
                .getName(),
            entityName(event.getSource()),
            resolveKillerType(event.getSource(), event.getType()));
    }

    @SubscribeEvent
    public void onNpcSpawned(NpcEvent.InitEvent event) {
        ChatBoxManager.dispatchNpcSpawned(
            event.getNpc()
                .getName());
    }

    @SubscribeEvent
    public void onNpcDamaged(NpcEvent.DamagedEvent event) {
        ChatBoxManager.dispatchNpcDamaged(
            event.getNpc()
                .getName(),
            entityName(event.getSource()),
            event.getDamage(),
            resolveKillerType(event.getSource(), event.getType()));
    }

    @SubscribeEvent
    public void onNpcKilledEntity(NpcEvent.KilledEntityEvent event) {
        noppes.npcs.api.entity.IEntityLivingBase<?> entity = event.getEntity();
        String entityName = entity != null ? entityName(entity) : "";
        String entityType = entity != null ? resolveKillerType(entity, "mob") : "";
        ChatBoxManager.dispatchNpcKilledEntity(
            event.getNpc()
                .getName(),
            entityName,
            entityType);
    }
}
