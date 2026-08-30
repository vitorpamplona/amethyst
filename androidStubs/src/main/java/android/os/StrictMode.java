package android.os;

/**
 * JVM stand-in for android.os.StrictMode.
 *
 * StrictMode is a debug-build *detector*, not a feature: it watches for disk
 * and network work on the main thread and for leaked closeables, and logs what
 * it finds. There is nothing to port — the JVM has no main-thread policy to
 * violate, and the leak checks are the JDK's own (try-with-resources, Cleaner).
 *
 * So the policies are accepted and dropped, and the absence is declared once
 * rather than left to look like a debug build that simply never found anything.
 */
public final class StrictMode {
    private StrictMode() {}

    public static void setThreadPolicy(ThreadPolicy policy) { declare(); }

    public static void setVmPolicy(VmPolicy policy) { declare(); }

    private static void declare() {
        com.vitorpamplona.amethyst.stubs.PlatformGaps.unavailable(
                "StrictMode",
                "StrictMode watches for main-thread IO and leaked closeables on Android. The JVM has "
                        + "no main-thread policy to violate and the JDK owns the leak checks, so there is "
                        + "nothing here for it to report.");
    }

    public static final class ThreadPolicy {
        public static final class Builder {
            public Builder detectDiskReads() { return this; }

            public Builder detectDiskWrites() { return this; }

            public Builder detectNetwork() { return this; }

            public Builder detectCustomSlowCalls() { return this; }

            public Builder detectResourceMismatches() { return this; }

            public Builder detectUnbufferedIo() { return this; }

            public Builder detectAll() { return this; }

            public Builder penaltyLog() { return this; }

            public Builder penaltyDeath() { return this; }

            public Builder penaltyFlashScreen() { return this; }

            public ThreadPolicy build() { return new ThreadPolicy(); }
        }
    }

    public static final class VmPolicy {
        public static final class Builder {
            public Builder detectLeakedSqlLiteObjects() { return this; }

            public Builder detectLeakedClosableObjects() { return this; }

            public Builder detectActivityLeaks() { return this; }

            public Builder detectLeakedRegistrationObjects() { return this; }

            public Builder detectFileUriExposure() { return this; }

            public Builder detectCleartextNetwork() { return this; }

            public Builder detectContentUriWithoutPermission() { return this; }

            public Builder detectUntaggedSockets() { return this; }

            public Builder detectCredentialProtectedWhileLocked() { return this; }

            public Builder detectImplicitDirectBoot() { return this; }

            public Builder detectIncorrectContextUse() { return this; }

            public Builder detectUnsafeIntentLaunch() { return this; }

            public Builder detectNonSdkApiUsage() { return this; }

            public Builder detectAll() { return this; }

            public Builder penaltyLog() { return this; }

            public Builder penaltyDeath() { return this; }

            public VmPolicy build() { return new VmPolicy(); }
        }
    }
}
