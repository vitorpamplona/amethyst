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

    /**
     * One Paint per polygon, not a fresh one per call. The app configures the
     * highlight's fill and stroke through these — {@code fillPaint.color = …},
     * {@code outlinePaint.strokeWidth = 4f} — so handing back a new instance
     * each time would throw every setting away the moment it was made.
     */
    public android.graphics.Paint getOutlinePaint() { return outlinePaint; }

    public android.graphics.Paint getFillPaint() { return fillPaint; }

    public void setInfoWindow(Object window) {}

    public interface OnClickListener {
        boolean onClick(Polygon polygon, MapView mapView, GeoPoint eventPos);
    }

    public void setOnClickListener(OnClickListener listener) { clickListener = listener; }

    public OnClickListener getOnClickListener() { return clickListener; }

    private final android.graphics.Paint outlinePaint = new android.graphics.Paint();
    private final android.graphics.Paint fillPaint = new android.graphics.Paint();
    private OnClickListener clickListener;
}
