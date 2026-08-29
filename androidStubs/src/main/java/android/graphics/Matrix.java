package android.graphics;

/**
 * JVM stand-in for android.graphics.Matrix.
 *
 * A 3x3 affine transform. Implemented for real because it is pure arithmetic
 * and callers compose transforms before handing them somewhere that does draw.
 */
public class Matrix {
    private final float[] values = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    public void reset() {
        System.arraycopy(new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, 0, values, 0, 9);
    }

    public void setScale(float scaleX, float scaleY) {
        reset();
        values[0] = scaleX;
        values[4] = scaleY;
    }

    public void setTranslate(float dx, float dy) {
        reset();
        values[2] = dx;
        values[5] = dy;
    }

    public void postScale(float scaleX, float scaleY) {
        values[0] *= scaleX;
        values[4] *= scaleY;
        values[2] *= scaleX;
        values[5] *= scaleY;
    }

    public void postTranslate(float dx, float dy) {
        values[2] += dx;
        values[5] += dy;
    }

    public void postRotate(float degrees) {
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float a = values[0];
        float b = values[1];
        float c = values[3];
        float d = values[4];
        values[0] = cos * a - sin * c;
        values[1] = cos * b - sin * d;
        values[3] = sin * a + cos * c;
        values[4] = sin * b + cos * d;
    }

    public void getValues(float[] out) { System.arraycopy(values, 0, out, 0, 9); }

    public void setValues(float[] in) { System.arraycopy(in, 0, values, 0, 9); }

    public boolean isIdentity() {
        return values[0] == 1 && values[1] == 0 && values[2] == 0
                && values[3] == 0 && values[4] == 1 && values[5] == 0;
    }

    /**
     * The same transform as an AWT one, so anything that actually draws can use
     * it. Android orders its 3x3 row-major (scaleX, skewX, transX, ...), which
     * is the (m00, m01, m02, m10, m11, m12) AffineTransform takes.
     */
    public java.awt.geom.AffineTransform toAffineTransform() {
        return new java.awt.geom.AffineTransform(
                values[0], values[3], values[1], values[4], values[2], values[5]);
    }
}
