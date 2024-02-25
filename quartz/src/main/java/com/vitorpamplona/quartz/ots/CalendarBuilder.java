package com.vitorpamplona.quartz.ots;

public interface CalendarBuilder {
    public ICalendar newSyncCalendar(String url);
    public ICalendarAsyncSubmit newAsyncCalendar(String url, byte[] digest);
}
