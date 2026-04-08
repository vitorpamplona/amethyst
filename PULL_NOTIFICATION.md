# Always-On Notification Service

Amethyst's always-on notification service maintains persistent WebSocket connections to the
user's inbox relays, ensuring real-time delivery of DMs, zaps, mentions, and other
notifications without depending on an external push server.

## Why

The existing push notification system (`push.amethyst.social`) can only monitor relays it
knows about. Private, paid, or obscure inbox relays get missed entirely. The only way to
guarantee 100% notification coverage is for the device itself to maintain connections to
the user's NIP-65 inbox relays.

## Architecture

```
User's inbox relays <──WebSocket──> [NotificationRelayService]
                                          |
                                          v
                                   EventNotificationConsumer
                                          |
                                          v
                                   Android Notification
```

The service shares the **same `NostrClient` instance** as the UI. This is the key design
decision. When the app is in the foreground, both the UI and the service are collecting
the `relayServices` flow. When the app backgrounds, UI subscriptions drop but the service
keeps collecting, so inbox relay connections stay alive. When the app returns to the
foreground, the UI piggybacks on the already-open connections. **Zero reconnection, zero
dropped messages.**

```
BACKGROUND MODE:
  inbox-relay-1 ──WebSocket──> [Service keeps collecting relayServices flow]
  inbox-relay-2 ──WebSocket──> [Service keeps collecting relayServices flow]

APP OPENS:
  inbox-relay-1 ──WebSocket──> [Same connection] <── UI adds feed subscriptions
  inbox-relay-2 ──WebSocket──> [Same connection] <── UI adds feed subscriptions
  outbox-relay-3 ──WebSocket──> [New connection]  <── feed-only relay
```

## Foreground Service

`NotificationRelayService` is a foreground service with `specialUse` type:

- **No time limit**: Unlike `dataSync` (6-hour limit on Android 15), `specialUse` has no
  timeout restriction.
- **BOOT_COMPLETED safe**: Can be started from boot receivers on Android 15+, unlike
  `dataSync` which is restricted.
- **START_STICKY**: Android will restart the service if it's killed by the system.
- **Persistent notification**: Shows "Connected to N inbox relays" with a Pause action.
  Uses `IMPORTANCE_LOW` so it's silent and unobtrusive.

### ForegroundServiceStartNotAllowedException

On Android 12+, starting a foreground service from the background can throw
`ForegroundServiceStartNotAllowedException`. The service catches this gracefully and stops
itself rather than crashing the app.

### Redundant startForeground()

