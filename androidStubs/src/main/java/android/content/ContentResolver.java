package android.content;

import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * JVM stand-in for android.content.ContentResolver.
 *
 * Reads and writes are abstract because only the platform knows where a URI
 * points. Insert and delete have defaults that refuse rather than pretend: an
 * insert that returned a URI nothing was ever written to would make "saved to
 * your gallery" appear over a file that does not exist.
 */
public abstract class ContentResolver {
    public abstract InputStream openInputStream(Uri uri);

    public abstract OutputStream openOutputStream(Uri uri);

    public abstract String getType(Uri uri);

    /** Null means the collection is not one this platform can write to. */
    public Uri insert(Uri collection, ContentValues values) { return null; }

    /** The number of rows removed. */
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    public OutputStream openOutputStream(Uri uri, String mode) { return openOutputStream(uri); }

    /**
     * Metadata about the thing a URI points at. Null means "nothing known",
     * which callers read as an unknown size — so a platform that can answer
     * should, rather than leaving an upload to announce 0 bytes.
     */
    public android.database.Cursor query(
            Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }
}
