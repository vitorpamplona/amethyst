package android.content;

/**
 * JVM stand-in for android.content.ActivityNotFoundException.
 *
 * Thrown for the same reason it is on Android — nothing on this system can
 * carry out the intent — so the catch blocks that already handle it (falling
 * back to a copied link, or telling the user no camera app is installed) work
 * unchanged.
 */
public class ActivityNotFoundException extends RuntimeException {
    public ActivityNotFoundException() { super(); }

    public ActivityNotFoundException(String message) { super(message); }

    public ActivityNotFoundException(String message, Throwable cause) { super(message, cause); }
}
