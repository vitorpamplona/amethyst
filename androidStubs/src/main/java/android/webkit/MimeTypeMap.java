package android.webkit;

import java.net.URLConnection;
import java.util.Locale;

/**
 * JVM stand-in for android.webkit.MimeTypeMap, backed by the JDK's own
 * content-type table plus the handful of media types it predates.
 */
public final class MimeTypeMap {
    private static final MimeTypeMap INSTANCE = new MimeTypeMap();

    private MimeTypeMap() {}

    public static MimeTypeMap getSingleton() { return INSTANCE; }

    public static String getFileExtensionFromUrl(String url) {
        if (url == null) return "";
        String path = url.split("[?#]", 2)[0];
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        return (dot > slash && dot >= 0) ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    public String getMimeTypeFromExtension(String extension) {
        if (extension == null) return null;
        switch (extension.toLowerCase(Locale.ROOT)) {
            case "webp": return "image/webp";
            case "avif": return "image/avif";
            case "heic": return "image/heic";
            case "mp4": return "video/mp4";
            case "webm": return "video/webm";
            case "mov": return "video/quicktime";
            case "m3u8": return "application/x-mpegURL";
            case "opus": return "audio/opus";
            case "m4a": return "audio/mp4";
            case "flac": return "audio/flac";
            default: return URLConnection.guessContentTypeFromName("file." + extension);
        }
    }

    public String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) return null;
        int slash = mimeType.indexOf('/');
        return slash >= 0 ? mimeType.substring(slash + 1) : null;
    }

    public boolean hasExtension(String extension) { return getMimeTypeFromExtension(extension) != null; }
}
