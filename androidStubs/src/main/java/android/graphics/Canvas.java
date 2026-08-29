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

    public void drawCircle(float cx, float cy, float radius, Paint paint) {
        graphics.setColor(toAwt(paint.getColor()));
        graphics.fillOval((int) (cx - radius), (int) (cy - radius), (int) (radius * 2), (int) (radius * 2));
    }

    public void drawBitmap(Bitmap source, float left, float top, Paint paint) {
        graphics.drawImage(source.getImage(), (int) left, (int) top, null);
    }

    public void drawText(String text, float x, float y, Paint paint) {
        graphics.setColor(toAwt(paint.getColor()));
        graphics.drawString(text, x, y);
    }

    private static java.awt.Color toAwt(int argb) {
        return new java.awt.Color(argb, true);
    }
}
