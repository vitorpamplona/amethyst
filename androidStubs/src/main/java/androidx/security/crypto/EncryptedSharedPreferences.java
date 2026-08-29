package androidx.security.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;

/**
 * JVM stand-in for androidx.security.crypto.EncryptedSharedPreferences.
 *
 * This holds secrets — nsec keys, bunker tokens, NWC connections — so it is the
 * one place where returning something that merely works would be dangerous.
 * Desktop has real encrypted storage (the OS keychain via jkeychain, which
 * :desktopApp already uses for exactly this), so the storage must come from
 * there through a {@link Backend} rather than falling back to plaintext
 * java.util.prefs.
 *
 * With no backend installed, create() throws. That is deliberate: a caller
 * that silently received unencrypted preferences would write private keys to
 * disk in the clear, and no error would say so.
 */
public final class EncryptedSharedPreferences {
    public enum PrefKeyEncryptionScheme { AES256_SIV }

    public enum PrefValueEncryptionScheme { AES256_GCM }

    /** Supplied by the desktop app, backed by the OS keychain. */
    public interface Backend {
        SharedPreferences open(String fileName);
    }

    private static volatile Backend backend;

    public static void setBackend(Backend value) { backend = value; }

    private EncryptedSharedPreferences() {}

    public static SharedPreferences create(
            Context context,
            String fileName,
            MasterKey masterKey,
            PrefKeyEncryptionScheme keyScheme,
            PrefValueEncryptionScheme valueScheme) {
        Backend installed = backend;
        if (installed == null) {
            PlatformGaps.report(
                    "EncryptedSharedPreferences",
                    "no encrypted storage backend installed; desktop must supply one from the OS keychain");
            throw new IllegalStateException(
                    "Refusing to open '" + fileName + "' unencrypted. EncryptedSharedPreferences holds private "
                            + "keys and tokens; install a keychain-backed Backend before using it.");
        }
        return installed.open(fileName);
    }
}
