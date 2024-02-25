package com.vitorpamplona.quartz.ots.http;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Holds the response from an HTTP request.
 */
public class Response {
    private InputStream stream;
    private String fromUrl;
    private Integer status;

    public Response() {
    }

    public Response(InputStream stream) {
        this.stream = stream;
    }

    public void setStream(InputStream stream) {
        this.stream = stream;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public boolean isOk() {
        return getStatus() != null && 200 == getStatus();
    }

    public String getFromUrl() {
        return fromUrl;
    }

    public void setFromUrl(String fromUrl) {
        this.fromUrl = fromUrl;
    }

    public InputStream getStream() {
        return this.stream;
    }

    public String getString() throws IOException {
        return new String(getBytes(), StandardCharsets.UTF_8);
    }

    public byte[] getBytes() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = this.stream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();

        return buffer.toByteArray();
    }

    public JSONObject getJson() throws IOException, JSONException {
        String jsonString = getString();
        JSONObject json = new JSONObject(jsonString);

        return json;
    }
}