package android.graphics;

/** JVM stand-in for android.graphics.Color. Pure arithmetic over packed ARGB. */
public final class Color {
    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int TRANSPARENT = 0;
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;

    private Color() {}

    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int rgb(int red, int green, int blue) { return argb(255, red, green, blue); }

    public static int alpha(int color) { return (color >>> 24) & 0xFF; }

    public static int red(int color) { return (color >> 16) & 0xFF; }

    public static int green(int color) { return (color >> 8) & 0xFF; }

    public static int blue(int color) { return color & 0xFF; }

    public static int parseColor(String colorString) {
        if (colorString == null || !colorString.startsWith("#")) {
            throw new IllegalArgumentException("Unknown color: " + colorString);
        }
        String hex = colorString.substring(1);
        if (hex.length() == 6) return (int) (0xFF000000L | Long.parseLong(hex, 16));
        if (hex.length() == 8) return (int) Long.parseLong(hex, 16);
        throw new IllegalArgumentException("Unknown color: " + colorString);
    }
}
