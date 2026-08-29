package android.media;

import android.content.Context;
import android.net.Uri;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/**
 * JVM stand-in for android.media.MediaPlayer.
 *
 * Playing a voice message is not an Android-only capability, so this plays: the
 * default backend is {@code javax.sound.sampled}, which decodes WAV, AU and
 * AIFF out of the box. What the JDK cannot decode is AAC-in-MP4 — which is
 * exactly what Android records — so a desktop build installs a real
 * {@link Backend} (the same ComposeMediaPlayer that backs video: Media
 * Foundation, AVFoundation, GStreamer) and gets every format.
 *
 * With neither, {@link #prepare} throws {@link IOException}, which is the
 * platform's own signal for an unplayable source and the path the callers
 * already handle.
 */
public class MediaPlayer {
    /** Installed by the desktop app to play what the JDK cannot decode. */
    public interface Backend {
        /** Null when this backend cannot play the source. */
        Handle open(String source);
    }

    /** One playing source. Positions and durations are milliseconds. */
    public interface Handle {
        void start();

        void pause();

        void stop();

        void seekTo(int positionMs);

        boolean isPlaying();

        int getCurrentPosition();

        int getDuration();

        void release();

        void setOnCompletion(Runnable callback);
    }

    public interface OnCompletionListener {
        void onCompletion(MediaPlayer player);
    }

    public interface OnPreparedListener {
        void onPrepared(MediaPlayer player);
    }

    public interface OnErrorListener {
        boolean onError(MediaPlayer player, int what, int extra);
    }

    private static volatile Backend backend;

    public static void setBackend(Backend value) { backend = value; }

    private String source;
    private Handle handle;
    private Clip clip;
    private long clipPositionOffsetUs;
    private OnCompletionListener onCompletion;
    private OnPreparedListener onPrepared;

    public void setDataSource(String path) { this.source = path; }

    public void setDataSource(Context context, Uri uri) { this.source = uri == null ? null : uri.toString(); }

    public void setDataSource(File file) { this.source = file == null ? null : file.getAbsolutePath(); }

    public void setOnCompletionListener(OnCompletionListener listener) {
        this.onCompletion = listener;
        if (handle != null) handle.setOnCompletion(this::fireCompletion);
    }

    public void setOnPreparedListener(OnPreparedListener listener) { this.onPrepared = listener; }

    public void setOnErrorListener(OnErrorListener listener) {}

    public void setLooping(boolean looping) {
        if (clip != null) clip.loop(looping ? Clip.LOOP_CONTINUOUSLY : 0);
    }

    public void setVolume(float left, float right) {}

    public void setAudioAttributes(AudioAttributes attributes) {}

    public void prepare() throws IOException {
        if (source == null) throw new IOException("no data source");

        Backend installed = backend;
        if (installed != null) {
            handle = installed.open(source);
            if (handle != null) {
                handle.setOnCompletion(this::fireCompletion);
                if (onPrepared != null) onPrepared.onPrepared(this);
                return;
            }
        }

        clip = openWithJdk(source);
        if (clip == null) {
            PlatformGaps.report(
                    "MediaPlayer.unsupportedFormat",
                    "the JDK decodes WAV, AU and AIFF only; " + source
                            + " needs the desktop media backend installed through MediaPlayer.setBackend");
            throw new IOException("cannot decode " + source);
        }
        if (onPrepared != null) onPrepared.onPrepared(this);
    }

    public void prepareAsync() {
        try {
            prepare();
        } catch (IOException e) {
            // The listener-based path reports through onError on Android; the
            // callers here all use the synchronous prepare().
        }
    }

    public void start() {
        if (handle != null) {
            handle.start();
        } else if (clip != null) {
            clip.start();
        }
    }

    public void pause() {
        if (handle != null) {
            handle.pause();
        } else if (clip != null) {
            clipPositionOffsetUs = clip.getMicrosecondPosition();
            clip.stop();
        }
    }

    public void stop() {
        if (handle != null) {
            handle.stop();
        } else if (clip != null) {
            clip.stop();
            clip.setMicrosecondPosition(0);
            clipPositionOffsetUs = 0;
        }
    }

    public void seekTo(int positionMs) {
        if (handle != null) {
            handle.seekTo(positionMs);
        } else if (clip != null) {
            clip.setMicrosecondPosition(Math.max(0L, positionMs) * 1000L);
            clipPositionOffsetUs = clip.getMicrosecondPosition();
        }
    }

    public boolean isPlaying() {
        if (handle != null) return handle.isPlaying();
        return clip != null && clip.isRunning();
    }

    public int getCurrentPosition() {
        if (handle != null) return handle.getCurrentPosition();
        if (clip == null) return 0;
        return (int) (clip.getMicrosecondPosition() / 1000L);
    }

    public int getDuration() {
        if (handle != null) return handle.getDuration();
        if (clip == null) return 0;
        long lengthUs = clip.getMicrosecondLength();
        return lengthUs < 0 ? 0 : (int) (lengthUs / 1000L);
    }

    public void reset() {
        release();
        source = null;
    }

    public void release() {
        if (handle != null) {
            handle.release();
            handle = null;
        }
        if (clip != null) {
            clip.close();
            clip = null;
        }
        clipPositionOffsetUs = 0;
    }

    private void fireCompletion() {
        OnCompletionListener listener = onCompletion;
        if (listener != null) listener.onCompletion(this);
    }

    private Clip openWithJdk(String path) {
        File file = new File(path);
        if (!file.isFile()) return null;
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
            Clip opened = AudioSystem.getClip();
            opened.open(stream);
            opened.addLineListener(event -> {
                if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP
                        && opened.getMicrosecondPosition() >= opened.getMicrosecondLength()) {
                    fireCompletion();
                }
            });
            return opened;
        } catch (Exception e) {
            return null;
        }
    }
}
