package android.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.content.Intent;
import android.content.IntentDispatcher;
import android.os.SystemClock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The watchdog schedules an alarm and cancels it through a *separately built*
 * PendingIntent. That only works because the platform treats two intents with
 * the same request code and target as one token — a stub handing back a fresh
 * object each time would make every cancel a silent no-op, and the watchdog
 * would keep firing forever.
 */
class AlarmSchedulingTest {
    private final AlarmManager alarms = new AlarmManager();

    @AfterEach
    void stopEverything() {
        IntentDispatcher.setHandler(null);
    }

    private static Intent watchdogIntent() {
        return new Intent("com.vitorpamplona.amethyst.WATCHDOG");
    }

    private static PendingIntent obtain(int flags) {
        return PendingIntent.getBroadcast(null, 7, watchdogIntent(), flags);
    }

    @Test
    void matchingIntentsAreTheSameToken() {
        PendingIntent first = obtain(PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent second = obtain(PendingIntent.FLAG_UPDATE_CURRENT);
        assertSame(first, second);
        first.cancel();
    }

    @Test
    void aDifferentRequestCodeIsADifferentToken() {
        PendingIntent first = PendingIntent.getBroadcast(null, 11, watchdogIntent(), 0);
        PendingIntent second = PendingIntent.getBroadcast(null, 12, watchdogIntent(), 0);
        assertNotSame(first, second);
        first.cancel();
        second.cancel();
    }

    @Test
    void aDifferentKindIsADifferentToken() {
        PendingIntent broadcast = PendingIntent.getBroadcast(null, 13, watchdogIntent(), 0);
        PendingIntent service = PendingIntent.getService(null, 13, watchdogIntent(), 0);
        assertNotSame(broadcast, service);
        broadcast.cancel();
        service.cancel();
    }

    @Test
    void noCreateReturnsNullWhenNothingIsRegistered() {
        // This is the branch the watchdog's cancel() takes to decide there is
        // nothing to cancel; a non-null here would cancel a token it never set.
        assertNull(PendingIntent.getBroadcast(null, 99, new Intent("NOTHING_SCHEDULED"), PendingIntent.FLAG_NO_CREATE));
    }

    @Test
    void noCreateFindsOneThatExists() {
        PendingIntent created = PendingIntent.getBroadcast(null, 21, watchdogIntent(), 0);
        PendingIntent found = PendingIntent.getBroadcast(null, 21, watchdogIntent(), PendingIntent.FLAG_NO_CREATE);
        assertSame(created, found);
        created.cancel();
        assertNull(PendingIntent.getBroadcast(null, 21, watchdogIntent(), PendingIntent.FLAG_NO_CREATE));
    }

    @Test
    void anAlarmActuallyFires() throws InterruptedException {
        CountDownLatch fired = new CountDownLatch(1);
        IntentDispatcher.setHandler(intent -> {
            if ("ALARM_ONCE".equals(intent.getAction())) fired.countDown();
            return true;
        });

        PendingIntent operation = PendingIntent.getBroadcast(null, 31, new Intent("ALARM_ONCE"), 0);
        alarms.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 30, operation);

        assertTrue(fired.await(5, TimeUnit.SECONDS), "the alarm never fired");
        assertFalse(alarms.isScheduled(operation), "a one-shot alarm should not stay scheduled");
        operation.cancel();
    }

    @Test
    void aRepeatingAlarmKeepsFiringUntilCancelled() throws InterruptedException {
        CountDownLatch threeTimes = new CountDownLatch(3);
        AtomicInteger count = new AtomicInteger();
        IntentDispatcher.setHandler(intent -> {
            if ("ALARM_REPEAT".equals(intent.getAction())) {
                count.incrementAndGet();
                threeTimes.countDown();
            }
            return true;
        });

        PendingIntent operation = PendingIntent.getBroadcast(null, 32, new Intent("ALARM_REPEAT"), 0);
        alarms.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), 1_000L, operation);

        assertTrue(threeTimes.await(10, TimeUnit.SECONDS), "expected three firings, saw " + count.get());
        assertTrue(alarms.isScheduled(operation));

        alarms.cancel(operation);
        assertFalse(alarms.isScheduled(operation));

        int after = count.get();
        Thread.sleep(2_500);
        assertTrue(count.get() <= after + 1, "the cancelled alarm kept firing");
        operation.cancel();
    }

    @Test
    void cancellingThroughARebuiltIntentStopsTheAlarm() throws InterruptedException {
        AtomicInteger count = new AtomicInteger();
        IntentDispatcher.setHandler(intent -> {
            if ("ALARM_WATCHDOG".equals(intent.getAction())) count.incrementAndGet();
            return true;
        });

        // Exactly the watchdog's shape: schedule with one PendingIntent...
        PendingIntent scheduled =
                PendingIntent.getBroadcast(
                        null, 41, new Intent("ALARM_WATCHDOG"),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        alarms.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), 1_000L, scheduled);

        // ...and cancel with one built again from scratch.
        PendingIntent found =
                PendingIntent.getBroadcast(
                        null, 41, new Intent("ALARM_WATCHDOG"),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        assertSame(scheduled, found);
        alarms.cancel(found);

        assertFalse(alarms.isScheduled(scheduled));
        int after = count.get();
        Thread.sleep(2_500);
        assertTrue(count.get() <= after + 1, "cancel through a rebuilt intent did not stop the alarm");
        scheduled.cancel();
    }

    @Test
    void rtcAndElapsedTriggerTimesAreReadOnTheirOwnClocks() throws InterruptedException {
        CountDownLatch fired = new CountDownLatch(1);
        IntentDispatcher.setHandler(intent -> {
            if ("ALARM_RTC".equals(intent.getAction())) fired.countDown();
            return true;
        });

        // A wall-clock trigger read as uptime would land decades away.
        PendingIntent operation = PendingIntent.getBroadcast(null, 51, new Intent("ALARM_RTC"), 0);
        alarms.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 30, operation);

        assertTrue(fired.await(5, TimeUnit.SECONDS), "an RTC alarm was scheduled against the wrong clock");
        operation.cancel();
    }
}
