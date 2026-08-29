package android.location;

/** JVM stand-in for android.location.LocationListener. */
public interface LocationListener {
    void onLocationChanged(Location location);

    default void onProviderEnabled(String provider) {}

    default void onProviderDisabled(String provider) {}

    default void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
}
