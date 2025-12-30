# Build Commands Reference

## Table of Contents
- [Core Build Tasks](#core-build-tasks)
- [Module-Specific Builds](#module-specific-builds)
- [Desktop Tasks](#desktop-tasks)
- [Android Tasks](#android-tasks)
- [Testing](#testing)
- [Analysis & Diagnostics](#analysis--diagnostics)
- [Performance Optimization](#performance-optimization)

## Core Build Tasks

### Full Project Build
```bash
./gradlew build                    # Build all modules
./gradlew clean build              # Clean build
./gradlew assemble                 # Build without tests
```

### Incremental Builds
```bash
./gradlew :quartz:build            # Build only quartz module
./gradlew :commons:build           # Build only commons module
./gradlew :desktopApp:build        # Build only desktop app
```

## Module-Specific Builds

### Quartz (KMP Library)
```bash
./gradlew :quartz:build                           # All targets
./gradlew :quartz:compileKotlinJvm                # JVM target only
./gradlew :quartz:compileDebugKotlinAndroid       # Android target only
./gradlew :quartz:linkDebugFrameworkIosArm64      # iOS framework
./gradlew :quartz:publishToMavenLocal             # Publish locally
```

### Commons (Shared UI)
```bash
./gradlew :commons:build                          # All targets
./gradlew :commons:compileKotlinJvm               # Desktop target
./gradlew :commons:compileDebugKotlinAndroid      # Android target
```

## Desktop Tasks

### Run Desktop App
```bash
./gradlew :desktopApp:run                         # Run desktop app
./gradlew :desktopApp:runDistributable            # Run packaged version
```

### Package Desktop App
```bash
./gradlew :desktopApp:createDistributable         # Create runnable package
./gradlew :desktopApp:packageDmg                  # macOS DMG
./gradlew :desktopApp:packageMsi                  # Windows MSI
./gradlew :desktopApp:packageDeb                  # Linux DEB
```

### Distribution Location
- macOS: `desktopApp/build/compose/binaries/main/dmg/`
- Windows: `desktopApp/build/compose/binaries/main/msi/`
- Linux: `desktopApp/build/compose/binaries/main/deb/`

## Android Tasks

### Compile & Assemble
```bash
./gradlew :amethyst:assembleDebug                 # Debug APK
./gradlew :amethyst:assembleRelease               # Release APK
./gradlew :amethyst:bundleRelease                 # Release AAB
```

### Install & Run
```bash
./gradlew :amethyst:installDebug                  # Install debug on device
adb shell am start -n com.vitorpamplona.amethyst/.MainActivity
```

### Proguard/R8
```bash
./gradlew :quartz:minifyReleaseWithR8             # Test R8 minification
```

## Testing

### Unit Tests
```bash
./gradlew test                                    # All unit tests
./gradlew :quartz:jvmTest                         # JVM unit tests
./gradlew :quartz:testDebugUnitTest               # Android unit tests
./gradlew :commons:test                           # Commons tests
```

### Android Instrumented Tests
```bash
./gradlew :quartz:connectedAndroidTest            # Requires device/emulator
```

### Test Reports
```bash
# Reports location: <module>/build/reports/tests/
open quartz/build/reports/tests/jvmTest/index.html
```

## Analysis & Diagnostics

### Dependency Analysis
```bash
./gradlew dependencies                            # All dependencies
./gradlew :quartz:dependencies                    # Quartz dependencies
./gradlew dependencyInsight --dependency okhttp   # Specific dependency
```

### Build Scan
```bash
./gradlew build --scan                            # Upload to scans.gradle.com
```

### Performance Profiling
```bash
./gradlew build --profile                         # Generate profile report
# Report: build/reports/profile/profile-<timestamp>.html
```

### Task Dependencies
```bash
./gradlew :desktopApp:run --dry-run               # Show task graph
./gradlew :desktopApp:dependencies --scan         # Visualize dependencies
```

## Performance Optimization

### Configuration Cache
```bash
./gradlew build --configuration-cache             # Enable config cache
./gradlew build --configuration-cache-problems=warn
```

### Build Cache
```bash
./gradlew build --build-cache                     # Enable build cache
./gradlew cleanBuildCache                         # Clear build cache
```

### Parallel Execution
```bash
./gradlew build --parallel --max-workers=8        # Parallel with 8 workers
```

### Daemon Management
```bash
./gradlew --stop                                  # Stop Gradle daemon
./gradlew --status                                # Daemon status
```

### Incremental Compilation
```bash
# Already enabled by default in Kotlin, but can verify:
./gradlew :quartz:compileKotlinJvm --info | grep "Incremental"
```

## gradle.properties Optimizations

Add to `gradle.properties` for faster builds:

```properties
# Daemon
org.gradle.daemon=true
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g

# Parallel
org.gradle.parallel=true
org.gradle.workers.max=8

# Caching
org.gradle.caching=true
org.gradle.configuration-cache=true

# Kotlin
kotlin.incremental=true
kotlin.daemon.jvmargs=-Xmx2g
```

## Common Workflows

### Full Desktop Build & Run
```bash
./gradlew :desktopApp:clean :desktopApp:run
```

### Quick Desktop Iteration
```bash
# No clean - incremental compilation
./gradlew :desktopApp:run
```

### Android Release Build
```bash
./gradlew :amethyst:clean :amethyst:bundleRelease
```

### Test All KMP Targets
```bash
./gradlew :quartz:test :quartz:testDebugUnitTest
```

### Publish Quartz Locally for Testing
```bash
./gradlew :quartz:publishToMavenLocal
# Then update version in consumer project to test
```
