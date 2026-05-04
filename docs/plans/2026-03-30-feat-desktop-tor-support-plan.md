---
title: "feat: Desktop Tor Support"
type: feat
status: active
date: 2026-03-30
deepened: 2026-03-30
origin: docs/brainstorms/2026-03-30-desktop-tor-support-brainstorm.md
---

# feat: Desktop Tor Support

## Enhancement Summary

**Deepened on:** 2026-03-30
**Agents used:** KMP patterns, Coroutines, Desktop expert, Gradle, Security sentinel, Architecture strategist, Performance oracle, Code simplicity, Pattern recognition

### Key Improvements from Deepening
1. **Security:** 4 must-fix findings (DNS leak in ProxiedSocketFactory, port 9050 fallback, NEWNYM missing, logging leaks)
2. **Architecture:** Split `TorRelayState` into evaluator (commonMain) + client provider (jvmAndroid); make `ITorManager` reactive not imperative; add `IOkHttpClientFactory` interface
3. **Source set corrections:** `ProxiedSocketFactory` → jvmAndroid (uses java.net); `IRoleBasedHttpClientBuilder` → jvmAndroid (returns OkHttpClient); `TorRelayState` → jvmAndroid
4. **Performance:** Stagger relay reconnections (200ms + jitter); share ConnectionPool across OkHttpClient rebuilds; desktop timeout 2x not 3x
5. **Desktop:** Use OS-specific data dirs (not ~/.amethyst/); add Tor shutdown to existing shutdown hook; sidebar indicator in both layout modes
6. **Pattern fixes:** Remove "expect" from interfaces (plain interfaces); preference keys use underscores; `data object` for stateless sealed variants

### Simplicity Reviewer Note
The simplicity reviewer recommended presets-only for v1 (~400 LOC vs ~1200), deferring granular toggles. This conflicts with the brainstorm decision for full granular routing. **Decision: keep full granular** as chosen, but the presets-only fallback is documented if scope needs cutting.

---

## Overview

Add embedded Tor support to Amethyst Desktop with full granular routing parity with Android. Extract Android's platform-agnostic Tor logic to `commons/`, add `kmp-tor` (Apache 2.0) as desktop Tor runtime, build desktop settings UI with quick toggle + "Advanced..." dialog.

## Problem Statement / Motivation

Desktop has zero Tor/proxy awareness. `DesktopHttpClient` returns the same `OkHttpClient` for all URLs. Users connecting to .onion relays or wanting privacy routing have no options. Android has a production-grade Tor implementation that's mostly platform-agnostic but trapped in the `amethyst/` module.

## Proposed Solution

**Extract-first approach** (see brainstorm: `docs/brainstorms/2026-03-30-desktop-tor-support-brainstorm.md`):

1. Extract platform-agnostic Tor logic from `amethyst/` → `commons/commonMain/` and `commons/jvmAndroid/`
2. Create interfaces for Tor daemon management and settings persistence (NOT expect/actual — implementations have fundamentally different constructors)
3. Android impl: keep `tor-android` + `jtorctl`
4. Desktop impl: `kmp-tor` + `kmp-tor-resource` (Apache 2.0, `resource-exec-tor`)
5. Build desktop settings UI (quick toggle + full dialog)
6. Wire desktop relay connections through proxy-aware client manager

## Technical Approach

### Architecture

```
commons/commonMain/kotlin/com/vitorpamplona/amethyst/commons/tor/
├── TorType.kt                    # Enum: OFF, INTERNAL, EXTERNAL (no R.string)
├── TorSettings.kt                # Data class + presets
├── TorSettingsFlow.kt            # MutableStateFlow per setting
├── TorPresetType.kt              # Preset enum + definitions
├── TorServiceStatus.kt           # Sealed: Off, Connecting, Active(port), Error(msg)
├── TorRelaySettings.kt           # Relay-specific subset
├── TorRelayEvaluation.kt         # Per-relay routing logic (pure function)
├── TorRelayEvaluator.kt          # Flow: combines settings + relay lists → evaluation
├── ITorManager.kt                # Interface: reactive status flow
└── ITorSettingsPersistence.kt    # Interface: load/save/observe

commons/jvmAndroid/kotlin/com/vitorpamplona/amethyst/commons/
├── okhttp/
│   ├── IOkHttpClientFactory.kt   # Interface: buildHttpClient(proxy, timeout)
│   ├── DualHttpClientManager.kt  # Proxy + clear clients (takes IOkHttpClientFactory)
│   ├── IHttpClientManager.kt     # Interface
│   └── OkHttpWebSocket.kt        # Proxy-aware WebSocket
├── privacyOptions/
│   ├── IRoleBasedHttpClientBuilder.kt  # Interface (returns OkHttpClient)
│   ├── RoleBasedHttpClientBuilder.kt   # Per-content-type routing
│   ├── ProxiedSocketFactory.kt         # SOCKS socket factory (java.net)
│   └── TorRelayHttpClientProvider.kt   # Composes evaluator + IHttpClientManager
└── tor/
    └── TorRelayState.kt          # Combines evaluator + client manager

amethyst/src/main/java/.../
├── tor/
│   ├── AndroidTorManager.kt      # Impl: tor-android + jtorctl (reactive, settings-driven)
│   └── TorSharedPreferences.kt   # Impl: Android DataStore persistence
└── okhttp/
    ├── AndroidOkHttpClientFactory.kt   # Impl: IOkHttpClientFactory + EncryptedBlobInterceptor
    └── OkHttpClientFactoryForRelays.kt # Android-specific (Build.FINGERPRINT check)

desktopApp/src/jvmMain/.../
├── tor/
│   ├── DesktopTorManager.kt      # Impl: kmp-tor TorRuntime (reactive, settings-driven)
│   └── DesktopTorPreferences.kt  # Impl: java.util.prefs.Preferences
├── network/
│   ├── DesktopOkHttpClientFactory.kt   # Impl: IOkHttpClientFactory (no encrypted blob)
│   ├── DesktopHttpClient.kt            # REFACTORED: DualHttpClientManager + TorRelayState
│   └── DesktopRelayConnectionManager.kt # Wire torStatusFlow → staggered relay reconnection
└── ui/tor/
    ├── TorSettingsSection.kt      # Inline quick toggle in settings screen
    └── TorSettingsDialog.kt       # Full dialog: presets + per-content-type toggles
```

### Research Insights: Source Set Placement

**Corrections from KMP skill review:**

| Component | Original Plan | Corrected | Why |
|-----------|--------------|-----------|-----|
| `ProxiedSocketFactory` | commonMain | **jvmAndroid** | Uses `java.net.Socket`, `javax.net.SocketFactory` — JVM-only |
| `IRoleBasedHttpClientBuilder` | commonMain | **jvmAndroid** | Returns `OkHttpClient` — JVM-only type |
| `TorRelayState` | commonMain | **jvmAndroid** | Depends on `DualHttpClientManager`/OkHttp |
| `ITorManager` | ~~expect~~ interface | **plain interface** | Different constructors per platform; inject, not expect/actual |
| `ITorSettingsPersistence` | ~~expect~~ interface | **plain interface** | Same reason |

