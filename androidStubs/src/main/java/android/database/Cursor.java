package android.database;

import java.io.Closeable;

/** JVM stand-in for android.database.Cursor: a positioned row reader. */
public interface Cursor extends Closeable {
    int getCount();

    int getColumnCount();

    String getColumnName(int columnIndex);

    /** -1 when the column is absent, as the platform's does. */
    int getColumnIndex(String columnName);

    boolean moveToFirst();

    boolean moveToNext();

    String getString(int columnIndex);

    int getInt(int columnIndex);

    long getLong(int columnIndex);

    boolean isNull(int columnIndex);

    @Override
    void close();
}
