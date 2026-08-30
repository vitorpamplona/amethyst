package android.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Window flags are requests the desktop shell still has to honour, so the stub
 * has to at least record them faithfully — code reads them back
 * (FullscreenSwipeControls restores the previous brightness from
 * getAttributes()), and a setFlags that ignored its mask would clear flags the
 * caller meant to keep.
 */
class WindowFlagsTest {
    @Test
    void addFlagsAccumulatesAndClearFlagsRemovesOnlyWhatItNames() {
        Window window = new Window();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        int flags = window.getAttributes().flags;
        assertTrue((flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        flags = window.getAttributes().flags;
        assertEquals(0, flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
    }

    @Test
    void setFlagsOnlyTouchesTheBitsInItsMask() {
        Window window = new Window();
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        // AccountBackupScreen's exact call shape.
        window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        int flags = window.getAttributes().flags;
        assertTrue((flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
        assertTrue(
                (flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0,
                "setFlags cleared a flag outside its mask");

        window.setFlags(0, WindowManager.LayoutParams.FLAG_SECURE);
        assertEquals(0, window.getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Test
    void brightnessSurvivesARoundTripThroughAttributes() {
        // FullscreenSwipeControls reads the params, edits, writes back, and
        // later restores BRIGHTNESS_OVERRIDE_NONE. Each step has to stick.
        Window window = new Window();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(window.getAttributes());
        params.screenBrightness = 0.4f;
        window.setAttributes(params);
        assertEquals(0.4f, window.getAttributes().screenBrightness);

        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        window.setAttributes(params);
        assertEquals(
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
                window.getAttributes().screenBrightness);
    }

    @Test
    void copyFromTakesEveryFieldAndLeavesTheSourceAlone() {
        WindowManager.LayoutParams source = new WindowManager.LayoutParams(320, 240);
        source.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        source.type = 99;
        source.dimAmount = 0.75f;

        WindowManager.LayoutParams copy = new WindowManager.LayoutParams();
        copy.copyFrom(source);

        assertEquals(320, copy.width);
        assertEquals(240, copy.height);
        assertEquals(WindowManager.LayoutParams.FLAG_DIM_BEHIND, copy.flags);
        assertEquals(99, copy.type);
        assertEquals(0.75f, copy.dimAmount);

        copy.flags = 0;
        assertNotEquals(0, source.flags);
    }

    @Test
    void aDetachedViewHasNoParent() {
        // Which is what tells the app it is not inside a dialog. A non-null
        // parent that was not a DialogWindowProvider would answer the same, but
        // this is the state the desktop is actually in.
        assertTrue(new View(null).getParent() == null);
    }
}
