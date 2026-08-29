package android.net;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JVM stand-in for android.net.Uri.
 *
 * Carries a real implementation rather than delegating: a URI is pure data with
 * no platform behaviour behind it. Android's Uri is deliberately lenient — it
 * does not throw on input java.net.URI would reject — so parsing falls back to
 * keeping the raw string rather than failing, matching what callers expect.
 */
public final class Uri implements Comparable<Uri> {
    private final String raw;
    private final URI parsed;

    private Uri(String raw) {
        this.raw = raw;
        URI p;
        try {
            p = new URI(raw);
        } catch (URISyntaxException e) {
            p = null;
        }
        this.parsed = p;
    }

    public static Uri parse(String uriString) {
        return uriString == null ? null : new Uri(uriString);
    }

    public static Uri fromFile(File file) {
        return new Uri(file.toURI().toString());
    }

    public static Uri fromParts(String scheme, String ssp, String fragment) {
        return new Uri(scheme + ":" + ssp + (fragment == null ? "" : "#" + fragment));
    }

    public String getScheme() {
        return parsed != null ? parsed.getScheme() : null;
    }

    public String getHost() {
        return parsed != null ? parsed.getHost() : null;
    }

    public int getPort() {
        return parsed != null ? parsed.getPort() : -1;
    }

    public String getPath() {
        return parsed != null ? parsed.getPath() : null;
    }

    public String getQuery() {
        return parsed != null ? parsed.getQuery() : null;
    }

    public String getFragment() {
        return parsed != null ? parsed.getFragment() : null;
    }

    public String getAuthority() {
        return parsed != null ? parsed.getAuthority() : null;
    }

    public String getSchemeSpecificPart() {
        return parsed != null ? parsed.getSchemeSpecificPart() : null;
    }

    public String getLastPathSegment() {
        List<String> segments = getPathSegments();
        return segments.isEmpty() ? null : segments.get(segments.size() - 1);
    }

    public List<String> getPathSegments() {
        String path = getPath();
        if (path == null || path.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) out.add(part);
        }
        return Collections.unmodifiableList(out);
    }

    public String getQueryParameter(String key) {
        String query = getQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) return pair.substring(eq + 1);
        }
        return null;
    }

    public Set<String> getQueryParameterNames() {
        String query = getQuery();
        if (query == null) return Collections.emptySet();
        Set<String> out = new LinkedHashSet<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            out.add(eq > 0 ? pair.substring(0, eq) : pair);
        }
        return Collections.unmodifiableSet(out);
    }

    public boolean isAbsolute() {
        return getScheme() != null;
    }

    public boolean isHierarchical() {
        return parsed != null && !parsed.isOpaque();
    }

    @Override
    public String toString() {
        return raw;
    }

    @Override
    public int compareTo(Uri other) {
        return raw.compareTo(other.raw);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Uri && raw.equals(((Uri) other).raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }
}
