package android.content;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.net.ConnectivityManager;
import android.os.PowerManager;
import org.junit.jupiter.api.Test;

/**
 * A null ActivityManager is not a harmless null: callers read the memory class
 * from it to size caches and the playback pool, and a null there means sizing
 * for zero bytes.
 */
class SystemServiceTest {
    private final Context context = new TestContext();

    @Test
    void modelledServicesComeBackReal() {
        assertNotNull(context.getSystemService(ActivityManager.class));
        assertNotNull(context.getSystemService(ConnectivityManager.class));
        assertNotNull(context.getSystemService(NotificationManager.class));
        assertNotNull(context.getSystemService(Context.ACTIVITY_SERVICE));
    }

    @Test
    void aServiceIsASingleton() {
        assertSame(
                context.getSystemService(ActivityManager.class),
                context.getSystemService(ActivityManager.class));
    }

    @Test
    void unmodelledServicesAreStillNull() {
        // Callers already handle null — Android returns it for a service a
        // device does not have — so this stays the honest answer.
        assertNull(context.getSystemService("something_else"));
        assertNull(context.getSystemService(PowerManager.class));
        assertNull(context.getSystemService((String) null));
    }

    @Test
    void memoryClassReflectsThisJvmsHeap() {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        int memoryClass = manager.getMemoryClass();

        assertTrue(memoryClass > 0, "a zero memory class would size every cache to nothing");
        assertTrue(memoryClass <= Runtime.getRuntime().maxMemory() / (1024 * 1024) + 1);
    }

    @Test
    void memoryInfoIsFilledIn() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        context.getSystemService(ActivityManager.class).getMemoryInfo(info);

        assertTrue(info.totalMem > 0);
        assertTrue(info.availMem > 0);
    }

    /** Minimal concrete Context; only the file/service parts are exercised. */
    private static final class TestContext extends Context {
        @Override public String getPackageName() { return "com.vitorpamplona.amethyst"; }

        @Override public android.content.res.Resources getResources() { return null; }

        @Override public String getString(int resId) { return ""; }

        @Override public String getString(int resId, Object... formatArgs) { return ""; }

        @Override public java.io.File getCacheDir() { return tempDir(); }

        @Override public java.io.File getFilesDir() { return tempDir(); }

        @Override public java.io.File getExternalCacheDir() { return tempDir(); }

        @Override public java.io.File getExternalFilesDir(String type) { return tempDir(); }

        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return null; }

        @Override public ContentResolver getContentResolver() { return null; }

        private static java.io.File tempDir() {
            return new java.io.File(System.getProperty("java.io.tmpdir"), "amethyst-test");
        }
    }
}
