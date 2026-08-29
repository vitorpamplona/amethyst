package android.os;

/**
 * JVM stand-in for android.os.Handler, posting onto the AWT event queue.
 *
 * Real rather than inert: posting work to the UI thread is something desktop
 * genuinely needs, and Compose Desktop runs on that queue.
 */
public class Handler {
    public Handler() {}

    public Handler(Looper looper) {}

    public boolean post(Runnable r) {
        java.awt.EventQueue.invokeLater(r);
        return true;
    }

    public boolean postDelayed(Runnable r, long delayMillis) {
        javax.swing.Timer timer = new javax.swing.Timer((int) Math.max(delayMillis, 0), e -> r.run());
        timer.setRepeats(false);
        timer.start();
        return true;
    }

    public void removeCallbacks(Runnable r) {}

    public void removeCallbacksAndMessages(Object token) {}
}
