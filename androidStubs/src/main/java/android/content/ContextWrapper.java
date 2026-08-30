package android.content;

import android.content.res.Resources;
import java.io.File;

/**
 * JVM stand-in for android.content.ContextWrapper.
 *
 * Real, not inert: the app walks this chain to find the Activity or Window
 * behind a Compose {@code LocalContext} ({@code tailrec fun
 * Context.getActivity()}). If {@code getBaseContext()} did not actually return
 * the next link, those walks would stop one step early and silently report "no
 * activity" — the kind of wrong answer that reads as a missing feature rather
 * than a missing stub.
 */
public class ContextWrapper extends Context {
    private Context base;

    public ContextWrapper(Context base) { this.base = base; }

    public Context getBaseContext() { return base; }

    protected void attachBaseContext(Context base) { this.base = base; }

    private Context require() {
        if (base != null) return base;
        return requireApplicationContext();
    }

    @Override public String getPackageName() { return require().getPackageName(); }

    @Override public Resources getResources() { return require().getResources(); }

    @Override public String getString(int resId) { return require().getString(resId); }

    @Override public String getString(int resId, Object... formatArgs) { return require().getString(resId, formatArgs); }

    @Override public File getCacheDir() { return require().getCacheDir(); }

    @Override public File getFilesDir() { return require().getFilesDir(); }

    @Override public File getExternalCacheDir() { return require().getExternalCacheDir(); }

    @Override public File getExternalFilesDir(String type) { return require().getExternalFilesDir(type); }

    @Override public SharedPreferences getSharedPreferences(String name, int mode) {
        return require().getSharedPreferences(name, mode);
    }

    @Override public ContentResolver getContentResolver() { return require().getContentResolver(); }

    @Override public Object getSystemService(String name) { return require().getSystemService(name); }

    @Override public <T> T getSystemService(Class<T> serviceClass) { return require().getSystemService(serviceClass); }
}
