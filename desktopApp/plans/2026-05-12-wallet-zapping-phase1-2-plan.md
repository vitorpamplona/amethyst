# Phase 1-2: NWC Wallet Parity + Zapping UX — Implementation Plan

**Date:** 2026-05-12
**Branch:** `feat/desktop-wallet-zapping`
**Status:** Plan — deepened, ready for work

## Deepening Corrections (from code verification)

1. **QR code**: ZXing already works on desktop (`QrCodeCanvas.kt`). No new lib needed. Step 1.7 is just reuse.
2. **Clipboard**: Both platforms have `ClipboardExt.kt` already. Step 1.3 wraps existing code in expect/actual.
3. **UserAvatar**: Already in `commons/` — transaction list can use it directly.
4. **WalletViewModel blocker**: `launchSigner` depends on `AccountViewModel.viewModelScope` + `toastManager`. Fix: inject `onSignerError: (String) -> Unit` callback instead of requiring AccountViewModel.
5. **No note focus tracking**: Desktop has no "focused note" concept. Keyboard shortcut zapping (Step 2.5) deferred to Phase 2b. One-click zap (Step 2.4) still works since note is passed explicitly to NoteActionsRow.
6. **NwcSignerState**: Zero Android deps confirmed. Can move to quartz or stay in commons.
7. **WalletViewModel constructor**: Takes NO params — initialized via `init(accountViewModel)` method. Extraction: make `init(account, scope, onError)` instead.

---

## Phase 1: NWC Wallet Parity

### Goal
Desktop gets a full wallet experience: connect NWC wallet, see balance, send/receive, view transactions — as a deck column.

### Architecture Decision: Wallet as Deck Column
- Fits the existing pattern (Settings, Relays, Chess are all column types)
- User adds via AppDrawer or MenuBar "Add Column > Wallet"
- In-column drill-down for sub-screens (Send, Receive, Detail, Transactions)
- Sidebar wallet icon with balance badge (optional, Phase 1b)

### Step 1.1: Extract Pure Types to Commons
**Target:** `commons/src/commonMain/kotlin/.../commons/viewmodels/wallet/`

| Type | Source | Notes |
|------|--------|-------|
| `WalletSendState` (sealed) | WalletViewModel.kt `SendState` | Idle, Sending, Success(preimage), Error(msg) |
| `WalletReceiveState` (sealed) | WalletViewModel.kt `ReceiveState` | Idle, Creating, Created(invoice, amount), Error(msg) |
| `TransactionFilter` (enum) | WalletViewModel.kt | ALL, ZAPS, NON_ZAPS |
| `WalletInfo` (data class) | WalletViewModel.kt | walletId, name, alias, balanceSats, isDefault, isLoading, error |

**Effort:** ~1 hour
**Files created:** `commons/src/commonMain/.../viewmodels/wallet/WalletTypes.kt`

### Step 1.2: Extract SharedWalletViewModel to Commons
**Target:** `commons/src/commonMain/kotlin/.../commons/viewmodels/wallet/SharedWalletViewModel.kt`

**Key refactoring:**
- Remove `ViewModel` base class → plain class
- Replace `viewModelScope` → constructor-injected `CoroutineScope`
- Keep all NIP-47 RPC logic (getBalance, getInfo, listTransactions, makeInvoice, payInvoice)
- Keep all state management (StateFlow<WalletInfo>, StateFlow<SendState>, etc.)
- `Account` dependency is already platform-agnostic

```kotlin
class SharedWalletViewModel(
    val account: Account,
    val scope: CoroutineScope,
) {
    val walletInfoList: StateFlow<List<WalletInfo>>
    val sendState: StateFlow<WalletSendState>
    val receiveState: StateFlow<WalletReceiveState>
    val transactions: StateFlow<List<Transaction>>
    val selectedFilter: StateFlow<TransactionFilter>

    fun selectWallet(id: String) { ... }
    fun setDefault(id: String) { ... }
    fun removeWallet(id: String) { ... }
    fun renameWallet(id: String, name: String) { ... }
    fun payInvoice(invoice: String) { ... }
    fun makeInvoice(amountSats: Long, description: String) { ... }
    fun refreshBalance() { ... }
    fun loadTransactions() { ... }
    fun filterTransactions(filter: TransactionFilter) { ... }
}
```

