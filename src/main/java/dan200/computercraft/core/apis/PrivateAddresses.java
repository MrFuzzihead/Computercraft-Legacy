package dan200.computercraft.core.apis;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;

/**
 * Blocks HTTP requests to private, loopback, link-local, CGNAT and otherwise reserved addresses.
 *
 * <p>
 * Without this, a computer on a server can reach anything the server can: the router's admin page,
 * the host's own services on {@code 127.0.0.1}, cloud metadata endpoints such as
 * {@code 169.254.169.254}, and any machine on the LAN. The rule set is the IANA special-purpose
 * registry (RFC 6890), which is what CC:Tweaked's {@code $private} address predicate uses.
 * </p>
 *
 * <p>
 * The check is by <em>resolved address</em>, not by host name, so a public name pointing at
 * {@code 192.168.1.1} is caught too. A host may resolve to several addresses; all of them must be
 * public. It runs on the HTTP worker thread, where DNS already happens, so it never blocks a
 * computer thread.
 * </p>
 *
 * <p>
 * Set {@code http_allow_local = true} to permit local addresses (the pre-existing behaviour); the
 * default is to deny, as upstream does.
 * </p>
 */
public final class PrivateAddresses {

    private PrivateAddresses() {}

    /**
     * Additional reserved ranges that {@link InetAddress} does not report through its
     * {@code isXxxAddress} helpers.
     *
     * <p>
     * Each entry is {@code network/bits}. Kept as data rather than special cases so the list can be
     * read against the IANA registry directly.
     * </p>
     */
    private static final String[] ADDITIONAL_RANGES = {
        // Shared Address Space (RFC 6598) — carrier-grade NAT.
        "100.64.0.0/10",
        // IETF Protocol Assignments (RFC 6890).
        "192.0.0.0/24",
        // TEST-NET-1 (RFC 5737).
        "192.0.2.0/24",
        // 6to4 Relay Anycast (RFC 3068).
        "192.88.99.0/24",
        // Network Interconnect Device Benchmark Testing (RFC 2544).
        "198.18.0.0/15",
        // TEST-NET-2 (RFC 5737).
        "198.51.100.0/24",
        // TEST-NET-3 (RFC 5737).
        "203.0.113.0/24",
        // IPv4/IPv6 translation (RFC 6052).
        "64:ff9b::/96",
        // Local-use IPv4/IPv6 translation prefix (RFC 8215).
        "64:ff9b:1::/48",
        // IETF protocol assignments, including Teredo and ORCHID (RFC 2928).
        "2001::/23",
        // Unique local address (RFC 4193).
        "fc00::/7", };

    /** Parsed form of {@link #ADDITIONAL_RANGES}, built once. */
    private static final List<Range> RANGES = parseRanges();

    /**
     * Whether an address is private, loopback, link-local, multicast or otherwise reserved.
     *
     * @param address the address to test
     * @return {@code true} if requests to it should be refused by default
     */
    public static boolean isPrivate(InetAddress address) {
        if (address == null) return true;

        // isAnyLocalAddress covers 0.0.0.0 and ::, isSiteLocalAddress covers
        // 10/8, 172.16/12, 192.168/16 and fec0::/10.
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }

