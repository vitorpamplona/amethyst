# Release Ops (Amethyst maintainers)

This is the **operational checklist the Amethyst team follows to ship a
release** — the account-specific, push-the-buttons side of cutting a version.
The *generic* build/release mechanics (how the CI pipeline works, the asset
naming contract, the secret names a fork must set, desktop packaging) live in
[`BUILDING.md`](BUILDING.md). Read that first; this doc only covers what is
specific to shipping the official Amethyst artifacts.

> Forks: you do **not** need this file. `BUILDING.md` has everything you need to
> build and release your own fork. This describes our accounts and channels.

---

## At a glance

A release is one tag push that fans out to four live distribution channels:

| Channel | Mechanism | Who pushes |
|---|---|---|
| **GitHub Releases** | Automatic — the `Create Release Assets` workflow builds + signs everything on the `v*` tag | CI |
| **Google Play** | **Manual** — download the signed AAB from the GH Release, upload in Play Console | Maintainer |
| **F-Droid** | **Pull** — F-Droid's build server builds the `fdroid` flavor from source when it sees the new tag | F-Droid (we just maintain the recipe + metadata) |
| **Zapstore** | `zsp publish` reads `zapstore.yaml`, signs a Nostr release event with Amethyst's nsec. Its `google` variant serves the **`complete`** APKs | Maintainer |
| **Homebrew + Winget** | ⚠️ **Not shipping.** The bump workflows run, but skip: neither package exists upstream yet | Nobody (see § 3) |

Maven Central (the `quartz` library) also publishes automatically from the same
workflow — as a *step* at the end of the `deploy-android` job, not a job of its
own, so don't expect to find it in the run's job list.

---

## 1. Pre-tag checklist

1. **Bump the version** in `gradle/libs.versions.toml` — both keys:
   ```toml
   app     = "1.12.1"   # semver, drives every module + the tag
   appCode = "449"      # Android versionCode, monotonic — must increment
   ```
   That single edit propagates to Android (`versionName`/`versionCode`),
   Desktop & CLI (`packageVersion`), `quartz` (Maven version) and `geode`
   (`RelayInfo.VERSION`). Nothing else hardcodes the version.

2. **Write the changelog** as `docs/changelog/vMAJOR.MINOR.PP.md` (zero-padded,
   e.g. `v1.12.01.md`) and add it to `docs/changelog/README.md`. Follow the
   house style: plain text, short verb-first sentences.

   For the `## Translations` section, generate the credits instead of writing
   them by hand — no token needed:
   ```bash
   scripts/translators.sh
   ```
   This runs offline. It reads `docs/changelog/translators.json` (kept next to
   the changelogs), which CI keeps fresh: the Crowdin sync workflow's
   `seed-translators` job records everyone who has translated since the last `v*`
   tag, with their languages, in the file's `sinceLastTag` list, and accumulates
   their npubs in the forever-growing `mappings` registry. The script resolves
   that list to npubs and prints the `## Translations` block grouped by language.
   Contributors with no npub yet are listed under `UNMAPPED` — credit them by
   hand, then add their npub under `mappings` so future releases pick them up
   automatically. (To re-query Crowdin live as a sanity check, run
   `scripts/translators.sh --seed` with `CROWDIN_PROJECT_ID` /
   `CROWDIN_PERSONAL_TOKEN` set, which refreshes the file.)

