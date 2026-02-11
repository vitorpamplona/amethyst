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
   - Required components: build-tools, platform-tools, platforms;android-35

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

Add to `amethyst/build.gradle` inside the `android {}` block:

```gradle
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
}

signingConfigs {
    release {
        if (keystorePropertiesFile.exists()) {
            storeFile rootProject.file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
        }
    }
}
```

Update the release buildType to use the signing config:
```gradle
buildTypes {
    release {
        signingConfig signingConfigs.release
        // ... existing config
    }
}
```

### 4. Disable Google Services (Required for F-Droid)

**⚠️ CRITICAL:** The Google Services plugin fails when you change the package name. For F-Droid builds, disable it.

Edit `amethyst/build.gradle`, comment out the plugin:
```gradle
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    // alias(libs.plugins.googleServices)  // DISABLED for F-Droid
    alias(libs.plugins.jetbrainsComposeCompiler)
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

Edit `amethyst/build.gradle`:
```gradle
android {
    defaultConfig {
        applicationId = "com.yourcompany.yourapp"
    }
}
```

### Change Project Name

Edit `settings.gradle`:
```gradle
rootProject.name = "YourAppName"
```

### Change App Icon

Replace icon files in:
- `amethyst/src/main/res/mipmap-*/ic_launcher.webp`
- `amethyst/src/main/res/mipmap-*/ic_launcher_round.webp`

### Add Client Tag to Posts

Make your app identify itself on posts with `["client", "YourAppName"]`.

**1. Create tag builder extension:**

Create `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/tags/clientTag/TagArrayBuilderExt.kt`:
```kotlin
package com.vitorpamplona.quartz.nip01Core.tags.clientTag

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

fun <T : Event> TagArrayBuilder<T>.client(clientName: String) = 
    addUnique(arrayOf(ClientTag.TAG_NAME, clientName))
```

**2. Add to TextNoteEvent:**

Edit `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip10Notes/TextNoteEvent.kt`:

Add import:
```kotlin
import com.vitorpamplona.quartz.nip01Core.tags.clientTag.client
```

In both `build()` functions, add after `alt(...)`:
```kotlin
client("YourAppName")
```

### Modify Default Relays

Edit relay configuration in `quartz/src/main/java/com/vitorpamplona/quartz/nip01Core/relay/` or the UI settings files.

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
