# Proguard Rules for Amethyst

Proguard configuration for optimizing and obfuscating Android APK while preserving necessary code.

## What is Proguard/R8?

**R8** is Android's default code shrinker and obfuscator (replaced Proguard in AGP 3.4.0+). It:
- **Shrinks** code by removing unused classes/methods
- **Obfuscates** code by renaming classes/methods to short names
- **Optimizes** code by inlining methods and removing dead code

## Amethyst Proguard Configuration

**File:** `amethyst/proguard-rules.pro`

### Keep Kotlin Metadata

```proguard
# Kotlin metadata is required for reflection
-keep class kotlin.Metadata { *; }
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-dontnote kotlinx.serialization.SerializationKt

-keep,includedescriptorclasses class com.vitorpamplona.**$$serializer { *; }
-keepclassmembers class com.vitorpamplona.** {
    *** Companion;
}
-keepclasseswithmembers class com.vitorpamplona.** {
    kotlinx.serialization.KSerializer serializer(...);
}
```

### Keep Nostr Event Classes

```proguard
# Nostr events are serialized/deserialized
-keep class com.vitorpamplona.quartz.events.** { *; }
-keep class com.vitorpamplona.quartz.encoders.** { *; }

# Keep event builders
-keep class com.vitorpamplona.quartz.builders.** { *; }

# Keep tag classes
-keep class com.vitorpamplona.quartz.nip01Core.tags.** { *; }
```

### Keep Data Classes

```proguard
# Data classes used in ViewModels and serialization
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep all data classes
-keep class com.vitorpamplona.amethyst.model.** { *; }
-keep class com.vitorpamplona.amethyst.service.model.** { *; }
```

### Keep Compose Classes

```proguard
# Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Compose runtime
-keep class androidx.compose.runtime.** { *; }

# Compose UI
-keep class androidx.compose.ui.** { *; }

# Material3
-keep class androidx.compose.material3.** { *; }

# Navigation Compose - Keep serializable routes
-keep class * implements java.io.Serializable { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
```

### Keep OkHttp/Retrofit

```proguard
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# OkHttp WebSockets (for Nostr relays)
-keep class okhttp3.internal.ws.** { *; }

# Retrofit (if used)
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
```

### Keep Jackson (JSON)

```proguard
# Jackson JSON library
-keep class com.fasterxml.jackson.** { *; }
-keep class org.codehaus.** { *; }
-keepclassmembers class * {
     @com.fasterxml.jackson.annotation.* <methods>;
}

# Jackson polymorphic types
-keepattributes RuntimeVisibleAnnotations
-keep @com.fasterxml.jackson.annotation.JsonTypeInfo class *
```

### Keep Secp256k1 (Crypto)

```proguard
# Secp256k1 native library
-keep class fr.acinq.secp256k1.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
```

### Keep Tor

```proguard
# Tor library
-keep class com.msopentech.thali.toronionproxy.** { *; }
-dontwarn com.msopentech.thali.toronionproxy.**
```

### Keep ExoPlayer (Media)

```proguard
# ExoPlayer (Media3)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**
```

### Keep Coil (Image Loading)

```proguard
# Coil image loading
-keep class coil.** { *; }
-keep class coil3.** { *; }
-dontwarn coil.**
-dontwarn coil3.**
```

### Keep ViewModels

```proguard
# ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# ViewModel factories
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory {
    <init>(...);
}

# Keep ViewModel constructors for reflection
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
```

### Keep Parcelable

```proguard
# Parcelable
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class * implements android.os.Parcelable {
  public <fields>;
  private <fields>;
}
```

### Keep Enums

```proguard
# Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
```

### Remove Logging (Production)

```proguard
# Remove debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep error/warning logs
-assumenosideeffects class android.util.Log {
    public static *** e(...) return false;
    public static *** w(...) return false;
}
```

### Keep Crashlytics/Firebase

```proguard
# Firebase Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
```

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

**Cause:** @Serializable route classes were obfuscated.

**Solution:**
```proguard
# Keep all route classes
-keep @kotlinx.serialization.Serializable class com.vitorpamplona.amethyst.ui.navigation.routes.** { *; }
```

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
