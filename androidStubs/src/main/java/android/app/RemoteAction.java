package android.app;

import android.graphics.drawable.Icon;

/**
 * JVM stand-in for android.app.RemoteAction: a button another process can draw
 * and fire on your behalf.
 *
 * Pure data, so it is carried faithfully. Where it ends up on Android is a
 * picture-in-picture window's control row, which is
 * {@code PictureInPicture} — declared as having no desktop counterpart.
 */
public final class RemoteAction {
    private final Icon icon;
    private final CharSequence title;
    private final CharSequence contentDescription;
    private final PendingIntent actionIntent;
    private boolean enabled = true;
    private boolean shouldShowIcon = true;

    public RemoteAction(
            Icon icon, CharSequence title, CharSequence contentDescription, PendingIntent actionIntent) {
        this.icon = icon;
        this.title = title;
        this.contentDescription = contentDescription;
        this.actionIntent = actionIntent;
    }

    public Icon getIcon() { return icon; }

    public CharSequence getTitle() { return title; }

    public CharSequence getContentDescription() { return contentDescription; }

    public PendingIntent getActionIntent() { return actionIntent; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean value) { this.enabled = value; }

    public boolean shouldShowIcon() { return shouldShowIcon; }

    public void setShouldShowIcon(boolean value) { this.shouldShowIcon = value; }
}
