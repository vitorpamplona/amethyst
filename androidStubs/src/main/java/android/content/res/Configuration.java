package android.content.res;

import android.os.LocaleList;

/** JVM stand-in for android.content.res.Configuration. See Context. */
public abstract class Configuration {
    public static final int ORIENTATION_PORTRAIT = 1;
    public static final int ORIENTATION_LANDSCAPE = 2;
    public static final int UI_MODE_NIGHT_YES = 0x20;
    public static final int UI_MODE_NIGHT_NO = 0x10;
    public static final int UI_MODE_NIGHT_MASK = 0x30;

    /**
     * Window metrics in dp. On Android these describe the app window; the JVM
     * implementation fills them from the actual screen, so a layout that
     * branches on height branches on something true.
     */
    public int screenWidthDp;
    public int screenHeightDp;
    public int smallestScreenWidthDp;
    public int densityDpi = 160;
    public int orientation = ORIENTATION_LANDSCAPE;
    public int uiMode;

    public abstract LocaleList getLocales();
}
