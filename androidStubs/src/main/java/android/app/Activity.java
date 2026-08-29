package android.app;

import android.content.Context;
import android.content.DelegatingContext;
import android.content.Intent;
import android.os.Bundle;

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

    public void attachBaseContext(Context base) {}

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

    /** No window system behind this stub; the desktop shell owns the window. */
    public Object getWindow() { return null; }
}
