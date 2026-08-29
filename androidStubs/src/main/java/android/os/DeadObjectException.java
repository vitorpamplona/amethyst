package android.os;

/** JVM stand-in for android.os.DeadObjectException. */
public class DeadObjectException extends RemoteException {
    public DeadObjectException() {}

    public DeadObjectException(String message) { super(message); }
}
