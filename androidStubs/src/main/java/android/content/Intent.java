package android.content;

import android.net.Uri;
import android.os.Bundle;

/**
 * JVM stand-in for android.content.Intent.
 *
 * Kept as a faithful data carrier — action, data, type, flags, categories and
 * extras — with the builder methods returning `this` as Android's do, so the
 * long chained `Intent().apply { ... }` blocks in shared code compile as
 * written. Dispatching an Intent is a platform concern and is NOT modelled
 * here: the desktop side handles the few intents that have a meaning (open a
 * URL, share text) at the call site, and everything else is inert by design.
 */
public class Intent {
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final String ACTION_SEND = "android.intent.action.SEND";
    public static final String ACTION_SENDTO = "android.intent.action.SENDTO";
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT";
    public static final String ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT";
    public static final String EXTRA_TEXT = "android.intent.extra.TEXT";
    public static final String EXTRA_SUBJECT = "android.intent.extra.SUBJECT";
    public static final String EXTRA_STREAM = "android.intent.extra.STREAM";
    public static final String EXTRA_TITLE = "android.intent.extra.TITLE";
    public static final String CATEGORY_DEFAULT = "android.intent.category.DEFAULT";
    public static final String CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE";

    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public static final int FLAG_ACTIVITY_CLEAR_TOP = 0x04000000;
    public static final int FLAG_ACTIVITY_SINGLE_TOP = 0x20000000;
    public static final int FLAG_ACTIVITY_CLEAR_TASK = 0x00008000;
    public static final int FLAG_GRANT_READ_URI_PERMISSION = 0x00000001;
    public static final int FLAG_GRANT_WRITE_URI_PERMISSION = 0x00000002;

    private String action;
    private Uri data;
    private String type;
    private int flags;
    private final Bundle extras = new Bundle();
    private final java.util.Set<String> categories = new java.util.LinkedHashSet<>();

    public Intent() {}

    public Intent(String action) { this.action = action; }

    public Intent(String action, Uri data) {
        this.action = action;
        this.data = data;
    }

    public Intent(Context packageContext, Class<?> cls) {}

    public String getAction() { return action; }

    public Intent setAction(String action) { this.action = action; return this; }

    public Uri getData() { return data; }

    public Intent setData(Uri data) { this.data = data; return this; }

    public String getType() { return type; }

    public Intent setType(String type) { this.type = type; return this; }

    public Intent setDataAndType(Uri data, String type) {
        this.data = data;
        this.type = type;
        return this;
    }

    public int getFlags() { return flags; }

    public Intent setFlags(int flags) { this.flags = flags; return this; }

    public Intent addFlags(int flags) { this.flags |= flags; return this; }

    public Intent addCategory(String category) { categories.add(category); return this; }

    public java.util.Set<String> getCategories() { return categories; }

    public Bundle getExtras() { return extras; }

    public Intent putExtra(String name, String value) { extras.putString(name, value); return this; }

    public Intent putExtra(String name, int value) { extras.putInt(name, value); return this; }

    public Intent putExtra(String name, long value) { extras.putLong(name, value); return this; }

    public Intent putExtra(String name, boolean value) { extras.putBoolean(name, value); return this; }

    public Intent putExtra(String name, String[] value) { extras.putStringArray(name, value); return this; }

    public Intent putExtras(Bundle source) {
        for (String key : source.keySet()) extras.putString(key, source.getString(key));
        return this;
    }

    public String getStringExtra(String name) { return extras.getString(name); }

    public int getIntExtra(String name, int defaultValue) { return extras.getInt(name, defaultValue); }

    public long getLongExtra(String name, long defaultValue) { return extras.getLong(name, defaultValue); }

    public boolean getBooleanExtra(String name, boolean defaultValue) { return extras.getBoolean(name, defaultValue); }

    public String[] getStringArrayExtra(String name) { return extras.getStringArray(name); }

    public boolean hasExtra(String name) { return extras.containsKey(name); }

    public static Intent createChooser(Intent target, CharSequence title) { return target; }
}
