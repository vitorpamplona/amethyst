# Desktop Wallet & Zapping Experience - Brainstorm Map

**Date:** 2026-05-12
**Status:** Brainstorm v2 — phase map with research findings + decisions

---

## Decisions Made

| # | Decision | Resolution |
|---|----------|------------|
| 1 | Cashu lib: build now? | Not building yet, but may do it anyway. Research first. |
| 2 | NIP-60/61 worth investment? | Worth investigating — adoption growing |
| 3 | Key storage | `java.security.KeyStore` — platform-agnostic |
| 4 | WalletViewModel extraction | Clean — no deep Android ties |
| 5 | Hot wallet opt-in/out | **Opt-in** — user chooses to setup desktop wallet OR connect existing (e.g. mobile) |
| 6 | NWC QR on desktop | Clipboard-only paste |
| 7 | Phase priority | **NWC parity first**, then layer Cashu/hot wallet |
| 8 | Mint trust | Needs deeper trade-off discussion |
| 9 | Balance limits | Soft warning at threshold, no hard cap (see research below) |

---

## Current State

### What Desktop Has Today
- Basic zap button in `NoteActions.kt` with preset amounts (21, 100, 500, 1k, 5k, 10k)
- `NwcPaymentHandler.kt` — NIP-47 payment flow (pay invoice, wait for response)
- `ZapReceiptsDialog` — top 10 zap receipts on a note
- Opens `lightning:` URI for external wallet fallback
- No wallet management UI, no zap type selection, no transaction history

### What Android Has (Full Feature Set)
- 6 wallet screens: Dashboard, Add, Detail, Send, Receive, Transactions
- `WalletViewModel` with full NIP-47 RPC (balance, info, transactions, invoice, pay)
- `ZapCustomDialog` with PUBLIC/PRIVATE/ANONYMOUS/NONZAP type selection
- `ReusableZapButton` with progress state
- Zap splits, zap polls, zap fundraisers
- Cashu token parsing + redemption (`CashuParser`, `MeltProcessor`)
- `ZapPaymentHandler` orchestrating splits + NWC routing
- Biometric auth for sensitive operations

### Shared Infrastructure (Already in quartz/commons)
- **NIP-57:** `LnZapEvent`, `LnZapRequestEvent`, `LnZapPrivateEvent`, private zap encryption
- **NIP-47:** `Nip47Client`, request/response events, full RPC method set
- **NIP-60:** `CashuWalletEvent`, `CashuTokenEvent`, `CashuSpendingHistoryEvent` (skeleton)
- **NIP-61:** `NutzapEvent`, `NutzapInfoEvent` (skeleton)
- **NIP-87:** `CashuMintEvent`, `FedimintEvent`, `MintRecommendationEvent`
- **LNURL:** `LightningAddressResolver` (in commons jvmAndroid)
- **ZapAction:** Shared zap invoice fetching (commons jvmAndroid)
- **Cashu parsing:** V3+V4 token parsers, `MeltProcessor` (Android only, extractable)
- **Icons:** `Zap.kt`, `ZapSplit.kt` in commons

---

## Competitor Landscape

| Client | Platform | Wallet Type | NWC | Cashu | Hot Wallet | Standout Feature |
|--------|----------|-------------|-----|-------|------------|------------------|
| **Primal** | Web+Mobile | Custodial (Strike), migrating to Spark | Yes | No | Yes | Zero-friction, 1M sat limit |
| **Damus** | iOS | External (NWC) | Yes | No | No | Clean wallet view, high-balance warning |
| **Nostura** | iOS/macOS | External (NWC) | Yes | No | No | Balance in zap sheet |
| **Vega** | Desktop | External (NWC) | Yes | No | No | Guided wizard, zap history tabs |
| **YakiHonne** | Mobile | Built-in Cashu + NWC | Yes | Yes | Yes | Zero-config via Cashu |
| **0xchat** | Mobile | Built-in Cashu | ? | Yes | Yes | Ecash-native messaging payments |
| **Coracle** | Web | External (NWC) | Yes | ? | No | WoT-focused |
| **Snort** | Web | External (NWC) | Yes | No | No | Performance-focused |

