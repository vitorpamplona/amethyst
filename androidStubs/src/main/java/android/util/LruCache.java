package android.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JVM stand-in for android.util.LruCache.
 *
 * New code should use androidx.collection.LruCache, which is already
 * multiplatform; this covers the files that have not been switched over.
 */
public class LruCache<K, V> {
    private final int maxSize;
    private final LinkedHashMap<K, V> map;

    public LruCache(int maxSize) {
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<K, V>(0, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LruCache.this.maxSize;
            }
        };
    }

    public synchronized V get(K key) {
        V value = map.get(key);
        if (value != null) return value;
        V created = create(key);
        if (created != null) map.put(key, created);
        return created;
    }

    public synchronized V put(K key, V value) { return map.put(key, value); }

    public synchronized V remove(K key) { return map.remove(key); }

    public synchronized void evictAll() { map.clear(); }

    /** Drops the least recently used entries until at most {@code size} remain. */
    public synchronized void trimToSize(int size) {
        if (size < 0) {
            map.clear();
            return;
        }
        java.util.Iterator<K> keys = map.keySet().iterator();
        while (map.size() > size && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    public synchronized int size() { return map.size(); }

    public int maxSize() { return maxSize; }

    public synchronized Map<K, V> snapshot() { return new LinkedHashMap<>(map); }

    protected V create(K key) { return null; }

    protected int sizeOf(K key, V value) { return 1; }
}
