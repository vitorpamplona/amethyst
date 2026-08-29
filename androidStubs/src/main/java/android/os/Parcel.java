package android.os;

/** JVM stand-in for android.os.Parcel. See Parcelable: nothing is marshalled on desktop. */
public final class Parcel {
    private Parcel() {}

    public static Parcel obtain() { return new Parcel(); }

    public void recycle() {}

    public void writeString(String value) {}

    public void writeInt(int value) {}

    public void writeLong(long value) {}

    public String readString() { return null; }

    public int readInt() { return 0; }

    public long readLong() { return 0L; }
}
