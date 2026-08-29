package android.content;

import java.util.ArrayList;
import java.util.List;

/** JVM stand-in for android.content.IntentFilter. Pure data, so implemented for real. */
public class IntentFilter {
    private final List<String> actions = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final List<String> schemes = new ArrayList<>();

    public IntentFilter() {}

    public IntentFilter(String action) { actions.add(action); }

    public void addAction(String action) { actions.add(action); }

    public void addCategory(String category) { categories.add(category); }

    public void addDataScheme(String scheme) { schemes.add(scheme); }

    public boolean hasAction(String action) { return actions.contains(action); }

    public int countActions() { return actions.size(); }

    public String getAction(int index) { return actions.get(index); }
}
