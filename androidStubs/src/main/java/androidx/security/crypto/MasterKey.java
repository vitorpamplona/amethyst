package androidx.security.crypto;

import android.content.Context;

/**
 * JVM stand-in for androidx.security.crypto.MasterKey.
 *
 * On Android this names a key held in hardware-backed Keystore. Desktop has an
 * equivalent — the OS keychain, which :desktopApp already reaches through
 * jkeychain — but it is addressed by service and account rather than by a
 * master-key alias, so this carries the alias for the storage layer to map.
 * No key material passes through here.
 */
public final class MasterKey {
    public enum KeyScheme { AES256_GCM }

    public static final String DEFAULT_MASTER_KEY_ALIAS = "_androidx_security_master_key_";

    private final String alias;

    private MasterKey(String alias) { this.alias = alias; }

    public String getKeyAlias() { return alias; }

    public static final class Builder {
        private String alias = DEFAULT_MASTER_KEY_ALIAS;

        public Builder(Context context) {}

        public Builder(Context context, String alias) { this.alias = alias; }

        public Builder setKeyScheme(KeyScheme scheme) { return this; }

        public Builder setUserAuthenticationRequired(boolean required) { return this; }

        public MasterKey build() { return new MasterKey(alias); }
    }
}
