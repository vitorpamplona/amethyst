package android.os;

/**
 * JVM stand-in for android.os.Debug.
 *
 * Memory figures come from the JVM's own Runtime, which is the meaningful
 * number on desktop; Android's PSS/native split has no counterpart.
 */
public final class Debug {
    private Debug() {}

    public static class MemoryInfo {
        public int dalvikPss;
        public int nativePss;
        public int otherPss;

        public int getTotalPss() { return dalvikPss + nativePss + otherPss; }
    }

    public static void getMemoryInfo(MemoryInfo out) {
        Runtime runtime = Runtime.getRuntime();
        long usedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L;
        out.dalvikPss = (int) Math.min(usedKb, Integer.MAX_VALUE);
        out.nativePss = 0;
        out.otherPss = 0;
    }

    public static long getNativeHeapAllocatedSize() { return 0L; }
}