### Key Insights
- NWC is table stakes — every client supports it
- Cashu is the frontier — YakiHonne, 0xchat leading; NIP-60/61 merged but early
- **No desktop client has a hot wallet** — differentiation opportunity
- Vega is the best desktop reference (wizard, history, keyboard shortcuts)
- Primal is **migrating from custodial (Strike) to self-custodial (Spark)** — signals industry direction
- Zero-config onboarding (Cashu) is the killer UX pattern

---

## Hot Wallet Technology Options

### Comparison Matrix

| Approach | Sovereignty | Complexity | UX | JVM/Kotlin | Maturity | Notes |
|----------|-------------|------------|-----|------------|----------|-------|
| **NWC (external wallet)** | High (user's wallet) | Very Low | Good | Just Nostr events | High | Phase 1 — proven, zero backend |
| **Cashu (ecash)** | Low (trust mint) | Medium | Excellent (instant) | cdk-kotlin Android-only; pure Kotlin feasible | Medium | Phase 3 candidate |
| **Breez SDK Nodeless** | Medium (Liquid federation) | Medium | Good (no channels) | Kotlin bindings exist | Medium-High | Self-custodial LN alternative |
| **LDK Node** | High (self-custody) | High | Fair (channel mgmt) | `ldk-node-jvm` on Maven | Medium | Full LN node as library |
| **lightning-kmp (ACINQ)** | High (self-custody) | Very High | Fair | Native KMP | High (Phoenix) | Best KMP fit, not designed for embedding |
| **phoenixd (sidecar)** | High (self-custody) | Medium | Good (auto liquidity) | HTTP API | High | Separate daemon |
| **Spark (Lightspark)** | High (self-custody) | Low | Excellent | **No JVM SDK yet** | Low (beta) | Watch — Primal + WoS adopting |
| **Custodial API (Strike)** | None | Low | Excellent | HTTP API | High | Requires business agreement + KYC |
| **LNbits** | Medium (your server) | Medium | Good | HTTP API | High | Requires separate server |

### Recommendation Path
1. **Now:** NWC parity (Phase 1+2) — let users bring their own wallet
2. **Next:** Cashu hot wallet (Phase 3+4) — zero-config spending wallet
3. **Watch:** Spark Kotlin SDK — when it ships, could be the best self-custodial option
4. **Consider:** Breez SDK Nodeless as a self-custodial alternative to Cashu

---

## Cashu Library Situation

### Existing Kotlin/JVM Options

| Library | Status | Desktop JVM? | Notes |
|---------|--------|-------------|-------|
| **cdk-kotlin** (cashubtc) | Active, v0.16.0 | **No** — Android AAR only | UniFFI wrapping Rust CDK; ARM/x86 Android ABIs only |
| **cashu-client** (thunderbiscuit) | Abandoned (2y stale) | KMP intended | Never completed |
| **cashu-bdhke-kmp** (gandlafbtc) | Dead (3y stale) | KMP | BDHKE only, usable as reference |

### Options to Get Cashu on JVM Desktop

| Option | Effort | Risk | Notes |
|--------|--------|------|-------|
| **A. Fork cdk-kotlin, add JVM targets** | 2-3 weeks | Medium | Build CDK Rust for desktop targets + swap AAR JNA for standard JNA |
| **B. Pure Kotlin implementation** | 4-6 weeks | Low | BDHKE ~400 lines using existing secp256k1-kmp; full mint client in Kotlin |
| **C. Hybrid (BDHKE in Kotlin + HTTP)** | 3-4 weeks | Medium | Pragmatic middle ground |

### What Amethyst Already Has (reusable for any option)
- NIP-60/61/87 event types in quartz (commonMain, KMP-ready)
- Cashu V3+V4 token parsing (Android, extractable to commons)
- `MeltProcessor` — HTTP calls to `/melt` and `/checkfees` (extractable)
- `secp256k1-kmp` — curve operations for BDHKE foundation
- NIP-44 encryption for wallet content

### What's Missing (regardless of library choice)
- BDHKE (blind signatures): hash-to-curve, blinding, unblinding
- Full mint API client (mint, swap, check state)
- P2PK token locking (NUT-11, needed for NIP-61 nutzaps)
- Proof management (coin selection, consolidation)
- Deterministic secret derivation (NUT-13)

---

## Balance Limits Research

| App | Type | Hard Limit | Warning | Pattern |
|-----|------|-----------|---------|---------|
| **Primal** | Custodial | 1M sats | Yes | Server-enforced, "use hardware wallet for more" |
| **Damus** | NWC | None | Dismissable high-balance reminder | Soft warning |
| **Cashu.me** | Cashu browser | None | "Small spending amounts only" | Onboarding copy |
| **Minibits** | Cashu mobile | None | Beta warning | General disclaimer |
| **Phoenix** | Self-custodial LN | None | None | No artificial limits |
| **WoS** | Custodial | 5 BTC | Implied | Server-enforced |

### Our Approach (for Cashu hot wallet)
- **No hard cap** — self-custodial ecash, user's choice
- **Soft dismissable warning** at configurable threshold (default ~500K sats)
- **"Spending wallet" framing** in onboarding — clear this isn't savings
- **"Move to cold storage" CTA** when warning triggers
- **User-configurable threshold** — let power users set their own comfort level

---

## NIP Infrastructure

| NIP | Purpose | Status | Amethyst Support |
|-----|---------|--------|------------------|
| **47** | Nostr Wallet Connect | Merged, mature | Full in quartz, partial desktop UI |
| **57** | Lightning Zaps | Merged, mature | Full in quartz + Android, basic desktop |
| **60** | Cashu Wallet (relay-stored) | Merged (draft) | Event types in quartz, parsing in Android |
| **61** | Nutzaps (ecash zaps) | Merged | Event types in quartz only |
| **87** | Mint Discoverability | Merged (draft) | Event types in quartz only |

---

## Phase Map

### Phase 1: NWC Wallet Parity (Foundation) -- START HERE
**Goal:** Desktop matches Android's NWC wallet experience
**Priority:** Highest — this is the foundation everything else builds on

| Work Item | Source | Action |
|-----------|--------|--------|
| Extract `WalletViewModel` | Android `WalletViewModel` | Move to `commons/commonMain/viewmodels/` |
| Wallet connection setup | Android `AddWalletScreen` | Desktop layout: paste `nostr+walletconnect://` URI |
| Wallet dashboard | Android `WalletScreen` | Desktop sidebar panel with balance + quick actions |
| Balance display | Nostura/Vega pattern | Persistent balance in sidebar |
| Send screen | Android `WalletSendScreen` | Desktop layout with paste-friendly invoice input |
| Receive screen | Android `WalletReceiveScreen` | Desktop layout with QR code + copy button |
| Transaction history | Android `WalletTransactionsScreen` | Desktop layout with search/filter/tabs |
| Multi-wallet switcher | Android `NwcSignerState` | Dropdown in wallet panel header |
| Extract NWC payment logic | Android `ZapPaymentHandler` | Shared NWC routing to commons (minus Android intents) |

**Layout decision needed:** Sidebar wallet panel vs deck column vs both?

---

### Phase 2: Zapping UX Upgrade
**Goal:** Rich zapping with desktop-first interactions
**Dependency:** Phase 1 wallet connection

| Work Item | Source | Action |
|-----------|--------|--------|
| Zap type selection | Android `ZapCustomDialog` | Add PUBLIC/PRIVATE/ANONYMOUS toggle |
| Zap splits display | Android `DisplayZapSplits` | Extract to commons |
| Configurable presets | Account settings | User-defined amounts (not hardcoded) |
| Zap progress feedback | Android `ReusableZapButton` | Extract progress component |
| One-click zap | New | Single click = default amount; right-click = dialog |
| Keyboard shortcut zap | New (no client does this!) | `Z` = zap focused note, `Shift+Z` = custom amount |
| Zap animations | New | Subtle lightning flash on success |
| Zap receipts panel | Vega pattern | Sent/received tabs with note previews |
| Zap polls | Android `ZapPollNote` | Extract + desktop layout |
| Zap fundraisers | Android zapraiser | Extract + desktop layout |

---

### Phase 3: Cashu Hot Wallet
**Goal:** Built-in ecash spending wallet — opt-in, zero-config zapping once funded
**Dependency:** Cashu crypto library (build or fork)

| Work Item | Source | Action |
|-----------|--------|--------|
| Cashu crypto (BDHKE) | Pure Kotlin or fork cdk-kotlin | Core blind signature operations |
| Mint HTTP client | Cashu NUT spec | mint, melt, swap, check, keysets |
| NIP-60 wallet manager | Quartz skeleton | Full read/write/encrypt of wallet state on relays |
| Token lifecycle | NIP-60 | Create, spend, delete kind 7375 events |
| Extract `CashuParser` | Android `service/cashu/` | Move to commons |
| Extract `MeltProcessor` | Android `service/cashu/` | Move to commons |
| P2PK token locking | NUT-11 | Lock tokens to pubkey (needed for Phase 4) |
| Wallet key derivation | NIP-60 | Dedicated key per wallet via `java.security.KeyStore` |
| Deposit flow | Cashu mint API | LN invoice → pay → receive proofs |
| Withdraw flow | Cashu mint API | Send proofs → receive LN payment |
| Balance display | Local | Sum unspent proofs across mints |
| Wallet setup wizard | New | Opt-in: "Create spending wallet" or "Connect existing wallet" |
| Soft balance warning | Damus pattern | Dismissable warning at threshold (default 500K sats) |
| "Spending wallet" framing | Primal/Cashu.me | Clear onboarding copy |

---

### Phase 4: Nutzaps (Ecash Zaps)
**Goal:** Trustless zapping via Cashu tokens (NIP-61)
**Dependency:** Phase 3 Cashu wallet + P2PK

| Work Item | Source | Action |
|-----------|--------|--------|
| Kind 10019 publish | NIP-61 | Publish trusted mints + P2PK pubkey |
| Kind 10019 parse | NIP-61 | Read recipient's nutzap preferences |
| Kind 9321 create | NIP-61 | P2PK-lock tokens to recipient |
| Kind 9321 redeem | NIP-61 | Detect incoming nutzaps, swap into wallet |
| Nutzap display | New | Show alongside LN zaps in UI |
| Auto-redeem | New | Background coroutine to claim incoming nutzaps |
| Mint matching | NIP-61 | Find common mint between sender + recipient |
| Smart routing | New | Nutzap if possible, LN zap fallback |
| Fallback UX | New | "No common mint — send LN zap instead?" |

---

### Phase 5: Advanced Wallet Features
**Goal:** Power-user and sovereignty features

| Work Item | Description | Priority |
|-----------|-------------|----------|
| **NIP-87 mint discovery** | Browse mints recommended by follows | High |
| **Multi-mint management** | Add/remove mints, per-mint balances | High |
| **Smart payment routing** | Cashu > NWC > external, configurable | High |
| **Proof management** | Swap, consolidate, check validity | Medium |
| **Auto-swap** | Background consolidation of small proofs | Medium |
| **Wallet backup export** | Encrypted state for offline backup | Medium |
| **Budget controls** | Daily/weekly spend limits, per-zap max | Medium |
| **Zap scheduler** | Recurring zaps to favorite creators | Low |
| **Paywall support** | Pay-to-unlock via Cashu or LN | Low |
| **P2P ecash sends** | Send ecash directly to npub | Low |
| **Fedimint support** | NIP-87 kind 38173 | Low |

### Phase 5b: Watch List (Not Building Yet)
| Technology | When to Revisit | Why |
|------------|----------------|-----|
| **Spark (Lightspark)** | When Kotlin/JVM SDK ships | Self-custodial, no channels, LN-compatible |
| **Breez SDK Nodeless** | If users want self-custodial LN | Kotlin bindings exist, Liquid-based |
| **lightning-kmp** | If ACINQ opens it for embedding | Native KMP, best language fit |

---

## Extraction Inventory (Android -> Commons)

| Component | Android Location | Extractable? | Phase |
|-----------|-----------------|-------------|-------|
| `WalletViewModel` | `amethyst/ui/screen/loggedIn/wallet/` | Yes (clean) | 1 |
| `ZapPaymentHandler` (NWC part) | `amethyst/service/` | Yes | 1 |
| `NwcSignerState` | `amethyst/model/nip47WalletConnect/` | Partially | 1 |
| `ZapCustomDialog` (state logic) | `amethyst/ui/note/` | Yes | 2 |
| `DisplayZapSplits` | `amethyst/ui/note/creators/zapsplits/` | Yes | 2 |
| `ReusableZapButton` (progress) | `amethyst/ui/components/` | Yes | 2 |
| `CashuParser` (V3+V4) | `amethyst/service/cashu/` | Yes | 3 |
| `CashuToken` / `Proof` | `amethyst/service/cashu/` | Yes | 3 |
| `MeltProcessor` | `amethyst/service/cashu/` | Yes | 3 |

---

## Differentiation Opportunities

1. **First desktop Nostr client with a hot wallet** — nobody does this
2. **Keyboard-driven zapping** — `Z` to zap, no client has this
3. **Opt-in wallet choice** — create desktop wallet OR connect mobile wallet
4. **Smart payment routing** — Cashu when possible, NWC fallback
5. **Zap history as deck column** — persistent visibility
6. **Drag-and-drop invoice payment** — drop BOLT11/cashu token
7. **Multi-mint visualization** — balance distribution across mints

---

## Brainstorm Sessions Needed

| Session | Phase | Key Questions |
|---------|-------|---------------|
| **Wallet panel layout** | 1 | Sidebar panel vs deck column? Persistent balance placement? |
| **NWC extraction** | 1 | WalletViewModel extraction plan, what stays Android-specific? |
| **Zap UX design** | 2 | Keyboard shortcuts, animation style, one-click vs dialog |
| **Cashu library strategy** | 3 | Fork cdk-kotlin vs pure Kotlin BDHKE? secp256k1-kmp capabilities? |
| **Mint trust model** | 3 | Curated list vs NIP-87 social discovery vs user-only? |
| **Nutzap routing** | 4 | Unified zap button with smart routing vs separate buttons? |

---

## Unanswered Questions

1. Does `secp256k1-kmp` (already in quartz) expose the low-level point arithmetic needed for BDHKE, or only sign/verify?
2. What is Vitor's appetite for adding native/Rust build deps to CI? (affects cdk-kotlin fork option)
3. Mint trust for new users with no social graph — curated default list? Who curates?
4. Cashu adoption curve — are enough people publishing kind 10019 (nutzap info) to make Phase 4 worthwhile near-term?
5. Should wallet panel be a permanent sidebar section or a toggleable deck column?
6. How does the "connect existing wallet" flow work cross-device? NWC URI shared how?
7. Will Spark ship a Kotlin/JVM SDK in 2026? Timeline unknown.
8. Is `cashu-bdhke-kmp` (3y stale) usable as reference code for pure Kotlin BDHKE?
9. Breez SDK Nodeless fees — are submarine swap costs acceptable for a spending wallet?
10. Should we contribute desktop JVM targets back to cdk-kotlin upstream rather than maintaining a fork?
