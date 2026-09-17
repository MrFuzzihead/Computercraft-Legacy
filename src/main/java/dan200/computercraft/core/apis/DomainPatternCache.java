package dan200.computercraft.core.apis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Single-entry cache: retains only the current domain list, not configuration history. */
final class DomainPatternCache {

    private volatile CompiledDomains m_cached;

    CompiledDomains get(String source) {
        CompiledDomains cached = m_cached;
        if (cached != null && cached.m_source.equals(source)) return cached;
        return rebuild(source);
    }

    private synchronized CompiledDomains rebuild(String source) {
        // Recheck after taking the lock: concurrent first requests compile only once.
        CompiledDomains cached = m_cached;
        if (cached == null || !cached.m_source.equals(source)) {
            cached = new CompiledDomains(source);
            m_cached = cached;
        }
        return cached;
    }

    /** Immutable patterns can be shared by workers; each match creates its own Matcher. */
    static final class CompiledDomains {

        private final String m_source;
        private final Pattern[] m_patterns;

        CompiledDomains(String source) {
            m_source = source;
            List<Pattern> patterns = new ArrayList<>();
            for (String entry : source.split(";")) {
                if (entry.isEmpty()) continue;
                // Keep legacy matching: case-sensitive, no trimming, only '*' is a wildcard.
                patterns.add(Pattern.compile("^\\Q" + entry.replace("*", "\\E.*\\Q") + "\\E$"));
            }
            m_patterns = patterns.toArray(new Pattern[0]);
        }

        boolean matches(String host) {
            for (Pattern pattern : m_patterns) {
                if (pattern.matcher(host)
                    .matches()) return true;
            }
            return false;
        }
    }
}
