package androidx.security.crypto;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.content.SharedPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * This class stores nsec keys, bunker tokens and NWC connections. The failure
 * mode that matters is not "it broke" — it is "it silently worked, in plaintext".
 */
class EncryptedSharedPreferencesTest {
    @AfterEach
    void reset() {
        EncryptedSharedPreferences.setBackend(null);
    }

    @Test
    void refusesToOpenWithoutAnEncryptedBackend() {
        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                EncryptedSharedPreferences.create(
                                        null,
                                        "secrets",
                                        new MasterKey.Builder(null).build(),
                                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM));

        // The message has to say why, because the tempting "fix" is to hand back
        // plain preferences and move on.
        assertTrue(thrown.getMessage().contains("unencrypted"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("secrets"), "name the file that was refused");
    }

    @Test
    void usesTheInstalledBackendWhenThereIsOne() {
        SharedPreferences fake = (SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[] {SharedPreferences.class},
                (proxy, method, args) -> null);
        EncryptedSharedPreferences.setBackend(fileName -> fake);

        assertNotNull(
                EncryptedSharedPreferences.create(
                        null,
                        "secrets",
                        new MasterKey.Builder(null).build(),
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM));
    }
}
