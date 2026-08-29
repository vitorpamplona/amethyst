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

    public void getValues(float[] out) { System.arraycopy(values, 0, out, 0, 9); }

    public void setValues(float[] in) { System.arraycopy(in, 0, values, 0, 9); }
}
