package android.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HandlerTest {
    @Test
    void removeCallbacksActuallyCancels() throws Exception {
        Handler handler = new Handler();
        AtomicInteger ran = new AtomicInteger();
        Runnable work = ran::incrementAndGet;

        handler.postDelayed(work, 60);
        assertEquals(1, handler.pendingCount());

        handler.removeCallbacks(work);
        assertEquals(0, handler.pendingCount());

        // Well past the delay: a cancelled runnable must never fire. Before the
        // timer was tracked this silently ran anyway.
        Thread.sleep(250);
        assertEquals(0, ran.get(), "a cancelled delayed runnable must not run");
    }

    @Test
    void delayedWorkStillRunsWhenNotCancelled() throws Exception {
        Handler handler = new Handler();
        CountDownLatch latch = new CountDownLatch(1);
        handler.postDelayed(latch::countDown, 20);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "an uncancelled delayed runnable must run");
        assertEquals(0, handler.pendingCount(), "a fired timer must not stay armed");
    }

    @Test
    void removeCallbacksAndMessagesClearsEverything() {
        Handler handler = new Handler();
        handler.postDelayed(() -> {}, 500);
        handler.postDelayed(() -> {}, 500);
        assertEquals(2, handler.pendingCount());
        handler.removeCallbacksAndMessages(null);
        assertEquals(0, handler.pendingCount());
    }

    @Test
    void postRunsOnTheEventQueue() throws Exception {
        Handler handler = new Handler();
        CountDownLatch latch = new CountDownLatch(1);
        handler.post(() -> {
            if (java.awt.EventQueue.isDispatchThread()) latch.countDown();
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS), "post must run on the AWT event queue");
    }
}
