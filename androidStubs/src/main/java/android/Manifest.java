package android;

/**
 * JVM stand-in for android.Manifest.
 *
 * The names are kept verbatim so permission-checking code compiles; on the
 * desktop every check resolves through Context.checkSelfPermission, which
 * reports denied unless the desktop app grants it.
 */
public final class Manifest {
    private Manifest() {}

    public static final class permission {
        public static final String CAMERA = "android.permission.CAMERA";
        public static final String RECORD_AUDIO = "android.permission.RECORD_AUDIO";
        public static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
        public static final String ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";
        public static final String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
        public static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
        public static final String WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";
        public static final String READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES";
        public static final String READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO";
        public static final String READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO";
        public static final String INTERNET = "android.permission.INTERNET";
        public static final String VIBRATE = "android.permission.VIBRATE";
        public static final String FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE";
        public static final String WAKE_LOCK = "android.permission.WAKE_LOCK";

        private permission() {}
    }
}
