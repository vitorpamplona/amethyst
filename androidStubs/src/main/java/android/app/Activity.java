package android.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import java.io.File;

/**
 * JVM stand-in for android.app.Activity.
 *
 * There is no Activity on desktop. This exists so that code passing an
 * Activity around as a Context — which is most of what the app does with it —
 * compiles; the lifecycle methods are present and inert.
 */
public class Activity extends Context {
    public static final int RESULT_OK = -1;
    public static final int RESULT_CANCELED = 0;

    private Context base;

    public void attachBaseContext(Context base) { this.base = base; }

    protected void onCreate(Bundle savedInstanceState) {}

    protected void onStart() {}

    protected void onResume() {}

    protected void onPause() {}

    protected void onStop() {}

    protected void onDestroy() {}

    public void finish() {}

    public boolean isFinishing() { return false; }

    public Intent getIntent() { return new Intent(); }

    public void setIntent(Intent intent) {}

    @Override public String getPackageName() { return base == null ? "com.vitorpamplona.amethyst" : base.getPackageName(); }

    @Override public Resources getResources() { return base.getResources(); }

    @Override public String getString(int resId) { return base.getString(resId); }

    @Override public String getString(int resId, Object... formatArgs) { return base.getString(resId, formatArgs); }

    @Override public File getCacheDir() { return base.getCacheDir(); }

    @Override public File getFilesDir() { return base.getFilesDir(); }

    @Override public File getExternalCacheDir() { return base.getExternalCacheDir(); }

    @Override public File getExternalFilesDir(String type) { return base.getExternalFilesDir(type); }

    @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
        return base.getSharedPreferences(name, mode);
    }

    @Override public android.content.ContentResolver getContentResolver() { return base.getContentResolver(); }
}
