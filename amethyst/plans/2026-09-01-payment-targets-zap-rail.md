# NIP-A3 Payment Targets as a zap rail

**Status:** proposal
**Modules:** `quartz`, `commons`, `amethyst`
**Goal:** when the sender and the recipient both publish a NIP-A3 payment
target of the *same* protocol, offer that protocol as a selectable segment in
the zap amount chip — but only on notes with no NIP-57 zap split.

---

## 1. What already exists (survey)

Almost all of the machinery is already in the tree. This feature is mostly
**wiring**, plus one small refactor and one honest UX decision.

| Component | Where | Verdict |
|---|---|---|
| `PaymentTarget(type, authority)`, `PaymentTargetTag`, `PaymentTargetsEvent` (kind 10133) | `quartz/…/experimental/nipA3/` | **Reuse** (one tweak, §4.0) |
| Sender's own targets as a `StateFlow<List<PaymentTarget>>` | `Account.paymentTargetsState.flow` (`Account.kt:902`) | **Reuse as-is** |
| **Recipient's** kind:10133 already co-loaded with kind:0 | `commons/…/watchers/FilterUserMetadataForKey.kt:50` | **Reuse — no new subscription** |
| Segmented multi-rail zap chip (CASHU/RELOAD/LIGHTNING/ONCHAIN toggle) | `ReactionsRow.kt:2362` `UnifiedZapAmountChip` | **Extend** |
| `RailCapability` + `RailCapabilityResolver.peek` (already reads `zapSplitSetup()`) | `amethyst/…/model/zap/RailCapability.kt` | **Extend** |
| `observeZapRailCapability` (async recompute keys) | `ReactionsRow.kt:2098` | **Extend** |
| `User.nutzapInfoNote` addressable-note accessor | `commons/…/model/User.kt:79` | **Pattern to copy** |
| `inAppPaymentRouteFor` (lightning/bitcoin targets → Send Payment screen) | `DisplayPaymentTargets.kt:82` | **Reuse** |
| `PaymentTargetsDialog` (list + copy + QR + pay) | `PaymentButton.kt:149` | **Reuse** for the overflow picker |
| `ReactionRowAction.Pay` — full target browser, `enabled = false` by default | `AccountSyncedSettingsInternal.kt:87`, `ReactionsRow.kt:371` | **Keep, don't duplicate** (§6.3) |
| Type-alias tables (`lightning/ln/lnurl`, `bitcoin/btc/onchain`, + the 15-entry style table) | `DisplayPaymentTargets.kt:67,70,190` | **Extract to `commons`** — currently duplicated twice inside one Android UI file |
| `showOnchainWallet` opt-out plumbing | `UiSettings.kt:62` → `UiSettingsFlow.kt:58` → `UISharedPreferences.kt:190` | **Pattern to copy** for the new setting |

**Verified constraints:**

- **There is no FX / bitcoin-price service anywhere in the repo.** Grepped
  `commons`, `amethyst`, `quartz`. This is the single fact that shapes the
  whole design (§3.2).
- Zap presets are **sats**. `MIN_ONCHAIN_ZAP_SATS = 1_000`,
  `CASHU_PREFERRED_BELOW_SATS = 10`, `ONCHAIN_PREFERRED_ABOVE_SATS = 10_000`.
- `PaymentTarget` is a plain `class` with **no `equals`** — identity equality
  only. This bites us in §4.0.

---

## 2. What the obvious plan gets wrong

The naive version is "add `PAYTO` to `enum class ZapRail` and a `hasPayTo`
boolean to `RailCapability`." That version ships five bugs:

1. **It invents amounts that don't exist.** The chip is amount-first: every
   segment shows `1000` and sends 1000 **sats**. Tapping a `venmo` or `iban`
   segment cannot mean 1000 sats, and we have no rate to convert with. The
   naive chip silently lies about how much money is moving.
2. **It double-renders Lightning and on-chain.** `lightning` / `ln` / `lnurl`
   / `bitcoin` / `btc` / `onchain` are legal `payto` types and are exactly the
   rails already on the chip. A naive match puts a second Bolt icon next to
   the first one.
3. **A boolean can't say *which* target.** `ZapRail` is a payload-free enum;
   `hasPayTo: Boolean` gets you a segment that doesn't know what to open.
4. **It corrupts the zap state machine.** A `payto://` handoff produces no
   kind:9735 receipt, so `zappingProgress`, `zapStartingTime`, the "zapped by
   you" icon and the counter must all stay untouched — a naive `send()` branch
   wires it in beside `onLightningZap` and inherits all of them.
