package android.media;

import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.nio.ByteBuffer;

/**
 * JVM stand-ins for the MediaCodec transcoding stack.
 *
 * These are the hardware codec APIs behind video compression and audio
 * re-encoding on upload. Desktop can absolutely transcode — :desktopApp
 * already bundles JCodec and shells out to ffmpeg — but that is a different
 * pipeline, not a reimplementation of MediaCodec's buffer protocol. So these
 * exist to compile, and every entry point reports rather than silently
 * producing nothing: an upload that quietly skips compression would ship a
 * 200 MB file, and one that quietly skips *stripping* would ship metadata.
 */
final class MediaCodecStubs {
    private MediaCodecStubs() {}

    static void unsupported(String api) {
        PlatformGaps.report(
                api,
                "desktop transcodes with JCodec/ffmpeg rather than MediaCodec; this path needs porting to that pipeline");
    }
}
