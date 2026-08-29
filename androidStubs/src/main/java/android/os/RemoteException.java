package android.os;

/** JVM stand-in for android.os.RemoteException — thrown across a Binder boundary that desktop does not have. */
public class RemoteException extends Exception {
    public RemoteException() {}

    public RemoteException(String message) { super(message); }
}
