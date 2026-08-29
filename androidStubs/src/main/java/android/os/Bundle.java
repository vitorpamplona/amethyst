package android.os;

import java.util.HashMap;
import java.util.Map;

/** JVM stand-in for android.os.Bundle: a string-keyed heterogeneous map. */
public class Bundle {
    private final Map<String, Object> values = new HashMap<>();

    public void putString(String key, String value) { values.put(key, value); }

    public void putInt(String key, int value) { values.put(key, value); }

    public void putLong(String key, long value) { values.put(key, value); }

    public void putBoolean(String key, boolean value) { values.put(key, value); }

    public void putFloat(String key, float value) { values.put(key, value); }

    public void putDouble(String key, double value) { values.put(key, value); }

    public void putStringArray(String key, String[] value) { values.put(key, value); }

    public void putBundle(String key, Bundle value) { values.put(key, value); }

    public String getString(String key) { return (String) values.get(key); }

    public String getString(String key, String defaultValue) {
        Object v = values.get(key);
        return v == null ? defaultValue : (String) v;
    }

    public int getInt(String key) { return getInt(key, 0); }

    public int getInt(String key, int defaultValue) {
        Object v = values.get(key);
        return v instanceof Integer ? (Integer) v : defaultValue;
    }

    public long getLong(String key) { return getLong(key, 0L); }

    public long getLong(String key, long defaultValue) {
        Object v = values.get(key);
        return v instanceof Long ? (Long) v : defaultValue;
    }

    public boolean getBoolean(String key) { return getBoolean(key, false); }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : defaultValue;
    }

    public String[] getStringArray(String key) { return (String[]) values.get(key); }

    public Bundle getBundle(String key) { return (Bundle) values.get(key); }

    public boolean containsKey(String key) { return values.containsKey(key); }

    public void remove(String key) { values.remove(key); }

    public java.util.Set<String> keySet() { return values.keySet(); }

    public int size() { return values.size(); }

    public boolean isEmpty() { return values.isEmpty(); }
}