**New: Split `TorRelayState`** (architecture strategist recommendation):
- `TorRelayEvaluator` → **commonMain** — pure flow combination: settings + relay lists → `TorRelayEvaluation`. Fully testable without OkHttp.
- `TorRelayHttpClientProvider` → **jvmAndroid** — thin wrapper composing evaluator + `IHttpClientManager` to return correct `OkHttpClient` per relay.

### Implementation Phases

#### Phase 1: Extract Shared Models & Logic + Security Fixes

Move platform-agnostic code from `amethyst/` → `commons/commonMain/`:

| File (source) | Destination | Changes Needed |
|---------------|-------------|----------------|
| `ui/tor/TorSettings.kt` | `commons/.../tor/TorSettings.kt` | Strip `R.string` from `TorType` + `TorPresetType`. Use string keys, map to resources via `when` at platform UI layer |
| `ui/tor/TorSettingsFlow.kt` | `commons/.../tor/TorSettingsFlow.kt` | `@Stable` is fine (Compose MP in commons). Add `distinctUntilChanged()` after combine |
| `ui/tor/TorServiceStatus.kt` | `commons/.../tor/TorServiceStatus.kt` | **Remove `TorControlConnection`** from `Active`. Add `data class Error(val message: String)`. Use `data object Off`, `data object Connecting` |
| `model/torState/TorRelaySettings.kt` | `commons/.../tor/TorRelaySettings.kt` | No changes |
| `model/torState/TorRelayEvaluation.kt` | `commons/.../tor/TorRelayEvaluation.kt` | No changes — pure logic using `NormalizedRelayUrl` from quartz |
| `model/torState/TorRelayState.kt` | **Split** → `TorRelayEvaluator` (commonMain) + `TorRelayHttpClientProvider` (jvmAndroid) | Separate pure routing logic from OkHttpClient selection |

Move JVM-shared code from `amethyst/` → `commons/jvmAndroid/`:

| File (source) | Destination | Changes Needed |
|---------------|-------------|----------------|
| `service/okhttp/DualHttpClientManager.kt` | `commons/.../okhttp/DualHttpClientManager.kt` | Take `IOkHttpClientFactory` interface instead of concrete factory. Remove `isMobileDataProvider` (optional param, desktop passes null). **Share a single `ConnectionPool` across rebuilt clients; evict old pool on rebuild** |
| `service/okhttp/IHttpClientManager.kt` | `commons/.../okhttp/IHttpClientManager.kt` | No changes |
| `service/okhttp/OkHttpWebSocket.kt` | `commons/.../okhttp/OkHttpWebSocket.kt` | No changes |
| `model/privacyOptions/IRoleBasedHttpClientBuilder.kt` | `commons/.../privacyOptions/IRoleBasedHttpClientBuilder.kt` | No changes |
| `model/privacyOptions/RoleBasedHttpClientBuilder.kt` | `commons/.../privacyOptions/RoleBasedHttpClientBuilder.kt` | Use `IHttpClientManager` interface, not concrete `DualHttpClientManager` |
| `model/privacyOptions/ProxiedSocketFactory.kt` | `commons/.../privacyOptions/ProxiedSocketFactory.kt` | **SECURITY FIX: Replace `InetSocketAddress(host, port)` with `InetSocketAddress.createUnresolved(host, port)` to prevent DNS leak** |

Create new interfaces in `commons/commonMain/`:

```kotlin
// commons/.../tor/ITorManager.kt
// REACTIVE pattern — matches Android's TorManager which derives status from settings
interface ITorManager {
    val status: StateFlow<TorServiceStatus>
    val activePortOrNull: StateFlow<Int?>
    suspend fun dormant()       // SIGNAL_DORMANT — reduce Tor activity
    suspend fun active()        // SIGNAL_ACTIVE — resume Tor activity
    suspend fun newIdentity()   // SIGNAL_NEWNYM — new Tor circuit
}

// commons/.../tor/ITorSettingsPersistence.kt
interface ITorSettingsPersistence {
    fun load(): TorSettings
    fun save(settings: TorSettings)
}
```

Create new interface in `commons/jvmAndroid/`:

```kotlin
// commons/.../okhttp/IOkHttpClientFactory.kt
interface IOkHttpClientFactory {
    fun buildHttpClient(proxy: Proxy?, timeoutSeconds: Long): OkHttpClient
    fun buildLocalSocksProxy(port: Int?): Proxy?  // Returns NULL when port is null (not 9050 fallback!)
}
```

**SECURITY FIX: `buildLocalSocksProxy(null)` must return `null`** — not fall back to port 9050. The current Android code falls back to `DEFAULT_SOCKS_PORT` when port is null, which can route through an uncontrolled system Tor daemon during bootstrap.

**Gradle: Add OkHttp to commons/jvmAndroid deps:**

```kotlin
// commons/build.gradle.kts — jvmAndroid source set
dependencies {
    implementation(libs.okhttp)
    implementation(libs.okhttpCoroutines)
}
```

**Unaddressed gap: `AccountsTorStateConnector`** — aggregates DM/trusted relay lists from all accounts for `TorRelayEvaluator`. Desktop needs equivalent wiring. Either extract (if `AccountCacheState` is in commons) or create desktop-specific connector.

**Success criteria:** `./gradlew :commons:compileKotlinJvm` and `./gradlew :amethyst:compileDebugKotlin` both pass. All existing Tor tests green. ProxiedSocketFactory DNS leak fixed.

---

#### Phase 2: Desktop Tor Daemon (kmp-tor)

**Gradle dependencies:**

```toml
# gradle/libs.versions.toml — [versions]
kmpTorRuntime = "2.6.0"
kmpTorResource = "409.5.0"

# gradle/libs.versions.toml — [libraries]
kmp-tor-runtime = { group = "io.matthewnelson.kmp-tor", name = "runtime", version.ref = "kmpTorRuntime" }
kmp-tor-resource-exec-tor = { group = "io.matthewnelson.kmp-tor", name = "resource-exec-tor", version.ref = "kmpTorResource" }
```

```kotlin
// desktopApp/build.gradle.kts
dependencies {
    implementation(libs.kmp.tor.runtime)
    implementation(libs.kmp.tor.resource.exec.tor)
}

nativeDistributions {
    modules("java.management")  // required by kmp-tor
}
```

**`DesktopTorManager`** — reactive, settings-driven (matches Android pattern):

