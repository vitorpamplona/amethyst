package android.speech.tts;

/** JVM stand-in for android.speech.tts.UtteranceProgressListener. */
public abstract class UtteranceProgressListener {
    public abstract void onStart(String utteranceId);

    public abstract void onDone(String utteranceId);

    /** Deprecated on Android in favour of the error-code overload; kept for source parity. */
    @Deprecated
    public void onError(String utteranceId) {}

    public void onError(String utteranceId, int errorCode) {
        onError(utteranceId);
    }

    public void onStop(String utteranceId, boolean interrupted) {}
}
