package androidx.health.connect.client.records;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * JVM stand-in for ExerciseSessionRecord. See HealthConnectClient: there are no
 * records to read on desktop, so these exist to let the workout code compile
 * and are never instantiated by anything but a test.
 */
public class ExerciseSessionRecord {
    public Instant getStartTime() { return Instant.EPOCH; }

    public Instant getEndTime() { return Instant.EPOCH; }

    public ZoneOffset getStartZoneOffset() { return ZoneOffset.UTC; }

    public ZoneOffset getEndZoneOffset() { return ZoneOffset.UTC; }

    public int getExerciseType() { return 0; }

    public String getTitle() { return null; }

    public String getNotes() { return null; }

    public static final int EXERCISE_TYPE_RUNNING = 56;
    public static final int EXERCISE_TYPE_WALKING = 79;
    public static final int EXERCISE_TYPE_BIKING = 8;
    public static final int EXERCISE_TYPE_SWIMMING_POOL = 73;
    public static final int EXERCISE_TYPE_OTHER_WORKOUT = 0;
}