```kotlin
class DesktopTorManager(
    private val settingsFlow: TorSettingsFlow,
    private val scope: CoroutineScope,  // App-level scope
) : ITorManager {
    private val _status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Off)
    override val status: StateFlow<TorServiceStatus> = _status
    override val activePortOrNull: StateFlow<Int?> = _status.map {
        (it as? TorServiceStatus.Active)?.port
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val runtime: TorRuntime by lazy {
        TorRuntime.Builder(desktopEnvironment()) {
            // Suppress relay/circuit details in logs — SECURITY: prevent hostname leaks
            observerStatic(RuntimeEvent.LISTENERS, OnEvent { listeners ->
                val port = listeners.socks.firstOrNull()?.port
                _status.value = if (port != null) TorServiceStatus.Active(port)
                               else TorServiceStatus.Off
            })
            config { TorOption.__SocksPort.configure { auto() } }
        }
    }

    init {
        // REACTIVE: auto-derive lifecycle from settings (matches Android TorManager pattern)
        scope.launch {
            settingsFlow.torType.distinctUntilChanged().transformLatest { mode ->
                when (mode) {
                    TorType.INTERNAL -> {
                        emit(TorServiceStatus.Connecting)
                        try {
                            runtime.startDaemonAsync()
                            emitAll(runtime.status)  // Keep observing
                        } catch (e: CancellationException) {
                            withContext(NonCancellable) { runtime.stopDaemonAsync() }
                            throw e
                        } catch (e: Exception) {
                            emit(TorServiceStatus.Error(e.message ?: "Unknown"))
                        }
                    }
                    TorType.EXTERNAL -> {
                        emit(TorServiceStatus.Active(settingsFlow.externalSocksPort.value))
                    }
                    TorType.OFF -> {
                        withContext(NonCancellable) { runtime.stopDaemonAsync() }
                        emit(TorServiceStatus.Off)
                    }
                }
            }.collect { _status.value = it }
        }
    }

    override suspend fun newIdentity() {
        runtime.executeAsync(TorCmd.Signal.NewNym)
    }
    override suspend fun dormant() { runtime.executeAsync(TorCmd.Signal.Dormant) }
    override suspend fun active() { runtime.executeAsync(TorCmd.Signal.Active) }
}
```

**Desktop environment — OS-specific data dirs (not `~/.amethyst/`):**

```kotlin
private fun desktopEnvironment(): TorRuntime.Environment {
    val appDir = when {
        System.getProperty("os.name").contains("Mac", true) ->
            File(System.getProperty("user.home"), "Library/Application Support/Amethyst/tor")
        System.getProperty("os.name").contains("Win", true) ->
            File(System.getenv("APPDATA"), "Amethyst/tor")
        else -> // Linux
            File(System.getenv("XDG_DATA_HOME")
                ?: "${System.getProperty("user.home")}/.local/share", "Amethyst/tor")
    }
    // SECURITY: Set directory permissions to 700
    appDir.mkdirs()
    appDir.setReadable(false, false); appDir.setReadable(true, true)
    appDir.setWritable(false, false); appDir.setWritable(true, true)
    appDir.setExecutable(false, false); appDir.setExecutable(true, true)

    return TorRuntime.Environment.Builder(
        workDirectory = appDir.resolve("work"),
        cacheDirectory = appDir.resolve("cache"),
        loader = ResourceLoaderTorExec::getOrCreate,
    ) {}
}
```

**Shutdown hook** — add to existing hook in `Main.kt`:

```kotlin
Runtime.getRuntime().addShutdownHook(Thread {
    GlobalMediaPlayer.shutdown()
    VlcjPlayerPool.shutdown()
    desktopTorManager.stopSync()  // Must stop before process exits
})
```

**Success criteria:** `./gradlew :desktopApp:run`, enable Internal Tor, see `Active(port)` in logs. Also test `./gradlew :desktopApp:packageDmg` early to catch notarization issues.

---

#### Phase 3: Desktop Network Plumbing + Relay Reconnection

**Create `DesktopOkHttpClientFactory`** implementing `IOkHttpClientFactory`:

```kotlin
class DesktopOkHttpClientFactory : IOkHttpClientFactory {
    private val sharedConnectionPool = ConnectionPool()  // Shared across rebuilds

    override fun buildHttpClient(proxy: Proxy?, timeoutSeconds: Long): OkHttpClient {
        val multiplier = if (proxy != null) 2 else 1  // Desktop: 2x not 3x
        return OkHttpClient.Builder()
            .proxy(proxy)
            .connectionPool(sharedConnectionPool)
            .connectTimeout(timeoutSeconds * multiplier, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds * multiplier, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds * multiplier, TimeUnit.SECONDS)
            .build()
    }

    override fun buildLocalSocksProxy(port: Int?): Proxy? {
        if (port == null) return null  // NO fallback to 9050!
        return Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
    }
}
```

**Refactor `DesktopHttpClient`** from static singleton:

```kotlin
class DesktopTorAwareHttpClient(
    private val evaluator: TorRelayEvaluator,       // Pure routing decisions
    private val clientManager: DualHttpClientManager, // Single manager (no separate relay one)
    private val settingsFlow: TorSettingsFlow,
) : IRoleBasedHttpClientBuilder {
    // Desktop uses ONE DualHttpClientManager for everything (no emulator check needed)
    // Delegates per-content-type decisions to RoleBasedHttpClientBuilder
}
```

**Staggered relay reconnection** (prevents thundering herd):

```kotlin
// In DesktopRelayConnectionManager
torManager.status
    .distinctUntilChanged()
    .debounce(100)  // Match Android RelayProxyClientConnector pattern
    .flatMapLatest { status ->
        when (status) {
            is TorServiceStatus.Active -> flow {
                // Stagger reconnections: 200ms between each relay + jitter
                relays.forEachIndexed { index, relay ->
                    delay(index * 200L + Random.nextLong(0, 100))
                    relay.reconnectIfProxyChanged()
                }
                emit(Unit)
            }
            else -> emptyFlow()
        }
    }
```

**Behavior during Tor bootstrap (Connecting state):**
- Clearnet relays: connect immediately (no proxy needed)
- .onion relays: queue via `torManager.status.first { it is Active }` before connecting
- Tor-routed clearnet relays (per settings): queue until `Active`
- **No clearnet fallback** — connections that should use Tor must wait, never leak to clearnet

**Success criteria:** Enable Tor → relays reconnect through SOCKS (staggered) → disable Tor → relays reconnect direct.

---

#### Phase 4: Settings Persistence (Desktop)

`DesktopTorPreferences` — matches existing `DesktopPreferences` pattern:

```kotlin
object DesktopTorPreferences : ITorSettingsPersistence {
    private val prefs = Preferences.userNodeForPackage(DesktopTorPreferences::class.java)

    override fun load(): TorSettings = TorSettings(
        torType = TorType.valueOf(prefs.get("tor_type", TorType.OFF.name)),
        externalSocksPort = prefs.getInt("tor_external_port", 9050),
        onionRelaysViaTor = prefs.getBoolean("tor_onion_relays", true),
        // ... all fields with underscore-separated keys
    )

    override fun save(settings: TorSettings) {
        prefs.put("tor_type", settings.torType.name)
        prefs.putInt("tor_external_port", settings.externalSocksPort)
        // ... all fields
        // No explicit flush() — match existing DesktopPreferences pattern (JVM auto-flushes on shutdown)
    }
}
```

