package android.net;

/** JVM stand-in for android.net.Network — an opaque handle to one connection. */
public final class Network {
    private final long handle;

    public Network(long handle) { this.handle = handle; }

    public long getNetworkHandle() { return handle; }

    @Override public boolean equals(Object other) {
        return other instanceof Network && ((Network) other).handle == handle;
    }

    @Override public int hashCode() { return Long.hashCode(handle); }

    @Override public String toString() { return "Network(" + handle + ")"; }
}
