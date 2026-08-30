package android.service.quicksettings;

/**
 * JVM stand-in for android.service.quicksettings.Tile.
 *
 * See {@link TileService}: there is no quick-settings panel to hold this, so a
 * tile built here is never shown.
 */
public final class Tile {
    public static final int STATE_UNAVAILABLE = 0;
    public static final int STATE_INACTIVE = 1;
    public static final int STATE_ACTIVE = 2;

    private int state = STATE_UNAVAILABLE;
    private CharSequence label;
    private CharSequence subtitle;
    private CharSequence contentDescription;

    public int getState() { return state; }

    public void setState(int value) { state = value; }

    public CharSequence getLabel() { return label; }

    public void setLabel(CharSequence value) { label = value; }

    public CharSequence getSubtitle() { return subtitle; }

    public void setSubtitle(CharSequence value) { subtitle = value; }

    public CharSequence getContentDescription() { return contentDescription; }

    public void setContentDescription(CharSequence value) { contentDescription = value; }

    public void updateTile() {}
}
