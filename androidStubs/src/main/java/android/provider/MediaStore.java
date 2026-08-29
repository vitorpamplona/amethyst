package android.provider;

import android.net.Uri;
import android.os.Environment;
import java.io.File;

/**
 * JVM stand-in for android.provider.MediaStore.
 *
 * On Android these constants name collections inside MediaProvider; on desktop
 * the same intent — "put this where the user's pictures/music/downloads live" —
 * is served by the XDG user directories {@link Environment} already maps. So a
 * collection URI here is a marker that {@link #directoryFor} turns into a real
 * directory, and a ContentResolver insert writes an actual file there.
 *
 * That is the whole of what the app uses MediaStore for: saving downloaded
 * media somewhere the user's file manager will show it.
 */
public final class MediaStore {
    private MediaStore() {}

    /** Columns an insert can set. Named as on Android so the callers compile. */
    public static final class MediaColumns {
        private MediaColumns() {}

        public static final String DISPLAY_NAME = "_display_name";
        public static final String MIME_TYPE = "mime_type";
        public static final String RELATIVE_PATH = "relative_path";
        public static final String DATA = "_data";
        public static final String SIZE = "_size";
        public static final String DATE_ADDED = "date_added";
        public static final String IS_PENDING = "is_pending";
    }

    public static final class Images {
        private Images() {}

        public static final class Media {
            private Media() {}

            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/images/media");
        }
    }

    public static final class Video {
        private Video() {}

        public static final class Media {
            private Media() {}

            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/video/media");
        }
    }

    public static final class Audio {
        private Audio() {}

        public static final class Media {
            private Media() {}

            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/media");
        }
    }

    public static final class Downloads {
        private Downloads() {}

        public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/downloads");
    }

    /**
     * The user directory a collection maps to, or null when the URI is not one
     * of these collections — the caller should then treat the insert as
     * unsupported rather than guessing a location.
     */
    public static File directoryFor(Uri collection) {
        if (collection == null) return null;
        String path = String.valueOf(collection);
        if (path.endsWith("/images/media")) return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (path.endsWith("/video/media")) return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (path.endsWith("/audio/media")) return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (path.endsWith("/downloads")) return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return null;
    }
}
