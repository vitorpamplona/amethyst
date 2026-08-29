package android.graphics;

/**
 * JVM stand-in for android.graphics.Path, backed by java.awt.geom.Path2D.
 *
 * Real rather than inert: a path is geometry, the JDK has the same geometry,
 * and callers build one before handing it to something that draws.
 */
public class Path {
    public enum FillType { WINDING, EVEN_ODD }

    private final java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
    private FillType fillType = FillType.WINDING;

    public void moveTo(float x, float y) { path.moveTo(x, y); }

    public void lineTo(float x, float y) { path.lineTo(x, y); }

    public void quadTo(float x1, float y1, float x2, float y2) { path.quadTo(x1, y1, x2, y2); }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        path.curveTo(x1, y1, x2, y2, x3, y3);
    }

    public void close() { path.closePath(); }

    public void reset() { path.reset(); }

    public boolean isEmpty() { return path.getCurrentPoint() == null; }

    public FillType getFillType() { return fillType; }

    public void setFillType(FillType value) {
        fillType = value;
        path.setWindingRule(value == FillType.EVEN_ODD ? java.awt.geom.Path2D.WIND_EVEN_ODD : java.awt.geom.Path2D.WIND_NON_ZERO);
    }

    /** The underlying geometry, for desktop code that wants to draw it. */
    public java.awt.geom.Path2D.Float toPath2D() { return path; }
}
