package androidx.work;

import java.util.HashMap;
import java.util.Map;

/** JVM stand-in for androidx.work.Data. Pure key/value data. */
public final class Data {
    public static final Data EMPTY = new Builder().build();

    private final Map<String, Object> values;

    private Data(Map<String, Object> values) { this.values = values; }

    public String getString(String key) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : null;
    }

    public int getInt(String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    public long getLong(String key, long fallback) {
        Object value = values.get(key);
        return value instanceof Long ? (Long) value : fallback;
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public String[] getStringArray(String key) {
        Object value = values.get(key);
        return value instanceof String[] ? (String[]) value : null;
    }

    public static final class Builder {
        private final Map<String, Object> values = new HashMap<>();

        public Builder putString(String key, String value) {
            values.put(key, value);
            return this;
        }

        public Builder putInt(String key, int value) {
            values.put(key, value);
            return this;
        }

        public Builder putLong(String key, long value) {
            values.put(key, value);
            return this;
        }

        public Builder putBoolean(String key, boolean value) {
            values.put(key, value);
            return this;
        }

        public Builder putStringArray(String key, String[] value) {
            values.put(key, value);
            return this;
        }

        public Data build() { return new Data(values); }
    }
}
