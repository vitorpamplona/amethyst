package org.osmdroid.events;

import org.osmdroid.util.GeoPoint;

/** JVM stand-in for osmdroid's MapEventsReceiver. */
public interface MapEventsReceiver {
    boolean singleTapConfirmedHelper(GeoPoint point);

    boolean longPressHelper(GeoPoint point);
}
