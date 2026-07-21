# Amethyst Builder Skill

Build customized Amethyst Nostr clients for Android. Fork, rebrand, customize, and distribute your own version.

## Overview

[Amethyst](https://github.com/vitorpamplona/amethyst) is the premier Nostr client for Android. This skill enables you to:
- Create rebranded versions (custom name, package, icons)
- Build F-Droid-compatible releases (no Google Play dependencies)
- Add or modify features
- Sign and distribute APKs

## Prerequisites

### Required Tools

1. **Java 21** (via SDKMAN)
   ```bash
   curl -s "https://get.sdkman.io" | bash
   source "$HOME/.sdkman/bin/sdkman-init.sh"
   sdk install java 21.0.5-tem
   sdk use java 21.0.5-tem
   ```

2. **Android SDK**
   - Command-line tools from https://developer.android.com/studio#command-line-tools-only
   - Required components: build-tools, platform-tools, platforms;android-37
   - The exact SDK level is `android-compileSdk` in `gradle/libs.versions.toml` —
     check there if this number has drifted.

3. **Git** for cloning the repository

### Environment Setup

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

## Build Workflow

### 1. Clone the Repository

```bash
mkdir -p ~/projects/your-app-name
cd ~/projects/your-app-name
git clone https://github.com/vitorpamplona/amethyst.git .
```

### 2. Create Signing Key

```bash
keytool -genkeypair -v \
  -keystore ./release-key.jks \
  -alias your-app \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

Create `keystore.properties` in project root:
```properties
storeFile=release-key.jks
storePassword=your-password
keyAlias=your-app
keyPassword=your-password
```

### 3. Configure Signing

Add to `amethyst/build.gradle.kts` inside the `android {}` block:

```kotlin
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

signingConfigs {
    create("release") {
        if (keystorePropertiesFile.exists()) {
            storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }
}
```

This needs `import java.util.Properties` at the top of the file.

Update the release buildType to use the signing config:
```kotlin
buildTypes {
    getByName("release") {
        signingConfig = signingConfigs.getByName("release")
        // ... existing config
    }
}
```

Verify with `./gradlew :amethyst:signingReport` — the release variants should
report your keystore rather than `~/.android/debug.keystore`.

### 4. Disable Google Services (Required for F-Droid)

**⚠️ CRITICAL:** The Google Services plugin fails when you change the package name. For F-Droid builds, disable it.

Edit `amethyst/build.gradle.kts`, comment out the plugin:
```kotlin
plugins {
    alias(libs.plugins.androidApplication)
    // alias(libs.plugins.googleServices)  // DISABLED for F-Droid
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.googleKsp)
}
```

### 5. Build

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.5-tem
./gradlew assembleFdroidRelease
```

**Build time:** ~9 minutes first build, faster on subsequent builds.

### 6. Locate APKs

Output directory: `amethyst/build/outputs/apk/fdroid/release/`

Files generated:
- `amethyst-fdroid-arm64-v8a-release.apk` - ARM64 (most phones)
- `amethyst-fdroid-universal-release.apk` - All architectures (larger)

## Customizations

### Change App Name

Edit `amethyst/src/main/res/values/strings.xml`:
```xml
<string name="app_name" translatable="false">YourAppName</string>
<string name="app_name_debug" translatable="false">YourAppName Debug</string>
```

### Change Package ID

Edit `amethyst/build.gradle.kts`:
```kotlin
android {
    defaultConfig {
        applicationId = "com.yourcompany.yourapp"
    }
}
```

### Change Project Name

Edit `settings.gradle.kts`:
```kotlin
rootProject.name = "YourAppName"
```

### Change App Icon

Replace icon files in:
- `amethyst/src/main/res/mipmap-*/ic_launcher.webp`
- `amethyst/src/main/res/mipmap-*/ic_launcher_round.webp`

### Add Client Tag to Posts

Make your app identify itself on posts with `["client", "YourAppName"]`.

You do **not** need to add the tag per event type. The client tag is applied
centrally by `NostrSignerWithClientTag`, a signer decorator that appends the tag
to everything it signs (and respects the user's "add client tag" privacy
setting). Changing the name is a one-constant edit:

Edit `amethyst/src/main/java/com/vitorpamplona/amethyst/model/accountsCache/AccountCacheState.kt`:
```kotlin
const val CLIENT_TAG_NAME = "YourAppName"
```

That constant is passed to `NostrSignerWithClientTag` when the account's signer
is built, so every signed event carries your name.

The tag itself lives in
`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip89AppHandlers/clientTag/`
(`ClientTag`, `TagArrayBuilderExt`, `NostrSignerWithClientTag`) — you only need to
touch it if you want the optional NIP-89 handler address / relay hint variants.

### Modify Default Relays

Edit `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/defaults/Constants.kt`
(see also `AmethystDefaults.kt` and `DefaultDmIndexerRelays.kt` in the same folder).

## Troubleshooting

### google-services.json error
Disable the Google Services plugin (see step 4).

### Java version error
```bash
sdk use java 21.0.5-tem
java -version  # Must show 21.x
```

### Out of memory
Edit `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
```

### Clean build
```bash
./gradlew --stop
./gradlew clean
./gradlew assembleFdroidRelease
```

## Distribution

Deploy APKs via:
- **Surge.sh:** `surge ./releases your-app.surge.sh`
- **Zapstore:** Submit to zapstore.dev
- **Direct download:** Host on any web server
