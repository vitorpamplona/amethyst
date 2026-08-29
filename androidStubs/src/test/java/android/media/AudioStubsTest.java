package android.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The voice-message path records and plays through these. The parts that need
 * real audio hardware cannot run here, so what is pinned instead is the
 * contract around them: the backend seam, and that an unplayable source fails
 * the way callers already handle rather than in some new way.
 */
class AudioStubsTest {
    @AfterEach
    void clearBackends() {
        MediaPlayer.setBackend(null);
        MediaRecorder.setEncoder(null);
    }

    @Test
    void anInstalledBackendTakesOverPlayback() throws IOException {
        AtomicBoolean started = new AtomicBoolean();
        AtomicInteger seeked = new AtomicInteger(-1);

        MediaPlayer.setBackend(source -> new MediaPlayer.Handle() {
            private boolean playing;

            @Override public void start() { playing = true; started.set(true); }

            @Override public void pause() { playing = false; }

            @Override public void stop() { playing = false; }

            @Override public void seekTo(int positionMs) { seeked.set(positionMs); }

            @Override public boolean isPlaying() { return playing; }

            @Override public int getCurrentPosition() { return 1_500; }

            @Override public int getDuration() { return 9_000; }

            @Override public void release() {}

            @Override public void setOnCompletion(Runnable callback) {}
        });

        MediaPlayer player = new MediaPlayer();
        player.setDataSource("https://example.com/voice.m4a");
        player.prepare();

        assertFalse(player.isPlaying());
        player.start();
        assertTrue(started.get());
        assertTrue(player.isPlaying());

        assertEquals(1_500, player.getCurrentPosition());
        assertEquals(9_000, player.getDuration());

        player.seekTo(4_500);
        assertEquals(4_500, seeked.get());

        player.pause();
        assertFalse(player.isPlaying());
        player.release();
    }

    @Test
    void completionReachesTheListener() throws IOException {
        AtomicBoolean completed = new AtomicBoolean();
        Runnable[] captured = new Runnable[1];

        MediaPlayer.setBackend(source -> new NoopHandle() {
            @Override public void setOnCompletion(Runnable callback) { captured[0] = callback; }
        });

        MediaPlayer player = new MediaPlayer();
        player.setDataSource("voice.m4a");
        player.setOnCompletionListener(p -> completed.set(true));
        player.prepare();

        captured[0].run();
        assertTrue(completed.get());
    }

    @Test
    void aBackendThatRefusesFallsThroughToTheJdk() {
        MediaPlayer.setBackend(source -> null);

        MediaPlayer player = new MediaPlayer();
        player.setDataSource("/nonexistent/voice.m4a");

        // Neither the backend nor the JDK can open it: IOException is the
        // platform's own signal, and the callers already catch it.
        assertThrows(IOException.class, player::prepare);
    }

    @Test
    void noDataSourceIsAnIoErrorNotACrash() {
        assertThrows(IOException.class, new MediaPlayer()::prepare);
    }

    @Test
    void anEmptyPlayerReportsZerosRatherThanThrowing() {
        MediaPlayer player = new MediaPlayer();
        assertEquals(0, player.getCurrentPosition());
        assertEquals(0, player.getDuration());
        assertFalse(player.isPlaying());
        // Release on a player that never prepared must be safe: the UI calls it
        // from onDispose whether or not prepare succeeded.
        player.release();
        player.release();
    }

    @Test
    void recorderWithoutAnOutputFileFailsBeforeTouchingTheMicrophone() {
        assertThrows(IOException.class, new MediaRecorder()::prepare);
    }

    @Test
    void stoppingARecorderThatNeverStartedIsAnIllegalState() {
        // Matches the platform: stop() outside a recording throws
        // IllegalStateException, which VoiceMessageRecorder catches.
        assertThrows(IllegalStateException.class, new MediaRecorder()::stop);
    }

    @Test
    void maxAmplitudeReadsAndResets() {
        MediaRecorder recorder = new MediaRecorder();
        assertEquals(0, recorder.getMaxAmplitude());
        assertEquals(0, recorder.getMaxAmplitude());
    }

    @Test
    void theRecorderAcceptsTheAndroidConfigurationChain() throws IOException {
        File output = Files.createTempFile("amethyst-voice", ".mp4").toFile();
        output.deleteOnExit();

        MediaRecorder recorder = new MediaRecorder();
        recorder.setAudioEncodingBitRate(16 * 44100);
        recorder.setAudioSamplingRate(44100);
        recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(output);

        // prepare() may fail for want of a microphone, which is a real IOException
        // and not a defect; what must not happen is a different failure.
        try {
            recorder.prepare();
            recorder.release();
        } catch (IOException expected) {
            // no capture device in this environment
        }
    }

    private static class NoopHandle implements MediaPlayer.Handle {
        @Override public void start() {}

        @Override public void pause() {}

        @Override public void stop() {}

        @Override public void seekTo(int positionMs) {}

        @Override public boolean isPlaying() { return false; }

        @Override public int getCurrentPosition() { return 0; }

        @Override public int getDuration() { return 0; }

        @Override public void release() {}

        @Override public void setOnCompletion(Runnable callback) {}
    }
}
