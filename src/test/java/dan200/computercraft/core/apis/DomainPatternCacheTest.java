package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;

class DomainPatternCacheTest {

    private static void restore(String whitelist, String blacklist) {
        ComputerCraft.http_whitelist = whitelist;
        ComputerCraft.http_blacklist = blacklist;
        HTTPRequest.prepareDomainPatterns();
    }

    @Test
    void checkURLAcceptsAndRejectsHostsPerWhitelistAndBlacklist() throws Exception {
        String whitelist = ComputerCraft.http_whitelist;
        String blacklist = ComputerCraft.http_blacklist;
        try {
            ComputerCraft.http_whitelist = "*.example.com;localhost";
            ComputerCraft.http_blacklist = "*.bad.example.com";
            HTTPRequest.prepareDomainPatterns();
            HTTPRequest.checkURL("http://shop.example.com/path");
            HTTPRequest.checkURL("http://localhost:8080/");
            assertThrows(LuaException.class, () -> HTTPRequest.checkURL("http://other.org/"));
            assertThrows(LuaException.class, () -> HTTPRequest.checkURL("http://sub.bad.example.com/"));
        } finally {
            restore(whitelist, blacklist);
        }
    }

    @Test
    void repeatedChecksReuseTheCompiledPatterns() throws Exception {
        String whitelist = ComputerCraft.http_whitelist;
        try {
            ComputerCraft.http_whitelist = "*.a.test;*.b.test";
            HTTPRequest.prepareDomainPatterns();
            DomainPatternCache.CompiledDomains compiled = HTTPRequest.WHITELIST.get(ComputerCraft.http_whitelist);
            for (int i = 0; i < 100; i++) {
                HTTPRequest.checkURL("http://host.a.test/");
                assertThrows(LuaException.class, () -> HTTPRequest.checkURL("http://host.c.test/"));
            }
            assertSame(compiled, HTTPRequest.WHITELIST.get(ComputerCraft.http_whitelist));
        } finally {
            restore(whitelist, ComputerCraft.http_blacklist);
        }
    }

    @Test
    void compiledPatternsKeepLegacyMatchingRules() {
        DomainPatternCache.CompiledDomains domains = new DomainPatternCache.CompiledDomains("*.one.test;;*.two.test");
        assertTrue(domains.matches("sub.one.test"));
        assertFalse(domains.matches("one.test"));
        assertFalse(domains.matches("xone.test"));
        assertFalse(domains.matches("sub.one.test.extra"));
        assertTrue(domains.matches("sub.two.test"));
        assertFalse(domains.matches("sub.two.test".toUpperCase()));
    }

    @Test
    void changedConfigurationCompilesNewPatterns() throws Exception {
        String whitelist = ComputerCraft.http_whitelist;
        try {
            ComputerCraft.http_whitelist = "*.old.test";
            HTTPRequest.prepareDomainPatterns();
            DomainPatternCache.CompiledDomains old = HTTPRequest.WHITELIST.get("*.old.test");
            ComputerCraft.http_whitelist = "*.new.test";
            HTTPRequest.checkURL("http://host.new.test/");
            assertNotSame(old, HTTPRequest.WHITELIST.get("*.new.test"));
        } finally {
            restore(whitelist, ComputerCraft.http_blacklist);
        }
    }

    @Test
    void concurrentFirstRequestsCompileOnceAndSharePatterns() throws Exception {
        String whitelist = ComputerCraft.http_whitelist;
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            DomainPatternCache cache = new DomainPatternCache();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(8);
            for (int t = 0; t < 8; t++) {
                workers.submit(() -> {
                    try {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        assertTrue(
                            cache.get("*.busy.test")
                                .matches("host.busy.test"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
            DomainPatternCache.CompiledDomains compiled = cache.get("*.busy.test");
            for (int i = 0; i < 50; i++) {
                assertSame(compiled, cache.get("*.busy.test"));
            }
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            restore(whitelist, ComputerCraft.http_blacklist);
        }
    }

    @Test
    void emptyAndEntrylessListsMatchNothing() {
        assertFalse(
            new DomainPatternCache().get(";;")
                .matches("localhost"));
        assertFalse(
            new DomainPatternCache().get("")
                .matches("localhost"));
        assertTrue(
            new DomainPatternCache().get("localhost")
                .matches("localhost"));
    }
}
