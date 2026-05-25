# Plan: Fix Desktop .deb Launch Crash + CI Smoke Test

**Issue:** [#2819](https://github.com/vitorpamplona/amethyst/issues/2819)
**Branch:** `fix/desktop-deb-launch-crash`
**Date:** 2026-05-19

## Problem

v1.08.0 `.deb` crashes on Ubuntu 24.04 with `java/lang/management/ManagementFactory` error.

### Root Cause

v1.08.0's `build.gradle.kts` had **no `modules()` declaration** — jlink auto-detection missed `java.management`. Current `main` already has the fix (`modules("java.management", ...)` + full ProGuard rules), but **no CI step verifies the packaged app actually launches**.

### Verification

- `proguardReleaseJars` passes on current main (verified locally)
- Can't build `.deb` on macOS (jpackage requires Linux)
- Can't run `.deb` on macOS (dpkg/Linux binary)
- **Need CI to build + launch-test the .deb**

---

## Phase 1: Release .deb Build + Launch Smoke Test

New `workflow_dispatch` workflow that builds the release .deb on ubuntu and verifies it launches.

### Step 1: Add `compose.desktop.uiTestJUnit4` dependency

**File:** `desktopApp/build.gradle.kts`

```kotlin
dependencies {
    // ... existing ...
    testImplementation(compose.desktop.uiTestJUnit4)
}
```

This gives us `createComposeRule()` for in-process Compose UI tests. Requires xvfb on Linux (Skiko needs a display server).

### Step 2: Write a Compose UI smoke test

**File:** `desktopApp/src/jvmTest/kotlin/.../DesktopLaunchSmokeTest.kt`

Test that the app's root composable renders the login screen (default state = LoggedOut):

```kotlin
class DesktopLaunchSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loginScreenRenders() {
        compose.setContent {
            // Minimal App shell — just enough to prove the dependency graph loads:
            // Jackson, SQLite, secp256k1, ManagementFactory, Compose, Skiko
            MaterialTheme {
                LoginScreen(
                    accountManager = AccountManager.create(),
                    onLoginSuccess = {},
                )
            }
        }
        // If we get here without NoClassDefFoundError / UnsatisfiedLinkError,
        // the ProGuard rules and jlink modules are correct.
        compose.onNodeWithText("Amethyst", substring = true).assertExists()
    }
}
```

This exercises:
- Compose + Skiko rendering (login screen paints)
- Jackson (AccountManager loads account storage)
- SQLite (if local relay store initializes)
- secp256k1 JNI (crypto library loaded)
- `java.management` module (ManagementFactory via coordinator — may need to poke it explicitly)

### Step 3: Run existing tests under xvfb in `build.yml`

**File:** `.github/workflows/build.yml`

The Linux leg already runs `:desktopApp:test`. Once we add the UI test, it needs xvfb:

```yaml
# In the build-desktop job, Linux leg
- name: Install xvfb (Linux)
  if: runner.os == 'Linux'
  run: sudo apt-get update && sudo apt-get install -y xvfb

- name: Test + Build Desktop (gradle)
  run: |
    CMD="./gradlew :quartz:jvmTest :commons:jvmTest ... :desktopApp:test :desktopApp:${{ matrix.desktop-task }}"
    if [ "${{ runner.os }}" = "Linux" ]; then
      xvfb-run --auto-servernum $CMD
    else
      $CMD
    fi
```

### Step 4: New workflow — Release .deb smoke test (manual trigger)

**File:** `.github/workflows/smoke-test-desktop.yml`

```yaml
name: Desktop Smoke Test
on:
  workflow_dispatch:
  # Also run on PRs that touch desktop packaging
  pull_request:
    paths:
      - 'desktopApp/build.gradle.kts'
      - 'desktopApp/compose-rules.pro'
      - '.github/workflows/smoke-test-desktop.yml'

jobs:
  smoke-test-linux-deb:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - Checkout
      - Setup JDK 21 (Zulu)
      - Setup Gradle (cache)
      - Pre-fetch VLC + UPX (reuse from build.yml)
      - Install xvfb + rpm tooling
      - Build release .deb:
          ./gradlew :desktopApp:packageReleaseDeb
      - Relax libicu dependency
      - Install .deb:
          sudo dpkg -i desktopApp/build/compose/binaries/main-release/deb/*.deb
      - Launch smoke test:
          xvfb-run --auto-servernum timeout 20 /opt/amethyst/bin/Amethyst &
          PID=$!; sleep 10
          kill -0 $PID && echo "PASS" && kill $PID || exit 1
      - Run Compose UI tests under xvfb:
          xvfb-run --auto-servernum ./gradlew :desktopApp:test
```

This tests TWO things:
1. **Installed .deb actually launches** (process survives 10s = no crash)
2. **Compose UI test passes** (login screen renders correctly)

### Step 5: Verify .deb install path

Need to confirm jpackage installs to `/opt/amethyst/bin/Amethyst`. Check by:
- Looking at jpackage docs (default for Linux is `/opt/<packageName>`)
- Or: `dpkg -L amethyst` after install in CI to list files

---

## Implementation Checklist

| # | Task | File(s) |
|---|------|---------|
| 1 | Add `compose.desktop.uiTestJUnit4` test dep | `desktopApp/build.gradle.kts` |
| 2 | Add test tag to LoginScreen title | `desktopApp/.../LoginScreen.kt` |
| 3 | Write `DesktopLaunchSmokeTest` | `desktopApp/src/jvmTest/.../DesktopLaunchSmokeTest.kt` |
| 4 | Add xvfb to Linux leg of `build.yml` | `.github/workflows/build.yml` |
| 5 | Create `smoke-test-desktop.yml` workflow | `.github/workflows/smoke-test-desktop.yml` |
| 6 | Verify install path in first CI run | manual check |
| 7 | `spotlessApply` | all modified files |

---

## Unanswered Questions

1. **Install path** — Is it `/opt/amethyst/bin/Amethyst` or `/opt/Amethyst/bin/Amethyst`? (case-sensitive; `packageName = "Amethyst"`)
2. **AccountManager.create()** in test — does it need filesystem access? May need temp dir or mock
3. **VLC init** — `Main.kt` pre-inits VLC on a background thread. Smoke test should skip this or tolerate VLC absence on CI
4. **xvfb + Skiko compatibility** — JetBrains uses xvfb in their own CI (skiko repo), so should work, but need to verify with our Compose version (1.10.3)
5. **Should we also smoke-test the release DMG on macos-14?** (open the DMG, launch, check process alive)
