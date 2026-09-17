# NIP-CC (Geocaching) — gap analysis for Quartz and Amethyst

Status: **step 1 done** — the Quartz protocol package (`quartz/…/nipCCGeocaching/`) and its
tests are in. Nothing in `amethyst/` or `commons/` touches NIP-CC yet, so the app is still blind
to geocaches; §3 onwards is unstarted.

Spec: <https://github.com/nostr-protocol/nips/blob/master/CC.md> (merged into
`master`; listed in the NIPs README at line 117 and in the kind tables for
`7516`, `7517`, `37516`, `37517`). Read at 2026-09-17.

## 1. What the spec defines

| Kind    | Class       | Purpose                                                                 |
|---------|-------------|-------------------------------------------------------------------------|
| `37516` | addressable | Geocache listing — name, location, difficulty/terrain/size, hint, …       |
| `7516`  | regular     | Found log — claims a find, optionally carrying an embedded `7517`         |
| `7517`  | regular     | Verification — **signed by the cache's verification key**, not the finder |
| `37517` | addressable | Curation list — ordered `a` references to `37516` listings                |
| `1111`  | existing    | DNF / note / maintenance / archived logs, as NIP-22 comments on the cache |

Secondary mechanics: `n` type modifiers (`first-to-find`, `art`) with a
"one per category, ignore unknown" rule, an `F` tag that locks in the
first-to-find winner, a `mission` ("Key Quest") tag, and a client SHOULD to
ROT13 hints so they don't spoil.

### Confirmed with a grep, not from memory

At the time of the survey,
```
grep -rn "37516\|7517\|37517\|[Gg]eocach" --include=*.kt
```
returned only incidental hex substrings in unrelated tests — no geocaching code anywhere in
`quartz`, `commons`, `commonsUI`, `amethyst`, or `cli`. The Quartz half of that gap is now
closed; the rest stands.

## 2. Quartz — new code

New package `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nipCCGeocaching/`,
laid out like `experimental/roadstr/` (event + `TagArrayExt` + `TagArrayBuilderExt`
+ `tags/`), which is the closest existing analogue: a geohash-anchored,
user-submitted place event.

```
nipCCGeocaching/
├── listing/
│   ├── GeocacheListingEvent.kt          # 37516, BaseAddressableEvent + SearchableEvent
│   ├── TagArrayExt.kt / TagArrayBuilderExt.kt
│   └── tags/
│       ├── CacheNameTag.kt              # "name" (required)
│       ├── DifficultyTag.kt             # "D", 1..5, reject out-of-range
│       ├── TerrainTag.kt                # "T", 1..5
│       ├── CacheSizeTag.kt              # "S" -> enum micro|small|regular|large|other
│       ├── CacheTypeTag.kt              # "t", defaults to `traditional` when absent
│       ├── TypeModifierTag.kt           # "n" -> first-to-find | art (+ category)
│       ├── HintTag.kt                   # "hint"
│       ├── MissionTag.kt                # "mission", first-wins if duplicated
│       ├── VerificationKeyTag.kt        # "verification" (hex pubkey)
│       └── FirstToFindWinnerTag.kt      # "F" (hex pubkey), first-wins
├── log/
│   ├── GeocacheFoundLogEvent.kt         # 7516
│   └── tags/EmbeddedVerificationTag.kt  # "verification" holding a 7517 as JSON
├── verification/
│   ├── GeocacheVerificationEvent.kt     # 7517
│   ├── tags/FinderCacheTag.kt           # the composite "a" tag — see §2.3
│   └── GeocacheVerificationValidator.kt # the 4-step check from the spec
├── curation/
│   ├── GeocacheCurationListEvent.kt     # 37517, BaseAddressableEvent + SearchableEvent
│   └── tags/ThemeTag.kt, MapStyleTag.kt # "theme", "map"
├── comment/GeocacheLogTypeTag.kt        # "t" on kind 1111: dnf|note|maintenance|archived
└── FirstToFindResolver.kt               # winner selection + F lock-in precedence
```

### 2.1 Reuse — do not re-implement

The listing event is mostly existing tags wearing a new kind number:

| Spec tag        | Reuse                                                                  |
|-----------------|------------------------------------------------------------------------|
| `d`             | `nip01Core/tags/dTag`                                                    |
| `g`             | `nip01Core/tags/geohash/GeoHashTag` + `geohashes()`                      |
| `image`         | `nip23LongContent/tags/ImageTag`                                         |
| `r`             | `nip51Lists/tags/RelayTag` (`"r"`)                                       |
| `a`             | `nip01Core/tags/aTag/ATag` (curation list + found log)                   |
| `title`, `description` (37517) | `nip51Lists/tags/TitleTag`, `.../DescriptionTag`          |
| embedded 7517   | `Event.fromJson(...)` (same trick `LnZapEvent.zapRequest()` uses)        |
| signature check | `Event.verifySignature()` in `nip01Core/crypto/EventExt.kt`              |
| kind 1111 logs  | `nip22Comments/CommentEvent` **as-is** — it already models `A/K/P` + `a/k/p` addressable root+parent (`rootAddress()`, `replyAddress()`, `hasRootAddress()`). Only the `t` log-type vocabulary is new. |
| required-tag gate | `containsAllTagNamesWithValues(REQUIRED_FIELDS)`, as `ClassifiedsEvent.isWellFormed()` does |

