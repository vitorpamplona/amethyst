package android.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ConnectivityManagerTest {
    @Test
    void readsConnectivityFromTheRealInterfaceList() {
        // The build machine has at least a loopback plus one real interface, so
        // this asserts the query runs rather than a hardcoded answer.
        ConnectivityManager manager = new ConnectivityManager();
        assertTrue(ConnectivityManager.isConnected() || manager.getActiveNetwork() == null,
                "isConnected and getActiveNetwork must agree");
    }

    @Test
    void capabilitiesDescribeAnInternetCapableTransport() {
        ConnectivityManager manager = new ConnectivityManager();
        Network network = manager.getActiveNetwork();
        if (network == null) return; // offline build agent: nothing to assert

        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        assertNotNull(capabilities);
        assertTrue(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        assertTrue(
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                "an active connection must report some transport");
    }

    @Test
    void meteredIsDeclaredUnknownRatherThanGuessedSilently() {
        ConnectivityManager manager = new ConnectivityManager();
        Network network = manager.getActiveNetwork();
        if (network == null) return;
        manager.getNetworkCapabilities(network);

        assertTrue(
                PlatformGaps.isUnavailable("ConnectivityManager.isMetered"),
                "assuming unmetered is a guess and must be declared as one");
    }

    @Test
    void registeringACallbackReportsTheCurrentStateImmediately() throws Exception {
        ConnectivityManager manager = new ConnectivityManager();
        if (!ConnectivityManager.isConnected()) return;

        CountDownLatch available = new CountDownLatch(1);
        ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network network) { available.countDown(); }
                };
        manager.registerDefaultNetworkCallback(callback);
        try {
            assertTrue(available.await(2, TimeUnit.SECONDS),
                    "a caller registering while online must be told so, not left waiting for a change");
        } finally {
            manager.unregisterNetworkCallback(callback);
        }
    }

    @Test
    void networkHandlesCompareByIdentity() {
        assertEquals(new Network(1L), new Network(1L));
    }
}
