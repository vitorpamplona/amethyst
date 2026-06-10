# Amethyst Skill Library — History & Changelog

> Historical record of how the `.claude/skills/` library was built and audited.
> The 8 original skills were created in 2025 using the skill-creator 10-step
> methodology (detailed per-skill progress logs pruned in 2026-06 — see git
> history of this file if you need them). For the current skill list and how
> the two skill layers (codebase-oriented vs technique-oriented) fit together,
> see the Skills section of `.claude/CLAUDE.md`.

## Phase 1 (2025): Core skills created

Eight hybrid domain skills (general expertise + Amethyst-specific patterns),
each with a SKILL.md plus bundled `references/` and `scripts/`:

1. **kotlin-multiplatform** — KMP architecture, the jvmAndroid source-set pattern, expect/actual catalog
2. **gradle-expert** — build system, version catalog, dependency troubleshooting
3. **kotlin-expert** — Flow state, sealed hierarchies, @Immutable, DSL builders
4. **compose-expert** — shared composables, state management, Material3, ImageVector
5. **desktop-expert** — Desktop UX, window management, Compose Desktop APIs
6. **android-expert** — Android navigation, permissions, platform APIs
7. **nostr-expert** — Nostr protocol, Quartz implementation, NIPs, events, tags
8. **ios-expert** — ⏸️ deferred (iOS targets are mature, but no iOS-specific UI work has surfaced in this repo yet)

## Phase 2 (2026-04): Audit & Expansion

After a full audit of the skill library, the following changes were made:

### Stale references fixed
- `CLAUDE.md` tech-stack versions replaced with a pointer to `gradle/libs.versions.toml` as the source of truth.
- `kotlin-multiplatform` reframed iOS as a mature target (not future) and added secp256k1-kmp version notes.
- `desktop-expert` Main.kt line references rewritten to match current layout (NavigationRail moved to `ui/deck/SinglePaneLayout.kt`); the obsolete "hardcoded ctrl = true anti-pattern" section replaced with a note that `isMacOS` branching is now applied throughout.

### Redundant files removed
- `.claude/skills/compose-desktop.md` deleted (superseded by `desktop-expert/`).

### New references added to existing skills
- `nostr-expert/references/nip19-bech32.md` — `Nip19Parser`, `Bech32Util`, `TlvBuilder`, entities.
- `nostr-expert/references/event-factory.md` — `EventFactory` dispatch + registering a new kind.
- `nostr-expert/references/crypto-and-encryption.md` — `EventHasher`, `Secp256k1Instance`, NIP-44, `SharedKeyCache`.
- `nostr-expert/references/large-cache.md` — `LargeCache<K,V>` + `ICacheOperations`.
- `kotlin-expert/references/common-utilities.md` — `NumberFormatters`, `TimeUtils`, `Hex`, `PubKeyFormatter`, `CoroutinesExt.launchIO`, etc.
- `compose-expert/references/rich-text-parsing.md` — `RichTextParser`, `UrlParser`, `GalleryParser`, NIP-92 imeta.
- `android-expert/references/image-loading.md` — Coil 3.x setup, custom fetchers, `MyAsyncImage`, `RobohashAsyncImage`.

### New skills created
- **`account-state/`** — `Account.kt` (50+ StateFlow properties) and `LocalCache.kt` event store.
- **`relay-client/`** — `ComposeSubscriptionManager`, filter assemblers, preloaders.
- **`feed-patterns/`** — `FeedFilter`, `AdditiveComplexFeedFilter`, `FeedViewModel` hierarchy in `commons/`.
- **`auth-signers/`** — `NostrSigner` abstraction across internal, NIP-46 remote, and NIP-55 external signers.

## Phase 3 (2026-06): Fable 5 config review

Instructions written to coach older models were removed now that the model
handles them natively; stale references fixed:

- `CLAUDE.md`: deleted the 5-step skill-approval "Workflow" section
  (skills auto-trigger; the approval loop blocked autonomous sessions);
  condensed "Verify, Don't Guess" to the repo-specific tooling pointers and
  dropped references to `/bugfix` / `/investigate` (never committed to this
  repo); replaced the mandated emoji survey matrix with one-line guidance.
- `android-expert` and `desktop-expert` SKILL.md gained YAML frontmatter —
  without it they were listed without trigger descriptions and never
  auto-invoked.
- `commands/extract.md`: fixed stale `shared-ui/` module name → `commons/`.
- `skills/quartz-kmp.md` breadcrumb deleted (KMP migration long complete;
  `quartz-integration` and `nostr-expert` cover its pointers).
- Stop hook moved to `.claude/hooks/stop-spotless.sh` and gated on modified
  Kotlin files, so Q&A-only turns no longer pay a Gradle invocation.
