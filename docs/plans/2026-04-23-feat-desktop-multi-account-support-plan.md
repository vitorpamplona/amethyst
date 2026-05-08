---
title: "feat: Desktop Multi-Account Support"
type: feat
status: active
date: 2026-04-23
origin: docs/brainstorms/2026-04-23-desktop-multi-account-brainstorm.md
---

# feat: Desktop Multi-Account Support

## Enhancement Summary

**Deepened on:** 2026-04-24
**Research agents used:** kotlin-expert, kotlin-coroutines, kotlin-multiplatform, compose-expert, desktop-expert, nostr-expert, security-reviewer, desktop-api-researcher

### Key Improvements
1. **KMP extraction corrected** — only extract types + interface to commons (not managers); AccountSessionManager/CacheState too Android-coupled
2. **Switch ordering fixed** — load new account BEFORE cancelling old scope (prevents unrecoverable partial failure)
3. **Encryption key strategy changed** — store random AES key in OS keychain via SecureKeyStorage instead of fragile machine-derived key
4. **SignerType changed to sealed class** — carries `packageName` in Remote subtype instead of leaking nullable field
5. **Background notification filters specified** — kinds 1, 6, 9735, 1059, 4 with `#p` tag; exclude kind 7 reactions (too noisy)
6. **Desktop API patterns grounded** — Compose `Tray` dynamic menus, custom `Painter` for badge overlay, `DialogWindow` for add-account, `DropdownMenu` with offset for sidebar

### Critical Corrections Found
- `AccountSessionManager` and `AccountCacheState` **cannot** go to `commons/commonMain` as written (Android-only deps: ContentResolver, MLS stores, Route class)
- `java.security.KeyStore` on JVM desktop provides **no real security** (file-based, no TPM) — use OS keychain instead
- `AccountState.LoggedIn` has mutable `var route` — breaks `@Stable` contract; must be `val`
- `nwc_connection.txt` stored in **plaintext** — high-severity, must migrate to encrypted storage
- macOS `TrayIcon.displayMessage()` is **broken** since macOS 10.14+ — use `two-slices` library or in-app toasts

---

## Overview

Add multi-account support to Amethyst Desktop — store multiple Nostr identities (nsec, NIP-46 bunker, npub view-only), switch between them via a sidebar dropdown, with background notification counts, system tray integration, and per-account desktop notifications. One active account at a time, kill & reconnect relay connections on switch.

## Problem Statement

Desktop currently supports only a single account with file-based storage (`last_account.txt`, `bunker_uri.txt`). Users who manage multiple Nostr identities (personal, work, project) must log out and re-enter credentials each time. Android Amethyst already has full multi-account support — desktop should match.

## Proposed Solution

Extract account **types and interfaces** to `commons/commonMain/`, build platform-specific session managers for both Android and Desktop, build desktop-specific UI (sidebar dropdown, add-account dialog), encrypted account metadata storage, background relay subscriptions for inactive account notification counts, system tray integration, and silent migration from single-account.

(see brainstorm: `docs/brainstorms/2026-04-23-desktop-multi-account-brainstorm.md`)

## Technical Approach

### Architecture

```
commons/commonMain/
  model/
    AccountInfo.kt                    # npub, signerType sealed class, isTransient
    AccountState.kt                   # Sealed: Loading, LoggedIn(IAccount), LoggedOff
    AccountStorage.kt                 # Interface only — injected, NOT expect/actual

desktopApp/
  account/
    AccountManager.kt                 # MODIFY — multi-account lifecycle (platform-specific)
    DesktopAccountStorage.kt          # NEW — encrypted JSON + SecureKeyStorage for nsecs
    DesktopAccountCacheState.kt       # NEW — Map<HexKey, DesktopIAccount> + scope lifecycle
    BackgroundNotificationManager.kt  # NEW — lightweight relay subs for inactive accounts
  ui/
    AccountSwitcherDropdown.kt        # NEW — top-of-sidebar dropdown
    AddAccountDialog.kt               # NEW — nsec / bunker / npub import via DialogWindow
  tray/
    DesktopTrayIntegration.kt         # NEW — Compose Tray with custom badge Painter

amethyst/
  AccountSessionManager.kt           # MODIFY — use commons AccountState/AccountInfo types
  LocalPreferences.kt                # MODIFY — implement AccountStorage interface
```

### Research Insight: What Goes Where

The KMP expert found that `AccountSessionManager` and `AccountCacheState` **cannot** be extracted to `commons/commonMain` as originally planned — they depend on Android-only types (`ContentResolver`, `AndroidMlsGroupStateStore`, `Route`). Only the **types and interface** go to commons:

