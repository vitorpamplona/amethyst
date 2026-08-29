package android.graphics;

import java.awt.image.BufferedImage;
import java.io.OutputStream;
import javax.imageio.ImageIO;

/**
 * JVM stand-in for android.graphics.Bitmap, backed by a BufferedImage.
 *
 * Real rather than inert: bitmaps are how the app moves image data between
 * decode, resize and upload, and all of that works on the JVM. The image is
 * exposed via {@link #getImage()} so desktop code can hand it to Skia or
 * ImageIO without going through a lossy round trip.
 */
public final class Bitmap {
    public enum Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE }

    public enum CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }

    private final BufferedImage image;
    private boolean recycled;

    private Bitmap(BufferedImage image) { this.image = image; }

    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap(new BufferedImage(Math.max(width, 1), Math.max(height, 1), BufferedImage.TYPE_INT_ARGB));
    }

    public static Bitmap createBitmap(int[] pixels, int width, int height, Config config) {
        BufferedImage out = new BufferedImage(Math.max(width, 1), Math.max(height, 1), BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, width, height, pixels, 0, width);
        return new Bitmap(out);
    }

    public static Bitmap createBitmap(Bitmap src) {
        return createBitmap(src, 0, 0, src.getWidth(), src.getHeight());
    }

    public static Bitmap createBitmap(Bitmap src, int x, int y, int width, int height) {
        return createBitmap(src, x, y, width, height, null, false);
    }

    /**
     * The crop-and-transform overload. Android applies the matrix after the
     * crop, which for the app's use — centre-crop then scale to a thumbnail
     * size — means the output is the transformed bounds, not the crop's.
     */
    public static Bitmap createBitmap(
            Bitmap src, int x, int y, int width, int height, Matrix matrix, boolean filter) {
        BufferedImage cropped =
                src.image.getSubimage(
                        Math.max(0, Math.min(x, src.getWidth() - 1)),
                        Math.max(0, Math.min(y, src.getHeight() - 1)),
                        Math.max(1, Math.min(width, src.getWidth() - x)),
                        Math.max(1, Math.min(height, src.getHeight() - y)));

        if (matrix == null || matrix.isIdentity()) {
            // getSubimage shares the parent's raster; copy so recycling or
            // mutating the source cannot corrupt the result.
            BufferedImage copy = new BufferedImage(cropped.getWidth(), cropped.getHeight(), BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = copy.createGraphics();
            g.drawImage(cropped, 0, 0, null);
            g.dispose();
            return new Bitmap(copy);
        }

        java.awt.geom.AffineTransform transform = matrix.toAffineTransform();
        java.awt.geom.Rectangle2D bounds =
                transform.createTransformedShape(
                                new java.awt.Rectangle(0, 0, cropped.getWidth(), cropped.getHeight()))
                        .getBounds2D();

        BufferedImage out =
                new BufferedImage(
                        Math.max(1, (int) Math.round(bounds.getWidth())),
                        Math.max(1, (int) Math.round(bounds.getHeight())),
                        BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                filter ? java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                       : java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.translate(-bounds.getX(), -bounds.getY());
        g.drawImage(cropped, transform, null);
        g.dispose();
        return new Bitmap(out);
    }

    public static Bitmap createScaledBitmap(Bitmap src, int dstWidth, int dstHeight, boolean filter) {
        BufferedImage out = new BufferedImage(Math.max(dstWidth, 1), Math.max(dstHeight, 1), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                filter ? java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                       : java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src.image, 0, 0, dstWidth, dstHeight, null);
        g.dispose();
        return new Bitmap(out);
    }

    public static Bitmap wrap(BufferedImage image) { return new Bitmap(image); }

    public BufferedImage getImage() { return image; }

    public int getWidth() { return image.getWidth(); }

    public int getHeight() { return image.getHeight(); }

    public Config getConfig() { return Config.ARGB_8888; }

    public boolean isRecycled() { return recycled; }

    public void recycle() { recycled = true; }

    public int getPixel(int x, int y) { return image.getRGB(x, y); }

    public void setPixel(int x, int y, int color) { image.setRGB(x, y, color); }

    public void getPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
        image.getRGB(x, y, width, height, pixels, offset, stride);
    }

    public void setPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
        image.setRGB(x, y, width, height, pixels, offset, stride);
    }

    /** Bytes a pixel occupies; ARGB_8888 throughout, as {@link #getConfig} reports. */
    public int getByteCount() { return getWidth() * getHeight() * 4; }

    public int getAllocationByteCount() { return getByteCount(); }

    public boolean hasAlpha() { return true; }

    public Bitmap copy(Config config, boolean mutable) { return createBitmap(this); }

    public boolean compress(CompressFormat format, int quality, OutputStream stream) {
        try {
            // ImageIO has no WEBP writer in the JDK; PNG is the lossless
            // fallback so a compress() never silently produces nothing.
            String informalName = (format == CompressFormat.JPEG) ? "jpg" : "png";
            return ImageIO.write(image, informalName, stream);
        } catch (Exception e) {
            return false;
        }
    }
}
