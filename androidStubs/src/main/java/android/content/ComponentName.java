package android.content;

/** JVM stand-in for android.content.ComponentName. Pure data. */
public final class ComponentName {
    private final String packageName;
    private final String className;

    public ComponentName(String packageName, String className) {
        this.packageName = packageName;
        this.className = className;
    }

    public ComponentName(Context context, String className) {
        this(context.getPackageName(), className);
    }

    public ComponentName(Context context, Class<?> cls) {
        this(context.getPackageName(), cls.getName());
    }

    public String getPackageName() { return packageName; }

    public String getClassName() { return className; }

    public String flattenToString() { return packageName + "/" + className; }

    @Override public String toString() { return flattenToString(); }
}
