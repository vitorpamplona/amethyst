package androidx.work;

import java.util.UUID;

/** JVM stand-in for androidx.work.WorkRequest. */
public abstract class WorkRequest {
    private final UUID id = UUID.randomUUID();

    public UUID getId() { return id; }
}
