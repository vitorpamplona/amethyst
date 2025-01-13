package com.vitorpamplona.quartz.nip03Timestamp.ots.http;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

/**
 * For making an HTTP request.
 */
public class Request implements Callable<Response> {
    private URL url;
    private byte[] data;
    private Map<String, String> headers;
    private BlockingQueue<Response> queue;

    public Request(URL url) {
        this.url = url;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setQueue(BlockingQueue<Response> queue) {
        this.queue = queue;
    }

    @Override
    public Response call() throws Exception {
        Response response = new Response();

        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setReadTimeout(10000);
            httpURLConnection.setConnectTimeout(10000);
            httpURLConnection.setRequestProperty("User-Agent", "OpenTimestamps Java");
            httpURLConnection.setRequestProperty("Accept", "application/json");
            httpURLConnection.setRequestProperty("Accept-Encoding", "gzip");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    httpURLConnection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if (data != null) {
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setRequestProperty("Content-Length", "" + Integer.toString(this.data.length));
                DataOutputStream wr = new DataOutputStream(httpURLConnection.getOutputStream());
                wr.write(this.data, 0, this.data.length);
                wr.flush();
                wr.close();
            } else {
                httpURLConnection.setRequestMethod("GET");
            }

            httpURLConnection.connect();

            int responseCode = httpURLConnection.getResponseCode();

            Log.i("OpenTimestamp", responseCode + " responseCode ");

            response.setStatus(responseCode);
            response.setFromUrl(url.toString());
            InputStream is = httpURLConnection.getInputStream();
            if ("gzip".equals(httpURLConnection.getContentEncoding())) {
                is = new GZIPInputStream(is);
            }
            response.setStream(is);
        } catch (Exception e) {
            Log.w("OpenTimestamp", url.toString() + " exception " + e);
        } finally {
            if (queue != null) {
                queue.offer(response);
            }
        }

        return response;
    }
}