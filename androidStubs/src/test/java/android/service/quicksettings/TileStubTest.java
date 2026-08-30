package android.service.quicksettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * The tile is never shown here, but the app's own refresh logic runs against
 * it, so the object has to behave like a value holder rather than swallow
 * writes.
 */
class TileStubTest {
    @Test
    void aTileStartsUnavailableAndKeepsWhatIsWrittenToIt() {
        Tile tile = new Tile();
        assertEquals(Tile.STATE_UNAVAILABLE, tile.getState());

        tile.setState(Tile.STATE_ACTIVE);
        tile.setSubtitle("Notifications on");
        tile.updateTile();

        assertEquals(Tile.STATE_ACTIVE, tile.getState());
        assertEquals("Notifications on", tile.getSubtitle());
    }

    @Test
    void theServiceHandsOutOneTile() {
        TileService service = new TileService();
        assertNotNull(service.getQsTile());
        assertSame(service.getQsTile(), service.getQsTile());
    }

    @Test
    void theLifecycleCallbacksAreSafeToCall() {
        TileService service = new TileService();
        service.onTileAdded();
        service.onStartListening();
        service.onClick();
        service.onStopListening();
        service.onTileRemoved();
        assertEquals(Tile.STATE_UNAVAILABLE, service.getQsTile().getState());
    }
}