| Component | Source Set | Reason |
|-----------|-----------|--------|
| `AccountInfo` data class | `commons/commonMain` | Pure Kotlin, no platform APIs |
| `AccountState` sealed class | `commons/commonMain` | Uses `IAccount` (already in commonMain), `String?` route |
| `AccountStorage` interface | `commons/commonMain` | Pure interface, domain types only |
| `AccountSessionManager` | **Platform-specific** | Android: NIP-55/ContentResolver; Desktop: file-based signers |
| `AccountCacheState` equivalent | **Platform-specific** | Android: MLS/Marmot stores; Desktop: different signer types |
| `DesktopAccountStorage` | `desktopApp/jvmMain` | `javax.crypto`, file I/O, OS keychain |

### Key Design Decisions (from brainstorm)

| Decision | Choice |
|----------|--------|
| Account model | One active, quick switch |
| UI placement | Top of sidebar dropdown |
| Relay on switch | Load new FIRST, then kill old scope |
| Login methods | nsec + NIP-46 bunker + npub (view-only) |
| Code sharing | Extract types + interface to commons; managers platform-specific |
| Nsec storage | SecureKeyStorage (already in quartz, uses OS keychain) |
| Metadata storage | Encrypted JSON file (`~/.amethyst/accounts.json.enc`) |
| Encryption key | Random AES key stored in OS keychain (not machine-derived) |
| App launch | Auto-login last account, optional lock |
| Add account UX | `DialogWindow` overlay from switcher dropdown |
| Unread badges | Live counts via lightweight background relay subs |
| Background subs | Up to 5 relay connections per inactive account |
| Background filters | Kinds 1, 6, 9735, 1059, 4 with `#p` tag (NOT kind 7) |
| Account removal | Delete everything (credentials + cached data) |
| NIP-46 multi-acct | One bunker URI per account (protocol is 1:1) |
| Migration | Auto-migrate existing single-account silently |
| Account ordering | Fixed by add date (stable kbd shortcuts 1-9) |
| Cache on switch | Keep SharedLocalCache across switches |

### Implementation Phases

#### Phase 1: Extract Account Types to Commons

**Goal:** Share account data types and storage interface across platforms.

**Tasks:**
- [x] Create `SignerType` as **sealed class** (not enum) in `commons/commonMain`:
  ```kotlin
  @Immutable
  sealed class SignerType {
      data object Internal : SignerType()
      data class Remote(val packageName: String) : SignerType()
      data object ViewOnly : SignerType()
  }
  ```
- [x] Create `AccountInfo` in `commons/commonMain`:
  ```kotlin
  @Immutable
  data class AccountInfo(
      val npub: String,
      val signerType: SignerType,
      val isTransient: Boolean = false,
  )
  ```
- [x] Create `AccountState` sealed class in `commons/commonMain`:
  ```kotlin
  sealed class AccountState {
      data object Loading : AccountState()
      data object LoggedOff : AccountState()
      @Immutable
      data class LoggedIn(
          val account: IAccount,      // NOT Android Account
          val route: String? = null,  // route ID, NOT Route class
      ) : AccountState()
  }
  ```
- [x] Create `AccountStorage` interface in `commons/commonMain` (constructor-injected, NOT expect/actual):
  ```kotlin
  interface AccountStorage {
      suspend fun loadAccounts(): List<AccountInfo>
      suspend fun saveAccount(info: AccountInfo)
      suspend fun deleteAccount(npub: String)
      suspend fun currentAccount(): String?
      suspend fun setCurrentAccount(npub: String)
  }
  ```
- [ ] Update Android `AccountSessionManager` to use commons `AccountInfo`, `AccountState` (future PR)
- [ ] Update Android `LocalPreferences` to implement `AccountStorage` (future PR)
- [ ] Tests: unit tests for `AccountInfo`/`AccountState` in `commons/jvmTest` (deferred — type tests trivial)

**Research Insights:**

**Kotlin patterns (kotlin-expert):**
- Use `data object` (Kotlin 1.9+) for `Loading`/`LoggedOff` — proper `toString()` and equality
- `LoggedIn` must be `data class` with `val route` (not `var`) — `var` breaks `@Stable`/`@Immutable`
- Route mutation via `_accountState.update { (it as? LoggedIn)?.copy(route = newRoute) ?: it }`
- Private `MutableStateFlow`, public `StateFlow` — never expose mutable flow externally

**KMP patterns (kotlin-multiplatform):**
- `AccountStorage` uses constructor injection because the interface is identical across platforms — only the storage substrate differs
- `SecureKeyStorage` uses expect/actual because the mechanism (Android Keystore vs macOS Keychain) is fundamentally different — `AccountStorage` doesn't need that

