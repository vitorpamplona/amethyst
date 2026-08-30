package android.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Notification buttons and picture-in-picture controls fire a PendingIntent
 * that a receiver in this same process handles — Android delivers those
 * in-process too, so this is the same behaviour, not an approximation. A bus
 * that dropped them would break every notification action with nothing in the
 * log.
 */
class BroadcastBusTest {
    private Context context;

    @BeforeEach
    void freshContext() {
        context = new BusTestContext();
        Context.installApplicationContext(context);
    }

    private static final class Recorder extends BroadcastReceiver {
        final List<String> received = new ArrayList<>();

        @Override public void onReceive(Context context, Intent intent) { received.add(intent.getAction()); }
    }

    @Test
    void aReceiverGetsTheActionsItsFilterNames() {
        Recorder recorder = new Recorder();
        IntentFilter filter = new IntentFilter("com.example.MUTE");
        filter.addAction("com.example.PLAY");
        context.registerReceiver(recorder, filter, Context.RECEIVER_NOT_EXPORTED);

        context.sendBroadcast(new Intent("com.example.PLAY"));
        context.sendBroadcast(new Intent("com.example.MUTE"));

        assertEquals(List.of("com.example.PLAY", "com.example.MUTE"), recorder.received);
        context.unregisterReceiver(recorder);
    }

    @Test
    void anActionNoFilterNamesReachesNobody() {
        Recorder recorder = new Recorder();
        context.registerReceiver(recorder, new IntentFilter("com.example.MUTE"), Context.RECEIVER_NOT_EXPORTED);

        context.sendBroadcast(new Intent("com.example.SOMETHING_ELSE"));

        assertTrue(recorder.received.isEmpty());
        context.unregisterReceiver(recorder);
    }

    @Test
    void unregisteringStopsDelivery() {
        Recorder recorder = new Recorder();
        context.registerReceiver(recorder, new IntentFilter("com.example.MUTE"), Context.RECEIVER_NOT_EXPORTED);
        context.unregisterReceiver(recorder);

        context.sendBroadcast(new Intent("com.example.MUTE"));

        assertTrue(recorder.received.isEmpty());
    }

    @Test
    void aReceiverMayUnregisterItselfWhileHandling() {
        // PokeyReceiver and the PiP controls both do exactly this.
        List<String> seen = new ArrayList<>();
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        seen.add(intent.getAction());
                        context.unregisterReceiver(this);
                    }
                };
        context.registerReceiver(receiver, new IntentFilter("com.example.ONCE"), Context.RECEIVER_NOT_EXPORTED);

        context.sendBroadcast(new Intent("com.example.ONCE"));
        context.sendBroadcast(new Intent("com.example.ONCE"));

        assertEquals(1, seen.size());
    }

    @Test
    void everyRegisteredReceiverGetsTheSameBroadcast() {
        Recorder first = new Recorder();
        Recorder second = new Recorder();
        context.registerReceiver(first, new IntentFilter("com.example.PLAY"), Context.RECEIVER_NOT_EXPORTED);
        context.registerReceiver(second, new IntentFilter("com.example.PLAY"), Context.RECEIVER_NOT_EXPORTED);

        context.sendBroadcast(new Intent("com.example.PLAY"));

        assertEquals(1, first.received.size());
        assertEquals(1, second.received.size());
        context.unregisterReceiver(first);
        context.unregisterReceiver(second);
    }

    @Test
    void anIntentWithNoActionIsHarmless() {
        Recorder recorder = new Recorder();
        context.registerReceiver(recorder, new IntentFilter("com.example.PLAY"), Context.RECEIVER_NOT_EXPORTED);
        context.sendBroadcast(new Intent());
        assertTrue(recorder.received.isEmpty());
        context.unregisterReceiver(recorder);
    }

    private static final class BusTestContext extends Context {
        @Override public String getPackageName() { return "com.vitorpamplona.amethyst"; }

        @Override public android.content.res.Resources getResources() { return null; }

        @Override public String getString(int resId) { return ""; }

        @Override public String getString(int resId, Object... formatArgs) { return ""; }

        @Override public java.io.File getCacheDir() { return null; }

        @Override public java.io.File getFilesDir() { return null; }

        @Override public java.io.File getExternalCacheDir() { return null; }

        @Override public java.io.File getExternalFilesDir(String type) { return null; }

        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return null; }

        @Override public ContentResolver getContentResolver() { return null; }
    }
}
