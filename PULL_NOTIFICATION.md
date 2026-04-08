# Always-On Notification Service

Amethyst's always-on notification service maintains persistent WebSocket connections to the
user's inbox relays and DM relays, ensuring real-time delivery of DMs, zaps, mentions, and
other notifications without depending on an external push server.

## Why

The existing push notification system (`push.amethyst.social`) can only monitor relays it
knows about. Private, paid, or obscure inbox relays get missed entirely. The only way to
guarantee 100% notification coverage is for the device itself to maintain connections to
the user's NIP-65 inbox relays and NIP-17 DM relays.

## Architecture

```
NIP-65 notification inbox relays ──WebSocket──┐
                                              ├──> [NotificationRelayService]
NIP-17 DM inbox relays ───────────WebSocket──┘           |
                                                         v
                                                  EventNotificationConsumer
                                                         |
                                                         v
                                                  Android Notification
```

The service shares the **same `NostrClient` instance** as the UI. This is the key design
decision. When the app is in the foreground, both the UI and the service are collecting
the `relayServices` flow. The `AccountFilterAssembler` subscription from the Compose UI
tree stays active as long as the Activity exists (even when stopped/backgrounded),
keeping notification and DM relay connections alive. When the app returns to the
foreground, the UI piggybacks on the already-open connections. **Zero reconnection, zero
dropped messages.**

```
BACKGROUND MODE (service running):
  inbox-relay-1 ──WebSocket──> [AccountFilterAssembler: notifications, metadata, follows]
  inbox-relay-2 ──WebSocket──> [AccountFilterAssembler: notifications, metadata, follows]
  dm-relay-1 ────WebSocket──> [AccountFilterAssembler: gift wraps]
  (outbox relays disconnected — no Home/Video/Discovery subscriptions)

APP FOREGROUNDS:
  inbox-relay-1 ──WebSocket──> [Same connection] <── Home/Discovery subs resume
  inbox-relay-2 ──WebSocket──> [Same connection] <── Home/Discovery subs resume
  dm-relay-1 ────WebSocket──> [Same connection]  <── ChatroomList subs resume
  outbox-relay-3 ──WebSocket──> [New connection]  <── Home/Video feed relay
```

## Subscription Architecture

### What the service does

The service does NOT create its own relay subscriptions. It only keeps the
`RelayProxyClientConnector` alive by collecting `relayServices`. The actual subscriptions
come from the `AccountFilterAssembler` in the Compose tree (`LoggedInPage`), which
stays active as long as the Activity exists and covers:

- **Metadata** — user profile, relay lists, mute lists, follows (via `AccountMetadataEoseManager`)
- **Follows** — follow list changes that affect notification filtering (via `AccountFollowsLoaderSubAssembler`)
- **Notifications** — mentions, zaps, reactions on NIP-65 inbox relays (via `AccountNotificationsEoseFromInboxRelaysManager`)
- **Gift wraps** — NIP-59 encrypted DMs on NIP-17 DM relays (via `AccountGiftWrapsEoseManager`)
- **Drafts** — draft events (via `AccountDraftsEoseManager`)

This is critical because notification filtering depends on follow lists, mute lists,
and relay configurations. If the service maintained its own isolated subscriptions,
it would miss follow list changes and display notifications from muted users.

### What pauses on background

Heavy feed subscriptions use `LifecycleAwareKeyDataSourceSubscription` which subscribes
on `ON_START` and unsubscribes on `ON_STOP`. When the app backgrounds:

| Subscription | Behavior | Why |
|-------------|----------|-----|
| `AccountFilterAssembler` | **Stays active** | Needed for notifications, DMs, follow/mute list changes |
| `HomeFilterAssembler` | **Pauses** | Home feed data with nobody viewing wastes bandwidth |
| `VideoFilterAssembler` | **Pauses** | Video feed data with nobody viewing wastes bandwidth |
| `DiscoveryFilterAssembler` | **Pauses** | Discovery feed data with nobody viewing wastes bandwidth |
| `ChatroomListFilterAssembler` | **Pauses** | Chatroom list updates with nobody viewing waste bandwidth |

