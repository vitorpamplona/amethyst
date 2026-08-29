package android.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * The share path carries a file through EXTRA_STREAM or ClipData. Losing either
 * on the way turns "share this export" into a no-op.
 */
class ShareIntentTest {
    @Test
    void aUriExtraComesBackAsAUri() {
        Uri uri = Uri.parse("file:///tmp/relays.zip");
        Intent intent = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri);

        assertEquals(uri.toString(), intent.getParcelableExtra(Intent.EXTRA_STREAM).toString());
        assertNull(intent.getParcelableExtra("absent"));
    }

    @Test
    void clipDataSurvivesTheIntent() {
        Uri uri = Uri.parse("file:///tmp/note.png");
        ClipData clip = ClipData.newUri(null, "Image", uri);
        Intent intent = new Intent(Intent.ACTION_SEND).setClipData(clip);

        assertSame(clip, intent.getClipData());
        assertEquals(uri.toString(), intent.getClipData().getItemAt(0).getUri().toString());
    }

    @Test
    void extrasKeepTheirTypesWhenCopiedWholesale() {
        Bundle source = new Bundle();
        source.putString("s", "text");
        source.putInt("i", 42);
        source.putBoolean("b", true);

        Intent intent = new Intent(Intent.ACTION_SEND).putExtras(source);

        assertEquals("text", intent.getStringExtra("s"));
        assertEquals(42, intent.getIntExtra("i", -1));
        assertTrue(intent.getBooleanExtra("b", false));
    }

    @Test
    void anExplicitIntentKeepsItsTarget() {
        Intent intent = new Intent(null, ShareIntentTest.class);
        assertEquals(ShareIntentTest.class, intent.getTargetClass());
        assertEquals(ShareIntentTest.class.getName(), intent.getComponentClassName());
    }

    @Test
    void sharingARealFilePutsItOnTheClipboard() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) return;

        File file = Files.createTempFile("amethyst-share", ".txt").toFile();
        file.deleteOnExit();
        Files.writeString(file.toPath(), "hello");

        Intent intent =
                new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
        IntentDispatcher.dispatch(intent);

        Object contents =
                java.awt.Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .getData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
        assertEquals(java.util.List.of(file), contents);
    }

    @Test
    void anInstalledHandlerWinsOverTheDefault() {
        java.util.concurrent.atomic.AtomicReference<Intent> seen = new java.util.concurrent.atomic.AtomicReference<>();
        IntentDispatcher.setHandler(
                intent -> {
                    seen.set(intent);
                    return true;
                });
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
            IntentDispatcher.dispatch(intent);
            assertSame(intent, seen.get());
        } finally {
            IntentDispatcher.setHandler(null);
        }
    }
}
