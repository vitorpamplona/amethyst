# NIP-46 Signer — device verification checklist

Everything below is behavior that JVM unit tests **cannot** exercise: interactive
consent dialogs, the foreground service, real relay traffic, deep links, and
cross-app interop. The protocol/authorization logic underneath is covered by
`quartz` (`NostrConnectSignerServiceTest`) and `commons`
(`Nip46PermissionAuthorizerTest`, `Nip46ConsentIntegrationTest`) unit tests; this
list is the manual pass that earns "first-class" on a real device.

Run as the signer on one device/account ("bunker"); use a second app/account as
the client.

## Pairing
- [ ] **Bunker flow**: Settings → Nostr Signer → turn on → scan/copy the
      `bunker://` QR into a client (nsec.app, Coracle, Nostrudel, or a second
      Amethyst via `amy login bunker://…`). Client resolves your npub via
      `get_public_key`.
- [ ] **NostrConnect flow**: client shows a `nostrconnect://` code → "Scan a
      code" on the signer screen pairs it and the signer turns on.
- [ ] **Global scanner**: scan a `nostrconnect://` from the profile/search
      camera → lands on the signer screen and pairs.
- [ ] **Deep link**: tap a `nostrconnect://` link (web/other app) → Amethyst
      opens the signer screen and pairs (cold start AND already-running).

## Note preview in the sign dialog (device-only)
- [ ] A `sign_event`/publish request renders the unsigned event as a **NoteCompose
      preview** (text + media + mentions, authored by the signing account), with
      the "Show event" JSON toggle still available below it.
- [ ] Works for both a NIP-46 remote app and a napplet Publish/SignEvent.
- [ ] When the main Activity is gone (app fully backgrounded, only the signer
      foreground service alive → `CallSessionBridge.accountViewModel` is null),
      the dialog falls back to the plain content quote + JSON without crashing.
- [ ] **Risk to watch:** NoteCompose is feed UI rendered inside a standalone
      dialog Activity; if it reads a CompositionLocal only provided by the main
      scaffold it could crash at runtime (compiles fine). Verify on device; if it
      misbehaves, the JSON fallback path is one boolean away.

## Entry point + connected-apps management (2026-07-16)
- [ ] **Drawer entry**: the signer opens from the left drawer's "You" section, directly
      under Wallet (moved out of Settings). It's also available as a bottom-bar favorite.
- [ ] **Dedicated apps screen**: "Manage connected apps" on the signer screen opens a
      NIP-46-only list (name, npub, relay count, last-used, trust chip), separate from the
      napplet/nsite/browser Connected Apps screen. NIP-46 apps no longer appear there.
- [ ] **Idle auto-forget**: an app left unused for 7 days is dropped on the next signer
      start (its background relay subscription goes with it); an app still signing is kept.

## Comes-to-front on a request (2026-07-16)
- [ ] **Backgrounded surfacing**: with Amethyst fully backgrounded (Android 12+), a client
      signing/connect request pops the consent dialog — via a full-screen-intent notification
      on the high-importance "Signing requests" channel (the `startActivity` fast path is
      BAL-blocked when backgrounded). On a locked screen it launches straight to the dialog;
      while actively on another app it shows a heads-up prompt to tap.
- [ ] **Foreground**: with Amethyst in the foreground the dialog opens directly (no extra
      notification — `SignerConsentNotifier` no-ops when `foregroundTracker.isForeground`).
- [ ] **Android 14+ caveat**: `USE_FULL_SCREEN_INTENT` is restricted for non-calling apps,
      so the FSI may degrade to a heads-up rather than auto-launch — verify the prompt still
      arrives and is tappable. Requires notification permission (already needed for the
      always-on service).

## Consent (Tier 1)
- [ ] **First-connect trust picker**: a bunker-flow connect with a valid secret
      shows the trust-level dialog (Full trust / Reasonable / Paranoid) BEFORE any
      signing; choosing a level records it in Connected Apps.
- [ ] **Cancel/Block**: dismissing the connect dialog rejects the connection (no
      silent grant).
- [ ] **Per-op ASK**: with a REASONABLE app, ask the client to sign a
      **kind 0 / kind 3 / delete (5)** or **decrypt a DM** → the per-op dialog
      appears (these are excluded from the auto-allowed set).
- [ ] **Remember variants**: "allow for this op" stops re-prompting; "session"
      stops until the signer restarts; "24h/30d" expire; "deny for op" sticks.
- [ ] **PARANOID app** prompts on every request; **FULL_TRUST** never prompts.
- [ ] **Timeout**: ignore a per-op dialog for 2 minutes → the request fails
      closed (deny) and the signer keeps serving later requests (not wedged).

