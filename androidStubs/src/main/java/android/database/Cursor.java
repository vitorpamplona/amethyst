package android.database;

import java.io.Closeable;

/** JVM stand-in for android.database.Cursor: a positioned row reader. */
public interface Cursor extends Closeable {
    int getCount();

    int getColumnCount();

    String getColumnName(int columnIndex);

    /** -1 when the column is absent, as the platform's does. */
    int getColumnIndex(String columnName);

    /**
     * Throws for an absent column, where {@link #getColumnIndex} returns -1.
     * The upload path uses this deliberately: a display name it cannot read is
     * a bug to surface, not a filename to guess at.
     */
    default int getColumnIndexOrThrow(String columnName) {
        int index = getColumnIndex(columnName);
        if (index < 0) throw new IllegalArgumentException("column '" + columnName + "' does not exist");
        return index;
    }

    boolean moveToFirst();

    boolean moveToNext();

    String getString(int columnIndex);

    int getInt(int columnIndex);

    long getLong(int columnIndex);

    boolean isNull(int columnIndex);

    @Override
    void close();
}
