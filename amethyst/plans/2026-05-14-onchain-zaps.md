# Onchain Zaps in Amethyst

**Date:** 2026-05-14
**Status:** Active

Implementation plan for NIP-BC (kind 8333) onchain Bitcoin zaps in Amethyst Android.
Quartz now ships the `OnchainZapEvent` data model; this document describes the
rest of the system.

## Design decisions

- **Wallet model.** Non-custodial. The user's Nostr pubkey IS the BIP-341
  internal key of a P2TR output, so every account has exactly one onchain
  address derived from its identity. No seed phrase, no separate wallet
  creation.
- **Signers.** v1 supports `NostrSignerInternal` (local keypair) and
  `NostrSignerExternal` (NIP-55 Android external signer). The NIP-55 path is
  broken until Amber implements `sign_psbt`; surface "update your signer"
  there. `NostrSignerRemote` (NIP-46) is deferred.
- **Chain backend.** User-configured Esplora-compatible API
  (mempool.space, blockstream.info, self-hosted). The configured server sees
  the user's UTXO queries — accepted tradeoff for v1. Header-only SPV mode is
  a future-phase add.
- **Scope.** Full send + receive + display loop on Android. Desktop is out of
  scope for v1.

## Architecture

| Layer | Concerns | Location |
|---|---|---|
| Quartz | Address derivation, PSBT codec, Esplora client, NIP-BC verifier, `signPsbt` on signer hierarchy | `quartz/.../nipBCOnchainZaps/{taproot,psbt,chain,build,verify}/` |
| Commons | Send/wallet ViewModels, shared composables | `commons/.../onchain/` |
| Amethyst | `OnchainSection` in existing `WalletScreen`, `LocalCache.consume(OnchainZapEvent)`, kind list edits in 8 filter files, NIP-55 intent plumbing | `amethyst/.../ui/onchain/`, `amethyst/.../model/LocalCache.kt`, existing filter files |

## Merging into the existing wallet UI

The existing `WalletScreen` is a NIP-47 NWC multi-wallet manager. Onchain wallet
fits as a separate top section, since it has different semantics (single
deterministic wallet per account, no NWC URI, chain-backed).

```
WalletScreen
├── TopAppBar
├── BitcoinSection           ← NEW, single card
│   └── OnchainWalletCard    (balance, bc1p address, tap → OnchainWalletDetailScreen)
└── LightningSection         ← existing MultiWalletHomeContent
    ├── NoWalletSetup
    └── NwcWalletCard × N
```

The "+" `Add wallet` icon still adds NWC entries only — onchain is implicit.
Section labels: "Bitcoin" and "Lightning".

## Subscription edits — extend existing kind lists

No new assemblers. Add `OnchainZapEvent.KIND` (8333) to the existing
`LnZapEvent.KIND` (9735) sites:

| File | Edit |
|---|---|
| `amethyst/.../FilterRepliesAndReactionsToNotes.kt:48-60` | Add to `RepliesAndReactionsKinds` (note `#e`) |
| `amethyst/.../FilterUserProfileZapReceived.kt:30` | Add to `UserProfileZapReceiverKinds` (profile `#p`) |
| `amethyst/.../zaps/dal/UserProfileZapsViewModel.kt:55` | Add to inline `kinds = listOf(...)` |
| `amethyst/.../FilterNotificationsToPubkey.kt:58-65` | Add to `SummaryKinds` (notifications `#p`) |
| `amethyst/.../NotificationFeedFilter.kt:123` | Add to `NOTIFICATION_KINDS` |
| `amethyst/.../NotificationDispatcher.kt:98` | Add to `NOTIFICATION_KINDS` |
| `amethyst/.../FilterMessagesToLiveStream.kt:46` | Add to live-activity zap kinds (`#a`) |
| `amethyst/.../FilterGoalForLiveActivity.kt:58` | Add to goal-zap kinds (`#e`) |

## Display path — fold into `Note.zapsAmount`

Today: `LocalCache.consume(LnZapEvent)` → `Note.addZap()` → `Note.updateZapTotal()`
sums lightning amounts into `Note.zapsAmount`, which `ReactionsRow` /
`ObserveZapAmountText` / `SlidingAnimationAmount` render. We add onchain zap
sats to the same `Note.zapsAmount` — no UI changes required.

