package com.vitorpamplona.quartz.nip03Timestamp.ots;

public class CalendarPureJavaBuilder implements CalendarBuilder {
    public ICalendar newSyncCalendar(String url) {
        return new Calendar(url);
    }
    public ICalendarAsyncSubmit newAsyncCalendar(String url, byte[] digest) {
        return new CalendarAsyncSubmit(url, digest);
    }
}