**Files modified:**
- `commons/commonMain/kotlin/.../model/AccountInfo.kt` (NEW)
- `commons/commonMain/kotlin/.../model/AccountState.kt` (NEW)
- `commons/commonMain/kotlin/.../model/AccountStorage.kt` (NEW)
- `commons/commonMain/kotlin/.../model/SignerType.kt` (NEW)
- `amethyst/.../AccountSessionManager.kt` (MODIFY — use commons types)
- `amethyst/.../LocalPreferences.kt` (MODIFY — implement AccountStorage)

#### Phase 2: Desktop Account Storage

**Goal:** Encrypted persistence for multiple accounts on desktop.

**Tasks:**
- [x] Create `DesktopAccountStorage.kt` implementing `AccountStorage`
  - Account metadata (list, active npub, per-account prefs) in encrypted JSON
  - File location: `~/.amethyst/accounts.json.enc`
  - Encryption: AES-256-GCM via `javax.crypto`
  - Nsec storage delegated to existing `SecureKeyStorage` (quartz) — already uses OS keychain
- [x] Implement encryption key bootstrap via OS keychain:
  ```kotlin
  private suspend fun getOrCreateMetadataKey(): ByteArray {
      val alias = "account-metadata-key"
      val existing = secureStorage.getPrivateKey(alias)
      if (existing != null) return Base64.getDecoder().decode(existing)
      val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
      secureStorage.savePrivateKey(alias, Base64.getEncoder().encodeToString(key))
      return key
  }
  ```
- [x] Implement two save paths (debounced + immediate):
  ```kotlin
  // Debounced for preference changes (500ms coalesce)
  fun saveAsync(accounts: List<AccountInfo>) { saveRequests.tryEmit(accounts) }
  // Immediate for critical operations (add, remove, logout)
  suspend fun saveImmediate(accounts: List<AccountInfo>) { writeToDisk(accounts) }
  ```
- [x] Migration logic: on first launch, detect `last_account.txt` + `bunker_uri.txt`:
  - Read existing npub, key, bunker URI
  - Create first `AccountInfo` entry
  - Write to new encrypted format, read back and verify decrypted npub matches
  - Best-effort secure-delete old files (zero-overwrite + delete)
- [ ] Migrate `nwc_connection.txt` to encrypted storage (currently **plaintext** — high-severity security issue)
- [ ] Per-account preferences namespace via `prefs.node(npub)`
- [ ] JSON serialization via Jackson (matches WorkspaceManager pattern)

**Research Insights:**

**Security (security-reviewer):**
- `java.security.KeyStore` on JVM desktop is file-based (PKCS12/JKS) with **no TPM/HSM backing** — provides zero additional security. If attacker can read `~/.amethyst/`, they can read both the encrypted file and the keystore
- **Use OS keychain** (same `java-keyring` / `SecureKeyStorage` already in quartz) to store a random 256-bit AES key. This is the password-manager pattern (1Password, Bitwarden)
- Hardware ID PBKDF2 is fragile — MAC addresses/disk serials change on hardware replacement/VM migration, locking users out
- `nwc_connection.txt` contains wallet credentials stored in plaintext — must migrate to encrypted storage
- `.bak` files should be zero-overwritten before deletion (best-effort on SSDs, but good hygiene)
- Remove `nsec` from `AccountState.LoggedIn` state object — fetch on-demand from `SecureKeyStorage` instead of caching in memory for the entire session

**Coroutines (kotlin-coroutines):**
- Debounced saves use `MutableSharedFlow` + `.debounce(500)` for preference changes
- Immediate saves bypass debounce for account add/remove/logout — don't want 500ms delay before confirming data written
- Both paths use `Dispatchers.IO`

**NIP-46 relay isolation** (from `docs/plans/2026-03-05-fix-nip46-relay-isolation-and-init-plan.md`):
- Each NIP-46 account MUST have a dedicated isolated `NostrClient`
- Created via `AccountManager.getOrCreateNip46Client()` with `Mutex` thread-safety
- Prevents relay pool pollution (bunker relays leaking into general pool)
- Cancel scope (not just job) on logout — kills any pending auth flows

**Files modified:**
- `desktopApp/.../account/DesktopAccountStorage.kt` (NEW)
- `desktopApp/.../account/AccountManager.kt` (MODIFY)
- `desktopApp/.../DesktopPreferences.kt` (MODIFY — add per-account scoping)

#### Phase 3: Account Switching Integration

**Goal:** Wire account switching into desktop app lifecycle.