`startForeground()` is called from both `onCreate()` and `onStartCommand()` as a safety
net. In rare edge cases, `onStartCommand()` can fire before `onCreate()` completes
(observed in ntfy issue #1520). The `foregroundStarted` flag prevents double invocation.

## 8-Layer Auto-Restart Defense

Android (and OEM battery optimizers) will aggressively try to kill background services.
The notification service uses 8 independent mechanisms to stay alive:

### Layer 1: START_STICKY

```kotlin
override fun onStartCommand(...): Int {
    ...
    return START_STICKY
}
```

When Android kills the service due to memory pressure, `START_STICKY` tells the system
to recreate it. The service will receive a new `onStartCommand()` call with a null intent.

### Layer 2: onTaskRemoved() Alarm

```kotlin
override fun onTaskRemoved(rootIntent: Intent?) {
    val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
    alarmManager.set(
        AlarmManager.ELAPSED_REALTIME,
        SystemClock.elapsedRealtime() + 1000,
        pendingIntent,
    )
}
```

When the user swipes the app from recents, some OEMs (Xiaomi, Huawei, Samsung) kill the
foreground service. This schedules a 1-second alarm to restart the service immediately.
Without this, the service would stay dead until the next watchdog or WorkManager cycle.

### Layer 3: onDestroy() Broadcast

```kotlin
override fun onDestroy() {
    sendBroadcast(Intent(this, AutoRestartReceiver::class.java))
}
```

When the service is destroyed for any reason, it broadcasts to `AutoRestartReceiver`,
which enqueues a one-time WorkManager task with a network connectivity constraint. This
catches kills that `START_STICKY` might miss (e.g., OEM battery optimizers that prevent
service restart).

### Layer 4: AlarmManager Watchdog (5 minutes)

```kotlin
alarmManager.setInexactRepeating(
    AlarmManager.ELAPSED_REALTIME_WAKEUP,
    SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS,
    WATCHDOG_INTERVAL_MS,  // 5 minutes
    pendingIntent,
)
```

`ServiceWatchdogManager` fires an alarm every 5 minutes. The `WatchdogReceiver` checks
if the service should be running and restarts it if needed. Uses
`ELAPSED_REALTIME_WAKEUP` to wake the device from sleep.

### Layer 5: WorkManager Periodic Catch-Up (15 minutes)

```kotlin
PeriodicWorkRequestBuilder<NotificationCatchUpWorker>(
    15, TimeUnit.MINUTES,
).setConstraints(networkConstraint).build()
```

`NotificationCatchUpWorker` runs every 15 minutes (WorkManager's minimum interval) with
a network connectivity constraint. It collects `relayServices` to ensure connections are
active, waits briefly for events to flow, and also restarts the foreground service if
it's supposed to be running but isn't.

### Layer 6: Network-Available One-Time Worker

```kotlin
OneTimeWorkRequestBuilder<NotificationCatchUpWorker>()
    .setConstraints(networkConnectedConstraint)
    .build()
```

When the `AutoRestartReceiver` fires, it enqueues a one-time WorkManager task that runs
as soon as network connectivity is available. This means the service restarts immediately
when the device comes back online, rather than waiting up to 15 minutes for the periodic
worker.

### Layer 7: Boot and Package Receivers

```kotlin
when (intent.action) {
    Intent.ACTION_BOOT_COMPLETED,
    "android.intent.action.QUICKBOOT_POWERON",
    Intent.ACTION_MY_PACKAGE_REPLACED,
    -> NotificationRelayService.start(context)
}
```

`BootCompletedReceiver` restarts the service after:
- **Device reboot** (`BOOT_COMPLETED`, `QUICKBOOT_POWERON`)
- **App update** (`MY_PACKAGE_REPLACED`) — without this, the service stays dead after
  Play Store updates until the user manually opens the app

### Layer 8: FCM / UnifiedPush (existing)

The existing push notification system (`PushNotificationReceiverService` for Play,
`PushMessageReceiver` for F-Droid) continues to work as a complementary wakeup mechanism.
Push notifications from the server can wake the app process even if all other layers fail.

## WakeLock During Notification Processing

```kotlin
private inline fun <T> withWakeLock(block: () -> T): T {
    val wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "amethyst:notification_processing",
    )
    wakeLock.acquire(10 * 60 * 1000L)  // 10-minute timeout
    try {
        return block()
    } finally {
        if (wakeLock.isHeld) wakeLock.release()
    }
}
```

`EventNotificationConsumer` acquires a `PARTIAL_WAKE_LOCK` with a 10-minute timeout when
processing incoming notifications. This ensures the CPU stays awake long enough to
decrypt NIP-59 gift wraps, verify signatures, resolve accounts, and dispatch the Android
notification — even if the device is in Doze mode.

## Battery Optimization Exemption

The service works best when the app is exempted from Android's battery optimizations
(Doze). Without the exemption, Android may restrict network access and defer alarms even
for foreground services.

`BatteryOptimizationHelper` checks the exemption status and provides a method to launch
the system settings dialog:

```kotlin
BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
BatteryOptimizationHelper.requestBatteryOptimizationExemption(context)
```

When the always-on service is enabled but the app isn't whitelisted, the settings screen
shows a warning banner with a "Fix now" button that opens the system battery optimization
settings directly.

Messaging apps are explicitly listed as a valid use case for this exemption in Google Play
policy.

## Coordinator

`AlwaysOnNotificationServiceManager` watches the account's `alwaysOnNotificationService`
setting (a `MutableStateFlow<Boolean>`) and activates or deactivates all layers in
response:

```
Setting ON  → Start foreground service + Schedule WorkManager + Schedule watchdog alarm
Setting OFF → Stop foreground service + Cancel WorkManager + Cancel watchdog alarm
```

The manager is initialized in `AppModules` and watches the account state. When a user
logs in, it starts watching their setting. When they log out, it stops.

## Settings

The always-on notification service is **opt-in** (off by default). Users enable it from
**App Settings** with a toggle switch. The setting is persisted per-account in
`AccountSettings.alwaysOnNotificationService` and saved to `EncryptedSharedPreferences`.

## Files

| File | Purpose |
|------|---------|
| `NotificationRelayService.kt` | Foreground service, connection lifecycle, auto-restart |
| `BootCompletedReceiver.kt` | Restart on boot and app update |
| `AutoRestartReceiver.kt` | Restart via WorkManager when service is destroyed |
| `NotificationCatchUpWorker.kt` | Periodic and on-demand catch-up worker |
| `ServiceWatchdogManager.kt` | AlarmManager-based health monitor |
| `AlwaysOnNotificationServiceManager.kt` | Coordinates all layers based on setting |
| `BatteryOptimizationHelper.kt` | Battery optimization check and exemption request |
| `EventNotificationConsumer.kt` | WakeLock wrapper for notification processing |
| `AccountSettings.kt` | `alwaysOnNotificationService` setting |
| `LocalPreferences.kt` | Setting persistence |
| `AppSettingsScreen.kt` | Toggle UI and battery optimization banner |
| `AndroidManifest.xml` | Permissions, service, and receiver declarations |

## Permissions

| Permission | Purpose |
|------------|---------|
| `FOREGROUND_SERVICE` | Run the foreground service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Declare `specialUse` service type |
| `RECEIVE_BOOT_COMPLETED` | Restart on boot |
| `WAKE_LOCK` | Keep CPU awake during notification processing |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Request Doze exemption |

## Inspiration

This implementation draws from battle-tested patterns in:

- **ntfy** — `onTaskRemoved()` alarm, `onDestroy()` broadcast restart,
  `ForegroundServiceStartNotAllowedException` handling, redundant `startForeground()`,
  battery optimization guidance, WakeLock during processing
- **Pokey** — `specialUse` foreground service type, `MY_PACKAGE_REPLACED` restart
- **Signal** — Hybrid FCM + persistent WebSocket architecture