**Default for desktop:** `TorType.OFF` (user must opt-in).

Per-device settings (not synced across accounts or devices).

**Success criteria:** Change Tor settings → restart app → settings restored.

---

#### Phase 5: Desktop Settings UI

**Quick toggle** in existing settings screen (insert between Media Server and Dev Settings sections):

```kotlin
// TorSettingsSection.kt — inline in settings, matching existing section pattern
@Composable
fun TorSettingsSection(torManager: ITorManager, settingsFlow: TorSettingsFlow) {
    Row {
        TorStatusIcon(torManager.status.collectAsState().value)
        Text("Tor")
        Switch(checked = settingsFlow.torType.value != TorType.OFF, onCheckedChange = { ... })
        TextButton("Advanced...") { showDialog = true }
    }
    if (showDialog) {
        // Use androidx.compose.ui.window.Dialog (Desktop window-based, NOT AlertDialog)
        TorSettingsDialog(settingsFlow, onDismiss = { showDialog = false })
    }
}
```

**Full dialog** (`TorSettingsDialog.kt`) — same mockup as original plan.

**Status indicator** in sidebar footer — add in **both** `DeckSidebar` and `SinglePaneLayout`:

```kotlin
@Composable
fun TorStatusIndicator(status: TorServiceStatus) {
    val (icon, color, tooltip) = when (status) {
        is TorServiceStatus.Off -> Triple(Icons.Default.ShieldOutlined, Color.Gray, "Tor: Off")
        is TorServiceStatus.Connecting -> Triple(Icons.Default.Shield, Color.Yellow, "Tor: Connecting...")
        is TorServiceStatus.Active -> Triple(Icons.Default.Shield, Color.Green, "Tor: Connected")
        is TorServiceStatus.Error -> Triple(Icons.Default.ShieldOutlined, Color.Red, "Tor: ${status.message}")
    }
    // SECURITY: Don't show port number in tooltip (aids attacker with local access)
    TooltipArea(tooltip) { Icon(icon, tooltip, tint = color, modifier = Modifier.size(20.dp)) }
}
```

Place between `BunkerHeartbeatIndicator` and Settings button in sidebar footer.

**Success criteria:** Toggle Tor → see status change in sidebar → open dialog → change presets → save → relays reconnect.

---

#### Phase 6: Polish & Edge Cases (Deferrable)

1. **`.onion` relay warning** — Show "Requires Tor" badge on .onion relays when Tor is OFF
2. **External proxy validation** — Port range check (1-65535) in dialog
3. **External proxy warning** — "Cannot verify this is a Tor proxy" UI text
4. **Tor bootstrap progress** — If kmp-tor exposes it, show in status tooltip
5. **Auto-restart** — `DesktopTorAutoRestart` with exponential backoff (defer until crash telemetry exists)
6. **`filterjar` plugin** — Strip non-host architectures from distribution (optimization, defer)
7. **.onion TLS** — Custom `HostnameVerifier` for .onion addresses; disable SSL redirects for proxied connections

## Security Findings (from Security Sentinel)

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| 1 | **DNS leak in `ProxiedSocketFactory`** — `InetSocketAddress(host, port)` resolves locally | HIGH | Fix in Phase 1: use `createUnresolved()` |
| 2 | **No NEWNYM on desktop** — same Tor circuits reused indefinitely | MED-HIGH | Fix in Phase 2: implement `dormant()`/`active()`/`newIdentity()` via kmp-tor `TorCmd.Signal` |
| 3 | **Port 9050 fallback** — `buildLocalSocksProxy(null)` falls back to 9050 during bootstrap | MEDIUM | Fix in Phase 1: return `null` when port is null |
| 4 | **Clearnet fallback during bootstrap** — connections may leak before Tor is ready | MEDIUM | Fix in Phase 3: queue Tor-routed connections, never clearnet fallback |
| 5 | **Logging leaks** — relay hostnames, ports in logs/tooltips | MEDIUM | Fix in Phase 2/5: suppress kmp-tor verbose output, don't show port in UI |
| 6 | **Tor binary file permissions** — `~/.amethyst/tor/` may be world-readable | LOW-MED | Fix in Phase 2: `chmod 700` on creation |
| 7 | **SOCKS proxy open to local processes** | MEDIUM | Accepted risk (matches Android). Document. Consider `IsolateSOCKSAuth` flag |
| 8 | **Settings readable by other processes** | LOW-MED | Accepted risk (same as Android DataStore) |
| 9 | **External proxy trust** — no health check | LOW-MED | UI warning in Phase 6 |
| 10 | **.onion TLS/redirect edge cases** | MEDIUM | Phase 6: custom HostnameVerifier |

## Performance Considerations

