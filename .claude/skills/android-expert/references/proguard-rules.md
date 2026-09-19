# Proguard Rules for Amethyst

Proguard configuration for optimizing and obfuscating Android APK while preserving necessary code.

## What is Proguard/R8?

**R8** is Android's default code shrinker and obfuscator (replaced Proguard in AGP 3.4.0+). It:
- **Shrinks** code by removing unused classes/methods
- **Obfuscates** code by renaming classes/methods to short names
- **Optimizes** code by inlining methods and removing dead code

## Amethyst Proguard Configuration

**Files:**

- `amethyst/proguard-rules.pro` — the app's rules. Read it before adding
  anything: it is commented rule by rule.
- `quartz/consumer-rules.pro` — merged into the R8 configuration of **every**
  app that depends on Quartz, this one included (wired via
  `optimization.consumerKeepRules` in `quartz/build.gradle.kts`). A rule added
  here silently applies to somebody else's whole program.
- The AGP default `proguard-android-optimize.txt`, which already contributes the
  Android-wide basics (Parcelable CREATOR fields, `native <methods>`, the enum
  `values()`/`valueOf()` pair, …). Don't restate its rules.
- `commons/`, `commonsUI/` and the other library modules deliberately have **no**
  rules files. They are not minified and they wire no consumer rules, so a file
  there would be dead configuration.

### The policy: keep only what is reached BY NAME

Google Play measures how much of a shipped app's DEX R8 actually optimized and
renamed, and warns — then restricts store visibility and publishing — below 25%
in either category. Amethyst has been on the wrong side of that line: a
`-dontobfuscate` plus `-keepnames class ** { *; }` at the top of both
`proguard-rules.pro` and `quartz/consumer-rules.pro`, and outright
`-keep class com.vitorpamplona.** { *; }` for the app's own code, produced 0%
obfuscation and 13% optimization. (`-keepnames` is not the mild rule it looks
like: it expands to `-keep,allowshrinking`, which permits shrinking but neither
renaming nor optimization — applied to `**` it disables R8 for the entire
program, libraries included.)

So the standing rule is: **a keep needs a named runtime mechanism that reads the
name.** In this app those are, exhaustively:

| Mechanism | Example | Rule shape |
|---|---|---|
| JNI symbol `Java_<class>_<method>` | `ArtiNative`, secp256k1 | covered by the default `native <methods>` rule |
| Native code calling *back* by name | `ArtiLogCallback.onLogLine`, looked up with `GetMethodID` in `tools/arti-build/src/lib.rs` | `-keep class …ArtiLogCallback { *; }` |
| JNA struct/callback mapping | lazysodium | `-keep class com.goterl.lazysodium.** { *; }` |
| Jackson **reflective** data binding | `nip47WalletConnect.rpc.**`, `experimental.clink.**` | `-keep class <pkg>.** { *; }` |
| Enum constant persisted as a string | `UISharedPreferences` writes `enum.name`, reads `Type.valueOf(s)` | `-keepclassmembers enum * { <fields>; … }` |
| Class name in a manifest `<meta-data android:value>` | `AmethystCastOptionsProvider` | explicit `-keep` — AGP generates keeps from component `android:name`, **not** from meta-data |
| Class name in WorkManager's database | the three `CoroutineWorker`s | `-keep class * extends androidx.work.ListenableWorker { <init>(...); }` |

What does **not** need a keep, and where the temptation usually comes from:

- **Quartz events and tags.** `Event`, `Filter`, `Message`, `Command`, `Rumor`,
  `EventTemplate`, `TagArray`, the NIP-46 Bunker messages and the NIP-55 intent
  results all go through hand-written `StdSerializer`/`StdDeserializer` pairs
  registered on `JacksonMapper` / `JsonMapperNip55`. Those read and write
  property names as string literals, and `EventFactory` dispatches on kind with
  a `when`, not by reflection. Renaming their fields changes nothing on the wire.
- **`@Serializable` (kotlinx) classes**, including the type-safe navigation
  routes. The compiler plugin generates a descriptor holding the serial name and
  every property name as **compile-time string literals**, so obfuscation cannot
  reach them. kotlinx-serialization ships its own consumer rules for the
  `$$serializer`/`Companion` plumbing.
- **Compose, Coil, OkHttp, Media3, Firebase, kotlin-reflect.** Every one of them
  ships consumer rules inside its own artifact. Check
  `build/outputs/mapping/<variant>/configuration.txt` — the fully merged
  configuration — before writing a rule for a third-party library.