- `commons/.../model/Note.kt:154-157, 621-632`
  - Add `var onchainZaps = mapOf<...>()` (separate map from `zaps`).
  - Extend `updateZapTotal()` to add **verified** onchain sats. Unverified or
    pending tx amounts are NOT counted.
- `amethyst/.../model/LocalCache.kt` (after the `consume(LnZapEvent)` block ~line 1667)
  - New `consume(event: OnchainZapEvent)` handler.
  - Reject self-zap.
  - Enqueue verification against the configured `OnchainBackend`.
  - On success: `Note.addOnchainZap(event, verifiedSats)` on each `repliesTo`.
  - On failure or zero verified amount: discard.
  - Dedupe by `(txid, target)`.

## Quartz additions

- `nipBCOnchainZaps/taproot/`
  - `SegwitAddress.kt` — bech32m segwit address encoder/decoder
  - `TaprootAddress.kt` — Nostr pubkey → bc1p P2TR address via BIP-341
    key-path-only tweak (uses `Secp256k1Instance.pubKeyTweakAdd`)
- `nipBCOnchainZaps/chain/`
  - `OnchainBackend` interface — `getTx`, `getUtxos`, `broadcast`,
    `tipHeight`, `feeEstimates`
  - `BitcoinTx`, `BitcoinTxOutput`, `Utxo` data models
  - `BitcoinTxParser` — minimal raw-tx parser (just enough for verification)
  - `EsploraBackend` (jvmAndroid) — OkHttp impl
- `nipBCOnchainZaps/verify/`
  - `OnchainZapVerifier` — implements all spec rules: reject self-zap, sum
    only outputs paying the derived recipient address, dedupe `(txid, target)`,
    cap claimed amount at verified amount
- `nipBCOnchainZaps/psbt/` (Phase A.2)
  - Minimal BIP-174 codec
  - `PsbtTaprootKeyPathSigner` — BIP-341 TapTweak + Schnorr sign
- `nipBCOnchainZaps/build/` (Phase A.2)
  - `OnchainZapBuilder` — given (sender, recipient, sats, feeRate, utxos),
    build a PSBT with change back to sender's Taproot
- `NostrSigner.signPsbt` (Phase A.2)
  - Internal: signs directly
  - External (NIP-55): launches `sign_psbt` intent — needs Amber update
  - Remote (NIP-46): `NotSupportedException` stub

## Commons additions

- `OnchainWalletViewModel` — derived address, balance, UTXO list
- `OnchainZapSendViewModel` — build → sign → broadcast → publish kind 8333
- `OnchainZapSendDialog`, `OnchainAddressQrCard`, `FeeRatePicker`

## Account state

In `AccountSettings.kt` next to `nwcWallets`:

```kotlin
val onchainEsploraEndpoint: MutableStateFlow<String>   // default mempool.space
val onchainDefaultFeeTier: MutableStateFlow<FeeTier>   // SLOW / NORMAL / FAST
```

Derived `Account.onchainBalance: StateFlow<Long?>` populated by
`OnchainWalletViewModel`.

## Send-from-note merge

Existing `ZapAmountChoicePopup` (`ReactionsRow.kt:1877-1977`) stays as the
Lightning fast path. Two minimal hooks:

1. Append `[ ⛓ Onchain… ]` row to the popup. Tapping it opens
   `OnchainZapSendDialog` (fee picker + confirmation), separate from the
   instant-tap Lightning UX.
2. Optional settings toggle "Show onchain zap option" — defaults off until a
   balance is observed.

## Phased delivery

| Phase | Deliverable | Status |
|---|---|---|
| **A.1** | Quartz foundation (receive side): taproot address, bech32m segwit, Esplora client, verifier + tests | **Shipped** |
| **C**   | Receive + display: `OnchainSection` in `WalletScreen` (address + live balance), Esplora sync, `LocalCache.consume(OnchainZapEvent)`, `updateZapTotal` fold-in, kind-list edits in all 7 in-scope filter files | **Shipped** |
| **A.2** | Quartz foundation (send side): BIP-174 PSBT codec, BIP-341 sighash, `OnchainZapBuilder`, `signPsbt` on the `NostrSigner` hierarchy. All crypto validated against BIP-341 wallet test vectors (31 tests). | **Shipped** |
| **D**   | Send flow: `OnchainZapSender` orchestrator, `Account.sendOnchainZap`, `OnchainZapSendDialog`, "Send" button on the wallet `OnchainSection`. | **Shipped** |
| **B**   | NIP-55 `sign_psbt` Intent + ContentResolver contract, wired through `NostrSignerExternal.signPsbt`. Works once the external signer app (Amber etc.) ships `sign_psbt` support — older signers reply with no `result`, surfaced as a send failure. | **Shipped** |

