package android.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageDecoderTest {
    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF112233);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void decodesRealBytesToARealBitmap() throws IOException {
        Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(png(4, 3)));
        assertNotNull(bitmap);
        assertEquals(4, bitmap.getWidth());
        assertEquals(3, bitmap.getHeight());
    }

    @Test
    void decodesFromAByteBufferWithoutConsumingIt() throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(png(2, 2));
        assertNotNull(ImageDecoder.decodeBitmap(ImageDecoder.createSource(buffer)));
        // Reading it twice has to work: the caller may retry.
        assertNotNull(ImageDecoder.decodeBitmap(ImageDecoder.createSource(buffer)));
    }

    @Test
    void theHeaderListenerSeesTheRealSize() throws IOException {
        AtomicReference<ImageDecoder.Size> seen = new AtomicReference<>();
        ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(png(5, 7)),
                (decoder, info, source) -> {
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE;
                    seen.set(info.getSize());
                });

        assertNotNull(seen.get());
        assertEquals(5, seen.get().getWidth());
        assertEquals(7, seen.get().getHeight());
    }

    @Test
    void anUnreadableFormatDecodesToNullRatherThanABlankBitmap() {
        // A blank bitmap here would put an empty thumbnail on the post.
        assertNull(ImageDecoder.decodeBitmap(ImageDecoder.createSource(new byte[] {1, 2, 3, 4})));
        assertNull(ImageDecoder.decodeBitmap(ImageDecoder.createSource(new byte[0])));
    }
}
