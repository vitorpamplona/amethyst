package android.text;

/** JVM stand-in for android.text.InputType. Constants only. */
public final class InputType {
    public static final int TYPE_CLASS_TEXT = 0x00000001;
    public static final int TYPE_CLASS_NUMBER = 0x00000002;
    public static final int TYPE_CLASS_PHONE = 0x00000003;
    public static final int TYPE_TEXT_VARIATION_PASSWORD = 0x00000080;
    public static final int TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x00000020;
    public static final int TYPE_TEXT_VARIATION_URI = 0x00000010;
    public static final int TYPE_TEXT_FLAG_MULTI_LINE = 0x00020000;
    public static final int TYPE_TEXT_FLAG_CAP_SENTENCES = 0x00004000;
    public static final int TYPE_TEXT_FLAG_NO_SUGGESTIONS = 0x00080000;
    public static final int TYPE_NUMBER_FLAG_DECIMAL = 0x00002000;
    public static final int TYPE_NULL = 0x00000000;

    private InputType() {}
}