**Android wrapper:**
```kotlin
// amethyst/
class WalletViewModel(account: Account) : ViewModel() {
    val shared = SharedWalletViewModel(account, viewModelScope)
    // Delegate all state/methods to shared
}
```

**Desktop usage:**
```kotlin
// desktopApp/
val scope = rememberCoroutineScope()
val walletVM = remember(account) { SharedWalletViewModel(account, scope) }
```

**Effort:** ~3 hours
**Files created:** `commons/.../viewmodels/wallet/SharedWalletViewModel.kt`
**Files modified:** `amethyst/.../wallet/WalletViewModel.kt` (delegate to shared)

### Step 1.3: Platform Utilities (expect/actual)
**Target:** `commons/src/commonMain/.../commons/platform/`

| Utility | commonMain (expect) | androidMain (actual) | jvmMain (actual) |
|---------|--------------------|--------------------|-----------------|
| `getClipboardText()` | `expect suspend fun` | `ClipboardManager` | `Toolkit.getDefaultToolkit().systemClipboard` |
| `setClipboardText(text)` | `expect fun` | `ClipboardManager` | `StringSelection` + `Toolkit` |

**Effort:** ~1 hour
**Files created:** 3 files (expect + 2 actual)

### Step 1.4: Add DeckColumnType.Wallet
**Files to modify:**

| File | Change |
|------|--------|
| `DeckColumnType.kt` | Add `object Wallet : DeckColumnType()` + title/typeKey |
| `AppDrawer.kt` | Add to `LAUNCHABLE_SCREENS`, category = `IDENTITY` |
| `DeckColumnContainer.kt` | Add case in `RootContent()` → render `WalletColumnScreen()` |
| `Main.kt` | Add to MenuBar "Add Column..." menu |

**Effort:** ~1 hour

### Step 1.5: Desktop Wallet Column Screen
**Target:** `desktopApp/src/jvmMain/.../desktop/ui/wallet/`

**Sub-screens (in-column navigation via navStack):**

#### 1.5a: WalletHomeScreen (default view)
```
┌─────────────────────────┐
│ Wallet          [+ Add] │
├─────────────────────────┤
│ ┌─────────────────────┐ │
│ │ Alby Hub     ★ def  │ │
│ │ 125,432 sats        │ │
│ │ [Send] [Receive]    │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │ Phoenix      ☆      │ │
│ │ 50,000 sats         │ │
│ │ [Send] [Receive]    │ │
│ └─────────────────────┘ │
│                         │
│ Recent Transactions     │
│ ⚡ Sent 1,000 sats  2m │
│ ⚡ Recv 5,000 sats 15m │
│ ⚡ Zap  500 sats   1h  │
└─────────────────────────┘
```

#### 1.5b: AddWalletScreen
```
┌─────────────────────────┐
│ ← Connect Wallet        │
├─────────────────────────┤
│ Paste NWC URI:          │
│ ┌─────────────────────┐ │
│ │ nostr+walletconnect… │ │
│ └─────────────────────┘ │
│ [Paste from clipboard]  │
│                         │
│ Wallet Name:            │
│ ┌─────────────────────┐ │
│ │ My Alby Hub         │ │
│ └─────────────────────┘ │
│                         │
│ [Connect]               │
│                         │
│ ────────────────────    │
│ Supported wallets:      │
│ Alby Hub, Phoenix,      │
│ Coinos, LNbits, Zeus    │
└─────────────────────────┘
```

