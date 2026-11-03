package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;

/**
 * Tests for {@link PrivateAddresses}, the guard that stops a computer reaching the server's own
 * network.
 *
 * <p>
 * These are pure address checks — no sockets, no DNS for the range table — because the ranges are
 * the part that is easy to get subtly wrong.
 * </p>
 */
class PrivateAddressesTest {

    private boolean savedAllowLocal;

    @BeforeEach
    void setUp() {
        savedAllowLocal = ComputerCraft.http_allow_local;
        ComputerCraft.http_allow_local = false;
    }

    @AfterEach
    void tearDown() {
        ComputerCraft.http_allow_local = savedAllowLocal;
    }

    private static boolean isPrivate(String literal) throws Exception {
        return PrivateAddresses.isPrivate(InetAddress.getByName(literal));
    }

    // -------------------------------------------------------------------------
    // Private, loopback, link-local, multicast: the Java accessors cover these
    // -------------------------------------------------------------------------

    @Test
    void loopbackIsPrivate() throws Exception {
        assertTrue(isPrivate("127.0.0.1"));
        assertTrue(isPrivate("127.1.2.3"));
        assertTrue(isPrivate("::1"));
    }

    @Test
    void anyLocalIsPrivate() throws Exception {
        assertTrue(isPrivate("0.0.0.0"));
        assertTrue(isPrivate("::"));
    }

    @Test
    void linkLocalIsPrivate() throws Exception {
        // 169.254.169.254 is the cloud metadata endpoint, the classic SSRF target.
        assertTrue(isPrivate("169.254.169.254"));
        assertTrue(isPrivate("fe80::1"));
    }

    @Test
    void siteLocalIsPrivate() throws Exception {
        assertTrue(isPrivate("10.0.0.1"));
        assertTrue(isPrivate("172.16.0.1"));
        assertTrue(isPrivate("172.31.255.255"));
        assertTrue(isPrivate("192.168.1.1"));
    }

    @Test
    void multicastIsPrivate() throws Exception {
        assertTrue(isPrivate("224.0.0.1"));
        assertTrue(isPrivate("ff02::1"));
    }

    // -------------------------------------------------------------------------
    // The additional IANA ranges
    // -------------------------------------------------------------------------

    @Test
    void carrierGradeNatIsPrivate() throws Exception {
        assertTrue(isPrivate("100.64.0.1"));
        assertTrue(isPrivate("100.127.255.255"));
        // Just outside the /10.
        assertFalse(isPrivate("100.63.255.255"));
        assertFalse(isPrivate("100.128.0.1"));
    }

    @Test
    void documentationAndBenchmarkRangesArePrivate() throws Exception {
        assertTrue(isPrivate("192.0.2.1")); // TEST-NET-1
        assertTrue(isPrivate("198.51.100.1")); // TEST-NET-2
        assertTrue(isPrivate("203.0.113.1")); // TEST-NET-3
        assertTrue(isPrivate("198.18.0.1")); // benchmark testing
        assertTrue(isPrivate("198.19.255.255"));
        assertTrue(isPrivate("192.88.99.1")); // 6to4 relay anycast
    }

    @Test
    void ipv6SpecialRangesArePrivate() throws Exception {
        assertTrue(isPrivate("fc00::1")); // unique local
        assertTrue(isPrivate("fd12:3456::1"));
        assertTrue(isPrivate("64:ff9b::1.2.3.4")); // IPv4/IPv6 translation
    }

    @Test
    void publicAddressesAreNotPrivate() throws Exception {
        assertFalse(isPrivate("8.8.8.8"));
        assertFalse(isPrivate("1.1.1.1"));
        assertFalse(isPrivate("93.184.216.34"));
        assertFalse(isPrivate("2606:4700:4700::1111"));
        // 100.128.x is outside the CGNAT /10, and 11.0.0.1 outside 10/8.
        assertFalse(isPrivate("11.0.0.1"));
        assertFalse(isPrivate("172.32.0.1"));
    }

    @Test
    void nullIsTreatedAsPrivate() {
        assertTrue(PrivateAddresses.isPrivate(null));
    }

    // -------------------------------------------------------------------------
    // Literal detection: a host name must never be mistaken for an address
    // -------------------------------------------------------------------------

    @Test
    void literalShapesAreRecognised() {
        assertTrue(PrivateAddresses.isLiteralShape("127.0.0.1"));
        assertTrue(PrivateAddresses.isLiteralShape("10.1.2.3"));
        assertTrue(PrivateAddresses.isLiteralShape("::1"));
        assertTrue(PrivateAddresses.isLiteralShape("2001:db8::1"));
    }

    @Test
    void namesAreNotMistakenForLiterals() {
        assertFalse(PrivateAddresses.isLiteralShape("example.com"));
        assertFalse(PrivateAddresses.isLiteralShape("localhost"));
        assertFalse(PrivateAddresses.isLiteralShape("127.example.com"));
        assertFalse(PrivateAddresses.isLiteralShape("1e100.net"));
        assertFalse(PrivateAddresses.isLiteralShape(""));
    }

    // -------------------------------------------------------------------------
    // check(URL): the policy entry point
    // -------------------------------------------------------------------------

    @Test
    void loopbackUrlsAreRefused() throws Exception {
        LuaException error = assertThrows(
            LuaException.class,
            () -> PrivateAddresses.check(new URL("http://127.0.0.1:8080/admin")));
        assertEquals("Domain not permitted", error.getMessage());
    }

    @Test
    void metadataUrlsAreRefused() throws Exception {
        assertThrows(LuaException.class, () -> PrivateAddresses.check(new URL("http://169.254.169.254/latest")));
    }

    @Test
    void privateUrlsAreRefused() throws Exception {
        assertThrows(LuaException.class, () -> PrivateAddresses.check(new URL("http://192.168.1.1/")));
        assertThrows(LuaException.class, () -> PrivateAddresses.check(new URL("https://10.0.0.5/")));
    }

    @Test
    void theConfigFlagAllowsLocalAddresses() throws Exception {
        ComputerCraft.http_allow_local = true;
        // No exception: the pre-existing behaviour is available for LAN servers.
        PrivateAddresses.check(new URL("http://192.168.1.1/"));
        PrivateAddresses.check(new URL("http://127.0.0.1:8080/"));
    }

    @Test
    void aPublicLiteralIsAllowed() throws Exception {
        PrivateAddresses.check(new URL("http://8.8.8.8/"));
    }

    @Test
    void anUnresolvableHostIsLeftToTheConnection() throws Exception {
        // The policy is about addresses, not about DNS failures; the request reports its own error.
        PrivateAddresses.check(new URL("http://this-host-does-not-exist.invalid/"));
    }
}
