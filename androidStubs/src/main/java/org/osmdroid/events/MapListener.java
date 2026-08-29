package org.osmdroid.events;

/** JVM stand-in for osmdroid's MapListener. */
public interface MapListener {
    boolean onScroll(ScrollEvent event);

    boolean onZoom(ZoomEvent event);
}
