package android.content;

import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;

/** JVM stand-in for android.content.ContentResolver. */
public abstract class ContentResolver {
    public abstract InputStream openInputStream(Uri uri);

    public abstract OutputStream openOutputStream(Uri uri);

    public abstract String getType(Uri uri);
}
