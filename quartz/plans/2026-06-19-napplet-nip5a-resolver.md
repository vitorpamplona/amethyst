# NIP-5A static-site resolver + napplet (NIP-5D) alignment

Date: 2026-06-19
Status: resolver landed (quartz commonMain); alignment questions open

## Context

[napplet.run](https://napplet.run) proposes "napplets" — small, sandboxed web
apps distributed over Nostr + Blossom, where a **shell** brokers dangerous
capabilities (signing, relay access, storage) and the applet runs as untrusted
code behind a trust boundary. Relevant upstream material:

- Web packages: <https://github.com/napplet/web>
- NAPs track (capability + wire-format specs): <https://github.com/napplet/naps>
- Runtime packages: <https://github.com/kehto/web>
- Playground: <https://kehto.github.io/web/playground>

The important overlap with Amethyst: napplet **distribution** reuses the nsite
static-website event shape that Quartz already implements —
`com.vitorpamplona.quartz.nip5aStaticWebsites` (`RootSiteEvent` kind 15128,
`NamedSiteEvent` kind 35128). Each manifest pins request paths to content-addressed
Blossom blobs via `path` tags (`[path, <sha256>]`) plus `server` tags. **NIP-5D**
is the web projection on top of NIP-5A (iframe hosting, `postMessage` transport,
`window.napplet.*` capability surface).

So Amethyst already owns the bottom half of the stack. The cheap, high-leverage
move (vs. building a full shell) is to be the reference **resolver** for NIP-5A and
help keep the event shape from forking across napplets / nsites / NMP / Tiles.

## What landed

A platform-agnostic resolver in `quartz/commonMain`, under
`nip5aStaticWebsites/resolver/`:

- **`StaticSitePathLookup.kt`** — `normalizeStaticPath()` (strips query/fragment +
  leading slash, expands root/dir requests to `index.html`), `List<PathTag>.resolvePath()`
  (leading-slash-insensitive match), `guessStaticContentType()` (web asset MIME map;
  Blossom serves blobs untyped, so the host must label them).
- **`StaticSiteResolver.kt`** — `verify(blob, hash)`, `candidateUrls(servers, hash)`,
  and `suspend resolve(requestPath, paths, servers, fetch): StaticSiteResolution`.
  HTTP is injected via a `BlobFetcher` typealias so Quartz keeps no HTTP dependency;
  `commons`/`amethyst` supply an OkHttp-backed fetcher.

**Trust model (the point):** the signed manifest is the authority; the Blossom
server is untrusted. `resolve` downloads the content-addressed blob from each listed
server in order and accepts the **first whose recomputed sha256 matches the pin**. A
server that substitutes/corrupts/truncates a blob fails verification and is silently
skipped — it can withhold content but can never forge it. Tests cover root/dir/query
normalization, slash-insensitive lookup, MIME guessing, and the security cases
(tampered server skipped → falls through to honest server; all-tampered →
`Unresolvable`; undeclared path → `PathNotInManifest` without fetching).

Deliberately **out of scope** in the protocol layer: SPA "serve index.html for any
unknown route" fallback (weakens the path→hash guarantee — a shell policy decision),
and the author's kind:10063 Blossom-list as a server fallback (caller appends it
before calling `resolve`; `BlossomServerResolver`/BUD-10 already exists in `amethyst`).

## Open alignment questions for the napplet author

These are worth resolving before three projects (napplets, nsites, NMP, Tiles) fork
the event shape. Raise upstream on napplet/naps:

1. **Manifest kind: 35128 vs 35129.** napplet/naps describes NIP-5A distribution as
   **kind 35128** (the exact `NamedSiteEvent` Amethyst already renders), while
   napplet/web describes the NIP-5D web manifest as **kind 35129**. Confirm the
   intent: is a napplet a *plain* NIP-5A nsite (35128) that a NIP-5D-aware shell
   simply *recognizes*, or a *distinct* 35129 event? If 35129 is distinct, what does
   it add over 35128 — and should it embed/reference a 35128 rather than duplicate the
   `path`/`server` tag set? Avoid silently colliding with the nsite 35128 Amethyst
   already publishes and resolves.

2. **Capability declaration vs NIP-89.** napplet manifests carry a `requires` /
   capability declaration (which NAP domains the applet needs: identity, relay,
   value, …). Amethyst already models "an app handles these event kinds" via NIP-89
   `AppDefinitionEvent` (kind 31990, `k`-tags). Should napplet capability `requires`
   reuse NIP-89 semantics (or a documented superset) instead of a parallel tag, so a
   single client can reason about both?

3. **Aggregate build hash.** Both repos mention an aggregate hash over the per-file
   set. Is that pinned as a dedicated tag on the manifest (canonical
   serialization/ordering defined), or only implied by the set of `path` hashes? The
   resolver verifies per-file hashes today; if there is a canonical aggregate, Quartz
   should expose `aggregateHash()` and verify it too.

4. **`server` semantics.** Are `server` tags an *ordered preference* list (our
   resolver assumes order = priority) or an unordered set the host load-balances? And
   is the author's kind:10063 Blossom list an implicit fallback, or must servers be
   exhaustively listed on the manifest?

## Possible follow-ups (not in this change)

- Wire an OkHttp `BlobFetcher` in `commons` and point `StaticWebsite.kt` at the
  resolver to render verified content (today it only shows manifest metadata + opens
  links in an external browser).
- The full shell: Android WebView host (`allow-scripts`, no `allow-same-origin`) +
  `postMessage`↔`NostrSigner`/Blossom/relay bridge + a per-applet permission ledger
  (reuse the signer-prompt design in
  `amethyst/plans/2026-05-25-appfunctions-signer-prompts.md`). The brokers it needs
  (three `NostrSigner` types, Blossom upload/download, relay client, NIP-57 zaps) all
  already ship — the WebView + consent UI are the only genuinely new pieces.