- **Manifest-declared components** (activities, services, receivers, providers)
  and classes named in layout/`res/xml`. AGP generates those keeps itself, which
  is why `Intent().setClassName(ctx, "…NappletBrowserService")` is safe.

### Attributes

```proguard
# What makes a crash report retraceable.
-keepattributes SourceFile,LineNumberTable

# jackson-module-kotlin reads @kotlin.Metadata; R8 requires InnerClasses and
# EnclosingMethod alongside Signature.
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod
```

`LocalVariableTable`, `LocalVariableTypeTable`, `MethodParameters` and
`-keepparameternames` are debug metadata that nothing in the app reads —
jackson-module-kotlin takes parameter names from `@kotlin.Metadata`, not from
`MethodParameters`. They were removed; don't add them back.

`-renamesourcefileattribute` is deliberately **not** set, and the reason is not
the usual one.

Once R8 is minifying it rewrites every class's `SourceFile` to the marker
`r8-map-id-<hash>` on its own — there is no rule that restores the original
per-class `.kt` name. Verified by building it both ways: with
`-renamesourcefileattribute SourceFile`, all 24,440 classes report the literal
`"SourceFile"`; without it, they report the marker. So the rule cannot buy
readability, it can only *destroy* the marker — and that marker is the
`pg_map_id` header of the mapping that produced the build, which is what lets a
pasted stack trace name the exact mapping file it needs.

Two related facts worth knowing before someone tries to "fix" stack traces with
keep rules:

- **Raw line numbers are no longer source line numbers.** R8 renumbers them so
  that one obfuscated line can encode a whole inlined frame stack. This is a
  cost of *optimization*, not of renaming — it is new only because the old
  blanket `-keepnames` had optimization switched off across the program, which
  is the thing Play was flagging.
- **Retrace therefore returns more than the old raw traces did**: it expands
  the frames R8 inlined instead of collapsing them into one misleading line. A
  one-frame crash can retrace to three.

The workflow is `scripts/retrace.sh <mapping> [trace]`, documented in
[`RELEASE_OPS.md` § 7](../../../../RELEASE_OPS.md). Every GitHub Release carries
`amethyst-{googleplay,fdroid}-mapping-<version>.txt.gz`; Play Console needs
nothing because AGP embeds the mapping in the `.aab` under
`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`.

### Verifying a change to these rules

R8 cannot see reflection, so a wrong keep rule fails **only in a release build,
at runtime**. Before changing them:

```bash
./gradlew :amethyst:assemblePlayRelease -PdisableAbiSplits=true -PdisableUniversalApk=true
```

then read `amethyst/build/outputs/mapping/playRelease/`:

- `configuration.txt` — every rule R8 actually saw, including each AAR's
  consumer rules. This is the file that answers "does library X already keep
  itself?"
- `mapping.txt` — what got renamed. Lines whose left and right sides are equal
  are classes a keep rule pinned; scan them for anything you did not intend.
- `seeds.txt` / `usage.txt` — what the keeps matched, and what was removed.

### The contract check

R8 cannot see reflection, so a keep rule that quietly stops matching — a class
moved to another package, a rule deleted in a merge, a DTO renamed — gives you a
green build and an APK that breaks on a user's device.
`tools/r8-verify/reflection-contract.txt` lists every name resolved from outside
the DEX, and the verifier asserts each against what R8 actually emitted:

```bash
python3 tools/r8-verify/verify_reflection_contract.py \
    amethyst/build/outputs/mapping/playRelease/
```

Under a second, no device. The release workflow runs it for both flavors before
collecting assets, so a break fails the release instead of shipping.

**Adding reflection means adding two things**: the keep rule, and a line in the
contract. A rule with no contract line is unverified and will rot.

It reads both `mapping.txt` and `usage.txt`, because neither is enough alone —
mapping.txt records only what *changed* (an intact `-keep ... { *; }` class has
an empty body, and an unrenamed member has no line at all, so absence there
means "preserved"), while a member R8 *deleted* appears only in usage.txt. It
also cross-checks that the two files come from the same R8 run: mapping.txt is
written during packaging, not at minify time, so a minify-only rebuild leaves a
stale one behind next to a fresh usage.txt.

What it cannot cover is reflection nobody wrote down. For that: a staged Play
rollout (the crash reporter retraces itself, so breaks are legible within
hours), and exercising NIP-47 wallet connect, NIP-46 bunker login, Tor,
scheduled posts and a settings round-trip (change theme/font, kill, relaunch) on
a minified build — those are the paths the keeps above exist for.

## Build Configuration

### Enable R8 in build.gradle

