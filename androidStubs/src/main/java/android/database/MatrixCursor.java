package android.database;

import java.util.ArrayList;
import java.util.List;

/**
 * JVM stand-in for android.database.MatrixCursor — an in-memory cursor.
 *
 * This is what a desktop ContentResolver answers a metadata query with: the
 * file's name and size really are the two columns callers ask for, so the query
 * returns them rather than null. A null cursor reads as "unknown size", which
 * makes an upload announce 0 bytes.
 */
public class MatrixCursor implements Cursor {
    private final String[] columnNames;
    private final List<Object[]> rows = new ArrayList<>();
    private int position = -1;

    public MatrixCursor(String[] columnNames) {
        this.columnNames = columnNames == null ? new String[0] : columnNames.clone();
    }

    public void addRow(Object[] values) { rows.add(values); }

    @Override public int getCount() { return rows.size(); }

    @Override public int getColumnCount() { return columnNames.length; }

    @Override public String getColumnName(int columnIndex) { return columnNames[columnIndex]; }

    @Override
    public int getColumnIndex(String columnName) {
        for (int i = 0; i < columnNames.length; i++) {
            if (columnNames[i].equals(columnName)) return i;
        }
        return -1;
    }

    @Override
    public boolean moveToFirst() {
        position = 0;
        return !rows.isEmpty();
    }

    @Override
    public boolean moveToNext() {
        position++;
        return position < rows.size();
    }

    @Override
    public String getString(int columnIndex) {
        Object v = value(columnIndex);
        return v == null ? null : String.valueOf(v);
    }

    @Override
    public int getInt(int columnIndex) {
        Object v = value(columnIndex);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    @Override
    public long getLong(int columnIndex) {
        Object v = value(columnIndex);
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    @Override public boolean isNull(int columnIndex) { return value(columnIndex) == null; }

    @Override public void close() {}

    private Object value(int columnIndex) {
        if (position < 0 || position >= rows.size()) return null;
        Object[] row = rows.get(position);
        return columnIndex < 0 || columnIndex >= row.length ? null : row[columnIndex];
    }
}
