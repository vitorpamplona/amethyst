package org.osmdroid.events;

import org.osmdroid.views.MapView;

/** JVM stand-in for osmdroid's ZoomEvent. */
public class ZoomEvent {
    private final MapView source;
    private final double zoomLevel;

    public ZoomEvent(MapView source, double zoomLevel) {
        this.source = source;
        this.zoomLevel = zoomLevel;
    }

    public MapView getSource() { return source; }

    public double getZoomLevel() { return zoomLevel; }
}
