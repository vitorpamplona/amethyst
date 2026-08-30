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
}