#### 1.5c: WalletSendScreen
```
┌─────────────────────────┐
│ ← Send                  │
├─────────────────────────┤
│ Invoice or LN Address:  │
│ ┌─────────────────────┐ │
│ │ lnbc...             │ │
│ └─────────────────────┘ │
│ [Paste]                 │
│                         │
│ Amount: 1,000 sats      │
│                         │
│ [Pay Invoice]           │
│                         │
│ Status: Sending...      │
│ ████████░░ 80%          │
└─────────────────────────┘
```

#### 1.5d: WalletReceiveScreen
```
┌─────────────────────────┐
│ ← Receive               │
├─────────────────────────┤
│ Amount (sats):          │
│ ┌─────────────────────┐ │
│ │ 10000               │ │
│ └─────────────────────┘ │
│ Description:            │
│ ┌─────────────────────┐ │
│ │ Coffee fund         │ │
│ └─────────────────────┘ │
│                         │
│ [Create Invoice]        │
│                         │
│ ┌───────────────┐       │
│ │  QR CODE      │       │
│ │  (invoice)    │       │
│ └───────────────┘       │
│ lnbc10u1pj...  [Copy]  │
└─────────────────────────┘
```

#### 1.5e: WalletTransactionsScreen
```
┌─────────────────────────┐
│ ← Transactions          │
├─────────────────────────┤
│ [All] [Zaps] [Non-Zaps] │
├─────────────────────────┤
│ Today                   │
│ ⚡↑ 1,000 sats  @alice │
│ ⚡↓ 5,000 sats  @bob   │
│ Yesterday               │
│ ⚡↑ 500 sats    @carol │
│ ...                     │
└─────────────────────────┘
```

**Effort:** ~6 hours (all sub-screens)
**Files created:**
- `desktopApp/.../ui/wallet/WalletColumnScreen.kt`
- `desktopApp/.../ui/wallet/WalletHomeContent.kt`
- `desktopApp/.../ui/wallet/AddWalletContent.kt`
- `desktopApp/.../ui/wallet/WalletSendContent.kt`
- `desktopApp/.../ui/wallet/WalletReceiveContent.kt`
- `desktopApp/.../ui/wallet/WalletTransactionsContent.kt`

### Step 1.6: Migrate NWC Config from RelaySettings to Wallet Column
- Remove NWC section from `RelaySettingsScreen`
- Add "Manage in Wallet column" link if wallet column exists
- NWC connection management now lives in AddWalletScreen

**Effort:** ~1 hour

### Step 1.7: QR Code Generation (Desktop)
- Need QR code composable for WalletReceiveScreen
- Options: `io.github.alexzhirkevich:qrose` (KMP QR library) or ZXing
- Already used on Android? Check and reuse if possible

**Effort:** ~2 hours (evaluate + integrate)

---

## Phase 2: Zapping UX Upgrade

### Goal
Rich zapping with zap types, configurable presets, one-click zaps, keyboard shortcuts, and progress feedback.

### Step 2.1: Extract Zap ViewModels to Commons
**Target:** `commons/src/commonMain/.../viewmodels/zap/`

| Component | Source | Notes |
|-----------|--------|-------|
| `ZapOptionViewModel` | ZapCustomDialog.kt:96-113 | customAmount + customMessage state |
| `UpdateZapAmountViewModel` | UpdateZapAmountViewModel.kt | zapAmounts, selectedZapType, NWC config |

**Effort:** ~1 hour
**Files created:** `commons/.../viewmodels/zap/ZapOptionViewModel.kt`, `ZapSettingsViewModel.kt`

### Step 2.2: Extract Zap UI Components to Commons
**Target:** `commons/src/commonMain/.../ui/components/zap/`

| Component | Source | Platform deps | Action |
|-----------|--------|--------------|--------|
| `ZapCustomDialog` | ZapCustomDialog.kt:117-332 | None | Move as-is |
| `ZapAmountChoicePopup` | ReactionsRow.kt:1778-1897 | None | Extract to own file |
| `ZapTypeSelector` | ZapCustomDialog.kt (type chips) | None | Extract as standalone |
| `UpdateZapAmountContent` | UpdateZapAmountDialog.kt:138-649 | BiometricPrompt (skip on desktop) | Extract, make auth optional |

