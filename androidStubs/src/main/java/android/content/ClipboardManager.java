package android.content;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

/**
 * JVM stand-in for android.content.ClipboardManager, backed by the AWT
 * clipboard — a real system clipboard, so this really copies and pastes.
 */
public class ClipboardManager {
    public void setPrimaryClip(ClipData clip) {
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).getText();
        if (text == null) return;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
        } catch (Exception e) {
            com.vitorpamplona.amethyst.stubs.PlatformGaps.report("ClipboardManager.setPrimaryClip", "clipboard unavailable: " + e);
        }
    }

    public ClipData getPrimaryClip() {
        try {
            Object contents = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return contents == null ? null : ClipData.newPlainText("", contents.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasPrimaryClip() { return getPrimaryClip() != null; }
}
