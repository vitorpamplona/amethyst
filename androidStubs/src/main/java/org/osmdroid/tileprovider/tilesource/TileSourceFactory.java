package org.osmdroid.tileprovider.tilesource;

/**
 * JVM stand-in for osmdroid's TileSourceFactory.
 *
 * Named tile sources are just URL templates, so they survive the port intact —
 * a desktop renderer fetches the same tiles from the same servers.
 */
public final class TileSourceFactory {
    public static final Object MAPNIK = new NamedTileSource("Mapnik", "https://tile.openstreetmap.org/");
    public static final Object OpenTopo = new NamedTileSource("OpenTopo", "https://tile.opentopomap.org/");

    private TileSourceFactory() {}

    public static final class NamedTileSource {
        private final String name;
        private final String baseUrl;

        NamedTileSource(String name, String baseUrl) {
            this.name = name;
            this.baseUrl = baseUrl;
        }

        public String name() { return name; }

        public String baseUrl() { return baseUrl; }
    }
}
