package dan200.computercraft.compat.customnpcs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import dan200.computercraft.compat.customnpcs.peripheral.npcinterface.NpcInterfacePeripheral;
import dan200.computercraft.compat.customnpcs.peripheral.npcinterface.TileNpcInterface;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;

class LoadedNpcIndexTest {

    private static ICustomNpc<?> npc(String uuid) {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        when(npc.getUniqueID()).thenReturn(uuid);
        return npc;
    }

    @Test
    void repeatedHitsAndMissesShareOneScan() {
        ICustomNpc<?> first = npc("first");
        ICustomNpc<?> second = npc("second");
        AtomicInteger scans = new AtomicInteger();
        LoadedNpcIndex index = new LoadedNpcIndex(() -> {
            scans.incrementAndGet();
            return new IEntity<?>[] { first, second };
        });
        for (int i = 0; i < 100; i++) {
            assertSame(first, index.find("first"));
            assertSame(second, index.find("second"));
            assertNull(index.find("missing"));
            assertEquals(Arrays.asList(first, second), index.findAll(Arrays.asList("second", "missing", "first")));
        }
        assertEquals(1, scans.get());
        verify(first, times(1)).getUniqueID();
        verify(second, times(1)).getUniqueID();
    }

    @Test
    void onlyNpcsAreIndexedAndFirstDuplicateWins() {
        IEntity<?> other = mock(IEntity.class);
        when(other.getUniqueID()).thenReturn("id");
        ICustomNpc<?> first = npc("id");
        ICustomNpc<?> duplicate = npc("id");
        LoadedNpcIndex index = new LoadedNpcIndex(() -> new IEntity<?>[] { null, other, first, duplicate });
        assertSame(first, index.find("id"));
        verify(other, never()).getUniqueID();
    }

    @Test
    void emptyRequestsDoNotBuildIndexAndReturnedListsDoNotMutateCache() {
        AtomicInteger scans = new AtomicInteger();
        ICustomNpc<?> npc = npc("id");
        LoadedNpcIndex index = new LoadedNpcIndex(() -> {
            scans.incrementAndGet();
            return new IEntity<?>[] { npc };
        });
        assertNull(index.find(null));
        assertTrue(index.findAll(Collections.emptySet()).isEmpty());
        assertEquals(0, scans.get());
        index.findAll(Collections.singleton("id")).clear();
        assertSame(npc, index.find("id"));
    }

    @Test
    void nextTickRefreshesMissingRemovedAndReplacedNpcs() {
        ICustomNpc<?> old = npc("id");
        ICustomNpc<?> replacement = npc("id");
        ICustomNpc<?> added = npc("new");
        AtomicReference<IEntity<?>[]> entities = new AtomicReference<>(new IEntity<?>[] { old });
        LoadedNpcIndex index = new LoadedNpcIndex(entities::get);
        assertSame(old, index.find("id"));
        assertNull(index.find("new"));
        entities.set(new IEntity<?>[] { replacement, added });
        // Membership is explicitly a per-tick snapshot, including negative lookups.
        assertSame(old, index.find("id"));
        assertNull(index.find("new"));
        index.onServerTick(new ServerTickEvent(Phase.START));
        assertSame(replacement, index.find("id"));
        assertSame(added, index.find("new"));
        entities.set(new IEntity<?>[0]);
        index.onServerTick(new ServerTickEvent(Phase.START));
        assertNull(index.find("id"));
        assertNull(index.find("new"));
    }

    @Test
    void livePropertiesAndDeadNpcRemainAvailableForCallerValidation() {
        ICustomNpc<?> npc = npc("id");
        LoadedNpcIndex index = new LoadedNpcIndex(() -> new IEntity<?>[] { npc });
        when(npc.getName()).thenReturn("before");
        when(npc.isAlive()).thenReturn(true);
        assertEquals("before", index.find("id").getName());
        when(npc.getName()).thenReturn("after");
        when(npc.isAlive()).thenReturn(false);
        assertEquals("after", index.find("id").getName());
        assertFalse(index.find("id").isAlive());
    }