**Platform-specific (expect/actual):**
| Function | commonMain | androidMain | jvmMain |
|----------|-----------|-------------|---------|
| `payInvoice(invoice)` | expect | Intent ACTION_VIEW | `Desktop.browse(URI("lightning:$invoice"))` |

**Effort:** ~3 hours
**Files created:** ~4 files in `commons/.../ui/components/zap/`

### Step 2.3: Upgrade Desktop ZapAmountDialog
Replace current basic `ZapAmountDialog` in `NoteActions.kt` with extracted `ZapCustomDialog`.

**New features:**
- Zap type selection (PUBLIC/PRIVATE/ANONYMOUS)
- Configurable preset amounts from account settings
- Custom amount + message input
- Progress feedback during payment

**Effort:** ~2 hours
**Files modified:** `desktopApp/.../ui/NoteActions.kt`

### Step 2.4: One-Click Zap
**Behavior:**
- Single left-click on zap icon → send default amount as default zap type (no dialog)
- Right-click on zap icon → open ZapCustomDialog
- Visual feedback: brief flash/highlight on successful zap

**Implementation:**
- Read `account.settings.syncedSettings.zaps.zapAmountChoices[0]` as default
- Read `account.settings.syncedSettings.zaps.defaultZapType` as default type
- Call `ZapAction.fetchZapInvoice()` → `NwcPaymentHandler.payInvoice()` inline
- Show success/error via snackbar

**Effort:** ~2 hours
**Files modified:** `desktopApp/.../ui/NoteActions.kt`

### Step 2.5: Keyboard Shortcut Zapping
**Design:**
- `Z` — zap focused/hovered note with default amount (same as one-click)
- `Shift+Z` — open ZapCustomDialog for focused/hovered note

**Implementation:**
- Desktop already has keyboard shortcuts in MenuBar (Main.kt)
- Need "focused note" concept — track which note the mouse is hovering over or keyboard-navigated to
- Add to existing `KeyShortcut` system

**Note:** This requires a "focused note" tracking system. If note focus doesn't exist yet, this becomes more complex. May need to defer to Phase 2b or implement basic hover-tracking first.

**Effort:** ~3 hours (if focus system exists) / ~6 hours (if building focus tracking)
**Risk:** Medium — depends on existing focus/hover infrastructure

### Step 2.6: Zap Progress & Animations
**Behavior:**
- During payment: zap icon pulses or shows mini spinner
- On success: brief lightning flash effect
- On error: red shake + snackbar

**Implementation:**
- Extract `ObserveZapIcon` pattern from Android
- Use `Animatable` for pulse/flash
- Integrate with `NwcPaymentHandler` response callback

**Effort:** ~2 hours

### Step 2.7: Zap Settings in Wallet Column
Add "Zap Settings" section to WalletHomeScreen:
- Configure preset amounts (drag-to-reorder)
- Set default zap type
- Set default zap amount for one-click

**Effort:** ~2 hours

---

## Implementation Order

```
Phase 1                              Phase 2
────────                             ────────
1.1 Extract types (1h)          ──→  2.1 Extract zap VMs (1h)
1.2 SharedWalletViewModel (3h)  ──→  2.2 Extract zap UI (3h)
1.3 Platform utils (1h)              2.3 Upgrade zap dialog (2h)
1.4 DeckColumnType.Wallet (1h)       2.4 One-click zap (2h)
1.5 Desktop wallet screens (6h)      2.5 Keyboard shortcuts (3-6h)
1.6 Migrate NWC config (1h)          2.6 Zap animations (2h)
1.7 QR code generation (2h)          2.7 Zap settings (2h)
────                                 ────
~15h total                           ~13-16h total
```

**Critical path:** 1.1 → 1.2 → 1.4 → 1.5 (wallet column must exist before zap settings in 2.7)
**Parallelizable:** 1.3 + 1.4 can happen alongside 1.2; 2.1 + 2.2 can happen alongside 1.5

