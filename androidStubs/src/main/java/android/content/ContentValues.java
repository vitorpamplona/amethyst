package android.content;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** JVM stand-in for android.content.ContentValues. Pure data. */
public class ContentValues {
    private final Map<String, Object> values = new HashMap<>();

    public void put(String key, String value) { values.put(key, value); }

    public void put(String key, Integer value) { values.put(key, value); }

    public void put(String key, Long value) { values.put(key, value); }

    public void put(String key, Boolean value) { values.put(key, value); }

    public Object get(String key) { return values.get(key); }

    public String getAsString(String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    public boolean containsKey(String key) { return values.containsKey(key); }

    public Set<String> keySet() { return values.keySet(); }

    public int size() { return values.size(); }
}
