package android.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.net.Uri;
import android.os.Environment;
import java.io.File;
import org.junit.jupiter.api.Test;

/**
 * Saving a download has to land somewhere a file manager will show it. A
 * collection that mapped to the wrong directory, or to none, is the difference
 * between "saved to your gallery" and a file the user never finds.
 */
class MediaStoreTest {
    @Test
    void eachCollectionMapsToItsUserDirectory() {
        assertEquals(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                MediaStore.directoryFor(MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
        assertEquals(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                MediaStore.directoryFor(MediaStore.Video.Media.EXTERNAL_CONTENT_URI));
        assertEquals(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                MediaStore.directoryFor(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI));
        assertEquals(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                MediaStore.directoryFor(MediaStore.Downloads.EXTERNAL_CONTENT_URI));
    }

    @Test
    void audioAndVideoAreNotTheSamePlace() {
        File audio = MediaStore.directoryFor(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        File video = MediaStore.directoryFor(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        assertNotNull(audio);
        assertNotNull(video);
        assertTrue(!audio.equals(video), "audio routed into the video folder is the bug this replaced");
    }

    @Test
    void anUnknownCollectionHasNoGuessedLocation() {
        assertNull(MediaStore.directoryFor(Uri.parse("content://media/external/something/else")));
        assertNull(MediaStore.directoryFor(null));
    }
}
