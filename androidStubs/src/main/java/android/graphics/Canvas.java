package android.graphics;

/**
 * JVM stand-in for android.graphics.Canvas, drawing into a {@link Bitmap}'s
 * BufferedImage.
 *
 * Real rather than inert: the JDK's Graphics2D does everything the handful of
 * remaining call sites ask for, and a Canvas that silently drew nothing would
 * leave blank images with no error.
 */
public class Canvas {
    private final java.awt.Graphics2D graphics;
    private final Bitmap bitmap;

    public Canvas(Bitmap bitmap) {
        this.bitmap = bitmap;
        this.graphics = bitmap.getImage().createGraphics();
        graphics.setRenderingHint(
                java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
    }

    public int getWidth() { return bitmap.getWidth(); }

    public int getHeight() { return bitmap.getHeight(); }

    public void drawColor(int color) {
        graphics.setColor(toAwt(color));
        graphics.fillRect(0, 0, getWidth(), getHeight());
    }

    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        graphics.setColor(toAwt(paint.getColor()));
        graphics.fillRect((int) left, (int) top, (int) (right - left), (int) (bottom - top));
    }

    /**
     * Honours a bitmap shader, because that is how every round avatar in the
     * app is made: fill a circle with the image. Ignoring the shader would draw
     * a solid disc where a face should be — a picture, not an error.
     */
    public void drawCircle(float cx, float cy, float radius, Paint paint) {
        int size = (int) (radius * 2);
        int x = (int) (cx - radius);
        int y = (int) (cy - radius);

        java.awt.Paint fill = paint.getShader() == null ? null : paint.getShader().toAwtPaint(size, size);
        if (fill != null) {
            java.awt.Graphics2D scoped = (java.awt.Graphics2D) graphics.create();
            try {
                scoped.setRenderingHint(
                        java.awt.RenderingHints.KEY_ANTIALIASING,
                        paint.isAntiAlias()
                                ? java.awt.RenderingHints.VALUE_ANTIALIAS_ON
                                : java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
                scoped.setPaint(fill);
                scoped.fillOval(x, y, size, size);
            } finally {
                scoped.dispose();
            }
            return;
        }

        graphics.setColor(toAwt(paint.getColor()));
        graphics.fillOval(x, y, size, size);
    }

    public void drawBitmap(Bitmap source, float left, float top, Paint paint) {
        graphics.drawImage(source.getImage(), (int) left, (int) top, null);
    }

    /**
     * The scale-and-place overload: take {@code src} of the source (or all of
     * it when null) and stretch it into {@code dst}. Used to composite a badge
     * into a corner, so getting it wrong puts the badge at the wrong size or in
     * the wrong place rather than failing.
     */
    public void drawBitmap(Bitmap source, Rect src, RectF dst, Paint paint) {
        if (source == null || dst == null) return;
        int sx = src == null ? 0 : src.left;
        int sy = src == null ? 0 : src.top;
        int sw = src == null ? source.getWidth() : src.width();
        int sh = src == null ? source.getHeight() : src.height();

        java.awt.Graphics2D scoped = (java.awt.Graphics2D) graphics.create();
        try {
            scoped.setRenderingHint(
                    java.awt.RenderingHints.KEY_INTERPOLATION,
                    paint != null && paint.isFilterBitmap()
                            ? java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                            : java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            scoped.drawImage(
                    source.getImage(),
                    (int) dst.left, (int) dst.top, (int) dst.right, (int) dst.bottom,
                    sx, sy, sx + sw, sy + sh,
                    null);
        } finally {
            scoped.dispose();
        }
    }

    public void drawBitmap(Bitmap source, Rect src, Rect dst, Paint paint) {
        if (dst == null) return;
        drawBitmap(source, src, new RectF(dst.left, dst.top, dst.right, dst.bottom), paint);
    }

    public void drawText(String text, float x, float y, Paint paint) {
        graphics.setColor(toAwt(paint.getColor()));
        graphics.drawString(text, x, y);
    }

    private static java.awt.Color toAwt(int argb) {
        return new java.awt.Color(argb, true);
    }
}
