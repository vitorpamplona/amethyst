package android.app;

import java.util.ArrayList;
import java.util.List;

/**
 * JVM stand-in for android.app.Notification.
 *
 * This carries the content rather than only the flags, because the desktop
 * presenter has to have something to show. A builder that accepted a title and
 * a body and then dropped them would make every notification arrive blank,
 * which looks exactly like a working notification system until someone reads
 * one.
 */
public class Notification {
    public static final int PRIORITY_LOW = -1;
    public static final int PRIORITY_DEFAULT = 0;
    public static final int PRIORITY_HIGH = 1;
    public static final int DEFAULT_ALL = -1;
    public static final int FLAG_ONGOING_EVENT = 0x00000002;

    public static final int FLAG_GROUP_SUMMARY = 0x00000200;
    public static final int FLAG_AUTO_CANCEL = 0x00000010;
    public static final int FLAG_FOREGROUND_SERVICE = 0x00000040;

    public int flags;
    public int priority;
    public String category;

    public String channelId;
    public CharSequence title;
    public CharSequence text;
    public CharSequence subText;
    public CharSequence bigText;
    public String group;
    public boolean groupSummary;
    public boolean ongoing;
    public boolean autoCancel;
    public boolean silent;
    public boolean onlyAlertOnce;
    public long when;
    public int smallIcon;
    public Object largeIcon;
    public PendingIntent contentIntent;
    public PendingIntent deleteIntent;
    public PendingIntent fullScreenIntent;

    /** The redacted copy a lock screen shows, when one was built. */
    public Notification publicVersion;

    /** The conversation this notification carries, for a presenter that renders one. */
    public Object messagingStyle;

    /** Progress, as a determinate bar; -1 max means "no progress bar". */
    public int progressMax = -1;

    public int progress;
    public boolean progressIndeterminate;

    /** Lines of an inbox-style notification, or the messages of a chat one. */
    public final List<CharSequence> lines = new ArrayList<>();

    /** Buttons, as (title, intent) pairs the presenter can offer. */
    public final List<Action> actions = new ArrayList<>();

    /** A notification button. */
    public static final class Action {
        public final int icon;
        public final CharSequence title;
        public final PendingIntent actionIntent;

        public Action(int icon, CharSequence title, PendingIntent actionIntent) {
            this.icon = icon;
            this.title = title;
            this.actionIntent = actionIntent;
        }
    }

    @Override
    public String toString() {
        return "Notification(" + title + ": " + text + ")";
    }
}
