package android.location;

/** JVM stand-in for android.location.Location. Pure data. */
public class Location {
    private final String provider;
    private double latitude;
    private double longitude;
    private float accuracy;
    private long time;

    public Location(String provider) { this.provider = provider; }

    public String getProvider() { return provider; }

    public double getLatitude() { return latitude; }

    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }

    public void setLongitude(double longitude) { this.longitude = longitude; }

    public float getAccuracy() { return accuracy; }

    public void setAccuracy(float accuracy) { this.accuracy = accuracy; }

    public boolean hasAccuracy() { return accuracy > 0f; }

    public long getTime() { return time; }

    public void setTime(long time) { this.time = time; }
}
