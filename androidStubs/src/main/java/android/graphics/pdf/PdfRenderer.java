package android.graphics.pdf;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;

/**
 * JVM stand-in for android.graphics.pdf.PdfRenderer.
 *
 * Rendering PDF pages to bitmaps is squarely something desktop does — PDFBox
 * has done it for years — so this is a real capability behind an SPI, not a
 * missing one. The library choice is an application decision (PDFBox is
 * Apache-2.0 but adds several MB), so the app installs a {@link Backend}.
 * Without one, opening a document reports a gap and yields zero pages, which
 * the viewer already handles as an unreadable file.
 */
public final class PdfRenderer implements AutoCloseable {
    public interface Backend {
        int pageCount(java.io.File file);

        /** Renders one page at the requested pixel size, or null if it cannot. */
        Bitmap renderPage(java.io.File file, int pageIndex, int widthPx, int heightPx);

        /** Page size in PDF points, used to pick an aspect-correct bitmap. */
        float[] pageSize(java.io.File file, int pageIndex);
    }

    private static volatile Backend backend;

    public static void setBackend(Backend value) { backend = value; }

    public final class Page implements AutoCloseable {
        public static final int RENDER_MODE_FOR_DISPLAY = 1;
        public static final int RENDER_MODE_FOR_PRINT = 2;

        private final int index;

        Page(int index) { this.index = index; }

        public int getIndex() { return index; }

        public int getWidth() { return (int) size()[0]; }

        public int getHeight() { return (int) size()[1]; }

        public void render(Bitmap destination, android.graphics.Rect clip, android.graphics.Matrix transform, int mode) {
            Backend installed = backend;
            if (installed == null || destination == null) return;
            Bitmap rendered = installed.renderPage(file, index, destination.getWidth(), destination.getHeight());
            if (rendered == null) return;
            java.awt.Graphics2D graphics = destination.getImage().createGraphics();
            graphics.drawImage(rendered.getImage(), 0, 0, null);
            graphics.dispose();
        }

        private float[] size() {
            Backend installed = backend;
            float[] size = installed == null ? null : installed.pageSize(file, index);
            return size == null ? new float[] {612f, 792f} : size;
        }

        @Override public void close() {}
    }

    private final java.io.File file;
    private final int pageCount;

    public PdfRenderer(ParcelFileDescriptor descriptor) {
        this.file = descriptor == null ? null : descriptor.getFile();
        Backend installed = backend;
        if (installed == null || file == null) {
            PlatformGaps.report(
                    "PdfRenderer",
                    "no desktop PDF backend installed; PDFBox renders pages to images and is Apache-2.0");
            this.pageCount = 0;
        } else {
            this.pageCount = installed.pageCount(file);
        }
    }

    public int getPageCount() { return pageCount; }

    public Page openPage(int index) { return new Page(index); }

    @Override public void close() {}
}
