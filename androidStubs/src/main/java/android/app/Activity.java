package android.app;

import android.content.Context;
import android.content.DelegatingContext;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Window;

/**
 * JVM stand-in for android.app.Activity.
 *
 * There is no Activity on desktop. This exists so that code passing an Activity
 * around as a Context — which is most of what the app does with it — compiles.
 * The lifecycle methods are no-ops here exactly as they are on Android.
 */
public class Activity extends DelegatingContext {
    public static final int RESULT_OK = -1;
    public static final int RESULT_CANCELED = 0;

    /**
     * Picture-in-picture is declared as having no desktop counterpart: Android
     * docks the Activity into a system overlay, and the desktop analogue would
     * be an always-on-top window — a different feature with different UX, not a
     * port of this one. So entering it is refused rather than faked, and the
     * caller's own "PiP unavailable" path is the one that runs.
     */
    public boolean isInPictureInPictureMode() { return false; }

    public boolean enterPictureInPictureMode(PictureInPictureParams params) {
        reportNoPictureInPicture();
        return false;
    }

    public void enterPictureInPictureMode() { reportNoPictureInPicture(); }

    public void setPictureInPictureParams(PictureInPictureParams params) {}

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {}

    public void finishAndRemoveTask() {}

    public void setShowWhenLocked(boolean value) {}

    public void setTurnScreenOn(boolean value) {}

    private static void reportNoPictureInPicture() {
        com.vitorpamplona.amethyst.stubs.PlatformGaps.unavailable(
                "PictureInPicture",
                "Android PiP docks an Activity into a system overlay. A desktop equivalent would be "
                        + "an always-on-top window, which is a different feature with different UX, "
                        + "not a port of this one.");
    }

    public void attachBaseContext(Context base) {}

    protected void onCreate(Bundle savedInstanceState) {}

    protected void onStart() {}

    protected void onResume() {}

    protected void onPause() {}

    protected void onStop() {}

    protected void onDestroy() {}

    /**
     * Called when the user leaves the activity by their own action rather than
     * by another one appearing over it — the cue Android gives for entering
     * picture-in-picture. Nothing drives it here; PiP is already declared as
     * having no desktop counterpart.
     */
    protected void onUserLeaveHint() {}

    /**
     * The other half of the new-intent path. On Android the framework calls
     * this; here the desktop shell delivers through
     * `ComponentActivity.dispatchNewIntent`, which is what feeds the listeners
     * the app actually registers.
     */
    protected void onNewIntent(Intent intent) {}

    public void finish() {}

    public boolean isFinishing() { return false; }

    public Intent getIntent() { return new Intent(); }

    public void setIntent(Intent intent) {}

    /**
     * A real {@link Window} whose flags are recorded, because the app sets them
     * for reasons that matter (FLAG_SECURE over the key backup, keep-screen-on
     * during video). Returning null here would make every one of those calls
     * silently vanish; Window itself is what reports the ones the desktop shell
     * still has to honour.
     */
    public Window getWindow() { return window; }

    private final Window window = new Window(this);
}
