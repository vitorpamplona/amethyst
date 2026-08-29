package org.osmdroid.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * GeoPoint and BoundingBox carry real arithmetic the app depends on — geohash
 * conversion and proximity checks — so they are implemented rather than stubbed,
 * and pinned against known distances.
 */
class GeoPointTest {
    @Test
    void haversineMatchesKnownDistances() {
        // London to Paris, ~343 km great-circle.
        GeoPoint london = new GeoPoint(51.5074, -0.1278);
        GeoPoint paris = new GeoPoint(48.8566, 2.3522);
        double km = london.distanceToAsDouble(paris) / 1000.0;
        assertTrue(km > 330 && km < 355, "expected ~343 km, got " + km);
    }

    @Test
    void distanceIsSymmetricAndZeroToItself() {
        GeoPoint a = new GeoPoint(10.0, 20.0);
        GeoPoint b = new GeoPoint(-5.0, 100.0);
        assertEquals(a.distanceToAsDouble(b), b.distanceToAsDouble(a), 1e-6);
        assertEquals(0.0, a.distanceToAsDouble(new GeoPoint(10.0, 20.0)), 1e-9);
    }

    @Test
    void boundingBoxContainsAndCenters() {
        BoundingBox box = new BoundingBox(10.0, 20.0, 0.0, 0.0);
        assertEquals(5.0, box.getCenterLatitude());
        assertEquals(10.0, box.getCenterLongitude());
        assertTrue(box.contains(new GeoPoint(5.0, 10.0)));
        assertFalse(box.contains(new GeoPoint(50.0, 10.0)));
        assertEquals(10.0, box.getLatitudeSpan());
        assertEquals(20.0, box.getLongitudeSpan());
    }

    @Test
    void pointsCompareByCoordinate() {
        assertEquals(new GeoPoint(1.5, 2.5), new GeoPoint(1.5, 2.5));
        assertEquals(new GeoPoint(1.5, 2.5).hashCode(), new GeoPoint(1.5, 2.5).hashCode());
    }
}
