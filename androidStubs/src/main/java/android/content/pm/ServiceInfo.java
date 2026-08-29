package android.content.pm;

/**
 * JVM stand-in for android.content.pm.ServiceInfo.
 *
 * Only the foreground-service type constants are used, and only to declare what
 * kind of foreground service to start — which desktop has no counterpart for.
 */
public class ServiceInfo {
    public static final int FOREGROUND_SERVICE_TYPE_MANIFEST = -1;
    public static final int FOREGROUND_SERVICE_TYPE_NONE = 0;
    public static final int FOREGROUND_SERVICE_TYPE_DATA_SYNC = 1;
    public static final int FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK = 2;
    public static final int FOREGROUND_SERVICE_TYPE_PHONE_CALL = 4;
    public static final int FOREGROUND_SERVICE_TYPE_LOCATION = 8;
    public static final int FOREGROUND_SERVICE_TYPE_MICROPHONE = 128;
    public static final int FOREGROUND_SERVICE_TYPE_CAMERA = 64;
    public static final int FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 1073741824;
}
