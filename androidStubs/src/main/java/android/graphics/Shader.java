package android.graphics;

/**
 * JVM stand-in for android.graphics.Shader — what a Paint fills with instead of
 * a flat colour.
 *
 * AWT has the same idea in {@code java.awt.Paint}, so this carries enough for
 * {@link Canvas} to build one: a shader is only useful if something draws with
 * it, and the one use here — a bitmap shader that rounds an avatar into a
 * circle — is a real drawing operation, not a decoration.
 */
public abstract class Shader {
    public enum TileMode { CLAMP, REPEAT, MIRROR }

    /** The AWT paint this shader stands for, sized to what is being filled. */
    abstract java.awt.Paint toAwtPaint(int width, int height);
}
