package org.osmdroid.util;

/** JVM stand-in for org.osmdroid.util.BoundingBox. Pure geometry, implemented for real. */
public class BoundingBox {
    private final double north;
    private final double east;
    private final double south;
    private final double west;

    public BoundingBox(double north, double east, double south, double west) {
        this.north = north;
        this.east = east;
        this.south = south;
        this.west = west;
    }

    public double getLatNorth() { return north; }

    public double getLatSouth() { return south; }

    public double getLonEast() { return east; }

    public double getLonWest() { return west; }

    public double getCenterLatitude() { return (north + south) / 2.0; }

    public double getCenterLongitude() { return (east + west) / 2.0; }

    public GeoPoint getCenterWithDateLine() { return new GeoPoint(getCenterLatitude(), getCenterLongitude()); }

    public boolean contains(GeoPoint point) {
        return point.getLatitude() <= north
                && point.getLatitude() >= south
                && point.getLongitude() <= east
                && point.getLongitude() >= west;
    }

    public double getLatitudeSpan() { return Math.abs(north - south); }

    public double getLongitudeSpan() { return Math.abs(east - west); }
}
