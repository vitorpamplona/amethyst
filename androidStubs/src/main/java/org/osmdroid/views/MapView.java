package org.osmdroid.views;

import android.content.Context;
import android.view.View;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.util.ArrayList;
import java.util.List;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

/**
 * JVM stand-in for org.osmdroid.views.MapView.
 *
 * Desktop maps are a real capability — the same OSM tiles, fetched over HTTP
 * and drawn into a Compose canvas — so this is a missing implementation, not a
 * missing platform feature. osmdroid itself is an Android View library and
 * cannot be ported, so the shape lives here while the actual rendering belongs
 * to a desktop map composable. Camera state is kept honestly (a caller that
 * sets a centre and reads it back gets what it set); anything that would
 * require pixels reports.
 */
public class MapView extends View {
    private final MapController controller = new MapController();
    private final List<Object> overlays = new ArrayList<>();
    private GeoPoint center = new GeoPoint(0.0, 0.0);
    private double zoom = 3.0;

    public MapView(Context context) {
        super(context);
        PlatformGaps.report(
                "osmdroid.MapView",
                "desktop needs its own OSM tile renderer; osmdroid is an Android View library and cannot be ported");
    }

    public MapController getController() { return controller; }

    public List<Object> getOverlays() { return overlays; }

    public GeoPoint getMapCenter() { return center; }

    public double getZoomLevelDouble() { return zoom; }

    public BoundingBox getBoundingBox() {
        double span = 90.0 / Math.pow(2, zoom);
        return new BoundingBox(
                center.getLatitude() + span,
                center.getLongitude() + span,
                center.getLatitude() - span,
                center.getLongitude() - span);
    }

    public void setTileSource(Object source) {}

    public void setMultiTouchControls(boolean enabled) {}

    public void setTilesScaledToDpi(boolean enabled) {}

    public void setMaxZoomLevel(Double value) {}

    public void setMinZoomLevel(Double value) {}

    public CustomZoomButtonsController getZoomController() { return new CustomZoomButtonsController(); }

    public void addMapListener(Object listener) {}

    public void onPause() {}

    public void onResume() {}

    public void onDetach() {}

    public class MapController {
        public void setCenter(GeoPoint point) { center = point; }

        public void setZoom(double value) { zoom = value; }

        public void animateTo(GeoPoint point) { center = point; }

        public void zoomTo(double value) { zoom = value; }
    }
}
