package android.media;

import android.graphics.Bitmap;

/**
 * JVM stand-in for android.media.MediaMetadataRetriever.
 *
 * Reading duration, dimensions and rotation out of a video is something the
 * desktop genuinely needs, but the JDK cannot do it alone — :desktopApp
 * already carries JCodec and an ffmpeg fallback for exactly this. Rather than
 * duplicating that here, extraction routes through a delegate the desktop app
 * installs. With none installed every key reads null, which is what this class
 * does on Android for an unsupported file anyway.
 */
public class MediaMetadataRetriever implements AutoCloseable {
    public static final int METADATA_KEY_DURATION = 9;
    public static final int METADATA_KEY_TITLE = 7;
    public static final int METADATA_KEY_ARTIST = 2;
    public static final int METADATA_KEY_ALBUM = 1;
    public static final int METADATA_KEY_ALBUMARTIST = 13;
    public static final int METADATA_KEY_VIDEO_WIDTH = 18;
    public static final int METADATA_KEY_VIDEO_HEIGHT = 19;
    public static final int METADATA_KEY_VIDEO_ROTATION = 24;
    public static final int METADATA_KEY_CAPTURE_FRAMERATE = 25;
    public static final int METADATA_KEY_MIMETYPE = 12;

    public static final int OPTION_PREVIOUS_SYNC = 0;
    public static final int OPTION_NEXT_SYNC = 1;
    public static final int OPTION_CLOSEST_SYNC = 2;
    public static final int OPTION_CLOSEST = 3;

    public static class BitmapParams {
        public Bitmap.Config preferredConfig = Bitmap.Config.ARGB_8888;

        public Bitmap.Config getPreferredConfig() { return preferredConfig; }

        public void setPreferredConfig(Bitmap.Config config) { this.preferredConfig = config; }
    }

    /** Installed by the desktop app; see the JCodec/ffmpeg extractor in :desktopApp. */
    public interface Extractor {
        String extractMetadata(String source, int keyCode);

        Bitmap frameAt(String source, long timeUs);

        /** Cover art, when the container has any. Default: none. */
        default byte[] embeddedPicture(String source) { return null; }
    }

    private static volatile Extractor extractor;

    public static void setExtractor(Extractor value) { extractor = value; }

    private String source;
    private java.io.File temporary;

    public void setDataSource(String path) { this.source = path; }

    public void setDataSource(String uri, java.util.Map<String, String> headers) { this.source = uri; }

    public void setDataSource(java.io.FileDescriptor fd) {
        com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                "MediaMetadataRetriever.setDataSource(FileDescriptor)",
                "the extractor works from a path; a descriptor has no path to give it");
    }

    /**
     * Resolves the URI to a path the extractor can open. A content URI that is
     * not a file is spilled to a temp file rather than dropped, because the
     * alternative — reading no metadata — silently posts a video with no
     * dimensions or duration.
     */
    public void setDataSource(android.content.Context context, android.net.Uri uri) {
        if (uri == null) return;
        String path = uri.getPath();
        if (path != null && new java.io.File(path).isFile()) {
            this.source = path;
            return;
        }
        try (java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return;
            java.io.File temp = java.io.File.createTempFile("amethyst-media", null);
            temp.deleteOnExit();
            java.nio.file.Files.copy(in, temp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            this.source = temp.getAbsolutePath();
            this.temporary = temp;
        } catch (Exception e) {
            com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                    "MediaMetadataRetriever.setDataSource(Uri)", "could not read " + uri + ": " + e);
        }
    }

    /**
     * The extractors this delegates to read files, so an in-memory source is
     * written to a temp file first. Copying a few megabytes once is cheaper
     * than the feature not working.
     */
    public void setDataSource(MediaDataSource dataSource) {
        if (dataSource == null) return;
        try {
            java.io.File temp = java.io.File.createTempFile("amethyst-media", null);
            temp.deleteOnExit();
            try (java.io.OutputStream out = new java.io.FileOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                long position = 0;
                while (true) {
                    int read = dataSource.readAt(position, buffer, 0, buffer.length);
                    if (read <= 0) break;
                    out.write(buffer, 0, read);
                    position += read;
                }
            }
            this.source = temp.getAbsolutePath();
            this.temporary = temp;
        } catch (Exception e) {
            com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                    "MediaMetadataRetriever.setDataSource(MediaDataSource)", "could not buffer the source: " + e);
        }
    }

    /** Cover art embedded in the container, or null when there is none. */
    public byte[] getEmbeddedPicture() {
        Extractor e = extractor;
        return (e == null || source == null) ? null : e.embeddedPicture(source);
    }

    public String extractMetadata(int keyCode) {
        Extractor e = extractor;
        return (e == null || source == null) ? null : e.extractMetadata(source, keyCode);
    }

    public Bitmap getFrameAtTime(long timeUs) {
        Extractor e = extractor;
        return (e == null || source == null) ? null : e.frameAt(source, timeUs);
    }

    public Bitmap getFrameAtTime() { return getFrameAtTime(0L); }

    public Bitmap getFrameAtTime(long timeUs, int option) { return getFrameAtTime(timeUs); }

    public Bitmap getFrameAtTime(long timeUs, int option, BitmapParams params) { return getFrameAtTime(timeUs); }

    public Bitmap getScaledFrameAtTime(long timeUs, int option, int width, int height, BitmapParams params) {
        return getFrameAtTime(timeUs);
    }

    public void release() {
        source = null;
        if (temporary != null) {
            temporary.delete();
            temporary = null;
        }
    }

    @Override public void close() { release(); }
}
