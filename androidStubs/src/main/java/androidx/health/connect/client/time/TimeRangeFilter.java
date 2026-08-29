package androidx.health.connect.client.time;

import java.time.Instant;

/** JVM stand-in for TimeRangeFilter. See HealthConnectClient. */
public final class TimeRangeFilter {
    private TimeRangeFilter() {}

    public static TimeRangeFilter between(Instant start, Instant end) { return new TimeRangeFilter(); }

    public static TimeRangeFilter after(Instant start) { return new TimeRangeFilter(); }

    public static TimeRangeFilter before(Instant end) { return new TimeRangeFilter(); }
}
