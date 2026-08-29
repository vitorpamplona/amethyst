package android.content;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

/** JVM stand-in for android.content.ClipData. Pure data; the clipboard itself is ClipboardManager. */
public final class ClipData {
    public static final class Item {
        private final CharSequence text;
        private final Uri uri;

        public Item(CharSequence text) {
            this.text = text;
            this.uri = null;
        }

        public Item(Uri uri) {
            this.text = null;
            this.uri = uri;
        }

        public CharSequence getText() { return text; }

        public Uri getUri() { return uri; }
    }

    private final String label;
    private final List<Item> items = new ArrayList<>();

    private ClipData(String label, Item first) {
        this.label = label;
        items.add(first);
    }

    public static ClipData newPlainText(CharSequence label, CharSequence text) {
        return new ClipData(String.valueOf(label), new Item(text));
    }

    public static ClipData newUri(ContentResolver resolver, CharSequence label, Uri uri) {
        return new ClipData(String.valueOf(label), new Item(uri));
    }

    public String getLabel() { return label; }

    public int getItemCount() { return items.size(); }

    public Item getItemAt(int index) { return items.get(index); }

    public void addItem(Item item) { items.add(item); }
}