**Tasks:**
- [x] Modify `AccountManager` to support multiple accounts:
  - `MutableStateFlow<AccountState>` driven by `DesktopAccountStorage`
  - `StateFlow<List<AccountInfo>>` for all accounts list
  - `switchAccount(accountInfo)`: **load new account FIRST**, then cancel old scope
  - `addAccount(key/bunkerUri/npub)`: validate, save to storage, optionally switch
  - `removeAccount(npub)`: cancel scope, delete from storage + SecureKeyStorage, clear preferences
- [ ] Implement safe switch ordering (CRITICAL — coroutines review):
  ```kotlin
  suspend fun switchAccount(newInfo: AccountInfo): Result<Unit> {
      val previousInfo = currentAccountInfo
      val previousScope = accountScope
      return try {
          // Phase 1: load + validate BEFORE cancelling old scope
          val newAccount = loadAccount(newInfo)  // throws on failure
          // Phase 2: now safe to transition
          _accountState.value = AccountState.Loading
          accountScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
          previousScope.cancel()  // Only cancel after new scope ready
          // Phase 3: bring up new connections
          relayConnectionManager.reconnect(newAccount.relays)
          _accountState.value = AccountState.LoggedIn(newAccount)
          Result.success(Unit)
      } catch (e: Exception) {
          // Old scope still alive — no partial state
          _accountState.value = AccountState.LoggedIn(previousInfo!!)
          Result.failure(e)
      }
  }
  ```
- [ ] Create `DesktopAccountCacheState`:
  - `MutableStateFlow<Map<HexKey, DesktopIAccount>>`
  - `loadAccount()` / `removeAccount()` with scope lifecycle
  - Mirrors Android pattern but uses `DesktopIAccount` (not extractable — Android has MLS/Marmot stores)
- [ ] Update `App` composable in `Main.kt`:
  - Gate on `AccountManager.accountState` (now multi-account aware)
  - Use `remember(activeAccountKey)` to rebuild deck/workspace state per account (no app restart)
  - Pass account switcher callbacks down to sidebar
- [ ] Share `DesktopLocalCache` across accounts (keep cache on switch)
- [ ] Update `RelayConnectionManager` lifecycle on switch

**Research Insights:**

**Coroutines (kotlin-coroutines) — CRITICAL FIX:**
- Original plan said "cancel old scope → load new account" — **wrong order**. You cannot reinstate a cancelled `CoroutineScope`. Load and validate the new account FIRST, then cancel old scope
- Use `SupervisorJob()` for account scope so individual relay failures don't kill the entire account
- `CoroutineExceptionHandler` on account scope to log unhandled errors

**Desktop (desktop-expert):**
- Do NOT use `key(appRestartKey)` for account switching (current pattern for logout/restart) — switching should be seamless
- Use `remember(activeAccountKey) { DeckState(...) }` — recreates when key changes, no app restart
- The `DeckState` and `WorkspaceManager` should be keyed per-account or reset on switch

**Files modified:**
- `desktopApp/.../account/AccountManager.kt` (MODIFY)
- `desktopApp/.../account/DesktopAccountCacheState.kt` (NEW)
- `desktopApp/.../Main.kt` (MODIFY)
- `desktopApp/.../model/DesktopIAccount.kt` (MODIFY)
- `desktopApp/.../network/RelayConnectionManager.kt` (MODIFY)
- `desktopApp/.../subscriptions/DesktopRelaySubscriptionsCoordinator.kt` (MODIFY)

#### Phase 4: Account Switcher UI

**Goal:** Sidebar dropdown for switching accounts.

**Tasks:**
- [x] Create `AccountSwitcherDropdown.kt`:
  - 48dp-wide avatar button replaces "A" logo at top of `DeckSidebar`
  - `BadgedBox` with total unread count on avatar
  - Click opens `DropdownMenu` with `DpOffset(x = 48.dp)` (expand right of sidebar)
  - Account rows: avatar (via existing `UserAvatar` commons composable), name, active checkmark, per-account `Badge` count
  - `HorizontalDivider` + "Add Account" at bottom
  - `AnimatedContent` with fade for avatar transition on switch
- [ ] Add keyboard shortcut `Cmd+Shift+A` / `Ctrl+Shift+A`:
  - Register in `MenuBar` "Accounts" menu (new menu between File and Edit)
  - No conflict with existing shortcuts (verified: Cmd+N, Cmd+S+Shift, Cmd+K, Cmd+D+Shift, Cmd+T, Cmd+W, Cmd+1..9 all free)
  - `Cmd+Shift+1..9` for direct account quick-select (also free)
