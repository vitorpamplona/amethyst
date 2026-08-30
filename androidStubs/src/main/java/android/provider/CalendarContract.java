package android.provider;

import android.net.Uri;

/**
 * JVM stand-in for android.provider.CalendarContract.
 *
 * The app never queries the calendar provider; it builds an ACTION_INSERT
 * intent and lets whatever calendar the user has take it. That has a real
 * desktop counterpart — an iCalendar (.ics) file handed to the default
 * handler, which is how every desktop calendar accepts an invitation — so this
 * is a missing implementation rather than a missing platform feature.
 *
 * The column names are the platform's own, so the extras an intent carries mean
 * the same thing on either side and the dispatcher that eventually writes the
 * .ics can read them straight off.
 */
public final class CalendarContract {
    private CalendarContract() {}

    public static final String AUTHORITY = "com.android.calendar";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public static final String EXTRA_EVENT_BEGIN_TIME = "beginTime";
    public static final String EXTRA_EVENT_END_TIME = "endTime";
    public static final String EXTRA_EVENT_ALL_DAY = "allDay";

    public static final class Events {
        private Events() {}

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/events");

        public static final String TITLE = "title";
        public static final String DESCRIPTION = "description";
        public static final String EVENT_LOCATION = "eventLocation";
        public static final String DTSTART = "dtstart";
        public static final String DTEND = "dtend";
        public static final String ALL_DAY = "allDay";
        public static final String EVENT_TIMEZONE = "eventTimezone";
        public static final String RRULE = "rrule";
        public static final String CALENDAR_ID = "calendar_id";
    }

    public static final class Calendars {
        private Calendars() {}

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/calendars");

        public static final String _ID = "_id";
        public static final String CALENDAR_DISPLAY_NAME = "calendar_displayName";
        public static final String ACCOUNT_NAME = "account_name";
    }

    public static final class Reminders {
        private Reminders() {}

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/reminders");

        public static final String EVENT_ID = "event_id";
        public static final String MINUTES = "minutes";
        public static final String METHOD = "method";
    }
}
