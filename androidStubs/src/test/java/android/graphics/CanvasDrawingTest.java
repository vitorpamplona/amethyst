package android.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Notification avatars are drawn here: a circle filled with a bitmap shader,
 * then a badge composited into a corner. Both fail *quietly* — a shader that is
 * ignored draws a flat disc where a face should be, and a badge placed by the
 * wrong overload lands in the wrong corner. So the pixels are checked.
 */
class CanvasDrawingTest {
    private static Bitmap filled(int width, int height, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) bitmap.setPixel(x, y, color);
        }
        return bitmap;
    }

    @Test
    void aBitmapShaderFillsTheCircleWithTheImageNotTheColour() {
        Bitmap source = filled(40, 40, 0xFF2266DD);
        Bitmap target = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // A colour that must NOT show up: if the shader were ignored, every
        // pixel in the circle would be this.
        paint.setColor(0xFFFF0000);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

        new Canvas(target).drawCircle(20, 20, 20, paint);

        int centre = target.getPixel(20, 20);
        assertEquals(0x22, (centre >> 16) & 0xFF, "red channel came from the paint colour, not the image");
        assertEquals(0x66, (centre >> 8) & 0xFF);
        assertEquals(0xDD, centre & 0xFF);
    }

    @Test
    void theCircleIsACircleAndNotTheWholeSquare() {
        Bitmap source = filled(40, 40, 0xFF2266DD);
        Bitmap target = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(target).drawCircle(20, 20, 20, paint);

        // The corner sits outside the inscribed circle, so it must be untouched.
        assertEquals(0, (target.getPixel(0, 0) >> 24) & 0xFF, "the corner was painted; this is not a circle crop");
        assertNotEquals(0, (target.getPixel(20, 20) >> 24) & 0xFF, "the centre was not painted");
    }

    @Test
    void withNoShaderTheCircleIsTheFlatPaintColour() {
        Bitmap target = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        paint.setColor(0xFF00FF00);

        new Canvas(target).drawCircle(10, 10, 10, paint);
        assertEquals(0xFF, (target.getPixel(10, 10) >> 8) & 0xFF);
    }

    @Test
    void theBadgeOverloadScalesIntoTheDestinationRect() {
        Bitmap badge = filled(8, 8, 0xFFFF0000);
        Bitmap target = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);

        // Bottom-right corner, 12x12 — the shape the notification badge uses.
        RectF dest = new RectF(28, 28, 40, 40);
        new Canvas(target).drawBitmap(badge, null, dest, new Paint(Paint.ANTI_ALIAS_FLAG));

        int inside = target.getPixel(34, 34);
        assertEquals(0xFF, (inside >> 16) & 0xFF, "the badge is not in the destination rect");
        // The opposite corner must be untouched: a badge drawn at 0,0 or
        // stretched over everything is the failure this catches.
        assertEquals(0, (target.getPixel(2, 2) >> 24) & 0xFF, "the badge covered the whole bitmap");
    }

    @Test
    void aSourceRectSelectsPartOfTheBadge() {
        Bitmap source = Bitmap.createBitmap(4, 1, Bitmap.Config.ARGB_8888);
        source.setPixel(0, 0, 0xFFFF0000);
        source.setPixel(1, 0, 0xFFFF0000);
        source.setPixel(2, 0, 0xFF0000FF);
        source.setPixel(3, 0, 0xFF0000FF);

        Bitmap target = Bitmap.createBitmap(4, 1, Bitmap.Config.ARGB_8888);
        // Take only the blue half and stretch it across the whole target.
        new Canvas(target).drawBitmap(source, new Rect(2, 0, 4, 1), new RectF(0, 0, 4, 1), new Paint());

        assertTrue((target.getPixel(0, 0) & 0xFF) > 200, "the source rect was ignored");
        assertTrue(((target.getPixel(0, 0) >> 16) & 0xFF) < 60);
    }
}