- [x] Modify `DeckSidebar.kt`:
  - Replace "A" text with `AccountSwitcherDropdown` at top
  - Add params: `activeAccount`, `allAccounts`, `unreadCounts`, `onSwitchAccount`, `onAddAccount`
- [ ] Create `AddAccountDialog.kt`:
  - Use `DialogWindow` (real OS window, not Compose overlay) — avoids AWT/VLC z-order issues
  - Size: `DpSize(480.dp, 600.dp)`, `resizable = false`
  - Wrap existing `LoginCard` composable (already has nsec/bunker/nostrconnect tabs) — do NOT duplicate
  - Suppress "Generate New" path, different title/subtitle
- [ ] Account context menu (right-click on account in dropdown):
  - "Remove Account" with confirmation dialog
  - "Copy npub"
- [ ] Add "Accounts" menu to `MenuBar` with shortcut items per account

**Research Insights:**

**Compose (compose-expert):**
- `@Immutable` on `AccountInfo` — passed to every row; without it, every row recomposes on any state change
- Use `derivedStateOf` inside each badge to isolate per-account count reads (prevents full dropdown recomposition on any count change)
- Use `ImmutableMap` from `kotlinx.collections.immutable` (already in project) for `unreadCounts` StateFlow
- `allAccounts.forEach` (not `LazyColumn`) inside `DropdownMenu` — Material3 `DropdownMenu` uses `Column` internally, `LazyColumn` causes scroll conflicts. Fine for max ~9 accounts
- `BadgedBox` + `Badge` — correct M3 API, available in CMP 1.7.x, no `@Experimental` needed
- Use `HorizontalDivider` (not deprecated `Divider`)

**Desktop (desktop-expert):**
- `DropdownMenu` is correct over modal Dialog for the switcher — lightweight, dismisses on outside click, expands right from 48dp sidebar
- `DialogWindow` (not `Dialog` composable) for AddAccount — gets OS-level modality, proper focus, avoids z-order conflicts with VLC
- macOS: tray icon renders monochrome by default — use template image with `apple.awt.enableTemplateImages=true` JVM arg
- `onPreviewKeyEvent` on Window for shortcuts (intercepts before children)

**Files modified:**
- `desktopApp/.../ui/AccountSwitcherDropdown.kt` (NEW)
- `desktopApp/.../ui/AddAccountDialog.kt` (NEW)
- `desktopApp/.../ui/deck/DeckSidebar.kt` (MODIFY)
- `desktopApp/.../Main.kt` (MODIFY — MenuBar "Accounts" menu)

#### Phase 5: Background Notification Subscriptions

**Goal:** Live unread counts for inactive accounts.

**Tasks:**
- [ ] Create `BackgroundNotificationManager.kt`:
  - Per-account jobs in `Mutex`-guarded `mutableMapOf<String, Job>()`
  - `supervisorScope` per account so one relay failure doesn't kill others
  - Connect to account's NIP-65 inbox relays (`readRelaysNorm()`, max 3) + DM relays (kind 10050, max 2)
  - Subscribe with these filters per inactive account:
    ```kotlin
    // Filter 1: Mentions + reposts
    Filter(kinds = listOf(1, 6), tags = mapOf("p" to listOf(pubkey)), since = lastSeen, limit = 50)
    // Filter 2: Zap receipts
    Filter(kinds = listOf(9735), tags = mapOf("p" to listOf(pubkey)), since = lastSeen, limit = 20)
    // Filter 3: NIP-17 gift-wrapped DMs (on DM inbox relays)
    Filter(kinds = listOf(1059), tags = mapOf("p" to listOf(pubkey)), since = lastSeen, limit = 50)
    // Filter 4: Legacy NIP-04 DMs
    Filter(kinds = listOf(4), tags = mapOf("p" to listOf(pubkey)), since = lastSeen, limit = 50)
    ```
  - Expose `StateFlow<ImmutableMap<String, Int>>` — npub → unread count
  - Reset count for account when user switches to it
- [ ] Reconnect with exponential backoff + jitter:
  ```kotlin
  val delayMs = minOf(30_000L, 1000L * (1L shl minOf(attempt, 5)))
      .plus(Random.nextLong(0, 500))
  ```
  - Use `while (currentCoroutineContext().isActive)` (not `while(true)`) for cancellation
- [ ] Wire counts into `AccountSwitcherDropdown` and tray menu
- [ ] Tie to app-level `CoroutineScope`, cancel in shutdown hook

**Research Insights:**

