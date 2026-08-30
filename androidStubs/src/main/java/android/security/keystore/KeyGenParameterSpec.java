package android.security.keystore;

import java.security.spec.AlgorithmParameterSpec;

/**
 * JVM stand-in for android.security.keystore.KeyGenParameterSpec.
 *
 * A parameter carrier, faithfully. What it cannot carry over is the thing that
 * makes it worth using: on Android these parameters go to the "AndroidKeyStore"
 * provider, which keeps the key inside the TEE or a StrongBox chip so the app
 * never holds the key material. The JVM has no such provider, and building one
 * out of a file is NOT the same guarantee.
 *
 * So this declares the gap rather than pretending. The caller
 * ({@code KeyStoreEncryption}) then fails loudly at
 * {@code KeyStore.getInstance("AndroidKeyStore")} — the JDK has no such
 * keystore type — instead of quietly encrypting the account's private keys with
 * a key sitting next to them on disk. A desktop implementation has to go
 * through the OS keychain (macOS Keychain, Windows DPAPI, libsecret) to be
 * worth anything.
 */
public final class KeyGenParameterSpec implements AlgorithmParameterSpec {
    private final String keystoreAlias;
    private final int purposes;
    private final String[] blockModes;
    private final String[] encryptionPaddings;
    private final boolean strongBoxBacked;
    private final boolean userAuthenticationRequired;

    private KeyGenParameterSpec(Builder builder) {
        this.keystoreAlias = builder.keystoreAlias;
        this.purposes = builder.purposes;
        this.blockModes = builder.blockModes;
        this.encryptionPaddings = builder.encryptionPaddings;
        this.strongBoxBacked = builder.strongBoxBacked;
        this.userAuthenticationRequired = builder.userAuthenticationRequired;
    }

    public String getKeystoreAlias() { return keystoreAlias; }

    public int getPurposes() { return purposes; }

    public String[] getBlockModes() { return blockModes == null ? new String[0] : blockModes.clone(); }

    public String[] getEncryptionPaddings() {
        return encryptionPaddings == null ? new String[0] : encryptionPaddings.clone();
    }

    public boolean isStrongBoxBacked() { return strongBoxBacked; }

    public boolean isUserAuthenticationRequired() { return userAuthenticationRequired; }

    public static final class Builder {
        private final String keystoreAlias;
        private final int purposes;
        private String[] blockModes;
        private String[] encryptionPaddings;
        private boolean strongBoxBacked;
        private boolean userAuthenticationRequired;

        public Builder(String keystoreAlias, int purposes) {
            this.keystoreAlias = keystoreAlias;
            this.purposes = purposes;
            com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                    "AndroidKeyStore",
                    "the desktop has no hardware-backed key store; account keys must be protected "
                            + "through the OS keychain (macOS Keychain, Windows DPAPI, libsecret) before "
                            + "this path can work. Until then key generation fails loudly rather than "
                            + "encrypting with a key kept beside the data.");
        }

        public Builder setBlockModes(String... modes) {
            this.blockModes = modes;
            return this;
        }

        public Builder setEncryptionPaddings(String... paddings) {
            this.encryptionPaddings = paddings;
            return this;
        }

        public Builder setIsStrongBoxBacked(boolean value) {
            this.strongBoxBacked = value;
            return this;
        }

        public Builder setUserAuthenticationRequired(boolean value) {
            this.userAuthenticationRequired = value;
            return this;
        }

        public Builder setRandomizedEncryptionRequired(boolean value) { return this; }

        public Builder setKeySize(int size) { return this; }

        public Builder setDigests(String... digests) { return this; }

        public KeyGenParameterSpec build() { return new KeyGenParameterSpec(this); }
    }
}