When the feed subscriptions pause, the relay pool automatically disconnects outbox relays
that no longer have any active subscriptions. Only inbox and DM relays stay connected
(because `AccountFilterAssembler` still has active subscriptions on them).

## Foreground Service

`NotificationRelayService` is a foreground service with `specialUse` type:

- **No time limit**: Unlike `dataSync` (6-hour limit on Android 15), `specialUse` has no
  timeout restriction. **Why it matters:** A notification service must run indefinitely.
  The `dataSync` type would force the service to stop after 6 cumulative hours per 24-hour
  period, making it useless for always-on notifications.
- **BOOT_COMPLETED safe**: Can be started from boot receivers on Android 15+, unlike
  `dataSync` which is restricted. **Why it matters:** Without this, the service couldn't
  auto-restart after a reboot on modern Android.
- **START_STICKY**: Android will restart the service if it's killed by the system.
- **Persistent notification**: Shows "Connected to N inbox relays" with a Pause action.
  Uses `IMPORTANCE_LOW` so it's silent and unobtrusive.

### ForegroundServiceStartNotAllowedException

On Android 12+, starting a foreground service from the background can throw
`ForegroundServiceStartNotAllowedException`. The service catches this gracefully and stops
itself rather than crashing the app.

**Why it matters:** Without this catch, if the watchdog alarm or WorkManager tries to
restart the service while the app lacks the background-start exemption (e.g., battery
optimization is active), the app would crash with an unhandled exception.

### Redundant startForeground()

