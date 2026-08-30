package android.security.keystore;

/**
 * JVM stand-in for android.security.keystore.StrongBoxUnavailableException.
 *
 * Thrown when a device has no StrongBox (a discrete security chip). Desktop
 * machines do not either, so the caller's own "fall back to a regular key"
 * branch is the one that would run — if a key could be created here at all;
 * see {@link KeyGenParameterSpec}.
 */
public class StrongBoxUnavailableException extends java.security.ProviderException {
    public StrongBoxUnavailableException() { super("no StrongBox on this platform"); }

    public StrongBoxUnavailableException(String message) { super(message); }
}
