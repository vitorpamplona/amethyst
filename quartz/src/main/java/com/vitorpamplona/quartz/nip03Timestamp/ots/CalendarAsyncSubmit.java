package com.vitorpamplona.quartz.nip03Timestamp.ots;

import com.vitorpamplona.quartz.nip03Timestamp.ots.http.Request;
import com.vitorpamplona.quartz.nip03Timestamp.ots.http.Response;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;

/**
 * For making async calls to a calendar server
 */
public class CalendarAsyncSubmit implements ICalendarAsyncSubmit {

    private String url;
    private byte[] digest;
    private BlockingQueue<Optional<Timestamp>> queue;

    public CalendarAsyncSubmit(String url, byte[] digest) {
        this.url = url;
        this.digest = digest;
    }

    public void setQueue(BlockingQueue<Optional<Timestamp>> queue) {
        this.queue = queue;
    }

    @Override
    public Optional<Timestamp> call() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.opentimestamps.v1");
        headers.put("User-Agent", "java-opentimestamps");
        headers.put("Content-Type", "application/x-www-form-urlencoded");

        URL obj = new URL(url + "/digest");
        Request task = new Request(obj);
        task.setData(digest);
        task.setHeaders(headers);
        Response response = task.call();

        if (response.isOk()) {
            byte[] body = response.getBytes();
            StreamDeserializationContext ctx = new StreamDeserializationContext(body);
            Timestamp timestamp = Timestamp.deserialize(ctx, digest);
            Optional<Timestamp> of = Optional.of(timestamp);
            queue.add(of);
            return of;
        }

        queue.add(Optional.empty());

        return Optional.empty();
    }
}