---

## Testing Strategy

### Phase 1 Testing
- [ ] Connect NWC wallet via clipboard paste (Alby Hub, Phoenix)
- [ ] Balance refreshes and displays correctly
- [ ] Send payment to BOLT11 invoice
- [ ] Create receive invoice + display QR
- [ ] Transaction list loads with filter tabs
- [ ] Multi-wallet: add second wallet, switch default
- [ ] Wallet column persists across app restart
- [ ] Remove wallet + re-add

### Phase 2 Testing
- [ ] Zap dialog shows type selection (PUBLIC/PRIVATE/ANONYMOUS)
- [ ] Custom amount + message work
- [ ] One-click zap sends default amount without dialog
- [ ] Right-click opens custom zap dialog
- [ ] Keyboard Z zaps focused note
- [ ] Keyboard Shift+Z opens dialog
- [ ] Zap progress animation shows during payment
- [ ] Success/error feedback via snackbar
- [ ] Zap settings persist (amounts, type, default)

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| WalletViewModel has hidden Android deps | Low | Agent analysis shows clean extraction |
| QR generation library compatibility | Low | Multiple KMP options exist |
| Keyboard shortcut needs focus tracking | Medium | Can defer to Phase 2b; one-click works without it |
| NWC wallet response timeouts | Low | Already handled in NwcPaymentHandler (30s timeout) |
| Account settings sync between platforms | Medium | Using existing AccountSettings infrastructure |

---

## Files Summary

### New Files (~15)
```
commons/src/commonMain/.../viewmodels/wallet/WalletTypes.kt
commons/src/commonMain/.../viewmodels/wallet/SharedWalletViewModel.kt
commons/src/commonMain/.../viewmodels/zap/ZapOptionViewModel.kt
commons/src/commonMain/.../viewmodels/zap/ZapSettingsViewModel.kt
commons/src/commonMain/.../ui/components/zap/ZapCustomDialog.kt
commons/src/commonMain/.../ui/components/zap/ZapAmountChoicePopup.kt
commons/src/commonMain/.../ui/components/zap/ZapTypeSelector.kt
commons/src/commonMain/.../platform/ClipboardUtils.kt (expect)
commons/src/androidMain/.../platform/ClipboardUtils.kt (actual)
commons/src/jvmMain/.../platform/ClipboardUtils.kt (actual)
desktopApp/.../ui/wallet/WalletColumnScreen.kt
desktopApp/.../ui/wallet/WalletHomeContent.kt
desktopApp/.../ui/wallet/AddWalletContent.kt
desktopApp/.../ui/wallet/WalletSendContent.kt
desktopApp/.../ui/wallet/WalletReceiveContent.kt
desktopApp/.../ui/wallet/WalletTransactionsContent.kt
```

### Modified Files (~8)
```
amethyst/.../wallet/WalletViewModel.kt (delegate to shared)
desktopApp/.../deck/DeckColumnType.kt (add Wallet)
desktopApp/.../deck/DeckColumnContainer.kt (add RootContent case)
desktopApp/.../deck/AppDrawer.kt (add to LAUNCHABLE_SCREENS)
desktopApp/.../Main.kt (MenuBar + migrate NWC)
desktopApp/.../ui/NoteActions.kt (upgrade zap dialog, one-click, keyboard)
desktopApp/.../RelaySettingsScreen (remove NWC section)
```

---

## Unanswered Questions

1. Does desktop have a "focused/hovered note" concept for keyboard shortcuts? If not, how much work to add?
2. QR code library: is `qrose` already a dependency, or do we need to add it? What does Android use?
3. Should wallet balance show in sidebar icon (badge) or only in wallet column?
4. UserPicture/UsernameDisplay for transaction list — are these already in commons or need extraction?
5. How should wallet column handle being opened when no wallet is connected? (show AddWallet immediately?)
6. Should the wallet column auto-refresh balance on a timer, or only on user action?
