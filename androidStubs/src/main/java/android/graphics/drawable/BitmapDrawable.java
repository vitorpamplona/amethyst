package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;

/**
 * JVM stand-in for android.graphics.drawable.BitmapDrawable.
 *
 * Real, because it is the one Drawable the app actually reads back: every
 * notification icon and avatar goes through
 * {@code (image.asDrawable(resources) as? BitmapDrawable)?.bitmap}. A version
 * that dropped the bitmap would make every one of those null, which reads as
 * "the image failed to load" rather than as a missing stub.
 */
public class BitmapDrawable extends Drawable {
    private final Bitmap bitmap;

    public BitmapDrawable(Bitmap bitmap) { this.bitmap = bitmap; }

    public BitmapDrawable(Resources resources, Bitmap bitmap) { this.bitmap = bitmap; }

    public Bitmap getBitmap() { return bitmap; }

    @Override
    public int getIntrinsicWidth() { return bitmap == null ? -1 : bitmap.getWidth(); }

    @Override
    public int getIntrinsicHeight() { return bitmap == null ? -1 : bitmap.getHeight(); }

    public void setFilterBitmap(boolean filter) {}

    public void setAntiAlias(boolean antiAlias) {}

    @Override
    public void draw(Canvas canvas) {
        if (bitmap != null && canvas != null) canvas.drawBitmap(bitmap, getBounds().left, getBounds().top, null);
    }
}
