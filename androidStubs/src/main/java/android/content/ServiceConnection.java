package android.content;

import android.os.IBinder;

/** JVM stand-in for android.content.ServiceConnection — desktop has no service binding. */
public interface ServiceConnection {
    void onServiceConnected(ComponentName name, IBinder service);

    void onServiceDisconnected(ComponentName name);
}
