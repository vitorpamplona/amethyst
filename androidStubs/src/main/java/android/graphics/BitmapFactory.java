package android.graphics;

import java.io.File;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** JVM stand-in for android.graphics.BitmapFactory, backed by ImageIO. */
public final class BitmapFactory {
    private BitmapFactory() {}

    public static class Options {
        public boolean inJustDecodeBounds;
        public int inSampleSize = 1;
        public int outWidth;
        public int outHeight;
        public String outMimeType;
        public Bitmap.Config inPreferredConfig = Bitmap.Config.ARGB_8888;
    }

    public static Bitmap decodeStream(InputStream stream) { return decodeStream(stream, null, null); }

    public static Bitmap decodeStream(InputStream stream, Object outPadding, Options options) {
        try {
            java.awt.image.BufferedImage img = ImageIO.read(stream);
            if (img == null) return null;
            if (options != null) {
                options.outWidth = img.getWidth();
                options.outHeight = img.getHeight();
                if (options.inJustDecodeBounds) return null;
            }
            return Bitmap.wrap(img);
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap decodeFile(String path) { return decodeFile(path, null); }

    public static Bitmap decodeFile(String path, Options options) {
        try {
            java.awt.image.BufferedImage img = ImageIO.read(new File(path));
            return img == null ? null : Bitmap.wrap(img);
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        return decodeStream(new java.io.ByteArrayInputStream(data, offset, length));
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length, Options options) {
        return decodeStream(new java.io.ByteArrayInputStream(data, offset, length), null, options);
    }
}
