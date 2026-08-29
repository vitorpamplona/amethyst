package androidx.health.connect.client.permission;

/** JVM stand-in for HealthPermission. See HealthConnectClient. */
public final class HealthPermission {
    private HealthPermission() {}

    public static String getReadPermission(Object recordType) { return "health.READ"; }

    public static String getWritePermission(Object recordType) { return "health.WRITE"; }
}