        return matchesAdditional(address.getAddress());
    }

    /**
     * The raw-bytes half of {@link #isPrivate(InetAddress)}, exposed so the range table can be
     * tested without DNS.
     *
     * @param address the address bytes, 4 (IPv4) or 16 (IPv6) long
     * @return whether the address falls in one of the additional reserved ranges
     */
    static boolean matchesAdditional(byte[] address) {
        for (Range range : RANGES) {
            if (range.matches(address)) return true;
        }
        return false;
    }

    /**
     * Rejects a URL whose host resolves to a private or reserved address.
     *
     * <p>
     * Resolving here is deliberate: checking the host <em>name</em> would not catch
     * {@code http://my-router.example.com} pointing at {@code 192.168.1.1}, nor a DNS-rebinding
     * name that resolves differently on a second lookup.
     * </p>
     *
     * @param url the URL about to be requested
     * @throws LuaException if the address is not permitted
     */
    public static void check(URL url) throws LuaException {
        if (ComputerCraft.http_allow_local) return;

        String host = url.getHost();
        if (host == null || host.isEmpty()) throw new LuaException("Domain not permitted");

        // A literal IPv4/IPv6 address needs no DNS.
        InetAddress literal = parseLiteral(host);
        if (literal != null) {
            if (isPrivate(literal)) throw new LuaException("Domain not permitted");
            return;
        }

        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // Let the connection report the failure; an unresolvable name is not a policy matter.
            return;
        }

        for (InetAddress address : resolved) {
            if (isPrivate(address)) throw new LuaException("Domain not permitted");
        }
    }

    /**
     * Parses a literal address without triggering DNS.
     *
     * <p>
     * {@link InetAddress#getByName(String)} resolves names, so it cannot be used here; a literal is
     * detected by shape first.
     * </p>
     *
     * @param host the host string from the URL
     * @return the address, or {@code null} if the host is not a literal
     */
    private static InetAddress parseLiteral(String host) {
        if (!isLiteralShape(host)) return null;

        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Whether a host looks like a literal IPv4 address or a bracketed/literal IPv6 address rather
     * than a name. Deliberately conservative: anything not matching is treated as a name and
     * resolved.
     *
     * @param host the host string
     * @return whether it is shaped like an IP literal
     */
    static boolean isLiteralShape(String host) {
        if (host.isEmpty()) return false;

        // IPv6 literals arrive from URL.getHost() without the brackets.
        if (host.indexOf(':') >= 0) return true;

        boolean digitSeen = false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c >= '0' && c <= '9') {
                digitSeen = true;
            } else if (c != '.') {
                // A name may contain letters, digits, '-' and '.'; anything else means this is
                // not an address at all.
                return false;
            }
        }

        // "127.1", "10" and even bare "1234" are all IPv4 literals as far as InetAddress is
        // concerned (the last one is 0.0.4.210), so no dotted quad is required. A host name made
        // only of digits and dots does not exist, so treating it as a literal is safe.
        return digitSeen;
    }

    private static List<Range> parseRanges() {
        List<Range> ranges = new ArrayList<>(ADDITIONAL_RANGES.length);
        for (String spec : ADDITIONAL_RANGES) {
            Range range = Range.parse(spec);
            if (range != null) ranges.add(range);
        }
        return ranges;
    }

    /** A CIDR range: a masked network address plus a prefix length. */
    private static final class Range {

        private final byte[] network;
        private final int bits;

        private Range(byte[] network, int bits) {
            this.network = network;
            this.bits = bits;
        }

        static Range parse(String spec) {
            int slash = spec.indexOf('/');
            if (slash < 0) return null;

            String addressPart = spec.substring(0, slash);
            int bits;
            try {
                bits = Integer.parseInt(spec.substring(slash + 1));
            } catch (NumberFormatException e) {
                return null;
            }

            byte[] address = parseAddress(addressPart);
            if (address == null) return null;
            if (bits < 0 || bits > address.length * 8) return null;

            return new Range(address, bits);
        }

        /** Parses a literal address without DNS, IPv4 or (bracketed) IPv6. */
        private static byte[] parseAddress(String text) {
            String value = text.startsWith("[") && text.endsWith("]") ? text.substring(1, text.length() - 1) : text;
            if (!isLiteralShape(value)) return null;

            try {
                // Safe: the shape check above guarantees a literal, so this cannot resolve a name.
                return InetAddress.getByName(value)
                    .getAddress();
            } catch (UnknownHostException e) {
                return null;
            }
        }

        boolean matches(byte[] address) {
            if (address == null || address.length != network.length) return false;

            int fullBytes = bits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) return false;
            }

            int remaining = bits % 8;
            if (remaining == 0) return true;

            int mask = 0xFF << (8 - remaining);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