`startForeground()` is called from both `onCreate()` and `onStartCommand()` as a safety
net. In rare edge cases, `onStartCommand()` can fire before `onCreate()` completes
(observed in ntfy issue #1520). The `foregroundStarted` flag prevents double invocation.

**Why it matters:** If `startForeground()` isn't called within 5 seconds of
`startForegroundService()`, the app crashes with an ANR. The redundant call ensures the
foreground notification is posted regardless of which lifecycle method runs first.

## 8-Layer Auto-Restart Defense

Android (and OEM battery optimizers) will aggressively try to kill background services.
The notification service uses 8 independent mechanisms to stay alive. Each addresses a
specific kill vector that the others don't cover:

### Layer 1: START_STICKY

**What:** When Android kills the service due to memory pressure, `START_STICKY` tells the
system to recreate it with a null intent.

**Why needed:** This is the baseline restart mechanism provided by Android. However, it's
unreliable in practice — many OEMs (Xiaomi MIUI, Huawei EMUI, Samsung One UI, Oppo
ColorOS) override this behavior and prevent sticky service restarts. That's why we need
the remaining 7 layers.

### Layer 2: onTaskRemoved() Alarm

**What:** When the user swipes the app from recents, schedules a 1-second alarm to restart
the service.

**Why needed:** On stock Android, swiping from recents only removes the task but leaves
the foreground service running. However, many OEMs treat swipe-from-recents as a force
stop, killing the foreground service. `START_STICKY` won't help because some OEMs block
sticky restarts after a task removal. The alarm bypasses this by scheduling the restart
through `AlarmManager`, which is a separate system that OEM modifications rarely touch.

### Layer 3: onDestroy() Broadcast

**What:** When the service is destroyed for any reason, it broadcasts to
`AutoRestartReceiver`, which enqueues a one-time WorkManager task with a network
connectivity constraint.

**Why needed:** This catches the gap between `START_STICKY` and `onTaskRemoved()`.
If the system kills the service during normal operation (not from recents), `START_STICKY`
should restart it — but if the OEM blocks that restart, the broadcast fires a WorkManager
task as a backup. WorkManager is harder for OEMs to suppress because it's part of
Google Play Services infrastructure.

### Layer 4: AlarmManager Watchdog (5 minutes)

**What:** `ServiceWatchdogManager` fires an `ELAPSED_REALTIME_WAKEUP` alarm every 5
minutes. The receiver checks if the service should be running and restarts it.

**Why needed:** This is the "belt and suspenders" layer. If all of the above layers fail
(sticky restart blocked, alarm from `onTaskRemoved` didn't fire, broadcast wasn't
delivered), the watchdog will catch it within 5 minutes. Uses `ELAPSED_REALTIME_WAKEUP` to
wake the device from sleep, ensuring the check happens even in Doze.

### Layer 5: WorkManager Periodic Catch-Up (15 minutes)

**What:** Runs every 15 minutes with a network connectivity constraint. Ensures relay
connections are active and restarts the foreground service if needed.

**Why needed:** WorkManager survives process death and device reboots — it's the most
persistent scheduling mechanism on Android. Even if the app process is completely dead,
WorkManager (backed by JobScheduler) will eventually wake it. The 15-minute interval is
WorkManager's minimum, ensuring regular catch-up even if the foreground service has been
dead for a while.

### Layer 6: Network-Available One-Time Worker

**What:** When `AutoRestartReceiver` fires, it enqueues a one-time WorkManager task that
runs as soon as network connectivity is available.

**Why needed:** If the service dies during a network outage, there's no point restarting it
immediately (the relays won't connect). This worker waits for connectivity and restarts
then, rather than waiting up to 15 minutes for the next periodic worker. This is especially
important after airplane mode, tunnel/elevator scenarios, or switching between WiFi and
cellular.

### Layer 7: Boot and Package Receivers

**What:** `BootCompletedReceiver` restarts the service after device reboot
(`BOOT_COMPLETED`, `QUICKBOOT_POWERON`) and app update (`MY_PACKAGE_REPLACED`).

**Why needed:** After a reboot, no services are running — `START_STICKY` doesn't apply
across reboots. The boot receiver is the only way to restart. After an app update, the
old process is killed and the new version's services don't auto-start. Without
`MY_PACKAGE_REPLACED`, users would need to manually open the app after every Play Store
update to restore notifications.

### Layer 8: FCM / UnifiedPush (existing)

**What:** The existing push notification system continues to work alongside the always-on
service.

**Why needed:** FCM is the only mechanism that survives a force stop (because Google Play
Services handles delivery outside the app's process). If the user explicitly force-stops
Amethyst from Settings, all 7 layers above are disabled. Only FCM can still deliver
notifications until the user opens the app again.

## WakeLock During Notification Processing

`EventNotificationConsumer` acquires a `PARTIAL_WAKE_LOCK` with a 10-minute timeout when
processing incoming notifications.

**Why needed:** When a notification event arrives from a relay, the app needs to decrypt
NIP-59 gift wraps, verify signatures, look up accounts, resolve display names, load
profile pictures, and construct the Android notification. In Doze mode, the CPU can sleep
between alarm windows. Without a WakeLock, the CPU could sleep mid-processing, causing the
notification to be delayed or lost. The 10-minute timeout is generous to handle slow
decryption (especially with external signers) while preventing indefinite wake locks from
battery drain.

## Battery Optimization Exemption

The service works best when the app is exempted from Android's battery optimizations (Doze).

**Why needed:** Even with a foreground service, Android can restrict network access during
Doze maintenance windows. The battery optimization exemption tells Android that this app's
network activity is user-expected and should not be deferred. Without it, relay connections
may be broken during Doze, causing missed notifications that only arrive when the device
exits Doze (which can be hours for a stationary, charging device).

`BatteryOptimizationHelper` checks the exemption status and provides a method to launch
the system settings dialog. When the always-on service is enabled but the app isn't
whitelisted, the settings screen shows a warning banner with a "Fix now" button.

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
| `NotificationRelayService.kt` | Foreground service, keeps relay connections alive, auto-restart |
| `LifecycleAwareKeyDataSourceSubscription.kt` | Pauses heavy feed subs when app backgrounds |
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

- **ntfy** (millions of users) — `onTaskRemoved()` alarm, `onDestroy()` broadcast restart,
  `ForegroundServiceStartNotAllowedException` handling, redundant `startForeground()`,
  battery optimization guidance, WakeLock during processing
- **Pokey** (Nostr notification app) — `specialUse` foreground service type,
  `MY_PACKAGE_REPLACED` restart
- **Signal** — Hybrid FCM + persistent WebSocket architecture