`GeoHashTag.geohashMipMap()` already emits the full precision ladder, but
**reversed and starting at 1 char**; the spec asks for precisions 3–9 in
coarse-to-fine order. Add a bounded variant (`geohashMipMap(min, max)`) rather
than publishing 1- and 2-character geohashes that are useless for proximity
search and noisy on relays.

### 2.2 Type modifiers need a category model, not a string set

Rule 2 ("at most one `n` per category, first occurrence wins") and rule 4
("ignore unrecognised values") mean `n` cannot be a flat `Set<String>`. Model it
as `enum class TypeModifierCategory { CLAIM_SEMANTICS, PRIZE_NATURE }` with
`first-to-find -> CLAIM_SEMANTICS`, `art -> PRIZE_NATURE`, and a parser that
keeps the first value per category and drops unknowns. Forward compatibility is
an explicit spec requirement, so unknown values must not make the event
unparseable.

### 2.3 Spec warts to handle defensively

1. **The `7517` `a` tag is not a NIP-01 `a` tag.** It is
   `"<finder-pubkey-hex>:<geocache-naddr>"` — two colon-joined parts, where a
   real `a` value is `kind:pubkey:d`. `Address.parse` will not parse it: a 2-part
   value fails the `parts.size > 2` guard, falls through to the `naddr1` branch,
   doesn't start with `naddr1` either, and returns null **after logging a
   warning** — so nothing silently misreads it, but any generic `a`-tag consumer
   that meets a `7517` will spam `AddressableId` warnings. It needs its own
   `FinderCacheTag` parser, and the `naddr` half goes
   through `nip19Bech32/entities/NAddress`. Worth an upstream issue; implement
   the spec as written, tolerate the sane `kind:pubkey:d` form on read.
2. **`7517` is signed by a key the user does not own.** The finder scans a QR at
   the cache carrying the verification *private* key, then signs a `7517` with
   it. That cannot go through the account's `NostrSigner` — it needs an ephemeral
   `NostrSignerInternal(KeyPair(privKeyFromQr))`. That private key must never
   touch the Keystore, account state, or a log line. See §4.
3. **`created_at` is forgeable, so first-to-find ordering is advisory.** The spec
   says so itself: earliest `created_at`, ties broken by ascending event `id`,
   *until* the owner publishes an `F` tag — after which `F` wins unconditionally.
   `FirstToFindResolver` should encode exactly that precedence and the UI should
   mark a pre-`F` winner as provisional.
4. **`37515` is referenced but never defined.** The 37517 `a`-tag prose says
   "kind 37516 or 37515". Accept both on read; only ever write `37516`.
5. **`NIP-GD.md` (Good Deed) does not exist.** The `mission` tag's cross-reference
   is a dangling link (`raw.githubusercontent.com/.../NIP-GD.md` → 404). Implement
   `mission` as free text; do not build a Good-Deed integration against a spec
   that isn't published.
6. **`archived` is an `n`-modifier-shaped value living in `t`.** Owners archive a
   cache by adding `["t", "archived"]` to the *listing*, while `t` on the listing
   otherwise means cache *type* (`traditional`/`multi`/`mystery`) and `t` on a
   *comment* means log type. Three meanings, one tag name — parsers must be
   scoped per kind, not shared.

### 2.4 Registration points (each one is a real integration, not a formality)

- `quartz/utils/EventFactory.kt` — 4 new `KIND ->` branches. Without this the
  events parse as bare `Event` and every accessor above is dead code.
- `quartz/kinds/KindNames.kt` — 4 `KindName(..., "CC")` entries.
- `quartz/nip50Search/SearchableKinds.kt` — add `7516`, `37516`, `37517` if they
  implement `SearchableEvent`. **`SearchableKindsTest` sweeps kinds 0–65535 and
  asserts this list is exactly the searchable set**, so a missed entry lands as a
  failing test naming the number. `indexableContent()` for `37516` should be
  name + content; the `hint` deliberately should *not* be indexed (searching for
  a hint is spoiling).
- Tests under `quartz/src/commonTest/.../nipCCGeocaching/`: round-trip each kind
  against the spec's own example JSON, the `D`/`T` range guards, the
  one-`n`-per-category rule, the `F`-beats-`created_at` precedence, and a
  verification event whose signature does *not* match the `verification` pubkey.

