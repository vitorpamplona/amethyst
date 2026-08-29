package android.graphics.drawable;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

/**
 * JVM stand-in for android.graphics.drawable.Icon.
 *
 * A lazily-resolved icon reference. It keeps what it was built from — a
 * drawable id, a bitmap, or a uri — so a desktop presenter that wants to draw
 * one has something to resolve; what it cannot do is hand the reference to
 * another process, which is what the class exists for on Android.
 */
public final class Icon {
    private final int resourceId;
    private final Bitmap bitmap;
    private final Uri uri;

    private Icon(int resourceId, Bitmap bitmap, Uri uri) {
        this.resourceId = resourceId;
        this.bitmap = bitmap;
        this.uri = uri;
    }

    public static Icon createWithResource(Context context, int resourceId) {
        return new Icon(resourceId, null, null);
    }

    public static Icon createWithResource(String packageName, int resourceId) {
        return new Icon(resourceId, null, null);
    }

    public static Icon createWithBitmap(Bitmap bitmap) { return new Icon(0, bitmap, null); }

    public static Icon createWithContentUri(Uri uri) { return new Icon(0, null, uri); }

    public static Icon createWithContentUri(String uri) { return new Icon(0, null, Uri.parse(uri)); }

    /** The drawable id, or 0 when this icon is not resource-backed. */
    public int getResId() { return resourceId; }

    public Bitmap getBitmap() { return bitmap; }

    public Uri getUri() { return uri; }
}
