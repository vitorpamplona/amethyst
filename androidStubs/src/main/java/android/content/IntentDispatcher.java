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
        if (Intent.ACTION_SEND.equals(action)) {
            if (copyFileToClipboard(streamOf(intent))) return;
            if (copyToClipboard(intent.getStringExtra(Intent.EXTRA_TEXT))) return;
        }

        PlatformGaps.report(
                "Intent." + (action == null ? "(no action)" : action),
                "no desktop handler; data=" + intent.getData() + " type=" + intent.getType());
    }

    /** The file being shared, from EXTRA_STREAM or the attached ClipData. */
    private static Uri streamOf(Intent intent) {
        Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream != null) return stream;
        ClipData clip = intent.getClipData();
        return clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).getUri() : null;
    }

    /**
     * Sharing a file has a real desktop equivalent: put it on the clipboard as
     * a file, which every file manager and mail client accepts as a paste. That
     * is a genuine hand-off, not a placeholder — the user finishes the share in
     * the app of their choice, which is what the share sheet was for.
     */
    private static boolean copyFileToClipboard(Uri uri) {
        if (uri == null) return false;
        String path = uri.getPath();
        if (path == null) return false;

        java.io.File file = new java.io.File(path);
        if (!file.isFile()) return false;

        try {
            java.util.List<java.io.File> files = java.util.List.of(file);
            java.awt.datatransfer.Transferable transferable =
                    new java.awt.datatransfer.Transferable() {
                        @Override
                        public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                            return new java.awt.datatransfer.DataFlavor[] {
                                java.awt.datatransfer.DataFlavor.javaFileListFlavor
                            };
                        }

                        @Override
                        public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                            return java.awt.datatransfer.DataFlavor.javaFileListFlavor.equals(flavor);
                        }

                        @Override
                        public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) {
                            return files;
                        }
                    };
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
            PlatformGaps.report(
                    "Intent.ACTION_SEND.file",
                    "no share sheet on desktop; " + file.getName() + " was copied to the clipboard as a file");
            return true;
        } catch (Exception e) {
            return false;
        }
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
