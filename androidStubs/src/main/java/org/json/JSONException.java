package org.json;

/** JVM stand-in for org.json.JSONException. See JSONObject for why this is not the org.json artifact. */
public class JSONException extends RuntimeException {
    public JSONException(String message) { super(message); }

    public JSONException(String message, Throwable cause) { super(message, cause); }
}
