package android.media;

import android.content.Context;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * JVM stand-in for android.media.MediaRecorder.
 *
 * Capturing the microphone is not Android-specific, so this really records:
 * {@code javax.sound.sampled} opens a {@link TargetDataLine} at the requested
 * sample rate and writes the capture to the output file, and
 * {@link #getMaxAmplitude} is computed from the samples as they arrive, so the
 * live waveform is the real signal rather than a placeholder.
 *
 * What the JDK does not have is an encoder. {@code setAudioEncoder(AAC)} and
 * {@code setOutputFormat(MPEG_4)} therefore cannot be honoured by the default
 * backend: the file is written as WAV/PCM and the substitution is reported,
 * because a recording silently labelled `audio/mp4` that is really a WAV is the
 * kind of thing that only shows up at the far end. A desktop build that wants
 * the requested format installs an {@link Encoder}.
 */
public class MediaRecorder {
    /** Installed by a desktop build that has a real encoder (ffmpeg, etc.). */
    public interface Encoder {
        /**
         * Starts encoding into {@code output}. Null when this encoder cannot
         * produce the requested format, which falls back to PCM/WAV.
         */
        Session start(File output, int outputFormat, int audioEncoder, int sampleRate, int bitRate);

        interface Session {
            /** One buffer of interleaved 16-bit mono PCM at the session's rate. */
            void write(byte[] pcm, int length);

            void finish() throws IOException;
        }
    }

    public static final class AudioSource {
        private AudioSource() {}

        public static final int DEFAULT = 0;
        public static final int MIC = 1;
        public static final int VOICE_RECOGNITION = 6;
        public static final int VOICE_COMMUNICATION = 7;
        public static final int UNPROCESSED = 9;
    }

    public static final class OutputFormat {
        private OutputFormat() {}

        public static final int DEFAULT = 0;
        public static final int THREE_GPP = 1;
        public static final int MPEG_4 = 2;
        public static final int AMR_NB = 3;
        public static final int AAC_ADTS = 6;
        public static final int OGG = 11;
    }

    public static final class AudioEncoder {
        private AudioEncoder() {}

        public static final int DEFAULT = 0;
        public static final int AMR_NB = 1;
        public static final int AAC = 3;
        public static final int HE_AAC = 4;
        public static final int AAC_ELD = 5;
        public static final int VORBIS = 6;
        public static final int OPUS = 7;
    }

    private static volatile Encoder encoder;

    public static void setEncoder(Encoder value) { encoder = value; }

    private static final int DEFAULT_SAMPLE_RATE = 44100;

    private int sampleRate = DEFAULT_SAMPLE_RATE;
    private int bitRate = 16 * DEFAULT_SAMPLE_RATE;
    private int outputFormat = OutputFormat.DEFAULT;
    private int audioEncoder = AudioEncoder.DEFAULT;
    private File output;

    private TargetDataLine line;
    private Thread pump;
    private Encoder.Session session;
    private volatile int maxAmplitude;
    private volatile boolean running;
    private java.io.ByteArrayOutputStream pcmBuffer;

    public MediaRecorder() {}

    public MediaRecorder(Context context) {}

    public void setAudioSource(int source) {}

    public void setAudioSamplingRate(int rate) { this.sampleRate = rate > 0 ? rate : DEFAULT_SAMPLE_RATE; }

    public void setAudioEncodingBitRate(int rate) { this.bitRate = rate; }

    public void setAudioChannels(int channels) {}

    public void setOutputFormat(int format) { this.outputFormat = format; }

    public void setAudioEncoder(int codec) { this.audioEncoder = codec; }

    public void setOutputFile(File file) { this.output = file; }

    public void setOutputFile(String path) { this.output = path == null ? null : new File(path); }

    /** Opens the microphone. Throws like the platform's when there is none. */
    public void prepare() throws IOException {
        if (output == null) throw new IOException("no output file");

        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new IOException("no microphone line for " + format);
        }
        try {
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
        } catch (Exception e) {
            throw new IOException("could not open the microphone", e);
        }

        Encoder installed = encoder;
        session = installed == null ? null : installed.start(output, outputFormat, audioEncoder, sampleRate, bitRate);
        if (session == null) {
            if (audioEncoder != AudioEncoder.DEFAULT || outputFormat != OutputFormat.DEFAULT) {
                PlatformGaps.report(
                        "MediaRecorder.encoder",
                        "the JDK has no AAC/MP4 encoder, so the recording is written as WAV/PCM; "
                                + "callers that label it by the requested format will mislabel it until "
                                + "a desktop encoder is installed through MediaRecorder.setEncoder");
            }
            pcmBuffer = new java.io.ByteArrayOutputStream();
        }
    }

    public void start() {
        if (line == null) throw new IllegalStateException("prepare() first");
        line.start();
        running = true;
        pump = new Thread(this::pump, "media-recorder");
        pump.setDaemon(true);
        pump.start();
    }

    public void stop() {
        if (!running && line == null) throw new IllegalStateException("not recording");
        running = false;
        if (line != null) line.stop();
        joinPump();

        try {
            if (session != null) {
                session.finish();
            } else if (pcmBuffer != null) {
                writeWav(pcmBuffer.toByteArray());
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not finish the recording", e);
        } finally {
            session = null;
            pcmBuffer = null;
        }
    }

    public void reset() { release(); }

    public void release() {
        running = false;
        joinPump();
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        session = null;
        pcmBuffer = null;
        maxAmplitude = 0;
    }

    /**
     * The loudest sample since the previous call, on the platform's 0..32767
     * scale — read and reset, exactly as Android's is.
     */
    public int getMaxAmplitude() {
        int value = maxAmplitude;
        maxAmplitude = 0;
        return value;
    }

    private void pump() {
        byte[] buffer = new byte[4096];
        while (running) {
            int read = line.read(buffer, 0, buffer.length);
            if (read <= 0) continue;
            trackAmplitude(buffer, read);
            if (session != null) {
                session.write(buffer, read);
            } else if (pcmBuffer != null) {
                pcmBuffer.write(buffer, 0, read);
            }
        }
    }

    /** Little-endian 16-bit signed mono, as the capture format declares. */
    private void trackAmplitude(byte[] buffer, int length) {
        int peak = maxAmplitude;
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            int magnitude = Math.abs(sample);
            if (magnitude > peak) peak = magnitude;
        }
        maxAmplitude = peak;
    }

    private void writeWav(byte[] pcm) throws IOException {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        File parent = output.getParentFile();
        if (parent != null) parent.mkdirs();
        try (AudioInputStream stream =
                new AudioInputStream(new java.io.ByteArrayInputStream(pcm), format, pcm.length / format.getFrameSize())) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, output);
        }
    }

    private void joinPump() {
        Thread thread = pump;
        pump = null;
        if (thread == null) return;
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
