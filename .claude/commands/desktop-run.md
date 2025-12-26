---
description: Build and run the desktop app
---

Build and run the Amethyst Desktop application:

```bash
./gradlew :desktopApp:run
```

## Troubleshooting

If the build fails, check:

1. **JDK Version**: Requires JDK 17+
   ```bash
   java -version
   ```

2. **Compose Multiplatform Plugin**: Verify version in `gradle/libs.versions.toml`

3. **Quartz Build**: Ensure Quartz compiles first
   ```bash
   ./gradlew :quartz:build
   ```

4. **Desktop Dependencies**: Check `desktopApp/build.gradle.kts` has:
   ```kotlin
   implementation(compose.desktop.currentOs)
   ```

## Creating Distributable

```bash
# macOS
./gradlew :desktopApp:packageDmg

# Windows
./gradlew :desktopApp:packageMsi

# Linux
./gradlew :desktopApp:packageDeb
```

Outputs will be in `desktopApp/build/compose/binaries/`
