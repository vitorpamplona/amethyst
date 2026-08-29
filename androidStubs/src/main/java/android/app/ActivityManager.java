package android.app;

/**
 * JVM stand-in for android.app.ActivityManager.
 *
 * Only the memory questions the app asks are meaningful off Android, and the
 * JVM can answer those for real: the heap ceiling is
 * {@code Runtime.maxMemory()}, not a per-app class the framework hands out.
 * Reporting a fabricated 256 MB would make the playback pool size itself for a
 * phone on a machine with 32 GB.
 */
public class ActivityManager {
    private static final long MB = 1024 * 1024;

    /** The JVM's own heap ceiling, in MB — the desktop's memory class. */
    public int getMemoryClass() {
        return (int) Math.max(1, Runtime.getRuntime().maxMemory() / MB);
    }

    /** Same ceiling: a desktop JVM has one heap limit, not two. */
    public int getLargeMemoryClass() {
        return getMemoryClass();
    }

    public boolean isLowRamDevice() { return false; }

    public void getMemoryInfo(MemoryInfo outInfo) {
        Runtime runtime = Runtime.getRuntime();
        outInfo.totalMem = runtime.maxMemory();
        outInfo.availMem = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        outInfo.threshold = runtime.maxMemory() / 16;
        outInfo.lowMemory = outInfo.availMem < outInfo.threshold;
    }

    public static class MemoryInfo {
        public long availMem;
        public long totalMem;
        public long threshold;
        public boolean lowMemory;
    }

    public static class RunningAppProcessInfo {
        public static final int IMPORTANCE_FOREGROUND = 100;
        public static final int IMPORTANCE_VISIBLE = 200;
        public static final int IMPORTANCE_BACKGROUND = 400;

        public String processName;
        public int pid;
        public int importance = IMPORTANCE_FOREGROUND;
    }
}