```gradle
android {
    buildTypes {
        release {
            minifyEnabled = true
            shrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            minifyEnabled = false
        }
    }
}
```

### Multiple Proguard Files

```gradle
android {
    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-quartz.pro",  // Library-specific rules
                "proguard-compose.pro"  // Compose-specific rules
            )
        }
    }
}
```

## Debugging Proguard Issues

### Generate Mapping File

R8 generates `mapping.txt` in `app/build/outputs/mapping/release/`:

```
# Original class name -> Obfuscated name
com.vitorpamplona.amethyst.ui.MainActivity -> a.b.c:
    void onCreate(Bundle) -> a
```

### Deobfuscate Stack Traces

```bash
# Using retrace (part of Android SDK)
retrace.sh mapping.txt stacktrace.txt
```

### Enable Proguard Output

```gradle
android {
    buildTypes {
        release {
            proguardFiles(...)

            // Generate reports
            postprocessing {
                proguardFiles = [...]
                obfuscate = true
                optimizeCode = true
                removeUnusedCode = true
            }
        }
    }
}
```

**Output files:**
- `build/outputs/mapping/release/configuration.txt` - All Proguard rules applied
- `build/outputs/mapping/release/mapping.txt` - Obfuscation mappings
- `build/outputs/mapping/release/seeds.txt` - Classes kept by `-keep` rules
- `build/outputs/mapping/release/usage.txt` - Code removed by R8

### Test Release Build

```bash
./gradlew assembleRelease

# Install and test
adb install app/build/outputs/apk/release/app-release.apk
```

## Common Issues

### Issue: NoSuchMethodException at Runtime

**Cause:** Proguard removed or renamed a method used via reflection.

**Solution:**
```proguard
-keep class com.example.YourClass {
    public <methods>;
}
```

### Issue: Serialization Fails

**Cause:** Data class fields were renamed.

**Solution:**
```proguard
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
```

### Issue: Compose Navigation Crashes

**Not** the route classes being obfuscated — that cannot happen. A type-safe
route's pattern comes from its kotlinx-serialization descriptor, and the compiler
plugin bakes the serial name and every property name in as string literals, so
renaming the class leaves the route string untouched. Adding
`-keep @kotlinx.serialization.Serializable class …routes.** { *; }` pins a large
tree for no reason and hides the real cause.

Look instead at whether `navigation-common`'s own consumer rules made it into
`configuration.txt`, and at the stack trace retraced through `mapping.txt`.

### Issue: Native Library Crashes

**Cause:** Native method signatures were changed.

**Solution:**
```proguard
-keepclasseswithmembernames class * {
    native <methods>;
}
```

## Optimization Tips

### 1. Keep Only What's Necessary

Don't use broad wildcards:
```proguard
# Bad - keeps everything
-keep class com.vitorpamplona.** { *; }

# Good - keeps only specific packages
-keep class com.vitorpamplona.quartz.events.** { *; }
```

### 2. Test Thoroughly

- Test all app features after enabling Proguard
- Test deep links and navigation
- Test serialization/deserialization
- Test external library integrations

### 3. Use AGP's Proguard Analysis

```gradle
android {
    buildTypes {
        release {
            // Generate R8 configuration
            android.debug.obsoleteApi = true
        }
    }
}
```

### 4. Analyze APK Size

```bash
# Build release APK
./gradlew assembleRelease

# Analyze APK with Android Studio
# Build > Analyze APK > Select app-release.apk
```

See `scripts/analyze-apk-size.sh` for automated analysis.

## Product Flavor Specific Rules

### Play Flavor (Firebase)

```proguard
# proguard-play.pro
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
```

### F-Droid Flavor (No Google Services)

```proguard
# proguard-fdroid.pro
# UnifiedPush
-keep class org.unifiedpush.** { *; }
```

**Configure in build.gradle:**
```gradle
android {
    flavorDimensions = ["channel"]
    productFlavors {
        create("play") {
            dimension = "channel"
            proguardFiles("proguard-play.pro")
        }
        create("fdroid") {
            dimension = "channel"
            proguardFiles("proguard-fdroid.pro")
        }
    }
}
```

## File Locations

- `amethyst/proguard-rules.pro` - Main Proguard rules
- `amethyst/build/outputs/mapping/release/` - Proguard output files
- `amethyst/build.gradle` - Proguard configuration

## Resources

- [Android R8 Documentation](https://developer.android.com/build/shrink-code)
- [Proguard Manual](https://www.guardsquare.com/manual/configuration)
- [Kotlinx Serialization Proguard](https://github.com/Kotlin/kotlinx.serialization#android)
