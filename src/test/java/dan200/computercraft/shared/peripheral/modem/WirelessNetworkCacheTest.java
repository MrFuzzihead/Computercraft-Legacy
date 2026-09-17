package dan200.computercraft.shared.peripheral.modem;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

class WirelessNetworkCacheTest {

    @Test
    void nullWorldAndGlobalNetworkContractsAreUnchanged() {
        assertNull(WirelessNetwork.get(null));
        assertSame(WirelessNetwork.getGlobal(), WirelessNetwork.getGlobal());
        World first = mock(World.class);
        World second = mock(World.class);
        assertSame(WirelessNetwork.get(first), WirelessNetwork.get(first));
        assertNotSame(WirelessNetwork.get(first), WirelessNetwork.get(second));
        assertNotSame(WirelessNetwork.get(first), WirelessNetwork.getGlobal());
    }

    @Test
    void cacheLookupAndCreationShareClassMonitor() throws Exception {
        assertTrue(
            Modifier.isSynchronized(
                WirelessNetwork.class.getMethod("get", World.class)
                    .getModifiers()));
    }

    @Test
    void concurrentFirstLookupsReturnOneNetworkPerWorld() throws Exception {
        World[] worlds = new World[16];
        for (int i = 0; i < worlds.length; i++) worlds[i] = mock(World.class);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<WirelessNetwork[]>> results = new ArrayList<>();
        try {
            for (int t = 0; t < 8; t++) {
                results.add(workers.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    WirelessNetwork[] networks = new WirelessNetwork[worlds.length];
                    for (int i = 0; i < worlds.length; i++) networks[i] = WirelessNetwork.get(worlds[i]);
                    return networks;
                }));
            }
            start.countDown();
            for (Future<WirelessNetwork[]> result : results) {
                WirelessNetwork[] networks = result.get(10, TimeUnit.SECONDS);
                for (int i = 0; i < worlds.length; i++) assertSame(WirelessNetwork.get(worlds[i]), networks[i]);
            }
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
