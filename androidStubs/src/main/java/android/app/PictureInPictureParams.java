package android.app;

import android.graphics.Rect;
import android.util.Rational;
import java.util.ArrayList;
import java.util.List;

/**
 * JVM stand-in for android.app.PictureInPictureParams.
 *
 * Carries what the caller configured — aspect ratio, source rect, actions —
 * rather than discarding it, because the desktop analogue (an always-on-top
 * window) would want the same values if it is ever built. Entering PiP is what
 * has no counterpart, and that is declared on {@link Activity}, not here.
 */
public final class PictureInPictureParams {
    private final Rational aspectRatio;
    private final Rational expandedAspectRatio;
    private final Rect sourceRectHint;
    private final List<RemoteAction> actions;
    private final boolean autoEnterEnabled;
    private final boolean seamlessResizeEnabled;
    private final CharSequence title;
    private final CharSequence subtitle;

    private PictureInPictureParams(Builder builder) {
        this.aspectRatio = builder.aspectRatio;
        this.expandedAspectRatio = builder.expandedAspectRatio;
        this.sourceRectHint = builder.sourceRectHint;
        this.actions = List.copyOf(builder.actions);
        this.autoEnterEnabled = builder.autoEnterEnabled;
        this.seamlessResizeEnabled = builder.seamlessResizeEnabled;
        this.title = builder.title;
        this.subtitle = builder.subtitle;
    }

    public Rational getAspectRatio() { return aspectRatio; }

    public Rational getExpandedAspectRatio() { return expandedAspectRatio; }

    public Rect getSourceRectHint() { return sourceRectHint; }

    public List<RemoteAction> getActions() { return actions; }

    public boolean isAutoEnterEnabled() { return autoEnterEnabled; }

    public boolean isSeamlessResizeEnabled() { return seamlessResizeEnabled; }

    public CharSequence getTitle() { return title; }

    public CharSequence getSubtitle() { return subtitle; }

    public static final class Builder {
        private Rational aspectRatio;
        private Rational expandedAspectRatio;
        private Rect sourceRectHint;
        private final List<RemoteAction> actions = new ArrayList<>();
        private boolean autoEnterEnabled;
        private boolean seamlessResizeEnabled = true;
        private CharSequence title;
        private CharSequence subtitle;

        public Builder setAspectRatio(Rational value) {
            this.aspectRatio = value;
            return this;
        }

        public Builder setExpandedAspectRatio(Rational value) {
            this.expandedAspectRatio = value;
            return this;
        }

        public Builder setSourceRectHint(Rect value) {
            this.sourceRectHint = value;
            return this;
        }

        public Builder setActions(List<RemoteAction> value) {
            this.actions.clear();
            if (value != null) this.actions.addAll(value);
            return this;
        }

        public Builder setAutoEnterEnabled(boolean value) {
            this.autoEnterEnabled = value;
            return this;
        }

        public Builder setSeamlessResizeEnabled(boolean value) {
            this.seamlessResizeEnabled = value;
            return this;
        }

        public Builder setTitle(CharSequence value) {
            this.title = value;
            return this;
        }

        public Builder setSubtitle(CharSequence value) {
            this.subtitle = value;
            return this;
        }

        public PictureInPictureParams build() { return new PictureInPictureParams(this); }
    }
}
