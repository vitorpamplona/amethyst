package android.content;

/** JVM stand-in for android.content.ComponentCallbacks2. */
public interface ComponentCallbacks2 {
    int TRIM_MEMORY_COMPLETE = 80;
    int TRIM_MEMORY_MODERATE = 60;
    int TRIM_MEMORY_BACKGROUND = 40;
    int TRIM_MEMORY_UI_HIDDEN = 20;
    int TRIM_MEMORY_RUNNING_CRITICAL = 15;
    int TRIM_MEMORY_RUNNING_LOW = 10;
    int TRIM_MEMORY_RUNNING_MODERATE = 5;

    void onTrimMemory(int level);

    void onLowMemory();
}
