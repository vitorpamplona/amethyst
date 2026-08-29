package org.osmdroid.views.overlay;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

/** JVM stand-in for osmdroid's Marker overlay. Holds its state; drawing is the renderer's job. */
public class Marker {
    public static final float ANCHOR_CENTER = 0.5f;
    public static final float ANCHOR_BOTTOM = 1.0f;
    public static final float ANCHOR_TOP = 0.0f;
    public static final float ANCHOR_LEFT = 0.0f;
    public static final float ANCHOR_RIGHT = 1.0f;

    private GeoPoint position = new GeoPoint(0.0, 0.0);
    private String title;
    private boolean draggable;

    public Marker(MapView mapView) {}

    public GeoPoint getPosition() { return position; }

    public void setPosition(GeoPoint value) { position = value; }

    public String getTitle() { return title; }

    public void setTitle(String value) { title = value; }

    public void setAnchor(float horizontal, float vertical) {}

    public void setIcon(Object drawable) {}

    public boolean isDraggable() { return draggable; }

    public void setDraggable(boolean value) { draggable = value; }

    public void setOnMarkerDragListener(Object listener) {}

    public void setInfoWindow(Object window) {}
}
