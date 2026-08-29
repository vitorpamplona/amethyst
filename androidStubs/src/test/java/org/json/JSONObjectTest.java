package org.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * org.json's `opt*` contract is the whole reason this class exists rather than
 * a direct Jackson call: a missing OR wrongly-typed value yields the fallback
 * instead of throwing, and the IME bridge relies on that for every field of
 * every message it parses.
 */
class JSONObjectTest {
    @Test
    void buildsAndSerializes() {
        String json = new JSONObject().put("type", "ime.resync").put("x", 1.5).toString();
        assertTrue(json.contains("\"type\":\"ime.resync\""), json);
        assertTrue(json.contains("1.5"), json);
    }

    @Test
    void optReturnsTheFallbackForMissingKeys() {
        JSONObject o = new JSONObject("{}");
        assertEquals(0.0, o.optDouble("nope", 0.0));
        assertEquals(7, o.optInt("nope", 7));
        assertEquals("", o.optString("nope"));
        assertFalse(o.optBoolean("nope", false));
        assertNull(o.optJSONObject("nope"));
    }

    @Test
    void optReturnsTheFallbackForWronglyTypedValues() {
        JSONObject o = new JSONObject("{\"n\":\"not a number\",\"b\":\"not a bool\"}");
        assertEquals(-1.0, o.optDouble("n", -1.0), "a string must not coerce into a double");
        assertFalse(o.optBoolean("b", false), "a string must not coerce into a boolean");
    }

    @Test
    void nullValuesReadAsAbsent() {
        JSONObject o = new JSONObject("{\"v\":null}");
        assertEquals("fallback", o.optString("v", "fallback"));
    }

    @Test
    void roundTripsANestedObject() {
        JSONObject o = new JSONObject("{\"geom\":{\"l\":1.0,\"t\":2.0}}");
        JSONObject geom = o.optJSONObject("geom");
        assertEquals(1.0, geom.optDouble("l", 0.0));
        assertEquals(2.0, geom.optDouble("t", 0.0));
    }

    @Test
    void malformedInputThrowsRatherThanReturningAnEmptyObject() {
        assertThrows(JSONException.class, () -> new JSONObject("{ not json"));
        assertThrows(JSONException.class, () -> new JSONObject("[1,2,3]"));
    }
}
