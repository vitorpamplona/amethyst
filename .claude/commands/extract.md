---
description: Extract a composable from amethyst to shared code
---

Extract the component `$ARGUMENTS` from the Android app to shared KMP code:

## Process

1. **Locate the component** in the amethyst module:
   ```bash
   find amethyst/src -name "*$ARGUMENTS*" -o -name "*$ARGUMENTS*"
   grep -r "fun $ARGUMENTS\|class $ARGUMENTS" amethyst/src/
   ```

2. **Analyze dependencies**:
   - Android-specific imports (Context, Intent, etc.)
   - Platform APIs (Camera, MediaStore, etc.)
   - Android Compose specifics vs standard Compose

3. **Identify what can be shared**:
   - Pure Composable functions → `shared-ui/commonMain/`
   - Business logic → `quartz/commonMain/`
   - Platform-specific → create expect/actual

4. **Create shared version**:
   - Move to appropriate shared module
   - Replace Android imports with multiplatform alternatives
   - Add expect declarations for platform-specific parts

5. **Update references**:
   - Change imports in amethyst module
   - Add implementations in desktopApp if needed

## Common Replacements

| Android | Multiplatform |
|---------|---------------|
| `LocalContext.current` | expect/actual or parameter |
| `stringResource()` | `Res.string.*` |
| `painterResource()` | `painterResource(Res.drawable.*)` |
| `Toast.makeText()` | Custom snackbar/notification |
| `Intent` | expect/actual for navigation |

## Example

```
/extract NoteCard
```

This will find NoteCard, analyze its dependencies, and guide you through extracting it to shared code.
