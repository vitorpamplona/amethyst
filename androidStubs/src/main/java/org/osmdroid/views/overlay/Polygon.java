package org.osmdroid.views.overlay;

import java.util.ArrayList;
import java.util.List;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

/** JVM stand-in for osmdroid's Polygon overlay. Holds its points; drawing is the renderer's job. */
public class Polygon {
    private List<GeoPoint> points = new ArrayList<>();

    public Polygon() {}

    public Polygon(MapView mapView) {}

    public List<GeoPoint> getPoints() { return points; }

    public void setPoints(List<GeoPoint> value) { points = new ArrayList<>(value); }

    public Object getOutlinePaint() { return new android.graphics.Paint(); }

    public Object getFillPaint() { return new android.graphics.Paint(); }

    public void setInfoWindow(Object window) {}
}
