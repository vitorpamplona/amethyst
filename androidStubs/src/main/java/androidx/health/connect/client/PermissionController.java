package androidx.health.connect.client;

import java.util.Collections;
import java.util.Set;

/** JVM stand-in for Health Connect's PermissionController. See HealthConnectClient. */
public interface PermissionController {
    default Set<String> getGrantedPermissions() { return Collections.emptySet(); }

    static Object createRequestPermissionResultContract() { return new Object(); }
}