**Nostr (nostr-expert):**
- Use `#p` tag filters (not `authors`) — listening *for* the account, not *from* it
- **Exclude kind 7 reactions** — too noisy for background counts. Use NIP-45 count query at foreground activation if needed
- **Include kind 6 reposts** — same filter as kind 1 mentions, relatively rare, worth tracking
- NIP-65 `readRelaysNorm()` for general notifications; kind 10050 `ChatMessageRelayListEvent` for DM-specific inbox relays
- Max 5 relay connections per inactive account (3 inbox + 2 DM)
- Always set `since` field — prevents relay flooding on reconnect
- Separate `RelayAuthenticator` for background connections (don't reuse active account's)

**Coroutines (kotlin-coroutines):**
- `supervisorScope` inside each account job so one relay failure doesn't kill other relays
- `Mutex` on `accountJobs` map — switch can race with notification events
- `startTracking(account)` cancels existing job for that account before creating new one
- `stopTracking(npub)` removes and cancels the job

**Files modified:**
- `desktopApp/.../account/BackgroundNotificationManager.kt` (NEW)
- `desktopApp/.../ui/AccountSwitcherDropdown.kt` (MODIFY — add badges)

#### Phase 6: System Tray Integration

**Goal:** Tray icon with account menu and notification badges.

**Tasks:**
- [ ] Create `DesktopTrayIntegration.kt`:
  - Use Compose Desktop `Tray` composable in `application {}` scope (NOT `java.awt.SystemTray`)
  - Dynamic menu driven by `collectAsState()` — updates when accounts added/removed
  - Custom `Painter` subclass for badge overlay on tray icon (no built-in badge API):
    ```kotlin
    class BadgedIconPainter(val base: Painter, val count: Int) : Painter() {
        override val intrinsicSize get() = base.intrinsicSize
        override fun DrawScope.onDraw() {
            with(base) { draw(size) }
            if (count > 0) {
                drawCircle(Color.Red, radius = size.minDimension * 0.25f,
                    center = Offset(size.width * 0.75f, size.height * 0.25f))
            }
        }
    }
    ```
  - Tray menu: active account (bold), separator, all accounts with unread counts, separator, Add Account, Quit
- [ ] Desktop notification integration:
  - **macOS**: `TrayIcon.displayMessage()` is broken since macOS 10.14+ — use `two-slices` library (`com.sshtools:two-slices:0.9.6`) for native Notification Center
  - **Windows/Linux**: `TrayIcon.displayMessage()` works, or `two-slices` for consistency
  - `two-slices` supports click callbacks (`defaultAction { }`) for notification-to-account routing
  - Prefix with account name: `[Alice] New mention from Bob`
  - Click notification → switch to that account
- [ ] macOS dark mode: set `apple.awt.enableTemplateImages=true` JVM arg for proper template image behavior

**Research Insights:**

**Desktop API (desktop-api-researcher):**
- Compose `Tray` composable wraps AWT internally but exposes clean composable API with recomposition support
- `TrayState.sendNotification(Notification)` available but limited (no click callbacks through Compose)
- For click-to-open-account on notification: need raw AWT `TrayIcon.addActionListener` or `two-slices` library
- No notification grouping API — tag account ID in title, route via click callback
- macOS tray icon size: 22x22, monochrome template; Windows: 16x16, colored OK; Linux: 22x22, varies by DE
- `Tray` `onAction` fires on primary action (macOS double-click, Windows left-click, Linux varies)

**Platform differences:**

| Platform | Tray click | Notifications | Icon theme |
|----------|-----------|---------------|------------|
| macOS | Single-click = menu | `two-slices` needed | Monochrome template |
| Windows | Left-click = `onAction` | `displayMessage()` works | Colored OK |
| Linux | Varies by DE | `displayMessage()` works | Monochrome |

**Files modified:**
- `desktopApp/.../tray/DesktopTrayIntegration.kt` (NEW)
- `desktopApp/.../Main.kt` (MODIFY — add `Tray` in `application {}` scope)

**Dependencies to add:**
- `com.sshtools:two-slices:0.9.6` (cross-platform desktop notifications with click callbacks)

#### Phase 7: App Launch & Auto-Login

**Goal:** Seamless startup with auto-login and optional lock.

**Tasks:**
- [ ] Modify app startup in `Main.kt`:
  - On launch: `DesktopAccountStorage.currentAccount()`
  - If account exists → auto-login (skip login screen)
  - If no accounts → show login screen
  - If lock enabled → show account picker with unlock
- [ ] Add lock setting to `DesktopPreferences`:
  - `appLockEnabled: Boolean` (default false)
  - When enabled: show account picker on every launch
  - Optional: PIN/passphrase unlock (stretch goal)
- [ ] Migration on first launch:
  - Detect `last_account.txt` / `bunker_uri.txt` / `nwc_connection.txt`
  - Auto-migrate to new encrypted multi-account store
  - **Verify**: read back decrypted npub matches before deleting old files
  - Set migrated account as active
  - Best-effort secure-delete old files (zero-overwrite + delete)
  - User sees no change

