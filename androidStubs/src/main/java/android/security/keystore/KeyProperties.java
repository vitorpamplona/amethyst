package android.security.keystore;

/**
 * JVM stand-in for android.security.keystore.KeyProperties.
 *
 * Pure constants, and the same strings the JCA uses — "AES", "GCM",
 * "NoPadding" — so a transformation assembled from them names the same cipher
 * on either platform. Nothing here decides where a key is kept; see
 * {@link KeyGenParameterSpec} for that.
 */
public final class KeyProperties {
    private KeyProperties() {}

    public static final String KEY_ALGORITHM_AES = "AES";
    public static final String KEY_ALGORITHM_HMAC_SHA256 = "HmacSHA256";
    public static final String KEY_ALGORITHM_RSA = "RSA";
    public static final String KEY_ALGORITHM_EC = "EC";

    public static final String BLOCK_MODE_GCM = "GCM";
    public static final String BLOCK_MODE_CBC = "CBC";
    public static final String BLOCK_MODE_CTR = "CTR";
    public static final String BLOCK_MODE_ECB = "ECB";

    public static final String ENCRYPTION_PADDING_NONE = "NoPadding";
    public static final String ENCRYPTION_PADDING_PKCS7 = "PKCS7Padding";
    public static final String ENCRYPTION_PADDING_RSA_PKCS1 = "PKCS1Padding";
    public static final String ENCRYPTION_PADDING_RSA_OAEP = "OAEPPadding";

    public static final String DIGEST_SHA256 = "SHA-256";
    public static final String DIGEST_SHA512 = "SHA-512";

    public static final int PURPOSE_ENCRYPT = 1;
    public static final int PURPOSE_DECRYPT = 2;
    public static final int PURPOSE_SIGN = 4;
    public static final int PURPOSE_VERIFY = 8;
}
