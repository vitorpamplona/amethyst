package android.media;

import java.util.HashMap;
import java.util.Map;

/** JVM stand-in for android.media.MediaFormat. Pure key/value data. */
public final class MediaFormat {
    public static final String KEY_MIME = "mime";
    public static final String KEY_WIDTH = "width";
    public static final String KEY_HEIGHT = "height";
    public static final String KEY_BIT_RATE = "bitrate";
    public static final String KEY_FRAME_RATE = "frame-rate";
    public static final String KEY_I_FRAME_INTERVAL = "i-frame-interval";
    public static final String KEY_COLOR_FORMAT = "color-format";
    public static final String KEY_DURATION = "durationUs";
    public static final String KEY_ROTATION = "rotation-degrees";
    public static final String KEY_CHANNEL_COUNT = "channel-count";
    public static final String KEY_SAMPLE_RATE = "sample-rate";
    public static final String KEY_MAX_INPUT_SIZE = "max-input-size";

    private final Map<String, Object> values = new HashMap<>();

    public static MediaFormat createVideoFormat(String mime, int width, int height) {
        MediaFormat format = new MediaFormat();
        format.setString(KEY_MIME, mime);
        format.setInteger(KEY_WIDTH, width);
        format.setInteger(KEY_HEIGHT, height);
        return format;
    }

    public static MediaFormat createAudioFormat(String mime, int sampleRate, int channelCount) {
        MediaFormat format = new MediaFormat();
        format.setString(KEY_MIME, mime);
        format.setInteger(KEY_SAMPLE_RATE, sampleRate);
        format.setInteger(KEY_CHANNEL_COUNT, channelCount);
        return format;
    }

    public void setString(String name, String value) { values.put(name, value); }

    public void setInteger(String name, int value) { values.put(name, value); }

    public void setLong(String name, long value) { values.put(name, value); }

    public void setFloat(String name, float value) { values.put(name, value); }

    public String getString(String name) { return (String) values.get(name); }

    public int getInteger(String name) {
        Object value = values.get(name);
        if (value instanceof Integer) return (Integer) value;
        throw new NullPointerException("no integer for key " + name);
    }

    public int getInteger(String name, int fallback) {
        Object value = values.get(name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    public long getLong(String name) {
        Object value = values.get(name);
        return value instanceof Long ? (Long) value : 0L;
    }

    public boolean containsKey(String name) { return values.containsKey(name); }
}