5. **It moves bank and Venmo handles into the feed by default.** Those carry
   legal names. Today they sit behind an explicit tap on a profile.

---

## 3. Design

### 3.1 Two classes of `payto` type — only one becomes a segment

Canonicalize the type, then split it:

- **Wallet-covered types** — `lightning`/`ln`/`lnurl` and
  `bitcoin`/`btc`/`onchain`. These **never** create a new segment; they are
  the existing LIGHTNING and ONCHAIN rails. (Optional later: a `bitcoin`
  target *enriches* ONCHAIN by supplying an explicit address instead of the
  `TaprootAddress.fromPubKey` derivation — out of scope here.) This kills bug 2.
- **Handoff types** — everything else (`venmo`, `paypal`, `cashapp`, `iban`,
  `upi`, `monero`, `ethereum`, …). These get **one** segment.

### 3.2 The handoff segment carries no sat amount — and says so

Because there is no FX service, the `PayTo` segment is the one segment that
does **not** display the amount when selected. It shows the protocol label and
the arrow (`VENMO →`), and the handoff URI is emitted **without** an RFC-8905
`amount=` parameter. The user names the amount in their bank/Venmo app.

This is deliberate. The alternatives were considered and rejected:

- *Prefill `amount=<ccy>:<value>` from the sat preset* — requires a rate we do
  not have, and would be wrong for every altcoin type too.
- *Restrict to BTC-denominated types only* — collapses the feature to almost
  nothing (those are exactly the wallet-covered types).
- *Add an FX service* — a real feature with its own privacy (who do we query?),
  Tor-routing and caching design. Not a prerequisite for this one.

Consequence to document in the UI string: **the note's zap counter will not
move.** No 9735, no receipt, nothing to count. Calling it a "zap rail" in code
is a convenience; to the user it must read as *pay*, not *zap*.

### 3.3 `ZapRail` becomes a sealed interface

```kotlin
internal sealed interface ZapRail {
    data object Cashu : ZapRail
    data object Reload : ZapRail
    data object Lightning : ZapRail
    data object Onchain : ZapRail
    data class PayTo(val target: PaymentTarget) : ZapRail
}
```

`PayTo` is **never** the `preferred` rail. The amount tiers
(cashu < 10 sats < lightning < 10 000 sats < on-chain) are untouched; `PayTo`
is always an explicit second tap. That keeps the one-tap muscle memory intact
and means an accidental tap never opens a banking app.

### 3.4 Gates (all must hold)

1. `showPayToZapRail` setting is on — **default off**, opt-in (§2 bug 5).
2. The note has **no** zap split: `baseNote.event?.zapSplitSetup().isNullOrEmpty()`.
   `RailCapabilityResolver.peek` already computes `splits`; reuse it.
3. The recipient (note author) publishes ≥1 handoff-class target.
4. The sender publishes a target of the **same canonical type**.
5. Author pubkey exists (payto pays a person, not a split set).

### 3.5 At most one segment, picker on overflow

If more than one canonical type matches, render **one** segment (generic wallet
icon) whose tap opens `PaymentTargetsDialog` filtered to the matches — it
already does list + copy + QR + pay. Rendering N segments would make the chip
grow without bound; today's worst case is already 4.

---

## 4. Implementation

### 4.0 Phase 0 — prep, no behaviour change

- **`quartz`**: `PaymentTarget` → `data class`. Without value equality,
  `ZapRail.PayTo` compares by identity, so `remember(preferred, present)` in
  `UnifiedZapAmountChip` resets `selectedRail` on every recompose that
  re-derives the list. (It also fixes the hand-rolled field-by-field dedupe in
  `PaymentTargetsViewModel.addTarget`.)
- **`commons`** — new `model/payments/PaymentTargetTypes.kt` (package already
  exists, holds `PaymentSourceResolver`):
  - `canonical(rawType): String` — `trim().lowercase()` + alias collapse.
  - `isWalletCovered(canonical): Boolean` — lightning + bitcoin families.
  - Move `LIGHTNING_TARGET_TYPES` / `BITCOIN_TARGET_TYPES` here and point
    `inAppPaymentRouteFor` and `paymentTargetStyleFor` at them. Non-UI and
    CLI-safe, per `commons/ARCHITECTURE.md`.
