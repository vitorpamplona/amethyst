package androidx.appcompat.app;

import android.app.Activity;

/** JVM stand-in for androidx.appcompat.app.AppCompatActivity. See android.app.Activity. */
public class AppCompatActivity extends Activity {
    public Object getSupportActionBar() { return null; }

    public Object getSupportFragmentManager() { return null; }
}
