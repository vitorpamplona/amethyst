package android.util;

/**
 * JVM stand-in for android.util.Log, writing to stderr/stdout.
 *
 * The app is migrating off this class in favour of its own lambda-taking
 * logger; this exists so the files that have not been converted yet still
 * compile, not as somewhere to add new calls.
 */
public final class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;

    private Log() {}

    private static int print(String level, String tag, String msg, Throwable tr) {
        java.io.PrintStream out = ("E".equals(level) || "W".equals(level)) ? System.err : System.out;
        out.println(level + "/" + tag + ": " + msg);
        if (tr != null) tr.printStackTrace(out);
        return 0;
    }

    public static int v(String tag, String msg) { return print("V", tag, msg, null); }

    public static int v(String tag, String msg, Throwable tr) { return print("V", tag, msg, tr); }

    public static int d(String tag, String msg) { return print("D", tag, msg, null); }

    public static int d(String tag, String msg, Throwable tr) { return print("D", tag, msg, tr); }

    public static int i(String tag, String msg) { return print("I", tag, msg, null); }

    public static int i(String tag, String msg, Throwable tr) { return print("I", tag, msg, tr); }

    public static int w(String tag, String msg) { return print("W", tag, msg, null); }

    public static int w(String tag, String msg, Throwable tr) { return print("W", tag, msg, tr); }

    public static int w(String tag, Throwable tr) { return print("W", tag, "", tr); }

    public static int e(String tag, String msg) { return print("E", tag, msg, null); }

    public static int e(String tag, String msg, Throwable tr) { return print("E", tag, msg, tr); }

    public static boolean isLoggable(String tag, int level) { return true; }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        tr.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
