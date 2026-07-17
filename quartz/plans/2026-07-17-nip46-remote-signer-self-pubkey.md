# NIP-46 remote signer: `pubKey` must be the user identity, not the transport key

**Date:** 2026-07-17
**Module:** `quartz` (core fix) + `desktopApp` / `cli` (affected front ends)
**Status:** design / not started
**Severity:** correctness — private data invisible/corrupted for **bunker (NIP-46) accounts** on desktop + CLI. Android unaffected.

## The bug

`NostrSignerRemote` extends `NostrSigner(signer.pubKey)`
(`quartz/.../nip46RemoteSigner/signer/NostrSignerRemote.kt:54-72`), where the
constructor arg `signer: NostrSignerInternal` is the **ephemeral NIP-46
transport keypair**. `NostrSigner.pubKey` is a plain `val` never reassigned. So
for a bunker account:

```
NostrSignerRemote.pubKey  == transport key T   (ephemeral, per-connection)
the user's real identity  == user key U        (only via get_public_key RPC)
events the bunker signs    have pubKey == U
```

Every site that treats `signer.pubKey` as "myself" therefore uses **T instead of
U**. Two failure modes:

### Mode A — authorship guard short-circuits (unconditional failure)
Sites that gate on `signer.pubKey == event.pubKey`. Event was signed by the
bunker (`pubKey == U`), `signer.pubKey == T`, so the guard blocks decryption
before any key math:
- `nip51Lists/PrivateTagArrayEvent.kt:44-66` — `decrypt()` throws
  `UnauthorizedDecryptionException`; `privateTags()` returns `null`.
- `nip51Lists/PrivateReplaceableTagArrayEvent.kt:44-53` — same guard.
- `nip37Drafts/DraftWrapEvent.kt:52` — `canDecrypt = signer.pubKey == pubKey`
  → always false.

Covers **all private NIP-51 lists** (private bookmarks, the private half of the
mute list, private follows/people lists, private hashtag/geohash lists…) and
**NIP-37 drafts**. Fails even for data Amethyst itself wrote through the bunker
(written as U, read back with T). Independent of key-stability nuances.

### Mode B — wrong self-peer / wrong author filter (no guard)
Sites that self-encrypt directly to `signer.pubKey`:
- `nip51Lists/encryption/PrivateTagsInContent.kt:41,57,64,74` — the crypto under
  the guarded lists (`nip44Encrypt(…, signer.pubKey)` / `decrypt(…, signer.pubKey)`).
- `concord/cord02Community/ConcordCommunityListEvent.kt:56,74` +
  `ConcordCommunityList.kt:170,222` — Concord list self-encryption.
- `nip60Cashu/token/CashuTokenEvent.kt:74`, `quote/CashuMintQuoteEvent.kt:136`.
- `amethyst/.../nip78AppSpecific/AppSpecificState.kt:60` (Android-only file → moot).

The bunker derives ECDH(u, T) instead of ECDH(u, U): standard-client content
(peer U) fails MAC; content Amethyst writes is sealed to peer T, unreadable by
every other client and by the same user's local-key login. Author filters like
`authors = listOf(signer.pubKey)` also query T, so the user's own U-authored
events are never fetched (e.g. `Account.importConcordCommunities` filter, the
CLI `concord list`).

## Impact by front end (verified)

- **Android `amethyst/`: UNAFFECTED.** No `NostrSignerRemote` is ever
  constructed (`AccountCacheState.loadAccount` builds only `NostrSignerInternal`
  / `NostrSignerExternal`; the only `NostrSignerRemote` reference is a type-check
  in `MeteringNostrSigner.kt:122`). Android has no bunker login, so
  `signer.pubKey == account pubkey` always.
- **Desktop `desktopApp/`: AFFECTED.** Bunker login is a first-class path
  (`AccountManager.loginWithBunker` / `loginWithNostrConnect`). Confirmed live:
  `BookmarksScreen.kt:189,355` → `list.privateBookmarks(account.signer)` → guard
  returns null → **private bookmarks always empty** for bunker accounts. Same
  guard breaks the private mute section, private follow lists, and drafts. Cashu
  self-encryption sealed to the wrong peer. (No Concord feature on desktop.)
- **CLI `cli/`: AFFECTED.** Same failures; `concord list` breaks on both the
  author filter and the decrypt. The `amy concord import`/`read` diagnostics
  (2026-07-17) already work around it by using `ctx.identity.pubKeyHex` — but
  every other self-encryption site in the CLI is still wrong.

