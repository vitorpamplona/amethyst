# Test Results — Desktop Moderation & Safety

**Executed:** 2026-07-24 · **Build:** branch `feat/desktop-moderation-safety`
**Method:** live app (`./gradlew :desktopApp:run`) on a real account (22 relays
connected), plus automated unit tests. Moderation publishes are logged under the
`[Moderation]` tag.

Legend: **[x] PASS** · [ ] not run.

## Live app — verified

- [x] **Enforcement** — muting a user removes their notes from feeds live (no restart).
- [x] **Mute publishes** — kind-10000 mute list signed + broadcast to relays.
- [x] **Report (NIP-56) publishes** — kind-1984 report signed + broadcast to relays.
- [x] **Discoverability** — note **⋮** overflow **and** right-click both open the
      note menu with Mute / Report / copy / broadcast (same items).
- [x] **Report dialog** — reason picker + comment + Report / Block & report.
- [x] **Profile ⋮** — Mute/Unmute + Report on another user's profile.
- [x] **Content-warning blur** — NIP-36 notes collapse with tap-to-reveal;
      "always show sensitive content" toggle persists.
- [x] **Management screens** — Content Filters → Moderation & Safety lists
      muted users / hidden words / muted threads with remove/add.
- [x] **Feedback (new)** — moderation actions now show a snackbar
      (Muted user / Report sent / Reported & muted) and log the relay count.

### Captured logs (this session)

```
INFO: [Moderation] mute+   kind=10000 id=95057975 → 22 relays
INFO: [Moderation] report(spam) kind=1984 id=447fbf93 → 22 relays
INFO: [Moderation] report(spam) kind=1984 id=c6f58e03 → 22 relays
```

→ one mute and two spam reports were signed and delivered to **22 relays** each,
with **no failures**. (A zero-relay send would log `WARNING … → 0 connected
relays (not delivered)`; a signer error logs `WARNING … report failed: …`.)

## Automated tests — green

- [x] `DesktopMuteEnforcementTest` — muted author hidden (even if followed),
      hidden-word note dropped, clean notes pass.
- [x] `PreferencesSensitiveContentSettingsTest` — sensitive toggle maps
      `on→true` / `off→null` (never false) and persists.
- [x] `./gradlew :desktopApp:test :commons:jvmTest` — **BUILD SUCCESSFUL**.
- [x] `spotlessApply` clean; pre-commit hook (spotless + tests) green on every commit.

## Notes / known gaps
- Reports broadcast to **connected relays** (not a NIP-56 outbox split) — advisory per NIP-56.
- Thread *root* is always shown even if the author is muted (intentional — you navigated in).
