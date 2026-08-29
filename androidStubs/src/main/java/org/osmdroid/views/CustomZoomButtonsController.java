package org.osmdroid.views;

/** JVM stand-in for osmdroid's zoom-button controller. */
public class CustomZoomButtonsController {
    public enum Visibility { ALWAYS, SHOW_AND_FADEOUT, NEVER }

    public void setVisibility(Visibility visibility) {}

    public void setZoomInEnabled(boolean enabled) {}

    public void setZoomOutEnabled(boolean enabled) {}
}