    @Test
    void tickEndReleasesReferencesEvenWithoutAnotherLookup() throws Exception {
        ICustomNpc<?> npc = npc("id");
        AtomicInteger scans = new AtomicInteger();
        LoadedNpcIndex index = new LoadedNpcIndex(() -> {
            scans.incrementAndGet();
            return new IEntity<?>[] { npc };
        });
        // Forge's bus registration requires LaunchClassLoader; check the subscription
        // metadata and invoke handlers directly in this plain JUnit environment.
        assertEquals(EventPriority.HIGHEST, LoadedNpcIndex.class.getMethod("onServerTick", ServerTickEvent.class)
            .getAnnotation(SubscribeEvent.class).priority());
        assertEquals(EventPriority.LOWEST, LoadedNpcIndex.class.getMethod("onServerTickEnd", ServerTickEvent.class)
            .getAnnotation(SubscribeEvent.class).priority());
        index.onServerTick(new ServerTickEvent(Phase.START));
        assertEquals(0, scans.get());
        assertSame(npc, index.find("id"));
        index.onServerTickEnd(new ServerTickEvent(Phase.END));
        assertTrue(entries(index).isEmpty());
        index.onServerTick(new ServerTickEvent(Phase.START));
        index.onServerTickEnd(new ServerTickEvent(Phase.END));
        assertEquals(1, scans.get());
        assertSame(npc, index.find("id"));
        assertEquals(2, scans.get());
    }

    @Test
    void unavailableSourceIsCachedForTickAndRetriedAfterClear() {
        AtomicInteger scans = new AtomicInteger();
        ICustomNpc<?> npc = npc("id");
        LoadedNpcIndex index = new LoadedNpcIndex(() -> {
            if (scans.incrementAndGet() == 1) throw new NoClassDefFoundError("optional CNPC");
            return new IEntity<?>[] { npc };
        });
        assertNull(index.find("id"));
        assertNull(index.find("id"));
        assertEquals(1, scans.get());
        index.clear();
        assertSame(npc, index.find("id"));
        assertEquals(2, scans.get());
    }

    @Test
    void nullSourceAndPartialFailureDoNotExposePartialResults() {
        assertNull(new LoadedNpcIndex(() -> null).find("id"));
        ICustomNpc<?> bad = npc("bad");
        when(bad.getUniqueID()).thenThrow(new IllegalStateException("incompatible wrapper"));
        LoadedNpcIndex index = new LoadedNpcIndex(() -> new IEntity<?>[] { npc("id"), bad });
        assertNull(index.find("id"));
        assertNull(index.find("bad"));
    }

    @Test
    void tileAndPeripheralUseSharedIndexAndUnlinkDoesNotEvictOtherUsers() throws Exception {
        ICustomNpc<?> npc = npc("id");
        LoadedNpcIndex local = new LoadedNpcIndex(() -> new IEntity<?>[] { npc });
        local.find("id");
        LoadedNpcIndex shared = LoadedNpcIndex.instance();
        shared.clear();
        // Seed a snapshot, not the optional static CNPC API; production callers must use it.
        entries(shared).putAll(entries(local));
        Field loaded = LoadedNpcIndex.class.getDeclaredField("m_loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(shared, true);
        TileNpcInterface tile = new TileNpcInterface();
        try {
            tile.setLink("id", "name");
            assertSame(npc, tile.resolveNpc());
            assertEquals(Collections.singletonList(npc), tile.resolveNpcs());
            NpcInterfacePeripheral peripheral = new NpcInterfacePeripheral(tile);
            var resolve = NpcInterfacePeripheral.class.getDeclaredMethod("resolveByUUID", String.class);
            resolve.setAccessible(true);
            assertSame(npc, resolve.invoke(peripheral, "id"));
            tile.clearLinks();
            assertNull(tile.resolveNpc());
            assertTrue(tile.resolveNpcs().isEmpty());
            assertSame(npc, shared.find("id"));
        } finally {
            tile.clearLinks();
            shared.clear();
        }
        assertTrue(entries(shared).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entries(LoadedNpcIndex index) throws Exception {
        Field field = LoadedNpcIndex.class.getDeclaredField("m_byUUID");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(index);
    }
}