Transport key is **persisted + stable across restarts** on both desktop and CLI,
so Mode-B data *round-trips within one install* (looks fine locally) but is
non-portable and non-standard; a fresh `nostrconnect://` session regenerating
the key makes it permanently unrecoverable. Mode-A fails regardless.

## Fix

**Core (quartz):** make `NostrSignerRemote.pubKey` return the **verified user
key U**, not the transport key T. Constraints:
- The user key is known only after the `get_public_key` RPC, so it can't be set
  from the raw `bunker://` parts at construction. Resolve it during the
  login/connect handshake (desktop `NostrConnectLoginUseCase` and CLI login
  already call `getPublicKey()`), and construct/finalize the signer with U as its
  identity.
- **Keep the transport key for NIP-46 transport.** Internal sites that legitimately
  need T — the response subscription filter `p: signer.pubKey`
  (`NostrSignerRemote.kt:91`), request addressing/encryption to the bunker
  (`RemoteSignerManager`, `remoteKey = remotePubkey`) — must reference the
  transport keypair explicitly (`this.signer.pubKey`), **not** the base-class
  `pubKey`. Audit every `signer.pubKey`/`pubKey` use inside `NostrSignerRemote`
  and `RemoteSignerManager` and pin transport uses to the transport keypair
  before flipping the base `pubKey`.

Two implementation options:
1. **Explicit identity param** — add `userPubkey: HexKey` to `NostrSignerRemote`
   (or a factory that RPCs `get_public_key`, then builds the signer with U as
   `NostrSigner(userPubkey)`). Cleanest; makes the contract explicit. Requires
   touching every construction site (desktop `AccountManager`, CLI `Context`,
   `NostrConnectLoginUseCase`).
2. **Late-resolved pubKey** — allow the base identity to be set once after the
   connect handshake. Smaller call-site churn, but `NostrSigner.pubKey` becoming
   non-`val` ripples widely; less desirable.

Prefer **(1)**.

For local (`NostrSignerInternal`) and external NIP-55 (`NostrSignerExternal`)
signers, `signer.pubKey` already equals the account key, so the change is a
**no-op** for them — only bunker accounts change behavior. That keeps the blast
radius to exactly the broken case.

Once `NostrSignerRemote.pubKey == U`, all Mode-A guards pass and all Mode-B
self-encryption uses the right peer; **amy can drop its manual-decrypt
workaround** and the `Account.importConcordCommunities` filter/decrypt become
correct for any future bunker use.

## Migration / data caveat

Any Mode-B data a bunker user *already wrote* was sealed to peer T. After the
fix (peer U) it becomes unreadable — but it was already unreadable everywhere
except that one install, so the fix trades a hidden-corruption state for a
correct one. Private NIP-51 lists / drafts (Mode-A) were never successfully
written wrong (the guard blocked the write path's read-modify-write too), so
there's nothing to migrate there — they simply start working. Call this out in
the PR; no migration code needed, but a note for affected desktop users is kind.

## Testing

- **quartz unit:** construct a `NostrSignerRemote` whose transport key ≠ user
  key; assert `pubKey == userKey`; assert `PrivateTagArrayEvent.decrypt` /
  `DraftWrapEvent.canDecrypt` succeed against a U-authored event; assert the
  NIP-46 response subscription still filters on the transport key.
- **round-trip:** self-encrypt a private list with the remote signer, decrypt
  with a *local* `NostrSignerInternal` for U → must match (proves portability).
- **desktop:** bunker login → private bookmarks / private mute / drafts render.
- **CLI:** `amy concord list` (not just `import`) loads communities for a bunker
  account; drop the `import` workaround and confirm `newest.decrypt(signer)`
  works.

## Suggested sequence

1. quartz: audit transport-vs-identity `pubKey` uses inside
   `NostrSignerRemote`/`RemoteSignerManager`; pin transport uses to the transport
   keypair.
2. quartz: add the explicit-identity construction (option 1) + unit tests.
3. desktop `AccountManager` + `NostrConnectLoginUseCase`: pass the verified U
   into the signer; verify private lists on-device.
4. CLI `Context`: pass `identity.pubKeyHex` as the signer identity; drop the amy
   Concord decrypt workaround.
