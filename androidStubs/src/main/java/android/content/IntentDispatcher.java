package android.content;

import android.net.Uri;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.awt.Desktop;
import java.net.URI;

/**
 * Carries out the Intents shared code fires, on a platform that has no Intents.
 *
 * Most of what Amethyst asks for is not Android-specific at all — open this
 * link, share this text — and the JDK can do both, so the default here really
 * works rather than pretending to. Anything it cannot carry out is reported to
 * {@link PlatformGaps} rather than dropped, because 61 call sites silently
 * doing nothing is indistinguishable from a broken app.
 *
 * An app can install a richer handler (a native share sheet, a compose window)
 * and fall back to this one.
 */
public final class IntentDispatcher {
    private IntentDispatcher() {}

    public interface Handler {
        /** Return false to let the default handling try. */
        boolean handle(Intent intent);
    }

    private static volatile Handler handler;

    public static void setHandler(Handler value) {
        handler = value;
    }

    public static void dispatch(Intent intent) {
        if (intent == null) return;

        Handler installed = handler;
        if (installed != null && installed.handle(intent)) return;

        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && browse(intent.getData())) return;
        if (Intent.ACTION_SENDTO.equals(action) && browse(intent.getData())) return;
        if (Intent.ACTION_SEND.equals(action) && copyToClipboard(intent.getStringExtra(Intent.EXTRA_TEXT))) return;

        PlatformGaps.report(
                "Intent." + (action == null ? "(no action)" : action),
                "no desktop handler; data=" + intent.getData() + " type=" + intent.getType());
    }

    private static boolean browse(Uri uri) {
        if (uri == null) return false;
        try {
            if (!Desktop.isDesktopSupported()) return false;
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return false;
            desktop.browse(URI.create(uri.toString()));
            return true;
        } catch (Exception e) {
            PlatformGaps.report("Intent.ACTION_VIEW", "could not open " + uri + ": " + e);
            return false;
        }
    }

    /**
     * Desktop has no share sheet. Putting the text on the clipboard is the
     * closest honest equivalent and leaves the user able to complete the share
     * themselves; it is still reported so the gap is not forgotten.
     */
    private static boolean copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            java.awt.Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
            PlatformGaps.report("Intent.ACTION_SEND", "no share sheet on desktop; text copied to the clipboard instead");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
