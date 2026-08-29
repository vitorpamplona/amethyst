package android.os;

/** JVM stand-in for android.os.Message. */
public final class Message {
    public int what;
    public int arg1;
    public int arg2;
    public Object obj;
    private Bundle data;

    public static Message obtain() { return new Message(); }

    public static Message obtain(Handler h, int what) {
        Message m = new Message();
        m.what = what;
        return m;
    }

    public Bundle getData() {
        if (data == null) data = new Bundle();
        return data;
    }

    public void setData(Bundle data) { this.data = data; }
}
