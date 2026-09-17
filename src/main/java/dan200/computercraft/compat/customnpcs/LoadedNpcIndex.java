package dan200.computercraft.compat.customnpcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;

/**
 * Shared, lazy index of loaded NPCs across all dimensions. Main-server-thread only.
 * Membership (including misses) is a snapshot until the next tick boundary; NPC
 * properties remain live. Both tick phases clear references, including on idle ticks.
 * Registered only when CustomNPCs is available.
 */
public final class LoadedNpcIndex {

    private static final LoadedNpcIndex INSTANCE = new LoadedNpcIndex(() -> {
        if (!AbstractNpcAPI.IsAvailable()) return new IEntity<?>[0];
        AbstractNpcAPI api = AbstractNpcAPI.Instance();
        return api == null ? new IEntity<?>[0] : api.getLoadedEntities();
    });

    private final Supplier<IEntity<?>[]> m_source;
    private final Map<String, Entry> m_byUUID = new HashMap<>();
    private boolean m_loaded;

    public static LoadedNpcIndex instance() {
        return INSTANCE;
    }

    LoadedNpcIndex(Supplier<IEntity<?>[]> source) {
        m_source = source;
    }

    public ICustomNpc<?> find(String uuid) {
        if (uuid == null) return null;
        load();
        Entry entry = m_byUUID.get(uuid);
        return entry == null ? null : entry.npc;
    }

    /** Resolve only linked UUIDs, retaining loaded-entity order for broadcast actions. */
    public List<ICustomNpc<?>> findAll(Collection<String> uuids) {
        List<ICustomNpc<?>> result = new ArrayList<>();
        if (uuids.isEmpty()) return result;
        load();
        List<Entry> entries = new ArrayList<>();
        for (String uuid : uuids) {
            Entry entry = m_byUUID.get(uuid);
            if (entry != null) entries.add(entry);
        }
        entries.sort(Comparator.comparingInt(entry -> entry.order));
        for (Entry entry : entries) result.add(entry.npc);
        return result;
    }

    private void load() {
        if (m_loaded) return;
        // Mark even failed/empty loads complete: missing NPC polling must not rescan.
        m_loaded = true;
        try {
            IEntity<?>[] entities = m_source.get();
            if (entities == null) return;
            for (int i = 0; i < entities.length; i++) {
                IEntity<?> entity = entities[i];
                if (entity instanceof ICustomNpc) {
                    m_byUUID.putIfAbsent(entity.getUniqueID(), new Entry((ICustomNpc<?>) entity, i));
                }
            }
        } catch (Throwable t) {
            // Preserve optional-CNPC resolution fallback without exposing a partial index.
            m_byUUID.clear();
        }
    }

    public void clear() {
        m_byUUID.clear();
        m_loaded = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerTick(ServerTickEvent event) {
        if (event.phase == Phase.START) clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTickEnd(ServerTickEvent event) {
        if (event.phase == Phase.END) clear();
    }

    private static final class Entry {
        final ICustomNpc<?> npc;
        final int order;

        Entry(ICustomNpc<?> npc, int order) {
            this.npc = npc;
            this.order = order;
        }
    }
}
