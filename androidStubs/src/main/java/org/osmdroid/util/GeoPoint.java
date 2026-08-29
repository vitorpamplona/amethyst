package org.osmdroid.util;

/**
 * JVM stand-in for org.osmdroid.util.GeoPoint.
 *
 * Implemented for real: a geographic point is arithmetic, and the app does
 * genuine work with it — geohash conversion, distance checks, bounding-box
 * membership — that has nothing to do with drawing a map.
 */
public class GeoPoint {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private double latitude;
    private double longitude;
    private double altitude;

    public GeoPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public GeoPoint(double latitude, double longitude, double altitude) {
        this(latitude, longitude);
        this.altitude = altitude;
    }

    public GeoPoint(GeoPoint other) { this(other.latitude, other.longitude, other.altitude); }

    public double getLatitude() { return latitude; }

    public void setLatitude(double value) { latitude = value; }

    public double getLongitude() { return longitude; }

    public void setLongitude(double value) { longitude = value; }

    public double getAltitude() { return altitude; }

    public void setAltitude(double value) { altitude = value; }

    /** Great-circle distance in metres (haversine), as osmdroid computes it. */
    public double distanceToAsDouble(GeoPoint other) {
        double dLat = Math.toRadians(other.latitude - latitude);
        double dLon = Math.toRadians(other.longitude - longitude);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(latitude))
                                * Math.cos(Math.toRadians(other.latitude))
                                * Math.sin(dLon / 2)
                                * Math.sin(dLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof GeoPoint)) return false;
        GeoPoint that = (GeoPoint) other;
        return Double.compare(latitude, that.latitude) == 0 && Double.compare(longitude, that.longitude) == 0;
    }

    @Override public int hashCode() { return Double.hashCode(latitude) * 31 + Double.hashCode(longitude); }

    @Override public String toString() { return latitude + "," + longitude; }
}
