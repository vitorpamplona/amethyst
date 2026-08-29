package android.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The thumbnail path centre-crops and then scales through
 * {@code Bitmap.createBitmap(src, x, y, w, h, matrix, filter)}. An overload
 * that ignored the crop or the matrix would put the wrong pixels — or the wrong
 * size — into every cached thumbnail.
 */
class BitmapTransformTest {
    private static Bitmap gradient(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bitmap.setPixel(x, y, 0xFF000000 | (x << 8) | y);
            }
        }
        return bitmap;
    }

    @Test
    void cropTakesTheRequestedRegion() {
        Bitmap cropped = Bitmap.createBitmap(gradient(10, 10), 3, 4, 2, 2);

        assertEquals(2, cropped.getWidth());
        assertEquals(2, cropped.getHeight());
        // The pixel at (0,0) of the crop is the source's (3,4).
        assertEquals(0xFF000000 | (3 << 8) | 4, cropped.getPixel(0, 0));
    }

    @Test
    void aCropIsIndependentOfItsSource() {
        Bitmap source = gradient(8, 8);
        Bitmap cropped = Bitmap.createBitmap(source, 2, 2, 4, 4);
        int before = cropped.getPixel(0, 0);

        source.setPixel(2, 2, 0xFFFFFFFF);

        assertEquals(before, cropped.getPixel(0, 0), "the crop shared the source's raster");
    }

    @Test
    void theMatrixScalesTheCroppedRegion() {
        Matrix matrix = new Matrix();
        matrix.setScale(0.5f, 0.5f);

        Bitmap scaled = Bitmap.createBitmap(gradient(20, 20), 0, 0, 16, 16, matrix, true);

        assertEquals(8, scaled.getWidth());
        assertEquals(8, scaled.getHeight());
    }

    @Test
    void anIdentityMatrixIsJustTheCrop() {
        Bitmap out = Bitmap.createBitmap(gradient(10, 10), 1, 1, 5, 5, new Matrix(), false);
        assertEquals(5, out.getWidth());
        assertEquals(5, out.getHeight());
    }

    @Test
    void createFromPixelsRoundTrips() {
        int[] pixels = {0xFF112233, 0xFF445566, 0xFF778899, 0xFFAABBCC};
        Bitmap bitmap = Bitmap.createBitmap(pixels, 2, 2, Bitmap.Config.ARGB_8888);

        assertEquals(2, bitmap.getWidth());
        assertEquals(0xFF112233, bitmap.getPixel(0, 0));
        assertEquals(0xFFAABBCC, bitmap.getPixel(1, 1));

        int[] read = new int[4];
        bitmap.getPixels(read, 0, 2, 0, 0, 2, 2);
        assertEquals(0xFF445566, read[1]);
    }

    @Test
    void matrixRotationChangesTheTransform() {
        Matrix matrix = new Matrix();
        assertTrue(matrix.isIdentity());

        matrix.postRotate(90f);
        assertTrue(!matrix.isIdentity());

        Bitmap rotated = Bitmap.createBitmap(gradient(8, 4), 0, 0, 8, 4, matrix, false);
        // A 90 degree turn swaps the bounds.
        assertEquals(4, rotated.getWidth());
        assertEquals(8, rotated.getHeight());
        assertNotEquals(rotated.getWidth(), rotated.getHeight());
    }

    @Test
    void byteCountReflectsTheRealSize() {
        assertEquals(10 * 20 * 4, Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888).getByteCount());
    }
}
