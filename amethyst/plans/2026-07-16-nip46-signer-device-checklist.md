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

## Deliberately NOT changed
The always-on foreground **notification** was left as-is: it is shared with the
relay/DM always-on service, so retitling it "Signing for N apps" or deep-linking
it to the signer screen would be wrong when the service is up for another reason.
Interactive consent uses its own dedicated dialog Activity, so it needs no
notification actions.