## 3. Amethyst — new code

Good news: the expensive parts already exist and were built for road events
(kind 1315/1316).

**Reuse as-is:**
- `amethyst/ui/note/creators/location/LocationPickerMap.kt`,
  `LocationPreviewMap.kt`, `MapPinIcon.kt`, `GeohashLocationPickerDialog.kt` —
  an osmdroid/OSM interactive map with markers, tap-to-place, and a geohash
  picker dialog.
- `service/location/LocationState` + the `AroundMe` top-nav feed for "caches
  near me".
- `commons/.../relayClient/geohash/FilterPostsByGeohash.kt` — adding
  `GeocacheListingEvent.KIND` to `PostsByGeohashKinds` is a one-line change that
  puts caches into the existing geohash feed.
- NIP-22 comment rendering and composing — DNF/note/maintenance logs are already
  displayable threads once the root kind is known.

**New:**
- `commonsUI/.../ui/note/GeocacheCard.kt` — cache card (name, D/T/S badges,
  type + modifier badges, distance, ROT13-toggle hint), modelled on
  `RoadEventCard.kt`.
- `amethyst/ui/note/types/Geocache.kt` + dispatch branches in
  `ui/note/NoteCompose.kt` (~line 1405/1450 is where `ClassifiedsEvent` and
  `RoadEventReportEvent` branch) and in `ThreadFeedView.kt`.
- `LocalCache.kt` — consume the four kinds (the `is ClassifiedsEvent,` /
  `is RoadEventReportEvent,` groups at ~3759/3933 are the template).
- Cache detail screen: map, logs tab, "Log a find" / "Log DNF" actions, and —
  for a `first-to-find` cache with any valid verified find — the spec's
  "render as effectively archived, hide find affordances, show the winner".
- Cache composer (owner side): map picker → geohash ladder, D/T/S pickers, hint,
  optional verification keypair generation + QR export, mission.
- QR scan → `7517` signing flow (see §4).
- Curation list (37517) browse + detail, honouring `theme`/`map` as *defaults*
  the user can override.
- ROT13 helper — **does not exist anywhere in the repo** (`grep -i rot13` is
  empty). Small, belongs in `commons` next to the other text utils, with the
  hint rendered rotated and a tap to reveal.
- Strings for every label above (`commonsUI` `composeResources`), plus any new
  Material Symbol codepoints — which means running
  `./tools/material-symbols-subset/subset.sh` and committing the regenerated
  `.ttf`, or the icon renders as tofu.

## 4. Security review items

These are the parts worth a careful reviewer, not the event codecs:

1. **Verification private keys from QR codes are attacker-controlled input.**
   They arrive by scanning an object in the physical world. Treat the scanned
   value as untrusted: validate it is a 32-byte secp256k1 scalar, use it for one
   ephemeral signature, never persist it, never log it, and never route it
   through `NostrSignerInternal` instances that outlive the call.
2. **A `7517` proves presence, nothing else.** Validation must check all four
   spec steps — signature valid, signer == the listing's `verification` pubkey,
   finder pubkey in the `a` tag == the 7516 author, and the naddr resolves to
   *this* cache. Skipping step 2 or 3 lets anyone replay someone else's
   verification into their own log.
3. **Cache locations are precise real-world locations of the user.** Publishing a
   9-character geohash is ~5m. The composer must make it obvious that this is
   public, and the "log a find" flow must not attach the *finder's* location.
4. **Hints/missions are untrusted remote text** — same rendering rules as any
   other user content (no auto-linkifying into a privileged surface).

## 5. Suggested sequencing

1. ~~Quartz protocol package + tests + the three registration files.~~ **Done** — 79 tests in
   `quartz/src/commonTest/…/nipCCGeocaching/`, plus the three new rows in the
   `indexable-content.golden` fixture and the `searchable-kinds.md` table. The layout follows
   nip88Polls: a folder per subject, each with its event, `TagArrayExt`, `TagArrayBuilderExt`
   and `tags/`.
2. Read path in Amethyst: `LocalCache` + `GeocacheCard` + `NoteCompose` dispatch
   + the one-line `PostsByGeohashKinds` addition. Caches become visible in the
   existing geohash/AroundMe feeds with zero new navigation.
3. Cache detail screen + NIP-22 logs (mostly wiring existing comment UI).
4. Write path: found logs, then DNF/note comments.
5. Verification: QR scan → ephemeral `7517` → embedded in the found log.
6. First-to-find / `F` lock-in, and the archived rendering rules.
7. Curation lists (37517) — independent of 1–6 and the easiest to defer.

Steps 1–2 are the "make Amethyst not blind to geocaches" milestone and are worth
shipping before anything in 3+ is designed.
