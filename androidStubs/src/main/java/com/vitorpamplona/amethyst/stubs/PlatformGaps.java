package com.vitorpamplona.amethyst.stubs;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Records platform behaviour that shared code asked for and this platform does
 * not implement yet.
 *
 * The desktop port is meant to end up with a real implementation of
 * everything, which makes a silently inert stub the most expensive kind: it
 * compiles, runs, does nothing, and nobody finds out until a user reports that
 * a button is dead. Anything that cannot be carried out must be reported here
 * instead of being dropped.
 *
 * The default reporter writes each distinct gap to stderr once, so a gap is
 * visible during development even before the app installs anything. An app can
 * replace it to surface gaps in the UI, count them, or fail tests on them.
 */
public final class PlatformGaps {
    private PlatformGaps() {}

    /** feature, detail. */
    private static volatile BiConsumer<String, String> reporter = PlatformGaps::warnOnce;

    private static final Set<String> ALREADY_WARNED = ConcurrentHashMap.newKeySet();

    public static void setReporter(BiConsumer<String, String> value) {
        reporter = value == null ? PlatformGaps::warnOnce : value;
    }

    /** All gaps hit so far, as "feature: detail". Useful for a diagnostics screen or a test. */
    public static Set<String> seen() {
        return Set.copyOf(ALREADY_WARNED);
    }

    public static void report(String feature, String detail) {
        ALREADY_WARNED.add(feature + ": " + detail);
        reporter.accept(feature, detail);
    }

    private static void warnOnce(String feature, String detail) {
        System.err.println("[platform-gap] " + feature + " is not implemented on this platform: " + detail);
    }
}
