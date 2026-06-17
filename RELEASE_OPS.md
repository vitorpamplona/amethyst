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

A release is one tag push that fans out to five distribution channels:

| Channel | Mechanism | Who pushes |
|---|---|---|
| **GitHub Releases** | Automatic — the `Create Release Assets` workflow builds + signs everything on the `v*` tag | CI |
| **Google Play** | **Manual** — download the signed AAB from the GH Release, upload in Play Console | Maintainer |
| **F-Droid** | **Pull** — F-Droid's build server builds the `fdroid` flavor from source when it sees the new tag | F-Droid (we just maintain the recipe + metadata) |
| **Zapstore** | `zsp publish` reads `zapstore.yaml`, signs a Nostr release event with Amethyst's nsec | Maintainer |
| **Homebrew + Winget** | Automatic — `bump-homebrew.yml` / `bump-winget.yml` fire on stable tags | CI |

Maven Central (the `quartz` library) also publishes automatically from the same
workflow.

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

3. **Publish the release-notes note on Nostr** with Amethyst's account and paste
   its event id into `amethyst/build.gradle.kts`:
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
classified **stable** and triggers the Homebrew/Winget bumps; anything with a
`-rc`/`-beta`/`-alpha`/`-dev` suffix is a prerelease and skips them.

When the `Create Release Assets` workflow finishes (~25–30 min) the GH Release
holds, per the asset-name contract:

- **Android:** 5 Google Play APKs + 5 F-Droid APKs + 2 AABs
  (`amethyst-googleplay-*-v…apk` / `.aab`, `amethyst-fdroid-*-v…apk` / `.aab`)
- **Desktop:** 8 assets (DMG/MSI/DEB/RPM/AppImage/zip/tar.gz)
- **CLI:** the `amy` artifacts
- **Maven Central:** `com.vitorpamplona.quartz:quartz:<version>` published

---

## 3. Per-channel shipping

### GitHub Releases — automatic
Nothing to do beyond pushing the tag. Verify the asset count and that Intel +
ARM DMGs are both present (BUILDING.md § Verify).

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
  libraries live behind the `play` flavor.
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
`variants` regexes that match our `*-fdroid-*.apk` / `*-googleplay-*.apk`
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
RELAY_URLS="wss://relay.zapstore.dev,wss://relay.damus.io,wss://nos.lol,wss://relay.nostr.band" \
  SIGN_WITH=<amethyst-nsec> zsp publish
```

Keep `wss://relay.zapstore.dev` in the list — that is the relay the Zapstore app
itself reads from.

### Homebrew + Winget — automatic
`bump-homebrew.yml` and `bump-winget.yml` fire on stable tags and open PRs
against `Homebrew/homebrew-cask` (cask `amethyst-nostr`) and
`microsoft/winget-pkgs` (`VitorPamplona.Amethyst`). No action unless one fails —
then see BUILDING.md § Bootstrap and § Incident response.

---

## 4. Operated infrastructure

### Push notification server

The Google Play (FCM) flavor delivers push through a server we operate at
`push.amethyst.social`, built from
[`vitorpamplona/amethyst-push-notif-server`](https://github.com/vitorpamplona/amethyst-push-notif-server).
It registers devices, watches their NIP-65 inbox / NIP-17 DM relays, and sends
wake-up pushes.

- **`play` flavor** → push via this server (Firebase/FCM).
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
| `HOMEBREW_TOKEN`, `WINGET_TOKEN` | Cask + winget bump PRs | **90-day cadence** (see BUILDING.md § Bootstrap) |
| `CROWDIN_PERSONAL_TOKEN`, `CROWDIN_PROJECT_ID` | Translation sync | On compromise |

Owner assignments and rotation reminders live with the team (issue tracker).

<!-- TODO(maintainer): name the owner per secret and where backups live. -->

---

## 6. Post-release verification

- [ ] GH Release: expected asset count, Intel + ARM DMGs, sizes sane.
- [ ] Maven Central: `quartz:<version>` resolves (allow propagation time).
- [ ] Play Console: rollout started, no policy rejection.
- [ ] Zapstore: release event visible.
- [ ] F-Droid: new version detected (may lag days).
- [ ] Homebrew + Winget bump PRs opened (stable only).
- [ ] In-app "Release Notes" link opens the note matching `RELEASE_NOTES_ID`.
- [ ] Push still works on a `play` build (only if the push contract changed —
      see § 4); UnifiedPush still works on an `fdroid` build.

If anything ships broken, see [`BUILDING.md` § Incident response](BUILDING.md#incident-response).
