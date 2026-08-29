package android.graphics;

/**
 * JVM stand-in for android.graphics.Paint.
 *
 * Carries its settings for real — they are plain values — but nothing here
 * draws: the one consumer left on desktop passes a Paint to a map marker, and
 * a desktop map renderer styles with its own API. Compose owns all other
 * drawing.
 */
public class Paint {
    public enum Style { FILL, STROKE, FILL_AND_STROKE }

    public enum Align { LEFT, CENTER, RIGHT }

    public static final int ANTI_ALIAS_FLAG = 1;

    private int color = 0xFF000000;
    private float strokeWidth = 0f;
    private float textSize = 12f;
    private Style style = Style.FILL;
    private Align textAlign = Align.LEFT;
    private boolean antiAlias;

    public Paint() {}

    public Paint(int flags) { antiAlias = (flags & ANTI_ALIAS_FLAG) != 0; }

    public int getColor() { return color; }

    public void setColor(int value) { color = value; }

    public float getStrokeWidth() { return strokeWidth; }

    public void setStrokeWidth(float value) { strokeWidth = value; }

    public float getTextSize() { return textSize; }

    public void setTextSize(float value) { textSize = value; }

    public Style getStyle() { return style; }

    public void setStyle(Style value) { style = value; }

    public Align getTextAlign() { return textAlign; }

    public void setTextAlign(Align value) { textAlign = value; }

    public boolean isAntiAlias() { return antiAlias; }

    public void setAntiAlias(boolean value) { antiAlias = value; }

    /** Rough advance width; exact metrics need a real font context. */
    public float measureText(String text) { return text == null ? 0f : text.length() * textSize * 0.5f; }
}
