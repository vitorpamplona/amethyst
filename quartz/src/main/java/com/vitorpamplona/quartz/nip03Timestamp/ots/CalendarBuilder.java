package com.vitorpamplona.quartz.nip03Timestamp.ots;

public interface CalendarBuilder {
    public ICalendar newSyncCalendar(String url);
    public ICalendarAsyncSubmit newAsyncCalendar(String url, byte[] digest);
}
