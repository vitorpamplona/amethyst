package android.media;

import android.content.Context;
import android.net.Uri;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;

/**
 * JVM stand-in for android.media.MediaScannerConnection.
 *
 * Android keeps a media index that a newly written file is invisible to until
 * something tells the scanner about it. No desktop has that intermediary: a
 * file manager reads the directory, and the platforms that do index (Spotlight,
 * Windows Search, Tracker) watch the filesystem themselves. So there is nothing
 * to notify, and the callback fires immediately with the file's own URI, which
 * is what callers wait for.
 */
public final class MediaScannerConnection {
    private MediaScannerConnection() {}

    public interface OnScanCompletedListener {
        void onScanCompleted(String path, Uri uri);
    }

    static {
        PlatformGaps.declareUnavailable(
                "MediaScannerConnection",
                "Android needs a newly written file announced to its media index before it appears in "
                        + "the gallery. Desktop file managers read directories directly and the indexers "
                        + "that exist watch the filesystem, so there is nothing to announce to.");
    }

    public static void scanFile(
            Context context, String[] paths, String[] mimeTypes, OnScanCompletedListener callback) {
        if (callback == null || paths == null) return;
        for (String path : paths) {
            callback.onScanCompleted(path, Uri.fromFile(new java.io.File(path)));
        }
    }
}
