package com.vitorpamplona.amethyst.stubs;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records platform behaviour that shared code asked for and this platform does
 * not provide.
 *
 * The desktop port is meant to end up with a real implementation of almost
 * everything, which makes a silently inert stub the most expensive kind of
 * placeholder: it compiles, runs, does nothing, and nobody finds out until a
 * user reports a dead button. Anything that cannot be carried out is recorded
 * here instead of being dropped.
 *
 * Two kinds, and the difference matters:
 *
 * <ul>
 *   <li>{@link #report} — <b>not built yet.</b> A desktop equivalent exists and
 *       someone should write it. This is a backlog item.
 *   <li>{@link #unavailable} — <b>no equivalent exists.</b> The platform simply
 *       has no counterpart (there is no desktop Health Connect, no desktop
 *       picture-in-picture). This is documentation, not a defect; the UI should
 *       hide the feature rather than offer a button that cannot work. If a
 *       desktop equivalent later appears, it moves to the first category.
 * </ul>
 *
 * The default reporter writes each distinct entry to stderr once, so gaps are
 * visible during development before anyone wires a reporter. An app can replace
 * it to surface gaps in the UI or fail tests on them.
 */
public final class PlatformGaps {
    private PlatformGaps() {}

    /** feature, detail, kind. */
    public interface Reporter {
        void onGap(String feature, String detail, Kind kind);
    }

    public enum Kind {
        /** A desktop equivalent exists; this is a backlog item. */
        NOT_IMPLEMENTED_YET,
        /** No desktop counterpart exists. Expected, documented, and hideable. */
        NO_PLATFORM_EQUIVALENT,
    }

    private static volatile Reporter reporter = PlatformGaps::warnOnce;

    private static final Map<String, Kind> SEEN = new ConcurrentHashMap<>();

    private static final Map<String, String> UNAVAILABLE = new ConcurrentHashMap<>();

    public static void setReporter(Reporter value) {
        reporter = value == null ? PlatformGaps::warnOnce : value;
    }

    /** Everything hit so far, as "feature: detail" mapped to its kind. */
    public static Map<String, Kind> seen() {
        return Map.copyOf(SEEN);
    }

    /**
     * Declared up front rather than on first use, so a UI can ask before it
     * draws a control instead of finding out after the user presses it.
     */
    public static void declareUnavailable(String feature, String reason) {
        UNAVAILABLE.put(feature, reason);
    }

    /** True when this platform has no counterpart for {@code feature}. */
    public static boolean isUnavailable(String feature) {
        return UNAVAILABLE.containsKey(feature);
    }

    /** Feature to reason, for a diagnostics screen or the docs. */
    public static Map<String, String> unavailableFeatures() {
        return Map.copyOf(UNAVAILABLE);
    }

    /** Not built yet — a desktop equivalent exists and someone should write it. */
    public static void report(String feature, String detail) {
        record(feature, detail, Kind.NOT_IMPLEMENTED_YET);
    }

    /** No desktop counterpart exists. Expected; the UI should hide the feature. */
    public static void unavailable(String feature, String reason) {
        UNAVAILABLE.putIfAbsent(feature, reason);
        record(feature, reason, Kind.NO_PLATFORM_EQUIVALENT);
    }

    private static void record(String feature, String detail, Kind kind) {
        if (SEEN.putIfAbsent(feature + ": " + detail, kind) == null) {
            reporter.onGap(feature, detail, kind);
        }
    }

    private static void warnOnce(String feature, String detail, Kind kind) {
        String prefix = kind == Kind.NO_PLATFORM_EQUIVALENT ? "[platform-unavailable] " : "[platform-gap] ";
        System.err.println(prefix + feature + ": " + detail);
    }
}
