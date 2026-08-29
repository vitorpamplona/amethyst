package org.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;

/**
 * JVM stand-in for org.json.JSONObject, backed by Jackson.
 *
 * Android bundles org.json; the JDK does not. The obvious fix — the
 * `org.json:json` artifact — carries the JSON License, whose "shall be used for
 * Good, not Evil" clause is not OSI-approved and is rejected outright by
 * Debian, Fedora and the ASF. That is not a dependency to add to an MIT
 * project, so this reimplements the small surface the app uses on top of
 * Jackson, which is already a dependency everywhere else in the codebase.
 *
 * Semantics follow org.json where they differ from Jackson: `opt*` returns the
 * fallback for a missing OR wrongly-typed value rather than throwing.
 */
public class JSONObject {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectNode node;

    public JSONObject() { this.node = MAPPER.createObjectNode(); }

    public JSONObject(String json) throws JSONException {
        try {
            JsonNode parsed = MAPPER.readTree(json);
            if (!(parsed instanceof ObjectNode)) throw new JSONException("not a JSON object: " + json);
            this.node = (ObjectNode) parsed;
        } catch (JSONException e) {
            throw e;
        } catch (Exception e) {
            throw new JSONException("could not parse JSON: " + e.getMessage());
        }
    }

    private JSONObject(ObjectNode node) { this.node = node; }

    public JSONObject put(String key, String value) {
        node.put(key, value);
        return this;
    }

    public JSONObject put(String key, double value) {
        node.put(key, value);
        return this;
    }

    public JSONObject put(String key, int value) {
        node.put(key, value);
        return this;
    }

    public JSONObject put(String key, long value) {
        node.put(key, value);
        return this;
    }

    public JSONObject put(String key, boolean value) {
        node.put(key, value);
        return this;
    }

    public JSONObject put(String key, JSONObject value) {
        node.set(key, value == null ? null : value.node);
        return this;
    }

    public boolean has(String key) { return node.has(key); }

    public Iterator<String> keys() { return node.fieldNames(); }

    public int length() { return node.size(); }

    public String optString(String key) { return optString(key, ""); }

    public String optString(String key, String fallback) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? fallback : value.asText(fallback);
    }

    public double optDouble(String key, double fallback) {
        JsonNode value = node.get(key);
        return value == null || !value.isNumber() ? fallback : value.asDouble();
    }

    public int optInt(String key, int fallback) {
        JsonNode value = node.get(key);
        return value == null || !value.isNumber() ? fallback : value.asInt();
    }

    public long optLong(String key, long fallback) {
        JsonNode value = node.get(key);
        return value == null || !value.isNumber() ? fallback : value.asLong();
    }

    public boolean optBoolean(String key, boolean fallback) {
        JsonNode value = node.get(key);
        return value == null || !value.isBoolean() ? fallback : value.asBoolean();
    }

    public JSONObject optJSONObject(String key) {
        JsonNode value = node.get(key);
        return value instanceof ObjectNode ? new JSONObject((ObjectNode) value) : null;
    }

    @Override public String toString() { return node.toString(); }
}
