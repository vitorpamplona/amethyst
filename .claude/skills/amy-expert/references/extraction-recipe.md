# Extract-from-Android recipe

The single most common reason an Amy feature request stalls: the
logic it needs lives in `amethyst/` with Android-only imports. You
cannot call it from `cli/`. You have to move it first.

This recipe is how.

## When to extract

Before writing a new command, ask:

1. Does the piece of logic I need exist in `quartz/` or `commons/`?
   - **Yes** → use it.
   - **No, but it's in `amethyst/`** → extract. This file.
   - **No, it doesn't exist anywhere** → design it in `commons/`
     directly. Write a plan doc under `commons/plans/` if it's a
     new subsystem.

2. Never duplicate `amethyst/` logic into `cli/`. That's a debt you
   will pay later when the Android caller drifts.

## Recipe

Land this as its own commit, **before** the commit that adds the
CLI command.

### Step 1 — Find the class

```bash
grep -rn "fun followUser\|class FollowListManager" amethyst/src/main/java/
```

Identify the minimum unit to move. Sometimes it's a whole file,
sometimes one function. Prefer the smallest unit that makes the
command possible.

### Step 2 — List Android-only dependencies

Walk the imports. The usual offenders:

| Dependency | Treatment |
|---|---|
| `android.content.Context` | Often accidental — inline if only used for logging or preferences. Otherwise, invert as constructor arg. |
| `android.content.SharedPreferences` | Abstract behind an interface in `commons/`; Android actual uses SharedPreferences, JVM actual uses a JSON file. |
| `androidx.work.WorkManager` | Rarely shareable — if the CLI needs it, simplify the flow to not require background scheduling. |
| `android.util.Log` | Replace with `quartz` `PlatformLog` (already multiplatform). |
| `android.graphics.Bitmap` | Almost never needed by Amy. Keep in Android and split the function. |
| `android.net.Uri` | Replace with `kotlinx.io` path types or a plain `String`. |
| `androidx.compose.*` | Must stay out of `commons/commonMain` unless you're in a Compose-Multiplatform module. Amy doesn't depend on Compose. |

### Step 3 — Pick a migration strategy per dependency

- **Inline-able.** One call, trivial. Delete it.
- **Platform-abstractable.** Add `expect` in `commons/commonMain/` +
  `actual` in `commons/androidMain/` + `actual` in `commons/jvmMain/`.
  See `kotlin-multiplatform` skill for the mechanics and for the
  `jvmAndroid` source-set pattern used throughout this repo.
- **Inversion-of-control.** Take the Android dependency as a
  constructor arg with an interface type. Amy supplies a JVM flavour;
  Android supplies the Context-backed one.

### Step 4 — Move the code

```bash
# Target location depends on what it is:
# - Protocol → quartz/src/commonMain/kotlin/…
# - Business logic → commons/src/commonMain/kotlin/…
# - UI → commons/src/commonMain/… (needs Compose Multiplatform)
git mv amethyst/src/main/java/com/.../FollowListManager.kt \
       commons/src/commonMain/kotlin/com/.../FollowListManager.kt
```

Update package declarations. Run `./gradlew spotlessApply`.

### Step 5 — Update the Android caller

The amethyst/ caller now imports from the new location. Often this
is the only code change visible in the Android app.

If the Android caller was using a concrete Android-backed
dependency, it now supplies that concrete dependency explicitly.

### Step 6 — Add a JVM test

In `commons/src/commonTest/kotlin/…` or `commons/src/jvmTest/kotlin/…`
(depending on what the code exercises), add a test that runs on JVM.
This guards against Android-only imports sneaking back in, and it's
the only way to be sure Amy can now call the code.

### Step 7 — Commit

Single commit, descriptive:

> refactor(follow): extract FollowListManager to commons for CLI reuse
>
> Move FollowListManager from amethyst/model/nip02FollowLists/ to
> commons/commonMain/.../followLists/. Android's SharedPreferences
> dependency is inverted behind FollowListStore (interface); Android
> keeps the SharedPreferences-backed actual, new JvmFollowListStore
> writes JSON to disk. No behaviour change on Android.

### Step 8 — Now add the CLI command

Separate commit. Follows the pattern in `command-template.md`.

## Cautionary notes

- **Don't extract speculatively.** Only extract what the current
  command needs. A feature-complete port can happen later; right now
  the goal is to unblock one command without adding surface area you
  don't have a second caller for.
- **Android is allowed to keep side-effects.** Notifications,
  background services, Intents, camera, permissions dialogs — those
  stay in `amethyst/`. Amy's job isn't to replicate UX, it's to
  exercise the protocol underneath.
- **Check the consumers.** Sometimes the "logic" you want is already
  partially in `commons/`, and the `amethyst/` class is just a thin
  wrapper. In that case, re-use the `commons/` class directly and
  delete the wrapper or keep it if Android genuinely needs it.
- **Tests first if you're nervous.** Copy the existing
  `amethyst/`-side test (if any), make it JVM-only by removing
  Android imports, and watch it pass after the move.

## Red flags during extraction

Stop and reconsider if:

- The Android class is 1000+ lines. Extract only the piece the CLI
  needs; leave the rest for a follow-up.
- You need `Context` in 30 places. It's probably being used as a
  grab-bag; sort by actual use (strings, preferences, services, …)
  and abstract those individually.
- You find yourself writing `expect class` with a dozen methods. A
  fine-grained interface is usually clearer than a monolithic
  expect-actual.
- You're about to add a Compose import to `cli/`. Stop.
