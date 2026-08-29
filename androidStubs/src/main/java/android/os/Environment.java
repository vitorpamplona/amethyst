package android.os;

import java.io.File;

/**
 * JVM stand-in for android.os.Environment.
 *
 * Android's shared-storage directories map onto the XDG user directories, which
 * is where a desktop user expects downloads and pictures to land.
 */
public final class Environment {
    public static final String DIRECTORY_DOWNLOADS = "Downloads";
    public static final String DIRECTORY_PICTURES = "Pictures";
    public static final String DIRECTORY_MOVIES = "Videos";
    public static final String DIRECTORY_MUSIC = "Music";
    public static final String DIRECTORY_DCIM = "Pictures";
    public static final String MEDIA_MOUNTED = "mounted";

    private Environment() {}

    public static File getExternalStorageDirectory() { return home(); }

    public static File getExternalStoragePublicDirectory(String type) {
        File dir = new File(home(), type == null ? "" : type);
        if (!dir.isDirectory() && !dir.mkdirs()) return home();
        return dir;
    }

    public static String getExternalStorageState() { return MEDIA_MOUNTED; }

    public static boolean isExternalStorageManager() { return true; }

    private static File home() { return new File(System.getProperty("user.home", ".")); }
}
