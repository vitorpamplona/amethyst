package org.osmdroid.events;

import org.osmdroid.views.MapView;

/** JVM stand-in for osmdroid's ScrollEvent. */
public class ScrollEvent {
    private final MapView source;

    public ScrollEvent(MapView source, int x, int y) { this.source = source; }

    public MapView getSource() { return source; }
}
