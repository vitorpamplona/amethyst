package android.os;

/**
 * JVM stand-in for android.os.Build.
 *
 * SDK_INT reports the compile-time target rather than a fabricated value: code
 * guarded by `Build.VERSION.SDK_INT >= X` is asking "is the modern API
 * available", and on the JVM the modern branch is always the right one — the
 * legacy branches exist for old handsets that have no desktop analogue.
 */
public final class Build {
    public static final String MANUFACTURER = "Desktop";
    public static final String MODEL = System.getProperty("os.name", "Desktop");
    public static final String DEVICE = "desktop";
    public static final String BRAND = "desktop";
    public static final String PRODUCT = "desktop";
    public static final String FINGERPRINT = "desktop/jvm";

    private Build() {}

    public static final class VERSION {
        /** Kept in step with the app's compileSdk. */
        public static final int SDK_INT = 36;
        public static final String RELEASE = "desktop";

        private VERSION() {}
    }

    public static final class VERSION_CODES {
        public static final int O = 26;
        public static final int P = 28;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
        public static final int S_V2 = 32;
        public static final int TIRAMISU = 33;
        public static final int UPSIDE_DOWN_CAKE = 34;
        public static final int VANILLA_ICE_CREAM = 35;
        public static final int BAKLAVA = 36;

        private VERSION_CODES() {}
    }
}
