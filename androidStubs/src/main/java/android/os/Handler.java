package android.os;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.Timer;

/**
 * JVM stand-in for android.os.Handler, posting onto the AWT event queue.
 *
 * Real rather than inert: posting work to the UI thread is something desktop
 * genuinely needs, and Compose Desktop runs on that queue.
 *
 * Pending delayed messages are tracked so {@link #removeCallbacks} can actually
 * cancel them. Dropping that on the floor would be worse than not having the
 * method: callers cancel precisely because the work has become wrong to run —
 * a stale refresh, a timeout for a request that already returned — and it would
 * fire anyway with nothing to explain it.
 */
public class Handler {
    private final Map<Runnable, List<Timer>> pending = new ConcurrentHashMap<>();

    public Handler() {}

    public Handler(Looper looper) {}

    public boolean post(Runnable r) {
        java.awt.EventQueue.invokeLater(r);
        return true;
    }

    public boolean postDelayed(Runnable r, long delayMillis) {
        Timer timer = new Timer((int) Math.max(delayMillis, 0), null);
        timer.setRepeats(false);
        timer.addActionListener(e -> {
            forget(r, timer);
            r.run();
        });
        pending.computeIfAbsent(r, key -> new CopyOnWriteArrayList<>()).add(timer);
        timer.start();
        return true;
    }

    public void removeCallbacks(Runnable r) {
        List<Timer> timers = pending.remove(r);
        if (timers == null) return;
        for (Timer timer : timers) timer.stop();
    }

    public void removeCallbacksAndMessages(Object token) {
        for (List<Timer> timers : pending.values()) {
            for (Timer timer : timers) timer.stop();
        }
        pending.clear();
    }

    /** Visible for tests: how many delayed runnables are still armed. */
    public int pendingCount() {
        return pending.values().stream().mapToInt(List::size).sum();
    }

    private void forget(Runnable r, Timer timer) {
        List<Timer> timers = pending.get(r);
        if (timers == null) return;
        timers.remove(timer);
        if (timers.isEmpty()) pending.remove(r);
    }
}