### Phase A.2 — shipped

- `nipBCOnchainZaps/psbt/`: `BitcoinIO` (LE byte codec + varint), `BitcoinTransaction`
  (legacy + segwit serialization, witness-stripped txid), `TaprootSigHash`
  (BIP-341 SigMsg + TapSighash, all sighash types), `Psbt` (BIP-174 subset,
  unknown-record-preserving), `PsbtSigner` (key-path signing), `PsbtFinalizer`.
- `nipBCOnchainZaps/build/OnchainZapBuilder`: largest-first coin selection +
  unsigned-PSBT assembly with dust-aware change.
- `TaprootAddress.tweakSecretKey`: BIP-341 `taproot_tweak_seckey`.
- `Secp256k1Instance.privKeyNegate`: new primitive (commonMain + 3 actuals).
- `NostrSigner.signPsbt`: real impl on internal signer; delegated by the
  client-tag wrapper; `UnsupportedMethodException` stubs on NIP-46 / NIP-55.

### Phase D — shipped

- `commons/onchain/OnchainZapSender`: stateless orchestrator —
  load UTXOs → `OnchainZapBuilder.build` → `signer.signPsbt` →
  `PsbtFinalizer` → `OnchainBackend.broadcast` → publish kind:8333 via an
  injected callback. Per-stage `OnchainZapSendResult.Failure`; broadcast txid
  preserved when only the receipt-publish stage fails.
- `Account.sendOnchainZap`: binds the account signer, `LocalCache.onchainBackend`,
  and `signAndComputeBroadcast` into the orchestrator.
- `OnchainZapSendDialog`: recipient npub (or fixed recipient + `zappedEvent`
  for a future note-zap-menu entry), amount, fee tier from the backend's
  estimates, comment; runs the send and shows progress + result.
- `OnchainSection`: "Send" button on the wallet-screen Bitcoin card.

### Phase B — shipped

- `CommandType.SIGN_PSBT` (`sign_psbt`) — also usable in NIP-55 `perms` lists
  via `Permission`, which wraps `CommandType` directly.
- `SignPsbtResult` result type; `SignPsbtQuery` (background ContentResolver),
  `SignPsbtRequest` / `SignPsbtResponse` (foreground Intent), mirroring the
  `derive_key` string-in/string-out shape — the PSBT hex rides the
  `nostrsigner:` URI, the signed PSBT comes back in `result`.
- `BackgroundRequestHandler.signPsbt` / `ForegroundRequestHandler.signPsbt`;
  `NostrSignerExternal.signPsbt` now does the real background-then-foreground
  query instead of throwing. External signers that predate `sign_psbt` reply
  with no `result` → `CouldNotPerformException`, surfaced by the send dialog.

### What's still pending

1. **Note-zap-menu entry point.** `OnchainZapSendDialog` already accepts
   `recipientPubKey` + `zappedEvent`; wiring an "Onchain" option into the
   existing `ZapAmountChoicePopup` is the remaining UI hook for event zaps.
2. **NIP-46 `sign_psbt`.** `NostrSignerRemote.signPsbt` still throws
   `UnsupportedMethodException` — the bunker-side command is not standardized
   yet.

## Risks / open questions

- **secp256k1-kmp tweak coverage** — pubKeyTweakAdd exists on JVM/Android/JNI
  but not on the pure-Kotlin native impl. iOS support deferred until upstream
  ships it or we contribute.
- **PSBT correctness** — single-key-path-only is a small surface; still needs
  thorough testing against BIP-174 test vectors before any user funds move.
- **Esplora privacy** — the configured server sees user UTXO queries. Default
  to a reputable provider, make user-configurable, consider future Tor option.
- **NIP-55 ecosystem** — Amber must implement `sign_psbt` for external signer
  accounts to use this feature.
- **`Note.zaps` shape** — separate `onchainZaps` map vs sealed-type fold-in.
  Leaning separate map for v1.
- **Verification network calls from `LocalCache`** — `consume` runs on the
  relay thread; verification needs a coroutine scope + `OnchainBackend`
  instance injected from `Account` on app start.