**Files modified:**
- `desktopApp/.../Main.kt` (MODIFY)
- `desktopApp/.../DesktopPreferences.kt` (MODIFY — add lock setting)

## System-Wide Impact

### Interaction Graph

- Account switch → `AccountManager.switchAccount()` → loads new account (validates) → creates new `CoroutineScope` → cancels old scope → relay connections killed → `BackgroundNotificationManager` starts tracking old account → new relay connections established → new subscriptions → UI recomposes via `AccountState.LoggedIn`
- Background notification → `BackgroundNotificationManager` receives event → updates `ImmutableMap` StateFlow → `derivedStateOf` in each badge → only affected badge recomposes → `DesktopTrayIntegration` updates menu via recomposition + fires OS notification via `two-slices`

### Error Propagation

- Failed relay connection during switch → show error toast, offer retry
- Corrupted encrypted storage → fallback to empty account list, show login. Nsecs survive in OS keychain
- SecureKeyStorage failure (OS keychain unavailable) → log error, fall back to encrypted file storage
- NIP-46 bunker unreachable on switch → show "Connecting to signer..." state, timeout after 30s
- **Switch failure** → old scope still alive (new ordering), revert to previous `AccountState.LoggedIn`

### State Lifecycle Risks

- **Partial switch failure**: With corrected ordering (load first, cancel second), old scope remains alive on failure. Revert by re-emitting previous `AccountState.LoggedIn`. No unrecoverable state.
- **Background subscription leak**: `BackgroundNotificationManager` tied to app-level `CoroutineScope`, cancelled in shutdown hook. Per-account jobs tracked in `Mutex`-guarded map.
- **Migration data loss**: Write new encrypted format → read back and verify decrypted content → only then secure-delete old files.
- **NIP-46 relay pollution**: Each bunker account has dedicated isolated `NostrClient` with its own `CoroutineScope` (proven fix from `docs/plans/2026-03-05-fix-nip46-relay-isolation-and-init-plan.md`).

### API Surface Parity

- `AccountStorage` interface shared between Android (`LocalPreferences`) and Desktop (`DesktopAccountStorage`)
- `AccountInfo`, `AccountState` types shared — both platforms use identical state representations
- `IAccount` interface already in commons — both platforms implement it
- Session managers remain platform-specific (necessary due to Android ContentResolver/MLS deps)

## Acceptance Criteria

### Functional Requirements

- [ ] Can add multiple accounts (nsec, NIP-46 bunker, npub view-only)
- [ ] Can switch between accounts via sidebar dropdown
- [ ] Relay connections properly killed and re-established on switch
- [ ] Active account persists across app restarts (auto-login)
- [ ] Per-account notification counts shown in switcher (kinds 1, 6, 9735, 1059, 4)
- [ ] System tray shows active account and switch menu with dynamic updates
- [ ] Desktop notifications tagged with account name (working on all platforms)
- [ ] Clicking notification switches to that account
- [ ] Account removal deletes all associated data
- [ ] Existing single-account users auto-migrated silently
- [ ] `Cmd+Shift+A` / `Ctrl+Shift+A` opens account switcher
- [ ] `Cmd+Shift+1..9` / `Ctrl+Shift+1..9` for direct account switching
- [ ] NWC URI migrated from plaintext to encrypted storage

### Non-Functional Requirements

- [ ] Account switch completes in <2s (relay reconnection)
- [ ] Background subscriptions use <5MB memory per inactive account
- [ ] Max 5 relay connections per inactive account
- [ ] Encrypted storage uses AES-256-GCM with random key in OS keychain
- [ ] No nsec stored in plaintext anywhere
- [ ] No nsec cached in AccountState (fetch on-demand from SecureKeyStorage)

### Quality Gates

- [ ] Unit tests for AccountInfo/AccountState types
- [ ] Unit tests for DesktopAccountStorage (encrypt/decrypt, migration, key bootstrap)
- [ ] Unit tests for switch ordering (load-first-cancel-second)
- [ ] Unit tests for BackgroundNotificationManager (start/stop tracking, count aggregation)
- [ ] Integration test: add account, switch, verify relay reconnection
- [ ] `./gradlew spotlessApply` passes
- [ ] `./gradlew :desktopApp:compileKotlin` succeeds
- [ ] `./gradlew :commons:compileKotlinJvm` succeeds
- [ ] Manual test: multi-account flow end-to-end on macOS, verify tray + notifications

## Dependencies & Prerequisites