## Anti-spam rotation (already shipped)
- [ ] "New address" → confirm dialog → old `bunker://` goes dark, connected apps
      drop, QR updates; re-pairing a legit app keeps its trust level.

## Visibility (Tier 2)
- [ ] Signer screen shows "Signing as npub1…", a live "Recent activity" feed
      (signed kind N / encrypted / decrypted / shared pubkey, green/red dot,
      relative time), and per-app history on the Connected-App detail screen.
- [ ] The Connected-App detail screen for a remote client shows its name/url,
      not a raw `nip46:` coordinate.

## Reliability (Tier 3)
- [ ] **Relay health**: kill connectivity → status shows "X of N relays
      connected"; restore → "all connected".
- [ ] **Boot restart**: enable the signer, reboot the device → the foreground
      service comes back and the signer answers a request without reopening the
      app. (Same for an app update via `MY_PACKAGE_REPLACED`.)
- [ ] **Doze/background**: after ~30 min idle in Doze, a request still gets
      serviced (may lag by a relay reconnect).

## Interop matrix
Pair + sign + nip44 encrypt/decrypt + logout against each:
- [ ] nsec.app
- [ ] Coracle
- [ ] Nostrudel
- [ ] snort / other NIP-46 client

## Audit findings — known limitations (2026-07-16)

An adversarial review of the signer logic surfaced these. The head-of-line
issues below share one root cause: `authorize()`/`onConnect()` run **inline** in
`NostrConnectSignerService`'s single-consumer loop, and relay-set changes restart
that loop via `collectLatest`.

- **FIXED — unbounded first-connect prompt.** `Nip46ConsentBridge.requestConnect`
  now has the same 120s `withTimeoutOrNull` as `requestOp`, so an ignored
  first-connect dialog can no longer wedge the loop forever.
- **FIXED — consent no longer blocks other clients (needs on-device
  validation).** The service now fans each request out into a child coroutine
  under a `Semaphore(maxConcurrentHandles=16)`; dedup/staleness/rate-limit stay on
  the single consumer, only `handle()` runs concurrently. So a request awaiting a
  prompt no longer stalls auto-allowed traffic, and several prompts can be pending
  at once. Two guards keep this safe: (1) the identity signer's crypto is
  serialized by `BunkerRequestProcessor.cryptoLock` — authorization (the prompt)
  runs UNLOCKED, only the sign/encrypt/decrypt holds the lock — so an external
  NIP-55 app never sees concurrent IPC ops; (2) first-connect consent is
  serialized by `Nip46PermissionAuthorizer.connectLock` so two connects can't stack
  dialogs. Per-op prompts batch: the shared `SignerConsentCoordinator.pending`
  flow drives one dialog (1 pending) or a checkbox list (>1). Covered by
  `BunkerRequestProcessorConcurrencyTest`, but the on-device paths below still need
  a real run:
  - [ ] **Burst batching:** a client fires several dangerous-kind requests at once
        → one batched sheet with checkboxes + select-all, Allow/Deny selected,
        "Remember" toggle. Approving a subset leaves the rest pending.
  - [ ] **Auto-allowed keeps flowing:** while a prompt sits open, a REASONABLE
        auto-allowed request from another app still gets signed and answered.
  - [ ] **No concurrent external-signer ops:** with a NIP-55 external signer, two
        approved requests do not drive overlapping IPC (they serialize).
  - [ ] **Fail-closed on dismiss:** backing out of the batched sheet denies every
        still-open request (not just the selected ones).
- **Relay-set change cancels in-flight work.** A `logout` (or a new nostrconnect
  pairing) mutates the listen set → `collectLatest` restarts the service →
  cancels the in-flight `handle()`. Practical impact is low (a logout ACK is lost
  but the client is leaving; a pairing-time cancel makes other clients retry).
  Proper fix: manage subscriptions incrementally (diff add/remove) instead of a
  full restart. Deferred (same reason).
- **Low-severity, left as-is:** activity-log records an O(capacity) list copy per
  serviced request (negligible under rate-limiting); the per-author rate limiter
  evicts by insertion order rather than LRU (the 3-arg `accessOrder`
  `LinkedHashMap` isn't in KMP commonMain); first-time transport-key/secret mint
  is unsynchronized (practically serialized on the UI thread).

## Deliberately NOT changed
The always-on foreground **notification** was left as-is: it is shared with the
relay/DM always-on service, so retitling it "Signing for N apps" or deep-linking
it to the signer screen would be wrong when the service is up for another reason.
Interactive consent uses its own dedicated dialog Activity, so it needs no
notification actions.