3. **Publish the release-notes note on Nostr** — *minor releases only.* In
   practice this id has only ever been bumped on `x.y.0` (1.11.0, 1.12.0,
   1.13.0); patch releases leave it pointing at their minor's note. Publish with
   Amethyst's account and paste the event id into `amethyst/build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "RELEASE_NOTES_ID", "\"<new-event-id-hex>\"")
   ```
   This id is what the in-app drawer's "Release Notes" link and the donation
   card open (`DrawerContent.kt`, `ShowDonationCard.kt`). It must point at the
   note for *this* version, so publish the note **before** tagging and commit
   the new id together with the version bump.

   <!-- TODO(maintainer): document the exact command/account used to publish the
        release-notes note (which signer, which relays). -->

4. **Sanity-build locally** (optional but cheap): `./gradlew assembleRelease`
   and a desktop `packageDistributionForCurrentOS`, or run the workflow's
   dry-run (see BUILDING.md § Dry-run).

---

## 2. Cut the release

Commit, tag, push — see [`BUILDING.md` § Release runbook](BUILDING.md#release-runbook)
for the exact commands. The tag must equal `app` from the catalog (the workflow
asserts this and fails fast otherwise). A clean `vMAJOR.MINOR.PATCH` tag is
classified **stable** and runs the Homebrew/Winget bump workflows; anything with
a `-rc`/`-beta`/`-alpha`/`-dev` suffix is a prerelease and skips them.

Heads-up on the `git push`: this repo has `git-credential-manager` configured as
a credential helper, and it blocks on an interactive prompt (a plain
`GIT_TERMINAL_PROMPT=0` does **not** stop it — the push just hangs). If that
happens, push using `gh`'s helper for the one command:

```bash
git -c credential.helper= -c credential.helper='!gh auth git-credential' push upstream main
```

When the `Create Release Assets` workflow finishes (~25–30 min) the GH Release
holds **55 assets**, per the asset-name contract:

- **Android (21):** 5 Complete APKs + 5 Google Play APKs + 5 F-Droid APKs +
  2 AABs + the F-Droid `.apks` set for Accrescent + 3 R8 mappings
  (`amethyst-complete-*-v…apk`, `amethyst-googleplay-*-v…apk` / `.aab`,
  `amethyst-fdroid-*-v…apk` / `.aab` / `.apks`). **Complete** is the full app;
  **Google Play** is the same build with Health Connect stripped out, which is
  the only one Play review accepts.
- **Desktop (14):** macOS DMG (**arm64 only** — there is no Intel DMG), Windows
  MSI (x64 only — **no arm64 MSI**) + portable zip (x64, arm64), and Linux
  DEB/RPM/AppImage/flatpak/tar.gz in both x64 and arm64. BUILDING.md § Release
  runbook has the per-leg breakdown and why the two gaps exist.
- **CLI (10):** the `amy` artifacts — the no-JRE `jvm.tar.gz`, macOS arm64,
  Windows x64 + arm64, and Linux DEB/RPM/tar.gz in both x64 and arm64
- **Relay (10):** the `geode` artifacts, same matrix as `amy`. The geode Docker
  image is **not** a release asset — it goes to the registry, so don't count it
  here.
- **Maven Central:** `com.vitorpamplona.quartz:quartz:<version>` published.
  `repo1.maven.org` lags the publish by tens of minutes — a 404 right after the
  run is normal. Confirm the step's log says "Deployment is being published to
  Maven Central", and compare against the *previous* version's POM before
  concluding anything is broken.

---

## 3. Per-channel shipping

### GitHub Releases — automatic
Nothing to do beyond pushing the tag. Verify the asset count (BUILDING.md
§ Verify). macOS is **arm64-only** — there is no Intel DMG, so a single
`amethyst-desktop-<version>-macos-arm64.dmg` is the expected, correct result.

Three of those assets are the R8 mapping files
(`amethyst-{complete,googleplay,fdroid}-mapping-<version>.txt.gz`). Do not prune
them from old releases — they are the only way to read a crash report from a
build that old (§ 7).

### Google Play — manual upload
1. Download `amethyst-googleplay-<version>.aab` from the GH Release.
2. Play Console → app `com.vitorpamplona.amethyst` → **Production** (or the
   staged-rollout track we're using) → create release → upload the AAB.
3. The release notes field can reuse the `docs/changelog` text.
4. Roll out.

### F-Droid — pull / build-from-source
F-Droid does **not** accept an upload from us. Its build server polls the repo,
and when it sees the new `v*` tag it builds the **`fdroid` product flavor** from
source (reproducibly) per the recipe in the separate
[`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) repo
(`metadata/com.vitorpamplona.amethyst.yml`), then signs and publishes to the
F-Droid repo on its own cadence.

What we own to keep that working:
- The **`fdroid` flavor** (`amethyst/src/fdroid/…`) must stay free of
  proprietary deps — it swaps Firebase/Google services for UnifiedPush and
  no-op/open implementations (ML Kit, writing assistant, push). Google-only
  libraries live in `amethyst/src/google/`, which only the `complete` and `play`
  flavors compile. Health Connect (`amethyst/src/health/`) is not one of them —
  `fdroid` keeps it.
- The fastlane metadata under `fastlane/metadata/android/` (descriptions,
  images). F-Droid reads per-version changelogs from
  `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` if present —
  add one (e.g. `449.txt`) when we want a changelog shown on F-Droid; otherwise
  none is displayed.
- The `AutoUpdateMode`/`UpdateCheckMode` in the fdroiddata recipe tracks tags,
  so a correct `vX.Y.Z` tag + bumped `versionCode` is usually all F-Droid needs.

After a release, just confirm F-Droid picked up the new version (it can lag a
few days): <https://f-droid.org/packages/com.vitorpamplona.amethyst/>.

