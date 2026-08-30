package android.content;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import android.app.Activity;
import android.app.Application;
import android.view.Window;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The app finds its Activity and its Window by walking up the Context chain
 * (`tailrec fun Context.getActivity()`), so two things have to hold: every link
 * must actually be reachable, and the chain must END. A wrapper whose base is
 * itself turns a tailrec walk into a hang, which is the worst failure of the
 * three — no exception, no log, just a frozen UI.
 */
class ContextChainTest {
    private ChainTestContext process;

    @BeforeEach
    void installProcessContext() {
        process = new ChainTestContext();
        Context.installApplicationContext(process);
    }

    @Test
    void anActivityIsAWrapperOverTheProcessContext() {
        Activity activity = new Activity();
        assertTrue(activity instanceof ContextWrapper);
        assertSame(process, activity.getBaseContext());
    }

    @Test
    void theChainTerminatesBecauseTheProcessContextIsNotAWrapper() {
        // This is the property the tailrec walks rely on. If the installed
        // process Context were itself a ContextWrapper the walk would step
        // forever instead of falling through to its `else -> null` branch.
        Context installed = process;
        assertTrue(!(installed instanceof ContextWrapper));
    }

    @Test
    void walkingUpFromAnActivityTerminates() {
        Context current = new Activity();
        for (int step = 0; step < 100; step++) {
            if (!(current instanceof ContextWrapper)) return;
            current = ((ContextWrapper) current).getBaseContext();
            if (current == null) return;
        }
        fail("the Context chain never terminated");
    }

    @Test
    void aComponentThatIsAlsoTheProcessContextDoesNotPointAtItself() {
        // The degenerate case: install an Application as the process Context.
        // Its base has to be null rather than itself, or the walk spins.
        Application app = new Application();
        Context.installApplicationContext(app);
        try {
            assertNull(app.getBaseContext());
        } finally {
            Context.installApplicationContext(process);
        }
    }

    @Test
    void aWrapperForwardsToItsBase() {
        ContextWrapper wrapper = new ContextWrapper(process);
        assertSame(process.getPackageName(), wrapper.getPackageName());
        assertSame(process, wrapper.getBaseContext());
    }

    @Test
    void anActivityHasARealWindow() {
        // Not null: the app sets flags on it that matter (FLAG_SECURE over the
        // key backup), and a null Window would make every one of those calls
        // disappear behind a safe-call operator.
        Window window = new Activity().getWindow();
        assertNotNull(window);
        assertNotNull(window.getAttributes());
    }

    /** Minimal process Context: deliberately NOT a ContextWrapper. */
    private static final class ChainTestContext extends Context {
        @Override public String getPackageName() { return "com.vitorpamplona.amethyst"; }

        @Override public android.content.res.Resources getResources() { return null; }

        @Override public String getString(int resId) { return ""; }

        @Override public String getString(int resId, Object... formatArgs) { return ""; }

        @Override public java.io.File getCacheDir() { return null; }

        @Override public java.io.File getFilesDir() { return null; }

        @Override public java.io.File getExternalCacheDir() { return null; }

        @Override public java.io.File getExternalFilesDir(String type) { return null; }

        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return null; }

        @Override public ContentResolver getContentResolver() { return null; }
    }
}
