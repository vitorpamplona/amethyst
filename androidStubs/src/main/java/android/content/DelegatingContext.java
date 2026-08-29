package android.content;

import android.content.res.Resources;
import java.io.File;

/**
 * Base for the Android components that are Contexts without being the process
 * Context — Application, Service, Activity.
 *
 * On Android each of these carries its own resource and service plumbing; on
 * the JVM there is one process Context, so they all forward to it. Kept in one
 * place so the three do not drift apart.
 */
public abstract class DelegatingContext extends Context {
    @Override public String getPackageName() { return requireApplicationContext().getPackageName(); }

    @Override public Resources getResources() { return requireApplicationContext().getResources(); }

    @Override public String getString(int resId) { return requireApplicationContext().getString(resId); }

    @Override public String getString(int resId, Object... formatArgs) {
        return requireApplicationContext().getString(resId, formatArgs);
    }

    @Override public File getCacheDir() { return requireApplicationContext().getCacheDir(); }

    @Override public File getFilesDir() { return requireApplicationContext().getFilesDir(); }

    @Override public File getExternalCacheDir() { return requireApplicationContext().getExternalCacheDir(); }

    @Override public File getExternalFilesDir(String type) { return requireApplicationContext().getExternalFilesDir(type); }

    @Override public SharedPreferences getSharedPreferences(String name, int mode) {
        return requireApplicationContext().getSharedPreferences(name, mode);
    }

    @Override public ContentResolver getContentResolver() { return requireApplicationContext().getContentResolver(); }

    @Override public Object getSystemService(String name) { return requireApplicationContext().getSystemService(name); }

    @Override public <T> T getSystemService(Class<T> serviceClass) {
        return requireApplicationContext().getSystemService(serviceClass);
    }
}
