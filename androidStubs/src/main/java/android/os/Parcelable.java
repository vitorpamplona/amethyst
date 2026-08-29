package android.os;

/**
 * JVM stand-in for android.os.Parcelable.
 *
 * Parcelable exists to move objects across a process boundary. Desktop is one
 * process, so nothing here marshals anything; the interface exists so types
 * that declare it compile.
 */
public interface Parcelable {
    int CONTENTS_FILE_DESCRIPTOR = 1;

    int describeContents();

    void writeToParcel(Parcel dest, int flags);

    interface Creator<T> {
        T createFromParcel(Parcel source);

        T[] newArray(int size);
    }
}
