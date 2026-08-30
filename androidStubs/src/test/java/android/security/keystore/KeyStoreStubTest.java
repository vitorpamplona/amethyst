package android.security.keystore;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyStore;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;

/**
 * The point of these stubs is that key generation CANNOT quietly succeed: the
 * app encrypts its accounts' private keys with whatever comes back, and a key
 * kept in a file beside the ciphertext protects nothing while looking exactly
 * like a key held in a TEE.
 */
class KeyStoreStubTest {
    @Test
    void thereIsNoAndroidKeyStoreProviderOnTheJvm() {
        // This is the loud failure KeyStoreEncryption hits at construction.
        // If some JVM provider ever registered under this name, the stubs would
        // be handing the app a false hardware guarantee.
        assertThrows(java.security.KeyStoreException.class, () -> KeyStore.getInstance("AndroidKeyStore"));
    }

    @Test
    void theConstantsNameTheSameCipherTheJcaDoes() throws Exception {
        String transformation =
                KeyProperties.KEY_ALGORITHM_AES
                        + "/"
                        + KeyProperties.BLOCK_MODE_GCM
                        + "/"
                        + KeyProperties.ENCRYPTION_PADDING_NONE;
        assertEquals("AES/GCM/NoPadding", transformation);
        // Resolves against the real JCA, so the app's transformation string is
        // not merely well-formed but actually the cipher it means.
        assertNotNull(Cipher.getInstance(transformation));
    }

    @Test
    void purposesAreABitmask() {
        assertEquals(3, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT);
    }

    @Test
    void theSpecCarriesEveryParameterItWasGiven() {
        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(
                                "AMETHYST_AES_KEY",
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setIsStrongBoxBacked(true)
                        .build();

        assertEquals("AMETHYST_AES_KEY", spec.getKeystoreAlias());
        assertEquals(3, spec.getPurposes());
        assertArrayEquals(new String[] {"GCM"}, spec.getBlockModes());
        assertArrayEquals(new String[] {"NoPadding"}, spec.getEncryptionPaddings());
        assertTrue(spec.isStrongBoxBacked());
    }

    @Test
    void strongBoxUnavailableIsAProviderExceptionSoTheFallbackCatchesIt() {
        // KeyStoreEncryption catches this specific type to fall back to a
        // regular key; if it were not a ProviderException the JCA could never
        // throw it and that branch would be dead.
        assertTrue(new StrongBoxUnavailableException() instanceof java.security.ProviderException);
    }
}