- **`commons`** — `User.paymentTargetsNote` + `paymentTargets()`, mirroring
  `nutzapInfoNote` (`User.kt:79`), so the resolver can read the recipient
  synchronously.

### 4.1 Phase 1 — the matcher (pure, headless)

`commons/…/model/payments/PayToRailMatcher.kt`:

```kotlin
fun match(
    senderTargets: List<PaymentTarget>,
    recipientTargets: List<PaymentTarget>,
): List<PaymentTarget>
```

Canonicalize both sides, drop wallet-covered types, keep recipient targets
whose type is in the sender's type set, de-dupe by canonical type keeping the
first. Pure function, no Android, no Compose — fully unit-testable.

### 4.2 Phase 2 — capability

- `RailCapability` += `payToTargets: List<PaymentTarget> = emptyList()`.
  Defaulted, so `RailCapabilityCashuStatusTest` and all existing call sites
  compile unchanged.
- `RailCapabilityResolver.peek(..., senderTargets = emptyList(), payToEnabled = false)`
  — **defaulted**, because `zapClick` (`ReactionsRow.kt:1464`) also calls
  `peek` for the one-tap fast path and that path must stay Lightning-only.
  Returns `emptyList()` when `splits.isNotEmpty()`.
- `observeZapRailCapability` adds three inputs, each both a subscription
  trigger and a `remember` key (same contract as the existing four — see the
  "do NOT delete these as unused" comment at `ReactionsRow.kt:2105`):
  `account.paymentTargetsState.flow`, the author's `paymentTargetsNote`, and
  `uiSettingsFlow.showPayToZapRail`.

### 4.3 Phase 3 — UI

- `ZapRail` → sealed interface; update `present`, `preferred`, `selectedRail`,
  `ZapRailIcon`, `previewPreferredRail`, `previewRailsFor` and the settings
  preview row.
- Selected `PayTo` renders the label + arrow, not the amount (§3.2).
- Action: try `inAppPaymentRouteFor` first (defensive — a bitcoin target that
  slipped through), else `uriHandler.openUri("payto://$type/$authority")`, else
  toast `no_payment_app_found_for_type` (string already exists). It must not
  touch `zappingProgress` / `zapStartingTime` / `accountViewModel.zap`.
- No new icons: `AccountBalanceWallet` is already referenced in
  `MaterialSymbols.kt`, so **no `subset.sh` run is needed**.

### 4.4 Phase 4 — settings + docs

`showPayToZapRail` through `UiSettings.kt` → `UiSettingsFlow.kt` →
`UISharedPreferences.kt` → `SettingsCatalogBuilder.kt`, mirroring
`showOnchainWallet`. New strings (+ `payment_targets_search_keywords`), and a
changelog entry.

---

## 5. Tests

| Level | Test | Asserts |
|---|---|---|
| `quartz/commonTest` | `PaymentTargetEqualityTest` | value equality; dedupe by value |
| `commons/commonTest` | `PaymentTargetTypesTest` | alias collapse, case/whitespace, wallet-covered set |
| `commons/commonTest` | `PayToRailMatcherTest` | empty sender → empty; no overlap → empty; `ln` vs `lightning` never matches (wallet-covered); `Venmo` vs `venmo` matches; multi-type dedupe |
| `amethyst/test` | extend `RailCapabilityCashuStatusTest` sibling | split present → `payToTargets` empty; setting off → empty; no author → empty; existing rails unaffected |
| Manual | | chip renders; tap opens the external app; counter does **not** move; a split note shows no payto segment |

---

## 6. Open decisions for review

1. **Private rumors.** On-chain is suppressed there (it would e-tag the rumor).
   A payto handoff publishes nothing, so it is arguably safe. *Recommend:
   allow* — but it is a deliberate divergence from the on-chain precedent.
2. **Default for `showPayToZapRail`.** Recommend **off**. It puts legal-name
   handles one tap from every feed note; opt-in is the conservative call and
   matches how `ReactionRowAction.Pay` already ships disabled.
3. **`ReactionRowAction.Pay` overlap.** Recommend keeping both and leaving
   `Pay` disabled by default: `Pay` browses *all* of a recipient's targets,
   the zap segment is the *matched* shortcut. Merging them is a bigger UX
   change than this feature needs.
4. **Symmetry heuristic.** "Both parties declare the protocol" is exactly
   right for closed loops (Venmo, Cash App, UPI) and arguably too strict for
   open ones (Monero — a sender needs a wallet, not a published address).
   Recommend shipping the strict rule first; relaxing it later is additive.
