package android.graphics;

/**
 * JVM stand-in for android.graphics.ColorMatrix.
 *
 * Implemented for real, because the app composes these by hand: the map's
 * night filter is a lightness invert concatenated with a hue rotation, and its
 * day filter is a desaturation post-concatenated with a brightness lift.
 * Getting {@link #postConcat} or {@link #setSaturation} wrong would not fail —
 * it would just render the wrong colours, which is the hardest kind of bug to
 * notice in a map.
 *
 * The 4x5 layout and the luminance weights are the platform's own.
 */
public class ColorMatrix {
    private final float[] array = new float[20];

    public ColorMatrix() { reset(); }

    public ColorMatrix(float[] source) { System.arraycopy(source, 0, array, 0, 20); }

    public ColorMatrix(ColorMatrix source) { System.arraycopy(source.array, 0, array, 0, 20); }

    public float[] getArray() { return array; }

    public void set(ColorMatrix other) { System.arraycopy(other.array, 0, array, 0, 20); }

    public void set(float[] source) { System.arraycopy(source, 0, array, 0, 20); }

    /** The identity: each channel passes through untouched. */
    public final void reset() {
        java.util.Arrays.fill(array, 0f);
        array[0] = 1f;
        array[6] = 1f;
        array[12] = 1f;
        array[18] = 1f;
    }

    /** Rec. 601 luma weights, as the platform uses. */
    public void setSaturation(float saturation) {
        reset();
        float inverse = 1 - saturation;
        float r = 0.213f * inverse;
        float g = 0.715f * inverse;
        float b = 0.072f * inverse;

        array[0] = r + saturation;
        array[1] = g;
        array[2] = b;
        array[5] = r;
        array[6] = g + saturation;
        array[7] = b;
        array[10] = r;
        array[11] = g;
        array[12] = b + saturation;
    }

    public void setScale(float redScale, float greenScale, float blueScale, float alphaScale) {
        java.util.Arrays.fill(array, 0f);
        array[0] = redScale;
        array[6] = greenScale;
        array[12] = blueScale;
        array[18] = alphaScale;
    }

    /** {@code this = other * this} — other applied after this, as on Android. */
    public void postConcat(ColorMatrix other) { setConcat(other, this); }

    /** {@code this = this * other} — this applied after other. */
    public void preConcat(ColorMatrix other) { setConcat(this, other); }

    public void setConcat(ColorMatrix matA, ColorMatrix matB) {
        float[] a = matA.array;
        float[] b = matB.array;
        float[] result = new float[20];
        int index = 0;
        for (int row = 0; row < 20; row += 5) {
            for (int column = 0; column < 4; column++) {
                result[index++] =
                        a[row] * b[column]
                                + a[row + 1] * b[column + 5]
                                + a[row + 2] * b[column + 10]
                                + a[row + 3] * b[column + 15];
            }
            // The translation column also picks up matA's own translation.
            result[index++] =
                    a[row] * b[4]
                            + a[row + 1] * b[9]
                            + a[row + 2] * b[14]
                            + a[row + 3] * b[19]
                            + a[row + 4];
        }
        System.arraycopy(result, 0, array, 0, 20);
    }
}
