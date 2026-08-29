package android.graphics;

import android.content.ContentResolver;
import android.net.Uri;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * JVM stand-in for android.graphics.ImageDecoder, backed by ImageIO.
 *
 * Android's decoder reads whatever the platform's codecs support; ImageIO reads
 * PNG, JPEG, GIF, BMP and WBMP out of the box, and more if a plugin is on the
 * classpath. AVIF and HEIF are the notable absences, so a decode of those
 * returns null and reports a gap once, naming the format — which is what the
 * callers already handle, since they guard AVIF decoding by API level and treat
 * null as "no preview". A decoder that returned a blank bitmap instead would
 * put an empty thumbnail on the post.
 */
public final class ImageDecoder {
    /** Matches Android's constants so the callers' listener bodies compile. */
    public static final int ALLOCATOR_DEFAULT = 0;
    public static final int ALLOCATOR_SOFTWARE = 1;
    public static final int ALLOCATOR_SHARED_MEMORY = 2;
    public static final int ALLOCATOR_HARDWARE = 3;

    public int allocator = ALLOCATOR_DEFAULT;
    public boolean isMutableRequired;

    private ImageDecoder() {}

    /** Where the bytes come from. Resolved lazily, as Android's is. */
    public abstract static class Source {
        abstract InputStream open() throws IOException;

        /** For the gap message when nothing can read it. */
        abstract String describe();
    }

    public interface OnHeaderDecodedListener {
        void onHeaderDecoded(ImageDecoder decoder, ImageInfo info, Source source);
    }

    /** The size and mime type Android reports before the full decode. */
    public static final class ImageInfo {
        private final int width;
        private final int height;
        private final String mimeType;

        ImageInfo(int width, int height, String mimeType) {
            this.width = width;
            this.height = height;
            this.mimeType = mimeType;
        }

        public Size getSize() { return new Size(width, height); }

        public String getMimeType() { return mimeType; }
    }

    /** Minimal stand-in for android.util.Size, which only appears here. */
    public static final class Size {
        private final int width;
        private final int height;

        Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() { return width; }

        public int getHeight() { return height; }
    }

    public static Source createSource(byte[] data) {
        return new Source() {
            @Override
            InputStream open() { return new ByteArrayInputStream(data); }

            @Override
            String describe() { return "a " + data.length + "-byte buffer"; }
        };
    }

    public static Source createSource(ByteBuffer buffer) {
        return new Source() {
            @Override
            InputStream open() {
                ByteBuffer copy = buffer.duplicate();
                byte[] bytes = new byte[copy.remaining()];
                copy.get(bytes);
                return new ByteArrayInputStream(bytes);
            }

            @Override
            String describe() { return "a " + buffer.remaining() + "-byte buffer"; }
        };
    }

    public static Source createSource(File file) {
        return new Source() {
            @Override
            InputStream open() throws IOException { return new java.io.FileInputStream(file); }

            @Override
            String describe() { return file.getName(); }
        };
    }

    public static Source createSource(ContentResolver resolver, Uri uri) {
        return new Source() {
            @Override
            InputStream open() { return resolver.openInputStream(uri); }

            @Override
            String describe() { return String.valueOf(uri); }
        };
    }

    public static Bitmap decodeBitmap(Source source) {
        return decodeBitmap(source, null);
    }

    public static Bitmap decodeBitmap(Source source, OnHeaderDecodedListener listener) {
        BufferedImage image = read(source);
        if (image == null) return null;

        if (listener != null) {
            // Android calls this between header and full decode so the caller
            // can set the allocator; nothing it can set changes an ImageIO
            // read, but the callback still has to fire or a caller that does
            // its own bookkeeping there would be skipped.
            listener.onHeaderDecoded(
                    new ImageDecoder(),
                    new ImageInfo(image.getWidth(), image.getHeight(), null),
                    source);
        }
        return Bitmap.wrap(image);
    }

    public static Drawable decodeDrawable(Source source) {
        Bitmap bitmap = decodeBitmap(source);
        return bitmap == null ? null : new Drawable(bitmap);
    }

    private static BufferedImage read(Source source) {
        try (InputStream stream = source.open()) {
            if (stream == null) return null;
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                PlatformGaps.report(
                        "ImageDecoder.unsupportedFormat",
                        "no ImageIO reader for " + source.describe()
                                + " (AVIF and HEIF need a plugin on the classpath); decoded as null");
            }
            return image;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The little that {@code decodeDrawable}'s callers need. */
    public static final class Drawable {
        private final Bitmap bitmap;

        Drawable(Bitmap bitmap) { this.bitmap = bitmap; }

        public Bitmap getBitmap() { return bitmap; }

        public int getIntrinsicWidth() { return bitmap.getWidth(); }

        public int getIntrinsicHeight() { return bitmap.getHeight(); }
    }
}
