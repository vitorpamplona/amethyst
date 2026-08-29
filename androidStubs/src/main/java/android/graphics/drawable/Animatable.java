package android.graphics.drawable;

/**
 * JVM stand-in for android.graphics.drawable.Animatable.
 *
 * The interface an animating drawable implements. Every use here is the same
 * shape — {@code if (drawable is Animatable) drawable.start()} — so what
 * matters is that a still image is correctly *not* one, which it is: nothing on
 * this platform implements this yet, so the check falls through exactly as it
 * does on Android for a non-animated result.
 */
public interface Animatable {
    void start();

    void stop();

    boolean isRunning();
}
