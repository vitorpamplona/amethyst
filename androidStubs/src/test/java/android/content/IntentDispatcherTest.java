package android.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.net.Uri;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntentDispatcherTest {
    private final List<String> gaps = new ArrayList<>();

    @BeforeEach
    void captureGaps() {
        PlatformGaps.setReporter((feature, detail, kind) -> gaps.add(feature));
    }

    @AfterEach
    void reset() {
        PlatformGaps.setReporter(null);
        IntentDispatcher.setHandler(null);
    }

    @Test
    void anInstalledHandlerWins() {
        List<Intent> seen = new ArrayList<>();
        IntentDispatcher.setHandler(intent -> {
            seen.add(intent);
            return true;
        });

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid"));
        IntentDispatcher.dispatch(intent);

        assertEquals(1, seen.size());
        assertTrue(gaps.isEmpty(), "a handled intent must not be reported as a gap");
    }

    @Test
    void aHandlerReturningFalseFallsThroughToTheDefault() {
        IntentDispatcher.setHandler(intent -> false);
        // No action the default understands, so it must land as a reported gap
        // rather than being silently dropped.
        IntentDispatcher.dispatch(new Intent("com.example.UNKNOWN_ACTION"));
        assertEquals(List.of("Intent.com.example.UNKNOWN_ACTION"), gaps);
    }

    @Test
    void anUnhandleableIntentIsReportedNotSwallowed() {
        IntentDispatcher.dispatch(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+15551234")));
        assertFalse(gaps.isEmpty(), "an intent desktop cannot carry out must be reported");
    }

    @Test
    void sharingTextFallsBackToTheClipboardAndSaysSo() {
        Intent share = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "hello");
        IntentDispatcher.dispatch(share);
        // Headless CI has no clipboard; either it copied and reported the
        // substitution, or it could not and reported the gap. Both are honest —
        // what must never happen is neither.
        assertFalse(gaps.isEmpty(), "a share must either happen or be reported");
    }

    @Test
    void aNullIntentIsIgnoredWithoutReporting() {
        IntentDispatcher.dispatch(null);
        assertTrue(gaps.isEmpty());
    }

    @Test
    void gapsAreRecordedForLaterInspection() {
        PlatformGaps.setReporter(null);
        PlatformGaps.report("Feature.x", "why");
        assertTrue(PlatformGaps.seen().keySet().stream().anyMatch(s -> s.startsWith("Feature.x")));
    }
}