### Zapstore — `zsp publish` with Amethyst's nsec
[Zapstore](https://zapstore.dev/) is a Nostr-native app store. The `zsp` CLI
reads [`zapstore.yaml`](zapstore.yaml) at the repo root (name, summary,
description, tags, license, `icon`, screenshots, `supported_nips`, and the
`variants` regexes that match our `*-fdroid-*.apk` / `*-complete-*.apk`
GH-release assets), then publishes a signed software-release event to Nostr
relays.

```bash
# from the repo root, after the GH Release assets exist
zsp publish
```

It signs with **Amethyst's nsec** — provide the key the way `zsp` expects
(`SIGN_WITH` env var / prompt / its own config), never commit it.

**Relays.** `zsp` does *not* take relays from `zapstore.yaml`; it reads the
`RELAY_URLS` env var (comma-separated) and defaults to `wss://relay.zapstore.dev`
when unset. To fan the release event out to more relays for discoverability,
set `RELAY_URLS` for the run:

```bash
RELAY_URLS="wss://relay.zapstore.dev,wss://nos.lol,wss://nostr.mom,wss://vitor.nostr1.com" \
  SIGN_WITH=<amethyst-nsec> zsp publish
```

Keep `wss://relay.zapstore.dev` in the list — that is the relay the Zapstore app
itself reads from.

### Homebrew + Winget — ⚠️ Winget not shipping yet

`bump-homebrew.yml` and `bump-winget.yml` are wired to open PRs against
`Homebrew/homebrew-cask` (cask `amethyst-nostr`), `Homebrew/homebrew-core`
(formula `amy`) and `microsoft/winget-pkgs` (`VitorPamplona.Amethyst`). They can
only *update* a package that already exists upstream, so until the one-time
bootstrap lands they detect the absence and skip with a `::warning::`.

Bootstrap status:

| Channel | Upstream package | State |
|---|---|---|
| **Homebrew cask** | `Homebrew/homebrew-cask` → `amethyst-nostr` | **Live** — merged 2026-08-24, upstream at 1.14.0 |
| **Homebrew formula** | `Homebrew/homebrew-core` → `amy` | **Live** |
| **Homebrew formula** | `Homebrew/homebrew-core` → `geode-relay` | Not submitted — renamed from `geode`, which is permanently reserved for Apache Geode |
| **Winget** | `microsoft/winget-pkgs` → `VitorPamplona.Amethyst` | **Submitted at v1.14.0** — [PR #422752](https://github.com/microsoft/winget-pkgs/pull/422752), still open pending CLA + review |

Until each lands, that channel delivers nothing and its users get the desktop
app or CLI from GitHub Releases only. Re-check before assuming — the state above
is a snapshot, and two calls answer it:
`gh api repos/microsoft/winget-pkgs/contents/manifests/v/VitorPamplona` and
`curl -s -o /dev/null -w '%{http_code}' https://formulae.brew.sh/api/cask/amethyst-nostr.json`
(404 = still absent).

Two separate faults kept this invisible until v1.13.1, both now fixed:

1. **The workflows never ran at all** — for *any* release. They triggered on
   `release: types: [released]`, and GitHub does not raise workflow-triggering
   events for a release created by `GITHUB_TOKEN`, which is exactly how
   `create-release.yml` creates it. They now trigger on `workflow_run` after
   `Create Release Assets` succeeds, which also fixes a latent race — the old
   event fired while assets were still uploading.
2. **Nothing exists upstream to bump.** `brew bump-cask-pr` and
   `winget-releaser` can only *update* an existing package. The first
   submission is a manual, human-reviewed PR: BUILDING.md § Homebrew cask
   (one-time initial PR) and § Winget (one-time initial submission).

Until someone does that bootstrap, a green release run means the bump workflows
*skipped cleanly* — not that Homebrew/Winget shipped. Check the run's warnings
if you want to confirm which case you're in.

**Both bumps are half-manual by design.** CI does the bookkeeping with
`GITHUB_TOKEN` only — verifying the artifact, computing hashes, and opening an
in-repo PR syncing the reference packaging files. Pushing upstream needs
credentials that would be dangerous as CI secrets (a `repo`-scoped PAT is
readable by anyone with push access here), so a maintainer runs the last step:

```bash
# after merging the sync PRs
export HOMEBREW_GITHUB_API_TOKEN=ghp_...     # classic PAT, `repo` scope
scripts/bump-homebrew-cask.sh v1.16.0

scripts/bump-winget.sh v1.16.0               # no token — uses your `gh` auth
```

Both scripts re-verify the published artifact's sha256 before submitting, and
the Homebrew one additionally refuses if the DMG is not notarized + stapled.
See BUILDING.md § Package-manager credentials.

All four in-repo sync workflows (`amy` formula, `geode` formula,
`amethyst-nostr` cask, winget manifests) open PRs against *this* repo on every
release. Merge them to keep the reference packaging files current.

---

## 4. Operated infrastructure

### Push notification server

The two Google-services (FCM) flavors deliver push through a server we operate at
`push.amethyst.social`, built from
[`vitorpamplona/amethyst-push-notif-server`](https://github.com/vitorpamplona/amethyst-push-notif-server).
It registers devices, watches their NIP-65 inbox / NIP-17 DM relays, and sends
wake-up pushes.

- **`complete` and `play` flavors** → push via this server (Firebase/FCM).
- **`fdroid` flavor** → UnifiedPush through a distributor app the user installs
  (e.g. ntfy); it does **not** use our server.
- Both are complemented by the on-device always-on `NotificationRelayService`
  (see [`PULL_NOTIFICATION.md`](PULL_NOTIFICATION.md)), which keeps the user's
  relay connections alive without any push server at all.

The push server has its **own repo, deploy, and release cadence** — a normal app
release does **not** redeploy it. Coordinate a server deploy only when the app
changes the registration/push contract (token format, payload, or endpoint), so
the running server stays compatible with the shipped app.

<!-- TODO(maintainer): document the push-server deploy steps + hosting, and
     which app-side changes require a coordinated push-server deploy. -->

---

## 5. Secrets ownership & rotation

The workflow's required secrets and what they sign are inventoried generically
in [`BUILDING.md` § Secrets](BUILDING.md#secrets-the-ci-needs). Amethyst-specific
ownership:

| Secret(s) | Protects | Rotation |
|---|---|---|
| `SIGNING_KEY`, `KEY_ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD` | The **Android upload keystore** — losing/leaking it is the worst case; Play app signing identity | Keep the keystore backed up offline; never rotate casually (Play upload key reset is a support process) |
| `SONATYPE_USERNAME`, `SONATYPE_PASSWORD` | Maven Central namespace `com.vitorpamplona` | On compromise |
| `SIGNING_PRIVATE_KEY`, `SIGNING_PASSWORD` | The **GPG key** signing Maven artifacts | Per GPG key expiry |
| *(none for Homebrew/Winget)* | — | Both bumps run on a maintainer's machine — `scripts/bump-homebrew-cask.sh` and `scripts/bump-winget.sh` — so neither channel's PAT ever becomes a CI secret. See BUILDING.md § Package-manager credentials |
| `CROWDIN_PERSONAL_TOKEN`, `CROWDIN_PROJECT_ID` | Translation sync | On compromise |

Owner assignments and rotation reminders live with the team (issue tracker).

<!-- TODO(maintainer): name the owner per secret and where backups live. -->

---

## 6. Post-release verification

- [ ] GH Release: 49 assets, sizes sane, and the asset-name set matches the
      previous release (see the `diff` one-liner in BUILDING.md § Release
      runbook). macOS is arm64-only — do **not** look for an Intel DMG.
- [ ] Maven Central: `quartz:<version>` resolves (allow tens of minutes of
      propagation; the publish step's log is the authoritative signal).
- [ ] Play Console: rollout started, no policy rejection.
- [ ] Zapstore: release event visible.
- [ ] F-Droid: new version detected (may lag days).
- [ ] Four sync PRs opened against this repo (`amy` formula, `geode` formula,
      `amethyst-nostr` cask, winget manifests) — merge them.
- [ ] Cask pushed upstream: `scripts/bump-homebrew-cask.sh vX.Y.Z` (manual, needs
      `HOMEBREW_GITHUB_API_TOKEN` in your shell).
- [ ] Winget pushed upstream: `scripts/bump-winget.sh vX.Y.Z` (manual, no token —
      uses your `gh` auth).
      Both scripts error clearly until the one-time bootstrap PRs land (§ 3).
- [ ] In-app "Release Notes" link opens the note matching `RELEASE_NOTES_ID`
      (only bumped on minor releases — patches keep pointing at the x.y.0 note).
- [ ] Push still works on a `complete` or `play` build (only if the push
      contract changed — see § 4); UnifiedPush still works on an `fdroid` build.

If anything ships broken, see [`BUILDING.md` § Incident response](BUILDING.md#incident-response).

---

## 7. Crash reports & retrace

Release builds are minified **and obfuscated** (they have to be: Play Console
drops apps whose DEX is under 25% optimized or obfuscated out of store surfaces
— see `amethyst/proguard-rules.pro` for the whole story). So a raw stack trace
from a release build looks like this:

```
java.lang.IllegalStateException: something blew up
	at onh.B(r8-map-id-12c710927a584543dbe1e2e867db95460bc44482efe53283f86798648c1cfc00:7)
```

That is not lost information, it is encoded information. Paste the report in and
run it:

```bash
scripts/retrace.sh crash-report.txt
pbpaste | scripts/retrace.sh            # or straight off the clipboard
```

Nothing else to supply. A report's first line names its own build —
`java.lang.IllegalStateException: 1.16.0-COMPLETE` — so the script resolves the
tag (`v1.16.0`) and the flavor (`COMPLETE` → `complete`, `PLAY` → `googleplay`),
downloads that release's mapping asset and caches it. For a bare stack trace with
no such header, name the build yourself with
`--release v1.16.0 --flavor complete`; for a build you made locally, pass its
`mapping.txt` directly.

```
java.lang.IllegalStateException: something blew up
	at androidx.compose.foundation.text.input.TextFieldCharSequence.getText(TextFieldCharSequence.kt:58)
	at androidx.compose.foundation.text.input.TextFieldState.getText(TextFieldState.kt:146)
	at com.vitorpamplona.amethyst.ui.screen.loggedIn.home.ShortNotePostViewModel.onMessageChanged(ShortNotePostViewModel.kt:1727)
```

Note that retrace gave back **three** frames where the crash reported one: R8
had inlined two of them. That is worth internalising — it is the reason a raw
trace's line number cannot be trusted even in the pre-obfuscation builds, where
optimization was already inlining. Retracing is not a tax obfuscation imposed;
it is how you read an optimized build at all.

**The wrong mapping is worse than none** — it produces confident, wrong names.
So the script refuses to guess: the `r8-map-id-<hash>` in the trace *is* the
`pg_map_id` header of the mapping that built it, and the two are compared before
anything is printed:

```
error: this mapping did not build this report.
       report:  deadbeef...
       mapping: 12c71092... (amethyst-googleplay-mapping-v1.16.0.txt.gz)
```

`--force` overrides if you really mean it. (`googleplay` vs `fdroid` matters —
the three flavors are separate R8 runs with different mappings.)

**Per channel:**

| Where the report came from | What to do |
|---|---|
| Play Console / Android vitals | Nothing. AGP embeds the mapping in the `.aab` (`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`), so Play deobfuscates automatically. |
| A NIP-17 DM from the in-app crash reporter, a GitHub issue, F-Droid, Zapstore, Accrescent | `scripts/retrace.sh <report>` — it reads the build off line 1 and fetches the mapping. |
| A build you made locally | `scripts/retrace.sh amethyst/build/outputs/mapping/<variant>/mapping.txt report.txt` |

`scripts/retrace.sh` downloads the R8 version named in the mapping's own header
from Google's Maven and caches it, so it needs no pinned tooling and keeps
working across AGP bumps. It needs no `gh` auth either — release assets on a
public repo are plain HTTPS downloads.

Two details of the report format in `ReportAssembler` exist for this and should
not be "tidied" away: the headline carries the **fully qualified** exception
class (a bare `simpleName` obfuscates to `a`, which retrace cannot resolve
because it has no package), and stack frames are written as `    at <frame>`
(retrace only rewrites frames it recognises, and it recognises them by the
leading `at`). The script repairs the missing `at` on reports from older builds,
but new reports should not need repairing.

**Do not delete mapping assets from old releases.** They are the only copy —
CI's are gone when the job ends, and a mapping cannot be regenerated after the
fact (it would need a bit-identical rebuild, and R8's renaming is not stable
across runs).

### Did obfuscation break anything?

R8 cannot see reflection, so nothing in the build tells you that a keep rule
stopped matching. `tools/r8-verify/reflection-contract.txt` lists every place
something outside the DEX resolves a name at runtime — JNI symbols, Jackson DTO
field names, enum constants persisted in DataStore, WorkManager's stored worker
class names, the Cast `OptionsProvider` named in a manifest `<meta-data>` value
— and the release workflow asserts each one against what R8 actually emitted,
for every shipped flavor, before any asset is collected:

```bash
python3 tools/r8-verify/verify_reflection_contract.py \
    amethyst/build/outputs/mapping/playRelease/
```

Run it on any local minified build too. It takes under a second and needs no
device.

**Adding reflection means adding two things**: the keep rule, and a line in the
contract. A rule with no contract line is unverified and will rot silently.

What this does *not* cover is reflection nobody wrote down. For that the honest
controls are a staged Play rollout (the crash reporter retraces itself now, so
a break is legible within hours) and exercising NIP-47 wallet connect, NIP-46
bunker login, Tor, scheduled posts and a settings round-trip on a minified
build before shipping.