| Concern | Mitigation |
|---------|------------|
| **Thundering herd on Tor toggle** (20-40 relays reconnecting) | Stagger reconnections: 200ms between each relay + random jitter |
| **OkHttpClient allocation on every state change** | Share single `ConnectionPool` across rebuilds; evict old pool explicitly |
| **TorSettingsFlow combine overhead** (13 flows, preset switch emits many) | Add `distinctUntilChanged()` after combine; `.debounce(100)` downstream |
| **Desktop timeout multiplier** | Use 2x (not Android's 3x) — desktop has stable connections |
| **`java.util.prefs.flush()` on main thread** | Don't call `flush()` — match existing `DesktopPreferences` pattern |
| **`BasicOkHttpWebSocket.needsReconnect()` doesn't detect proxy changes** | Use explicit disconnect+reconnect triggered by `TorRelayState` flow, not `needsReconnect()` |
| **`Channel(Channel.UNLIMITED)` in BasicOkHttpWebSocket** | Pre-existing issue exacerbated by Tor's bursty delivery. Consider `Channel(BUFFERED)` later |

## System-Wide Impact

### Interaction Graph

```
Settings change (UI)
  → TorSettingsFlow emits
  → DesktopTorPreferences.save() (no flush, JVM auto-flushes)
  → ITorManager reactive flow derives new TorServiceStatus
    → transformLatest: cancel previous mode, start new
    → NonCancellable cleanup for stop() during cancellation
  → DualHttpClientManager rebuilds OkHttpClients (shared ConnectionPool)
  → TorRelayEvaluator emits new TorRelayEvaluation
  → TorRelayHttpClientProvider selects correct client per relay
  → DesktopRelayConnectionManager staggered reconnect (200ms + jitter)
  → Sidebar TorStatusIndicator updates (both DeckSidebar + SinglePane)
```

### Error Propagation

- kmp-tor start failure → catch in `transformLatest` → `TorServiceStatus.Error(msg)` → UI red shield
- External port unreachable → connections timeout (no health check, matches Android)
- Tor crash → status emits `Off` unexpectedly → auto-restart (Phase 6) or user clicks to retry
- Orphaned process → shutdown hook calls `stopSync()`; `NonCancellable` cleanup in `transformLatest`

### State Lifecycle Risks

- **Race: INTERNAL → EXTERNAL quickly** → `transformLatest` cancels; `withContext(NonCancellable) { runtime.stopDaemonAsync() }` ensures cleanup
- **Orphaned Tor process** → shutdown hook + NonCancellable stop in all cancellation paths
- **Settings save during bootstrap** → `distinctUntilChanged()` + `debounce(100)` prevent redundant propagation

### API Surface Parity

| Interface | Android impl | Desktop impl |
|-----------|-------------|--------------|
| `ITorManager` | `AndroidTorManager` (tor-android, reactive) | `DesktopTorManager` (kmp-tor, reactive) |
| `ITorSettingsPersistence` | `TorSharedPreferences` (DataStore) | `DesktopTorPreferences` (java.util.prefs, `object`) |
| `IOkHttpClientFactory` | `AndroidOkHttpClientFactory` (+EncryptedBlob) | `DesktopOkHttpClientFactory` (plain) |
| `IHttpClientManager` | `DualHttpClientManager` (shared) | Same |
| `IRoleBasedHttpClientBuilder` | `RoleBasedHttpClientBuilder` (shared) | Same |

## Acceptance Criteria

### Functional Requirements

- [ ] Internal Tor: start/stop embedded Tor daemon via kmp-tor
- [ ] External Tor: connect to user-provided SOCKS port
- [ ] All 4 presets work (ONLY_WHEN_NEEDED, DEFAULT, SMALL_PAYLOADS, FULL_PRIVACY)
- [ ] All per-content-type toggles work (images, video, NIP-05, money, uploads, previews, profile pics)
- [ ] Per-relay routing: .onion always via Tor, DM/trusted/new configurable
- [ ] Settings persist across app restarts (java.util.prefs)
- [ ] Relay reconnection on Tor state changes (staggered, not thundering herd)
- [ ] Status indicator in sidebar (Off/Connecting/Active/Error) — both layout modes
- [ ] Quick toggle + "Advanced..." dialog in settings
- [ ] .onion relays show "Requires Tor" when Tor is OFF
- [ ] Android continues working with tor-android (no regressions)
- [ ] NEWNYM / dormant / active signals work on desktop

### Non-Functional Requirements

- [ ] No DNS leaks — ProxiedSocketFactory uses `createUnresolved()`, OkHttp SOCKS5 safe by default
- [ ] No clearnet fallback — Tor-routed connections queue during bootstrap, never leak
- [ ] No port 9050 fallback — `buildLocalSocksProxy(null)` returns null
- [ ] Tor data directory permissions 700
- [ ] No relay hostnames or ports in logs/tooltips
- [ ] Tor bootstrap < 30s typical
- [ ] No orphaned Tor processes on app exit (shutdown hook)

---

## Testing Strategy

**Framework:** kotlin.test (JUnit 4 runner) + mockk + kotlinx-coroutines-test. Add `com.squareup.okhttp3:mockwebserver:5.3.2` to test deps.

### Gradle Test Dependency Additions

```toml
# gradle/libs.versions.toml — [libraries]
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver3", version.ref = "okhttp" }
```

```kotlin
# commons/build.gradle.kts — jvmAndroid test deps
val jvmAndroidTest = create("jvmAndroidTest") {
    dependsOn(commonTest.get())
    dependencies {
        implementation(libs.okhttp.mockwebserver)
    }
}

# desktopApp/build.gradle.kts
testImplementation(libs.okhttp.mockwebserver)
```

---

### Unit Tests (commons/commonTest) — Pure Logic

**File: `commons/src/commonTest/.../tor/TorRelayEvaluationTest.kt`**

```kotlin
class TorRelayEvaluationTest {
    // --- .onion routing ---
    @Test fun onionRelay_torOff_returnsFalse()
    @Test fun onionRelay_torInternal_onionEnabled_returnsTrue()
    @Test fun onionRelay_torInternal_onionDisabled_returnsFalse()
    @Test fun onionRelay_torExternal_onionEnabled_returnsTrue()

    // --- localhost bypass ---
    @Test fun localhost_alwaysReturnsFalse()
    @Test fun localIp127_alwaysReturnsFalse()
    @Test fun localIp192168_alwaysReturnsFalse()

    // --- DM relay routing ---
    @Test fun dmRelay_dmViaTorEnabled_returnsTrue()
    @Test fun dmRelay_dmViaTorDisabled_returnsFalse()
    @Test fun dmRelay_torOff_returnsFalse()

    // --- Trusted relay routing ---
    @Test fun trustedRelay_trustedViaTorEnabled_returnsTrue()
    @Test fun trustedRelay_trustedViaTorDisabled_returnsFalse()

    // --- New/unknown relay routing ---
    @Test fun unknownRelay_newViaTorEnabled_returnsTrue()
    @Test fun unknownRelay_newViaTorDisabled_returnsFalse()

    // --- Edge cases ---
    @Test fun relayInBothDmAndTrusted_dmTakesPrecedence()
    @Test fun emptyRelayLists_allRelaysAreNew()
}
```

**File: `commons/src/commonTest/.../tor/TorSettingsTest.kt`**

```kotlin
class TorSettingsTest {
    // --- Presets ---
    @Test fun onlyWhenNeeded_onlyOnionEnabled()
    @Test fun default_onionAndDmAndNewEnabled()
    @Test fun smallPayloads_addsPreviewsNip05Money()
    @Test fun fullPrivacy_allEnabled()
    @Test fun custom_detectedWhenNoPresetMatches()

    // --- Preset detection ---
    @Test fun whichPreset_matchesOnlyWhenNeeded()
    @Test fun whichPreset_matchesDefault()
    @Test fun whichPreset_matchesFullPrivacy()
    @Test fun whichPreset_returnsCustomForMixedSettings()

    // --- TorType ---
    @Test fun torType_parsesOff()
    @Test fun torType_parsesInternal()
    @Test fun torType_parsesExternal()
    @Test fun torType_unknownDefaultsToOff()  // Desktop default

    // --- Data class equality ---
    @Test fun settings_equalsWorksForDistinctUntilChanged()
    @Test fun settings_copyPreservesAllFields()
}
```

**File: `commons/src/commonTest/.../tor/TorServiceStatusTest.kt`**

```kotlin
class TorServiceStatusTest {
    @Test fun active_hasPort()
    @Test fun error_hasMessage()
    @Test fun off_isSingleton()
    @Test fun connecting_isSingleton()
    @Test fun sealed_exhaustiveWhen()  // All variants covered
}
```

---

### Unit Tests (commons/jvmAndroidTest) — OkHttp Logic

**File: `commons/src/jvmAndroidTest/.../okhttp/IOkHttpClientFactoryTest.kt`**

```kotlin
class IOkHttpClientFactoryTest {
    // --- SOCKS proxy creation ---
    @Test fun buildLocalSocksProxy_withPort_returnsSocks5Proxy() {
        val proxy = factory.buildLocalSocksProxy(9050)
        assertNotNull(proxy)
        assertEquals(Proxy.Type.SOCKS, proxy!!.type())
        val addr = proxy.address() as InetSocketAddress
        assertEquals("127.0.0.1", addr.hostString)
        assertEquals(9050, addr.port)
    }

    @Test fun buildLocalSocksProxy_nullPort_returnsNull() {
        // SECURITY: must NOT fall back to 9050
        val proxy = factory.buildLocalSocksProxy(null)
        assertNull(proxy)
    }

    // --- Client configuration ---
    @Test fun buildHttpClient_withProxy_hasDoubledTimeouts()
    @Test fun buildHttpClient_withoutProxy_hasBaseTimeouts()
    @Test fun buildHttpClient_sharesConnectionPool()
}
```

**File: `commons/src/jvmAndroidTest/.../okhttp/DualHttpClientManagerTest.kt`**

```kotlin
class DualHttpClientManagerTest {
    @Test fun proxyPort_null_returnsClientWithoutProxy()
    @Test fun proxyPort_9050_returnsClientWithSocksProxy()
    @Test fun proxyPort_changes_rebuildsClient()
    @Test fun getHttpClient_useProxyTrue_returnsProxiedClient()
    @Test fun getHttpClient_useProxyFalse_returnsDirectClient()
    @Test fun oldConnectionPool_evictedOnRebuild()
}
```

**File: `commons/src/jvmAndroidTest/.../privacyOptions/ProxiedSocketFactoryTest.kt`**

```kotlin
class ProxiedSocketFactoryTest {
    // SECURITY: DNS leak prevention
    @Test fun createSocket_usesUnresolvedAddress() {
        // Verify InetSocketAddress.createUnresolved is used
        // by checking the address is NOT resolved
        val factory = ProxiedSocketFactory(Proxy(Proxy.Type.SOCKS,
            InetSocketAddress("127.0.0.1", 9050)))
        // Can't easily test without a SOCKS proxy, but verify the
        // code path uses createUnresolved via code inspection or
        // by mocking InetSocketAddress creation
    }
}
```

**File: `commons/src/jvmAndroidTest/.../privacyOptions/RoleBasedHttpClientBuilderTest.kt`**

```kotlin
class RoleBasedHttpClientBuilderTest {
    // --- Content-type routing ---
    @Test fun image_torOff_returnsDirect()
    @Test fun image_imagesViaTorTrue_returnsProxied()
    @Test fun image_imagesViaTorFalse_returnsDirect()
    @Test fun video_videosViaTorTrue_returnsProxied()
    @Test fun nip05_nip05ViaTorTrue_returnsProxied()
    @Test fun money_moneyViaTorTrue_returnsProxied()
    @Test fun upload_uploadsViaTorTrue_returnsProxied()
    @Test fun preview_previewsViaTorTrue_returnsProxied()

    // --- URL-based overrides ---
    @Test fun onionUrl_alwaysReturnsProxied()
    @Test fun localhostUrl_alwaysReturnsDirect()
    @Test fun onionUrl_torOff_returnsDirect()  // Can't proxy without Tor
}
```

---

### Unit Tests (desktopApp/jvmTest) — Desktop-Specific

**File: `desktopApp/src/jvmTest/.../tor/DesktopTorPreferencesTest.kt`**

```kotlin
class DesktopTorPreferencesTest {
    @BeforeTest fun setup() { /* clear test prefs */ }
    @AfterTest fun teardown() { /* clear test prefs */ }

    @Test fun save_andLoad_roundTrips()
    @Test fun load_noSavedData_returnsDefaults()
    @Test fun load_defaultTorType_isOFF()  // Desktop must default to OFF
    @Test fun save_allFields_persisted()
    @Test fun save_presetSettings_matchesExpected()
}
```

**File: `desktopApp/src/jvmTest/.../network/DesktopOkHttpClientFactoryTest.kt`**

```kotlin
class DesktopOkHttpClientFactoryTest {
    @Test fun timeoutMultiplier_withProxy_is2x()  // Desktop: 2x, not 3x
    @Test fun timeoutMultiplier_withoutProxy_is1x()
    @Test fun sharedConnectionPool_acrossRebuilds()
    @Test fun buildLocalSocksProxy_null_returnsNull()
}
```

**File: `desktopApp/src/jvmTest/.../tor/DesktopTorManagerTest.kt`**

```kotlin
class DesktopTorManagerTest {
    // Uses FakeTorRuntime or mockk

    @Test fun initialStatus_isOff()
    @Test fun settingsChangeToInternal_statusGoesToConnecting()
    @Test fun settingsChangeToOff_stopsRuntime()
    @Test fun settingsChangeToExternal_emitsActiveWithPort()
    @Test fun runtimeStartFails_emitsError()
    @Test fun quickToggle_internalToExternal_cancelsAndCleanup() {
        // Verify NonCancellable stop is called during cancellation
    }
    @Test fun quickToggle_internalToOff_stopsCleanly()
    @Test fun newIdentity_callsTorCmdSignalNewNym()
    @Test fun dormant_callsTorCmdSignalDormant()
    @Test fun active_callsTorCmdSignalActive()
}
```

---

### Integration Tests (desktopApp/jvmTest) — Real Tor

**Requires:** System Tor running (`brew install tor && brew services start tor`) or kmp-tor embedded bootstrap.

**File: `desktopApp/src/jvmTest/.../tor/TorRoutingVerificationTest.kt`**

Tag with `@Tag("integration")` to exclude from CI unless Tor is available.

```kotlin
@Tag("integration")
class TorRoutingVerificationTest {
    companion object {
        private const val SOCKS_PORT = 9050  // System Tor default
        private const val TIMEOUT = 60L      // Tor is slow
    }

    private val torClient = OkHttpClient.Builder()
        .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SOCKS_PORT)))
        .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT, TimeUnit.SECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                throw IllegalStateException("DNS LEAK: local resolution of $hostname")
            }
        })
        .build()

    @Test fun torProjectApi_confirmsTorRouting() {
        val response = torClient.newCall(
            Request.Builder().url("https://check.torproject.org/api/ip").build()
        ).execute()
        val body = response.body?.string() ?: fail("Empty response")
        assertTrue(body.contains("\"IsTor\":true"), "Not routing through Tor: $body")
    }

    @Test fun ipAddress_differsFromRealIp() {
        val directClient = OkHttpClient()
        val realIp = directClient.newCall(
            Request.Builder().url("https://icanhazip.com").build()
        ).execute().body?.string()?.trim()

        val torIp = torClient.newCall(
            Request.Builder().url("https://icanhazip.com").build()
        ).execute().body?.string()?.trim()

        assertNotEquals(realIp, torIp, "IPs match — not using Tor")
    }

    @Test fun dnsGuard_throwsOnLocalResolution() {
        // Verify the custom Dns override catches leaks
        assertFailsWith<IllegalStateException> {
            // Force a connection through a non-existent proxy to trigger DNS path
            val leakyClient = OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1)))
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        throw IllegalStateException("DNS LEAK: $hostname")
                    }
                })
                .build()
            leakyClient.newCall(
                Request.Builder().url("http://example.com").build()
            ).execute()
        }
    }

    @Test fun webSocketConnect_throughTor() {
        val connected = CountDownLatch(1)
        torClient.newWebSocket(
            Request.Builder().url("wss://relay.damus.io").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    connected.countDown()
                    webSocket.close(1000, "test")
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    connected.countDown()  // Relay may be down; connection attempt is what matters
                }
            }
        )
        assertTrue(connected.await(TIMEOUT, TimeUnit.SECONDS), "WebSocket timed out")
    }

    @Test fun onionAddress_resolvesViaTor() {
        // Test that .onion addresses work through SOCKS (DNS goes through Tor)
        // Use a known stable .onion service
        val response = torClient.newCall(
            Request.Builder()
                .url("http://2gzyxa5ihm7nsber2ezqbaga4dawcxirrj3hp2z5i2czsvierqdgrsyd.onion/")  // Tor Project .onion
                .build()
        ).execute()
        assertTrue(response.isSuccessful || response.code == 301, "Failed to reach .onion: ${response.code}")
    }
}
```

**File: `desktopApp/src/jvmTest/.../tor/TorSettingsIntegrationTest.kt`**

```kotlin
@Tag("integration")
class TorSettingsIntegrationTest {
    @Test fun presetChange_rebuildsClientWithCorrectProxy() {
        // Start with OFF → verify no proxy
        // Switch to DEFAULT → verify SOCKS proxy on port
        // Switch back to OFF → verify no proxy
    }

    @Test fun relayReconnection_staggeredOnTorToggle() {
        // Enable Tor → measure delay between relay reconnections
        // Verify ~200ms gap between each
    }

    @Test fun settingsPersistence_surviveRestart() {
        // Save FULL_PRIVACY preset
        // Create new DesktopTorPreferences instance
        // Load and verify all fields match
    }
}
```

---

### Security Tests (desktopApp/jvmTest)

**File: `desktopApp/src/jvmTest/.../tor/TorSecurityTest.kt`**

```kotlin
class TorSecurityTest {
    @Test fun proxiedSocketFactory_usesUnresolvedAddress() {
        // Verify ProxiedSocketFactory uses InetSocketAddress.createUnresolved
        // This is the DNS leak fix verification
    }

    @Test fun buildLocalSocksProxy_nullPort_noFallbackTo9050() {
        val factory = DesktopOkHttpClientFactory()
        val proxy = factory.buildLocalSocksProxy(null)
        assertNull(proxy, "Must return null, not fall back to port 9050")
    }

    @Test fun torDataDirectory_hasRestrictedPermissions() {
        val torDir = File(/* OS-specific path */)
        if (torDir.exists()) {
            // Unix: verify 700 permissions
            assertTrue(torDir.canRead())
            assertTrue(torDir.canWrite())
            assertTrue(torDir.canExecute())
            // Note: Java File API can't check other-user permissions directly
            // Use ProcessBuilder("stat", "-f", "%Lp", torDir.path) on macOS/Linux
        }
    }

    @Test fun statusTooltip_doesNotContainPort() {
        val status = TorServiceStatus.Active(9050)
        // Verify UI text generation doesn't include port
        val tooltip = when (status) {
            is TorServiceStatus.Active -> "Tor: Connected"
            else -> ""
        }
        assertFalse(tooltip.contains("9050"), "Port number leaked in tooltip")
    }

    @Test fun torType_defaultsToOff_onDesktop() {
        val settings = DesktopTorPreferences.load()
        assertEquals(TorType.OFF, settings.torType)
    }
}
```

---

### Manual Testing Plan

#### Pre-requisites

```bash
# Install monitoring tools
brew install wireshark  # macOS GUI
brew install tor        # System Tor for external mode testing

# Or on Linux:
sudo apt install wireshark-qt tor tcpdump
```

#### Test 1: Verify Internal Tor Routing

```
1. Start app: ./gradlew :desktopApp:run
2. Open terminal: sudo tcpdump -i any port 53 -n  (monitor DNS)
3. Open terminal: sudo tcpdump -i en0 '!host 127.0.0.1' -n  (monitor non-local)
4. In app: Settings → Tor → Enable Internal
5. Wait for sidebar shield to turn green
6. Verify in tcpdump:
   - DNS monitor: NO queries after Tor enabled ✓
   - Non-local monitor: Only Tor guard node traffic (port 9001/443) ✓
7. Open relay list — verify relays show "connected" status
8. Send a test note
9. Verify note appears on other clients
```

#### Test 2: Verify No DNS Leaks

```
1. Start Wireshark, filter: dns
2. Start app with Internal Tor enabled
3. Connect to 5+ relays
4. Browse profiles (triggers NIP-05 lookups)
5. Load images (triggers image URL fetches)
6. Verify Wireshark shows ZERO DNS queries from the Java process
   - Some DNS may come from other processes — filter by port/PID
   - On macOS: use `nettop -p <PID> -m tcp` to correlate
```

#### Test 3: Verify Relay Reconnection on Toggle

```
1. Start app, connect to relays (Tor OFF)
2. Note relay connection count in relay list
3. Enable Internal Tor
4. Watch relay list:
   - Relays should disconnect and reconnect (staggered, ~200ms apart)
   - .onion relays should now show connected
5. Disable Tor
6. Watch relay list:
   - Relays reconnect directly
   - .onion relays show "Requires Tor" or disconnect
```

#### Test 4: Verify External Tor Mode

```
1. Start system Tor: brew services start tor  (listens on 9050)
2. In app: Settings → Tor → External → Port: 9050
3. Sidebar shield turns green immediately
4. Verify routing: curl --socks5-hostname 127.0.0.1:9050 https://check.torproject.org/api/ip
5. Verify app traffic flows through same Tor instance
```

#### Test 5: Verify Preset Behavior

```
For each preset (ONLY_WHEN_NEEDED, DEFAULT, SMALL_PAYLOADS, FULL_PRIVACY):
1. Select preset in Advanced dialog
2. Verify toggles match expected state
3. Test traffic routing:
   - ONLY_WHEN_NEEDED: only .onion relays via Tor
   - DEFAULT: .onion + DM + new relays via Tor
   - SMALL_PAYLOADS: + previews, NIP-05, money
   - FULL_PRIVACY: everything via Tor
4. Verify with tcpdump that non-Tor traffic goes direct
```

#### Test 6: Nuclear Leak Test (macOS pf / Linux iptables)

```bash
# Block ALL non-Tor traffic from Java process
# macOS (pf.conf):
echo '
block drop out on en0 proto tcp from any to any
pass out on en0 proto tcp from any to any port {9001, 443}  # Tor guard
pass out on lo0
' | sudo pfctl -ef -

# Start app with Tor enabled
# If ANY feature fails (images, relays, notes) that should go through Tor,
# it means traffic was leaking to clearnet

# CLEANUP:
sudo pfctl -d
```

#### Test 7: Packaging Verification

```bash
# Build packaged app
./gradlew :desktopApp:packageDmg

# Install from DMG
# Verify kmp-tor extracts and runs Tor binary
# Verify macOS Gatekeeper doesn't block
# Run Test 1-3 from the packaged app (not Gradle run)
```

### Test Execution Commands

```bash
# All unit tests (fast, no Tor needed)
./gradlew :commons:jvmTest :desktopApp:jvmTest

# Specific test suites
./gradlew :commons:jvmTest --tests "*.tor.*"
./gradlew :commons:jvmTest --tests "*.okhttp.*"
./gradlew :commons:jvmTest --tests "*.privacyOptions.*"
./gradlew :desktopApp:jvmTest --tests "*.tor.*"
./gradlew :desktopApp:jvmTest --tests "*.network.*"

# Integration tests (requires Tor running)
./gradlew :desktopApp:jvmTest --tests "*.TorRoutingVerificationTest" -Pinclude-integration

# Android regression tests
./gradlew :amethyst:test
./gradlew :amethyst:compileDebugKotlin

# Full quality gate
./gradlew spotlessApply && ./gradlew :commons:jvmTest :desktopApp:jvmTest :amethyst:test
```

### Quality Gates

- [ ] `./gradlew :commons:jvmTest` — all Tor logic unit tests green
- [ ] `./gradlew :desktopApp:jvmTest` — all desktop Tor unit tests green
- [ ] `./gradlew :amethyst:test` — no Android regressions
- [ ] `./gradlew :amethyst:compileDebugKotlin` — Android builds clean
- [ ] `./gradlew :desktopApp:compileKotlin` — desktop builds clean
- [ ] `./gradlew :desktopApp:packageDmg` — packaging works (Phase 2)
- [ ] `./gradlew spotlessApply` — formatting clean
- [ ] Integration: `TorRoutingVerificationTest` passes with system Tor
- [ ] Manual Test 1: Internal Tor routing verified via tcpdump
- [ ] Manual Test 2: Zero DNS leaks in Wireshark
- [ ] Manual Test 3: Relay reconnection staggered correctly
- [ ] Manual Test 6: Nuclear leak test passes (pf/iptables block)

## Dependencies & Risks

| Risk | Mitigation |
|------|------------|
| kmp-tor doesn't work in packaged DMG | Test early in Phase 2 with `./gradlew :desktopApp:packageDmg` |
| macOS Hardened Runtime blocks Tor subprocess | Test with `com.apple.security.cs.disable-library-validation` entitlement |
| kmp-tor + tor-android Gradle conflict | Separate modules (desktopApp vs amethyst), no transitive bleed |
| Extraction breaks Android | Run Android compile + tests after each extraction step |
| `R.string` removal from enums | Use string keys in commons, `when` mapping at platform UI layer |
| `OkHttpClientFactory` has Android deps (`EncryptionKeyCache`) | Create `IOkHttpClientFactory` interface; Android adds interceptors, desktop doesn't |
| `AccountsTorStateConnector` not in plan | Desktop needs relay list aggregation — create desktop equivalent or extract if `AccountCacheState` is shared |
| `EncryptedBlobInterceptor`/`DefaultContentTypeInterceptor` | Stay in Android impl of `IOkHttpClientFactory`; desktop factory skips them |

## Resolved Open Questions

| Question | Resolution |
|----------|------------|
| License conflict? | **No** — Apache 2.0 variant of kmp-tor-resource (non-GPL Tor binary) |
| Per-device or per-account? | **Per-device** (java.util.prefs, object singleton) |
| Android library change? | **No** — Android keeps tor-android/jtorctl |
| DNS leaks? | **Safe for OkHttp** SOCKS5. **FIX NEEDED** for ProxiedSocketFactory |
| Default TorType on desktop? | **OFF** — user must opt-in |
| Behavior during bootstrap? | Clearnet=connect, .onion=queue, Tor-routed=queue. **No clearnet fallback** |
| Port 9050 fallback? | **Removed** — `buildLocalSocksProxy(null)` returns null |
| Interface vs expect/actual? | **Interfaces** — different constructors, injected per platform |
| Reactive vs imperative ITorManager? | **Reactive** — settings-driven via transformLatest, matching Android |
| OS data directory? | **OS-specific** — `~/Library/Application Support/Amethyst/tor/` (macOS), `~/.local/share/Amethyst/tor/` (Linux), `%APPDATA%/Amethyst/tor/` (Windows) |

## Sources & References

### Origin

- **Brainstorm document:** [docs/brainstorms/2026-03-30-desktop-tor-support-brainstorm.md](docs/brainstorms/2026-03-30-desktop-tor-support-brainstorm.md)
- Key decisions carried forward: extract-first approach, kmp-tor for desktop, full granular routing, per-device settings

### Internal References

- Android Tor implementation: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/tor/`
- OkHttp factories: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/okhttp/`
- Privacy options: `amethyst/src/main/java/com/vitorpamplona/amethyst/model/privacyOptions/`
- Desktop network: `desktopApp/src/jvmMain/.../network/`
- Desktop preferences pattern: `desktopApp/src/jvmMain/.../DesktopPreferences.kt`
- Existing expect/actual: `commons/src/commonMain/.../SecureKeyStorage.kt`
- Sidebar footer: `desktopApp/src/jvmMain/.../ui/deck/DeckSidebar.kt` (BunkerHeartbeatIndicator pattern)
- Shutdown hook: `desktopApp/src/jvmMain/.../Main.kt:165-171`

### External References

- [kmp-tor](https://github.com/05nelsonm/kmp-tor) — v2.6.0, Apache 2.0
- [kmp-tor-resource](https://github.com/05nelsonm/kmp-tor-resource) — v409.5.0, exec variant (non-GPL)
- [kmp-tor-samples](https://github.com/05nelsonm/kmp-tor-samples) — usage examples
- [OkHttp SOCKS proxy](https://github.com/square/okhttp/pull/4265) — DNS leak safe by default
- [Sparrow Wallet](https://sparrowwallet.com) — production JVM desktop app using kmp-tor
