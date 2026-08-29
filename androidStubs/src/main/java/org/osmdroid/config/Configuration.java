package org.osmdroid.config;

import java.io.File;

/** JVM stand-in for osmdroid's Configuration singleton. */
public final class Configuration {
    private static final Configuration INSTANCE = new Configuration();

    private String userAgentValue = "Amethyst";
    private File osmdroidBasePath = new File(System.getProperty("java.io.tmpdir"), "osmdroid");

    private Configuration() {}

    public static Configuration getInstance() { return INSTANCE; }

    public String getUserAgentValue() { return userAgentValue; }

    public void setUserAgentValue(String value) { userAgentValue = value; }

    public File getOsmdroidBasePath() { return osmdroidBasePath; }

    public void setOsmdroidBasePath(File value) { osmdroidBasePath = value; }

    public File getOsmdroidTileCache() { return new File(osmdroidBasePath, "tiles"); }

    public void setOsmdroidTileCache(File value) {}

    public void load(Object context, Object preferences) {}

    public void save(Object context, Object preferences) {}
}
