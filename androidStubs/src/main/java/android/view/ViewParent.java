package android.view;

/**
 * JVM stand-in for android.view.ViewParent.
 *
 * Only the type matters here: the app tests a view's parent with {@code as?
 * DialogWindowProvider} to find out whether it is inside a dialog. Nothing on
 * the JVM implements that, and a null result is the true answer.
 */
public interface ViewParent {
    ViewParent getParent();

    /**
     * A no-op with nothing to intercept: the app calls this so an ancestor
     * (drawer, pager, feed) stops stealing a drag from a map. Compose Desktop
     * has no View hierarchy competing for the pointer, so there is nothing to
     * ask.
     */
    default void requestDisallowInterceptTouchEvent(boolean disallow) {}
}
