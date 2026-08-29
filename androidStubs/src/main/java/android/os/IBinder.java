package android.os;

/** JVM stand-in for android.os.IBinder — an opaque cross-process handle with no desktop analogue. */
public interface IBinder {
    String getInterfaceDescriptor();

    boolean isBinderAlive();
}
