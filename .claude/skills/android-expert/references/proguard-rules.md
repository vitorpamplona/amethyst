# R8 rules for Amethyst

## Files

- `amethyst/proguard-rules.pro` — the app's rules. Read it before adding
  anything: it is commented rule by rule.
- `quartz/consumer-rules.pro` — merged into the R8 configuration of **every**
  app that depends on Quartz, this one included (wired via
  `optimization.consumerKeepRules` in `quartz/build.gradle.kts`). A rule added
  here silently applies to somebody else's whole program.
- The AGP default `proguard-android-optimize.txt`, which already contributes the
  Android-wide basics (Parcelable CREATOR fields, `native <methods>`, the enum
  `values()`/`valueOf()` pair, …). Don't restate its rules.
- `commons/consumer-rules.pro` and `commonsUI/consumer-rules.pro` exist but are
  empty, and neither module's `build.gradle.kts` wires them. These modules are not
  minified on their own, so a rule written there is dead configuration. Put it in
  `amethyst/proguard-rules.pro`.

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
| Generated code reflected over by a library | the AppFunctions bridge (`appfunctions.**`, play flavor only) | `-keep class <pkg>.** { *; }` |
| Enum constant persisted as a string | `model/preferences/UiSharedPreferences.kt` writes `enum.name`, reads `Type.valueOf(s)` | `-keepclassmembers enum * { <fields>; … }` |
| Class name in a manifest `<meta-data android:value>` | `AmethystCastOptionsProvider` | explicit `-keep` — AGP generates keeps from component `android:name`, **not** from meta-data |
| Class name in WorkManager's database | the three `CoroutineWorker`s | none of ours: androidx.work's own consumer rules keep it; the contract still checks the names |

What does **not** need a keep, and where the temptation usually comes from:

- **Quartz events and tags.** `Event`, `Filter`, `Message`, `Command`, `Rumor`,
  `EventTemplate`, `TagArray`, the NIP-46 Bunker messages and the NIP-55 intent
  results all go through quartz's hand-written Kite reader/writer or hand-written
  kotlinx serializers. Those read and write property names as string literals,
  and `EventFactory` dispatches on kind with a `when`, not by reflection.
  Renaming their fields changes nothing on the wire.
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

# Generic signatures for code that reads type arguments reflectively; R8 requires
# InnerClasses and EnclosingMethod alongside Signature.
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod
```

`LocalVariableTable`, `LocalVariableTypeTable`, `MethodParameters` and
`-keepparameternames` are debug metadata that nothing in the app reads. They were
removed; don't add them back.

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

Scope a line to one flavor with a leading `@play` / `@fdroid` when the class is
only compiled into that variant — play-only code is absent from the F-Droid APK,
and "absent" is indistinguishable from "R8 deleted it", so an unscoped line for
it fails that release.

Reflection that names a class R8 *cannot* rename needs no rule and no line: the
platform trust manager `quic` probes for a 3-arg `checkServerTrusted` ships in
Android, not in our DEX. The opposite case is the one to watch — a name of
*ours* used as a value. `AmethystAppFunctions` compared
`signer::class.qualifiedName` against `"…NostrSignerExternal"`; R8 renames that
class, so the branch silently stopped running the day obfuscation was turned on.
Prefer `is` over a name comparison; there is no keep rule that makes the latter
safe to write.

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
