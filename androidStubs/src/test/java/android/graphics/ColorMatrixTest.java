package android.graphics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * The map's day and night looks are hand-built colour matrices — a desaturation
 * post-concatenated with a brightness lift, and a lightness invert composed with
 * a hue rotation. Getting the maths wrong does not fail; it renders the wrong
 * colours, which in a map is the hardest kind of bug to spot.
 */
class ColorMatrixTest {
    /** Applies the 4x5 matrix to an RGBA quad, as the platform would. */
    private static float[] apply(ColorMatrix matrix, float r, float g, float b, float a) {
        float[] m = matrix.getArray();
        float[] out = new float[4];
        float[] in = {r, g, b, a};
        for (int row = 0; row < 4; row++) {
            float sum = m[row * 5 + 4];
            for (int col = 0; col < 4; col++) sum += m[row * 5 + col] * in[col];
            out[row] = sum;
        }
        return out;
    }

    @Test
    void aFreshMatrixIsTheIdentity() {
        float[] out = apply(new ColorMatrix(), 12f, 34f, 56f, 78f);
        assertArrayEquals(new float[] {12f, 34f, 56f, 78f}, out, 1e-4f);
    }

    @Test
    void fullSaturationLeavesColoursAlone() {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(1f);
        assertArrayEquals(new float[] {200f, 100f, 50f, 255f}, apply(matrix, 200f, 100f, 50f, 255f), 1e-3f);
    }

    @Test
    void zeroSaturationCollapsesToRec601Luma() {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        float[] out = apply(matrix, 200f, 100f, 50f, 255f);
        float luma = 0.213f * 200f + 0.715f * 100f + 0.072f * 50f;
        assertEquals(luma, out[0], 1e-3f);
        assertEquals(luma, out[1], 1e-3f);
        assertEquals(luma, out[2], 1e-3f);
        // Alpha must survive a desaturation untouched.
        assertEquals(255f, out[3], 1e-3f);
    }

    @Test
    void postConcatAppliesTheOtherMatrixAfterThisOne() {
        // Exactly the map's day filter: desaturate, THEN lift brightness.
        ColorMatrix saturation = new ColorMatrix();
        saturation.setSaturation(0f);

        ColorMatrix brightness = new ColorMatrix(new float[] {
            2f, 0f, 0f, 0f, 0f,
            0f, 2f, 0f, 0f, 0f,
            0f, 0f, 2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        });

        ColorMatrix combined = new ColorMatrix();
        combined.setSaturation(0f);
        combined.postConcat(brightness);

        float[] expected = apply(brightness, apply(saturation, 200f, 100f, 50f, 255f)[0],
                apply(saturation, 200f, 100f, 50f, 255f)[1],
                apply(saturation, 200f, 100f, 50f, 255f)[2], 255f);
        assertArrayEquals(expected, apply(combined, 200f, 100f, 50f, 255f), 1e-3f);
    }

    @Test
    void concatCarriesTheTranslationColumn() {
        // The night filter is an invert: negative coefficients plus a +255
        // translation. Dropping the translation would render the map black.
        ColorMatrix invert = new ColorMatrix(new float[] {
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        });
        ColorMatrix identityThenInvert = new ColorMatrix();
        identityThenInvert.postConcat(invert);

        assertArrayEquals(new float[] {55f, 155f, 205f, 255f},
                apply(identityThenInvert, 200f, 100f, 50f, 255f), 1e-3f);
    }

    @Test
    void copyingDoesNotAliasTheSource() {
        ColorMatrix source = new ColorMatrix();
        ColorMatrix copy = new ColorMatrix(source);
        copy.setSaturation(0f);
        assertNotSame(source.getArray(), copy.getArray());
        assertArrayEquals(new float[] {12f, 34f, 56f, 78f}, apply(source, 12f, 34f, 56f, 78f), 1e-4f);
    }

    @Test
    void aFilterKeepsTheMatrixItWasGiven() {
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(new float[] {
            0.574f, -1.430f, -0.144f, 0f, 255f,
            -0.426f, -0.430f, -0.144f, 0f, 255f,
            -0.426f, -1.430f, 0.856f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        });
        assertEquals(0.574f, filter.getColorMatrix().getArray()[0], 1e-4f);
        assertEquals(255f, filter.getColorMatrix().getArray()[4], 1e-4f);
    }

    @Test
    void aPolygonKeepsOnePaintSoConfigurationSticks() {
        // The app writes fillPaint.color and outlinePaint.strokeWidth directly.
        // A getter returning a fresh Paint would throw both away silently.
        org.osmdroid.views.overlay.Polygon polygon = new org.osmdroid.views.overlay.Polygon();
        assertSame(polygon.getFillPaint(), polygon.getFillPaint());
        assertNotSame(polygon.getFillPaint(), polygon.getOutlinePaint());

        polygon.getOutlinePaint().setStrokeWidth(4f);
        polygon.getFillPaint().setColor(0x22FF0000);
        assertEquals(4f, polygon.getOutlinePaint().getStrokeWidth(), 1e-4f);
        assertEquals(0x22FF0000, polygon.getFillPaint().getColor());
    }
}