- `SecureKeyStorage` in quartz (already implemented — used for both nsecs and metadata AES key)
- `IAccount` interface in commons (already exists)
- NIP-46 relay isolation fix (from `docs/plans/2026-03-05-fix-nip46-relay-isolation-and-init-plan.md`)
- Compose Desktop `Tray` API (available in Compose Multiplatform 1.7.x)
- `kotlinx.collections.immutable` (already in project)
- `com.sshtools:two-slices:0.9.6` (NEW — cross-platform desktop notifications)

## Risk Analysis & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| NIP-46 relay pool pollution | Bunker relays leak into general pool | Dedicated isolated NostrClient per NIP-46 account (proven fix) |
| Encrypted storage corruption | Account metadata lost | Nsecs survive in OS keychain independently; re-add accounts from keychain |
| Background sub memory leak | Growing memory usage | `Mutex`-guarded job map, `supervisorScope`, app-scope cancel in shutdown hook |
| Migration breaks existing users | Can't login after update | Verify read-back before deleting old files; keep `.bak` as fallback |
| OS keychain unavailable | Can't store nsecs or AES key | Fallback to encrypted file (SecureKeyStorage already handles this) |
| macOS notification broken | No OS notifications | `two-slices` library uses native Notification Center instead of broken AWT |
| Switch failure mid-transition | Stuck in loading state | Load new account FIRST; old scope alive until success; revert on failure |
| Kind 7 reaction flood | Background sub memory explosion | Excluded from background filters by design |

## Future Considerations

- **Simultaneous account views** — architecture supports it (clean scope per account), would need column-level account binding
- **NIP multi-key bunker** — if NIP-46 adds `get_public_keys`, could auto-discover keys from bunker
- **Account sync** — export/import encrypted account bundle between machines
- **Drag-to-reorder accounts** — if users want custom ordering
- **NIP-45 reaction counts** — query on foreground activation instead of background subscription
- **Global keyboard shortcuts** — `jnativehook` library for shortcuts even when app unfocused (stretch goal)

## Open Questions

1. **`lastSeenTimestamp` persistence** — where to store per-account `since` timestamp for background subs? In encrypted metadata file or separate preference?
2. **NIP-65 relay list for new accounts** — if relay list hasn't been fetched yet for a newly added inactive account, fetch eagerly during add or use defaults?
3. **Legacy NIP-04 DMs** — always include kind 4 in background filters, or only if account has prior DM history?
4. **`two-slices` maturity** — evaluate library stability; fallback to in-app toast notifications (dorkbox/Notify) if issues arise
5. **Workspace state per-account** — should deck layout and workspace configuration be per-account or global?

## Sources & References

### Origin

- **Brainstorm document:** [docs/brainstorms/2026-04-23-desktop-multi-account-brainstorm.md](docs/brainstorms/2026-04-23-desktop-multi-account-brainstorm.md) — Key decisions: one-active-account model, top-of-sidebar dropdown, kill & reconnect relays, extract to commons, encrypted storage

### Internal References

- Android AccountSessionManager: `amethyst/src/main/java/.../ui/screen/AccountSessionManager.kt`
- Android AccountCacheState: `amethyst/src/main/java/.../model/accountsCache/AccountCacheState.kt`
- Android LocalPreferences: `amethyst/src/main/java/.../LocalPreferences.kt`
- Desktop AccountManager: `desktopApp/src/jvmMain/.../desktop/account/AccountManager.kt`
- Desktop DeckSidebar: `desktopApp/src/jvmMain/.../desktop/ui/deck/DeckSidebar.kt`
- Desktop LoginCard: `desktopApp/src/jvmMain/.../desktop/ui/auth/LoginCard.kt`
- SecureKeyStorage: `commons/src/commonMain/.../commons/keystorage/SecureKeyStorage.kt`
- IAccount: `commons/src/commonMain/.../commons/model/IAccount.kt`
- NIP-46 relay isolation: `docs/plans/2026-03-05-fix-nip46-relay-isolation-and-init-plan.md`
- Workspace persistence pattern: `docs/plans/2026-04-17-feat-workspaces-v1c-plan.md`
- Cache state extraction pattern: `docs/plans/2026-03-24-feat-weakref-cache-state-extraction-plan.md`

### External References

- [Compose Desktop Tray API](https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Tray_Notifications_MenuBar_new/README.md)
- [two-slices notifications library](https://github.com/sshtools/two-slices)
- [Compose Desktop keyboard handling](https://kotlinlang.org/docs/multiplatform/compose-desktop-keyboard.html)
- [DropdownMenu positioning issue #3129](https://github.com/JetBrains/compose-multiplatform/issues/3129)
