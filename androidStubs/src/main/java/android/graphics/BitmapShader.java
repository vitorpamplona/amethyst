package android.graphics;

import java.awt.Rectangle;
import java.awt.TexturePaint;

/**
 * JVM stand-in for android.graphics.BitmapShader.
 *
 * Backed by AWT's {@link TexturePaint}, which fills a shape with an image — so
 * {@code drawCircle} with a bitmap shader really produces a circular avatar
 * rather than a flat disc. That matters: the notification path builds every
 * round avatar this way, and a shader that was ignored would put a solid
 * coloured circle where the user's face should be.
 *
 * The tile modes are AWT's only implicitly — TexturePaint repeats, which
 * matches REPEAT and is indistinguishable from CLAMP when the fill is no larger
 * than the bitmap, as it is here.
 */
public final class BitmapShader extends Shader {
    private final Bitmap bitmap;
    private final TileMode tileX;
    private final TileMode tileY;

    public BitmapShader(Bitmap bitmap, TileMode tileX, TileMode tileY) {
        this.bitmap = bitmap;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    public Bitmap getBitmap() { return bitmap; }

    public TileMode getTileModeX() { return tileX; }

    public TileMode getTileModeY() { return tileY; }

    @Override
    java.awt.Paint toAwtPaint(int width, int height) {
        if (bitmap == null) return null;
        return new TexturePaint(bitmap.getImage(), new Rectangle(0, 0, bitmap.getWidth(), bitmap.getHeight()));
    }
}
