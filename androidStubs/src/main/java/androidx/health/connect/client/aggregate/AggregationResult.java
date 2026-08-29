package androidx.health.connect.client.aggregate;

/** JVM stand-in for AggregationResult. See HealthConnectClient. */
public final class AggregationResult {
    public <T> T get(Object metric) { return null; }

    public boolean contains(Object metric) { return false; }
}
