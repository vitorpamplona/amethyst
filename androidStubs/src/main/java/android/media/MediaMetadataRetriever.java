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

    public static class BitmapParams {
        public Bitmap.Config preferredConfig = Bitmap.Config.ARGB_8888;

        public Bitmap.Config getPreferredConfig() { return preferredConfig; }

        public void setPreferredConfig(Bitmap.Config config) { this.preferredConfig = config; }
    }

    /** Installed by the desktop app; see the JCodec/ffmpeg extractor in :desktopApp. */
    public interface Extractor {
        String extractMetadata(String source, int keyCode);

        Bitmap frameAt(String source, long timeUs);
    }

    private static volatile Extractor extractor;

    public static void setExtractor(Extractor value) { extractor = value; }

    private String source;

    public void setDataSource(String path) { this.source = path; }

    public void setDataSource(String uri, java.util.Map<String, String> headers) { this.source = uri; }

    public String extractMetadata(int keyCode) {
        Extractor e = extractor;
        return (e == null || source == null) ? null : e.extractMetadata(source, keyCode);
    }

    public Bitmap getFrameAtTime(long timeUs) {
        Extractor e = extractor;
        return (e == null || source == null) ? null : e.frameAt(source, timeUs);
    }

    public Bitmap getFrameAtTime() { return getFrameAtTime(0L); }

    public Bitmap getScaledFrameAtTime(long timeUs, int option, int width, int height, BitmapParams params) {
        return getFrameAtTime(timeUs);
    }

    public void release() { source = null; }

    @Override public void close() { release(); }
}
