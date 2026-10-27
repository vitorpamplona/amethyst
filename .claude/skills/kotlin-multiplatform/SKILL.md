---
name: kotlin-multiplatform
description: KMP placement and Gradle in Amethyst: which module or source set, expect/actual vs ports, verifyKmpPurity, extracting Android composables to commonsUI, build.gradle.kts/libs.versions.toml edits, secp256k1 JNI variants, R8.
---

# Kotlin Multiplatform and Gradle in Amethyst

Module roles, the "share vs keep native" rule and the CLI-safe boundary are in
`AGENTS.md` (Architecture, Feature Workflow). Package placement inside `commons` /
`commonsUI` is in `commons/ARCHITECTURE.md` ("Where does my code go?", "Source sets")
and `commonsUI/ARCHITECTURE.md` ("Platform slots"). Read those first; this skill
adds the source-set mechanics and the build traps.

## Modules

`settings.gradle.kts` includes 13: `amethyst`, `nappletHost`, `benchmark`,
`baselineprofile`, `geode`, `commons`, `commonsUI`, `nestsClient`,
`marmotQuic`, `desktopApp`, `cli`, `relayBench`, `marmotBench`. Quartz is the
external `libs.quartz` (vitorpamplona/quartz) and QUIC the external `libs.quic`
(vitorpamplona/quic-kmp); `-PquartzDir=../quartz` / `-PquicKmpDir=../quic-kmp` build a
local checkout instead (an included build, so quic's own quartz dependency follows).

## Source sets

| Source set | Holds | Targets |
|---|---|---|
| `commonMain` | Pure Kotlin. Compiled for iOS/native too, so no JVM APIs | all |
| `jvmAndroid` | JVM libraries shared by Android + Desktop: OkHttp, `nestsClient`, NWC/LNURL | android, jvm |
| `androidMain` / `jvmMain` | Platform actuals (Keystore/DataStore vs keyring, Coil bridges) | one each |
| `iosMain`, `appleMain`, `nativeMain`, `linuxMain`, `macosMain` | quartz only (plus `iosMain` in commons/commonsUI) | Apple / native |
| `skikoMain` | commonsUI code shared by Desktop and iOS | jvm, ios |

Targets: `quartz` builds android, jvm, iosArm64, iosSimulatorArm64, linuxX64,
macosArm64. `commons` and `commonsUI` build android, jvm, iosArm64,
iosSimulatorArm64. `nestsClient`, `marmotQuic` are android + jvm. There is
no `iosX64`.

`jvmAndroid` is a custom intermediate set created with `create("jvmAndroid")`
before `androidMain`/`jvmMain` depend on it (see `commons/build.gradle.kts`).
Prefer the most common set that compiles: `commonMain` → `jvmAndroid` → platform.

kotlinx.serialization is the house JSON stack and OkHttp the house HTTP stack
(`AGENTS.md` › Tech Stack). Nostr JSON (events, messages, filters, tags) goes through
quartz's Kite (`OptimizedJsonMapper`, `KiteWriter`, `KitePretty`); quartz dropped Jackson
in 2.4.0, so don't add Jackson back. Don't migrate JVM HTTP to Ktor. Jackson and OkHttp
are banned in iOS-compiled sets, which the purity gate enforces.

## expect/actual vs ports and slots

- **expect/actual** for a small platform verb or value with no app state:
  `KmpLock`, `ConcurrentSet`, `SecureKeyStorage`, `isDebug`, `formattedDateTime`
  (commons), `rememberTextSharer`, `rememberShortNotice` (commonsUI). Put the actual
  in `jvmAndroid` when Android and Desktop share it.
- **A port** (interface the shell implements) when the platform side needs app
  state or services: `AccountViewModelHost`, `UserNoticeSink`, `LocalCacheHost`.
- **A slot** (CompositionLocal the front end installs) for things only a front end
  can draw: `LocalNotePlatform`, `LocalRichTextPlatform`.
- Not every quartz singleton is expect: `Secp256k1Instance`, `Log` and
  `LibSodiumInstance` are plain `object`s in `quartz/.../utils/`.
- Don't `expect` a whole screen or navigation; share it and put only the leaf
  behind a port or slot.

## Purity gate: `verifyKmpPurity`

`gradle/kmp-purity.gradle.kts`, applied by `commons` and `commonsUI` (quartz keeps a copy) and
wired into `check`, scans every iOS/native-compiled source set (`commonMain`,
`iosMain`, `skikoMain`, …, and their tests) and fails on `com.fasterxml.jackson`,
`okhttp3`, `System.currentTimeMillis` (use `TimeUtils.now()`), `Thread.sleep`,
`java.util.UUID` (use `kotlin.uuid.Uuid`), `kotlin.jvm.Synchronized` or bare
`synchronized(` (use `KmpLock`), `kotlin.jvm.Volatile` (use
`kotlin.concurrent.Volatile`). CI runs it on Linux in the lint job; the macOS
`test-commons-ios` / `test-commonsui-ios` jobs are authoritative. Run
`./gradlew :commons:verifyKmpPurity :commonsUI:verifyKmpPurity`
after touching `commonMain`. Quartz has its own copy of the task and its own iOS CI.

## Gradle traps

- **Versions live only in `gradle/libs.versions.toml`.** Add the library there and
  reference `libs.*`; never hardcode a version or copy one into docs. Check the
  license first (`AGENTS.md` › Dependency Licensing).
- **`test` is an alias for each KMP module's `jvmTest`.** Other targets
  (`:commonsUI:testAndroidHostTest`, the native ones) run only when named; see
  `AGENTS.md` › Build Commands.
- **testFixtures in KMP:** the source-set `dependencies { }` block
  (`KotlinDependencyHandler`) has no `testFixtures(...)`. Use a top-level block with
  the configuration name as a string:
  `dependencies { "jvmAndroidTestImplementation"(testFixtures(project(":geode"))) }`.
- **secp256k1 variants:** `secp256k1-kmp-common` in `commonMain`,
  `secp256k1-kmp-jni-android` in `androidMain`, `secp256k1-kmp-jni-jvm` in `jvmMain`
  and JVM tests. All three share one `version.ref`. A JVM classpath with the
  android variant fails at load with `UnsatisfiedLinkError`.
- **R8:** library keep rules ship in `quartz/consumer-rules.pro`. Never add a blanket
  `-keep class com.vitorpamplona.quartz.** { *; }`. R8 can't see reflection, so a
  moved/renamed reflective class is a silent release break: before tagging, run
  `python3 tools/r8-verify/verify_reflection_contract.py amethyst/build/outputs/mapping/<variant>/`
  (the release workflow checks `playRelease` and `fdroidRelease`) and update
  `tools/r8-verify/reflection-contract.txt` when you move a listed class.

## Extracting Android code to shared modules

Steps and the Android → multiplatform replacement table:
[references/extract-composable.md](references/extract-composable.md).
