package android.speech.tts;

import android.content.Context;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * JVM stand-in for android.speech.tts.TextToSpeech.
 *
 * Every desktop OS ships a speech synthesiser — `say` on macOS, SAPI via
 * PowerShell on Windows, `spd-say`/`espeak` on Linux — so this is a real
 * capability rather than a gap. Driving those is a subprocess concern that does
 * not belong in a stub jar, so synthesis goes through a {@link Synthesizer} the
 * desktop app installs; with none installed, speaking reports a gap instead of
 * silently doing nothing, which for a screen-reader feature would be the worst
 * possible failure.
 */
public class TextToSpeech {
    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int QUEUE_FLUSH = 0;
    public static final int QUEUE_ADD = 1;
    public static final int LANG_AVAILABLE = 0;
    public static final int LANG_MISSING_DATA = -1;
    public static final int LANG_NOT_SUPPORTED = -2;
    public static final int ERROR_SYNTHESIS = -3;
    public static final int ERROR_SERVICE = -4;
    public static final int ERROR_OUTPUT = -5;
    public static final int ERROR_NETWORK = -6;
    public static final int ERROR_NETWORK_TIMEOUT = -7;
    public static final int ERROR_INVALID_REQUEST = -8;
    public static final int ERROR_NOT_INSTALLED_YET = -9;

    public interface OnInitListener {
        void onInit(int status);
    }

    /** Installed by the desktop app; wraps whatever the OS provides. */
    public interface Synthesizer {
        /** Returns false when the utterance could not be started. */
        boolean speak(CharSequence text, Locale locale, String utteranceId);

        void stop();

        Set<Locale> availableLocales();
    }

    private static volatile Synthesizer synthesizer;

    public static void setSynthesizer(Synthesizer value) { synthesizer = value; }

    private Locale language = Locale.getDefault();
    private UtteranceProgressListener listener;

    public TextToSpeech(Context context, OnInitListener onInit) {
        // Report success only when something can actually speak; a screen reader
        // that reports ready and then stays silent is worse than one that fails.
        if (onInit != null) onInit.onInit(synthesizer != null ? SUCCESS : ERROR);
    }

    public int setLanguage(Locale locale) {
        Synthesizer engine = synthesizer;
        if (engine == null) return LANG_NOT_SUPPORTED;
        language = locale;
        return engine.availableLocales().contains(locale) ? LANG_AVAILABLE : LANG_NOT_SUPPORTED;
    }

    public Locale getLanguage() { return language; }

    public Set<Locale> getAvailableLanguages() {
        Synthesizer engine = synthesizer;
        return engine == null ? Collections.emptySet() : new HashSet<>(engine.availableLocales());
    }

    public int setSpeechRate(float rate) { return SUCCESS; }

    public int setPitch(float pitch) { return SUCCESS; }

    public void setAudioAttributes(android.media.AudioAttributes attributes) {}

    public int setOnUtteranceProgressListener(UtteranceProgressListener listener) {
        this.listener = listener;
        return SUCCESS;
    }

    public int speak(CharSequence text, int queueMode, android.os.Bundle params, String utteranceId) {
        Synthesizer engine = synthesizer;
        if (engine == null) {
            PlatformGaps.report(
                    "TextToSpeech.speak",
                    "no desktop synthesizer installed; every desktop OS has one (say / SAPI / spd-say)");
            if (listener != null) listener.onError(utteranceId, ERROR_SERVICE);
            return ERROR;
        }

        if (listener != null) listener.onStart(utteranceId);
        boolean spoke = engine.speak(text, language, utteranceId);
        if (listener != null) {
            if (spoke) listener.onDone(utteranceId);
            else listener.onError(utteranceId, ERROR_SYNTHESIS);
        }
        return spoke ? SUCCESS : ERROR;
    }

    public int stop() {
        Synthesizer engine = synthesizer;
        if (engine != null) engine.stop();
        return SUCCESS;
    }

    public void shutdown() { stop(); }

    public boolean isSpeaking() { return false; }
}
