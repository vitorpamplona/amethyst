package android.service.quicksettings;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * JVM stand-in for android.service.quicksettings.TileService.
 *
 * A quick-settings tile is a control the user drags into the system's pull-down
 * panel. Desktop environments have no such panel that an application can add
 * to; the nearest thing is a tray icon or a menu-bar item, which is a different
 * surface with a different lifecycle — not a port of this one. So the type
 * exists (the app's tile subclasses it) and the framework never calls it.
 *
 * The switch it toggles is not lost: it is the same "keep notifications
 * running" preference the settings screen owns, so the desktop reaches it
 * there. Nothing here should be made to look like a working tile.
 */
public class TileService extends Service {
    private final Tile tile = new Tile();

    /** The tile the framework would hand out. Never displayed here. */
    public final Tile getQsTile() { return tile; }

    public void onTileAdded() {}

    public void onTileRemoved() {}

    public void onStartListening() {
        com.vitorpamplona.amethyst.stubs.PlatformGaps.unavailable(
                "TileService",
                "there is no quick-settings panel on the desktop for an app to add a tile to; the "
                        + "same switch lives in the app's notification settings.");
    }

    public void onStopListening() {}

    public void onClick() {}

    @Override public IBinder onBind(Intent intent) { return null; }
}
