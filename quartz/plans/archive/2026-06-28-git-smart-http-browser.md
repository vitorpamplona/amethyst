# Git smart-HTTP browser for NIP-34 repositories

> **Status:** shipped — the full git smart-HTTP v2 client (`PktLine`, `Packfile`, `GitDelta`, `GitSmartHttpTransport`, `GitHttpClient`) ships in `nip34Git/git/` (jvmAndroid) with protocol/packfile/commit tests.
> _Audited 2026-06-30._

Date: 2026-06-28
Module: `quartz` (protocol client) + `amethyst` (UI)

## Goal

On the git repository screen, render the repo's `README` in the first tab and add
a **Code** tab that browses the repository's file tree and renders source files
(syntax-highlighted), reading directly from the repo's git `clone` URL.

NIP-34 (`GitRepositoryEvent`, kind 30617) only guarantees a git `clone` URL — a
plain git-over-HTTP(S) endpoint. To work with **every** server in the Nostr git
ecosystem (GRASP / `ngit` bare servers as well as GitHub/GitLab/Gitea), we read
files via the **git smart-HTTP protocol v2** rather than host-specific web APIs.

## Survey (what already exists — reused, not duplicated)

- `GitRepositoryEvent.clones()` / `webs()` — quartz, kept as-is. Source of the
  endpoint URLs.
- `OkHttpClientFactory` + the `(String) -> OkHttpClient` provider already injected
  into quartz fetchers (`OkHttpNip05Fetcher`, `OkHttpLnurlEndpointResolver`). The
  git client takes the same lambda, so it inherits Tor/proxy routing and the
  onion-rewrite interceptors for free.
- `RenderContentAsMarkdown` (amethyst) — renders the `README.md` rich-text.
- `GitRepositoryScreen` HorizontalPager + `SecondaryTabRow` — the tab host we
  extend.

Genuinely new: the git smart-HTTP client (no git/pack code existed anywhere) and
a syntax-highlighted code viewer.

## Protocol client (`quartz`, `jvmAndroid` source set)

Placed in `jvmAndroid` (shared by Android + Desktop JVM) so it can use
`java.util.zip.Inflater`, `java.security.MessageDigest`, and OkHttp directly with
no expect/actual. Not needed on iOS/native.

Package `com.vitorpamplona.quartz.nip34Git.git`:

- `PktLine` — git pkt-line frame reader/writer (flush `0000`, delim `0001`,
  response-end `0002`, data otherwise).
- `GitObjectType`, `GitTreeEntry`, `GitTree`/commit parsers — pure byte parsing of
  loose object payloads (`<mode> <name>\0<20-byte oid>` tree entries; `tree <oid>`
  from a commit).
- `Packfile` — parses a v2 packfile: object headers, zlib inflate per object,
  `OBJ_OFS_DELTA` / `OBJ_REF_DELTA` resolution, and SHA-1 oid computation
  (`sha1("<type> <len>\0" + content)`).
- `GitDelta` — copy/insert delta instruction decoder.
- `GitSmartHttpTransport` — the three HTTP exchanges:
  1. `GET  {clone}/info/refs?service=git-upload-pack` (`Git-Protocol: version=2`)
     → capability advertisement (we read `fetch` features incl. `filter`, and
     `object-format`).
  2. `POST {clone}/git-upload-pack` `command=ls-refs` → HEAD oid + default branch.
  3. `POST {clone}/git-upload-pack` `command=fetch` → sideband-framed packfile.
- `GitHttpRepository` / `GitHttpClient` — high level:
  - `loadRepository(cloneUrl)` → `fetch want <HEAD> deepen 1 filter blob:none`,
    yielding the commit + **all** trees in one shallow request. Builds
    `treeOid -> entries` and a navigable snapshot (`entriesAt(path)`).
  - `loadBlob(oid)` → lazy partial-clone fetch `want <oid> filter blob:none`
    (cached). Falls back to a full `deepen 1` (no filter) snapshot for servers
    that don't advertise `filter`, in which case all blobs arrive up-front.

### Why this shape

`filter blob:none` + `deepen 1` is exactly how a git partial clone browses a tip
without downloading file contents; lazy blob-by-oid fetch is the same mechanism
git uses to backfill missing blobs, so any server that advertises `filter`
supports both. We do **not** request `thin-pack`, so packs are self-contained and
deltas are `OFS_DELTA` (offset based) — no cross-pack base lookups.

Validated end-to-end against `github.com/octocat/Hello-World.git`; the captured
wire bytes are checked in as offline test fixtures (CI-safe, no network).

## UI (`amethyst`)

- Tabs become: **README**, **Code**, Overview, Issues, Patches.
- `GitReadmeTab` — fetches `README(.md/.markdown/...)` from the root tree, renders
  via `RenderContentAsMarkdown`; falls back to the repo description / overview.
- `GitCodeTab` — file browser (breadcrumb + folders-first listing) backed by a
  `GitRepositoryBrowserViewModel`; tapping a file opens `GitFileViewer`.
- `GitFileViewer` — markdown for `.md`, otherwise monospace + syntax highlighting
  via the `dev.snipme:highlights` KMP library (Apache-2.0 — permissive, OK).

## Out of scope (future)

- Writing/committing, branch switching beyond HEAD, history/blame, large-binary
  preview, sha256 object-format repos.
