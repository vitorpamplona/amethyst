package android.graphics;

/**
 * JVM stand-in for android.graphics.ColorMatrixColorFilter.
 *
 * Carries the matrix so a desktop tile renderer can apply it; the app builds
 * two of these for the map's day and night looks and hands them to the tile
 * overlay.
 */
public class ColorMatrixColorFilter extends ColorFilter {
    private final ColorMatrix matrix;

    public ColorMatrixColorFilter(ColorMatrix matrix) { this.matrix = new ColorMatrix(matrix); }

    public ColorMatrixColorFilter(float[] array) { this.matrix = new ColorMatrix(array); }

    public ColorMatrix getColorMatrix() { return matrix; }
}
