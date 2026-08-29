package androidx.health.connect.client.records;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * JVM stand-in for ActiveCaloriesBurnedRecord. See HealthConnectClient: there are no
 * records to read on desktop, so these exist to let the workout code compile
 * and are never instantiated by anything but a test.
 */
public class ActiveCaloriesBurnedRecord {
    public Instant getStartTime() { return Instant.EPOCH; }

    public Instant getEndTime() { return Instant.EPOCH; }

    public ZoneOffset getStartZoneOffset() { return ZoneOffset.UTC; }

    public ZoneOffset getEndZoneOffset() { return ZoneOffset.UTC; }
}
