# Extracting an Android composable, screen or ViewModel

Goal: Android and Desktop render the same code. First check the component's
dependencies actually allow sharing; if one Android API remains, put it behind a
port or slot rather than leaving the whole screen in `amethyst/`.

## Steps

1. **Locate it and its callers.** Android code lives in `amethyst/src/main/java/`.
   ```bash
   git grep -n "fun Name\b\|class Name\b" -- amethyst/src
   ```
2. **List what ties it to Android:** `android.*` imports, `Context`/`Intent`,
   `R.string`/`R.drawable`, `LocalContext`, Android-only libraries (media3, CameraX,
   WebView), and app-only helpers from `amethyst/`.
3. **Move strings first.** Keys still in `amethyst/src/main/res/values/strings.xml`
   move with `tools/strings-migrate/migrate.py <keys…>` (all locales, byte for byte),
   then `R.string.x` becomes `Res.string.x`. Rules: `AGENTS.md` › Strings.
4. **Pick the destination** (mechanical rule in `commonsUI/ARCHITECTURE.md`):
   - Composables, screens, anything importing Compose UI, Coil or `Res` →
     `commonsUI/src/commonMain/`, same `com.vitorpamplona.amethyst.commons.*`
     package tree.
   - Headless ViewModels, state holders, services → `commons/src/commonMain/`
     (`viewmodels/` or `<feature>/`), never under a `ui.*` package.
   - Nostr protocol logic → `quartz/src/commonMain/` in the quartz repo (a quartz
     PR; build against it here with `-PquartzDir=../quartz`).
5. **Replace Android APIs** (table below). Each leftover platform need becomes an
   expect/actual verb, a port, or a slot (see SKILL.md).
6. **Repoint callers** in `amethyst/` to the shared version and delete the Android
   copy. Desktop renders the shared UI, so it usually needs no change beyond any new
   port/actual.
7. **Verify:** compile every target the module builds,
   `./gradlew :commonsUI:jvmTest :commonsUI:verifyKmpPurity` (or `:commons:…`),
   `./gradlew :amethyst:compilePlayDebugKotlin`, then `./gradlew spotlessApply`.

## Common replacements

| Android | Shared |
|---|---|
| `stringResource(R.string.x)` | `stringRes(Res.string.x)` (`commons/ui/StringRes.kt`); `pluralStringRes(Res.plurals.x, n, …)` |
| String outside composition | `loadStringRes(…)` from a coroutine, or resolve in composition and pass a `String` |
| `painterResource(R.drawable.x)` | `painterRes(Res.drawable.x, …)` (`commons/ui/PainterRes.kt`) |
| `Toast.makeText(…)` in UI | `accountViewModel.toastManager.toast(…)` |
| Toast/message from headless code | report a typed `UserNotice` (`commons.notices`) to a `UserNoticeSink`; word it in `commonsUI` `notices/ui/UserNoticeText.kt` |
| `LocalContext.current` for share / open URI | `rememberTextSharer()`, `rememberBlossomUriOpener()`, Compose `LocalUriHandler` |
| `LocalContext.current` for anything else | a port on `AccountViewModelHost`, a slot such as `LocalNotePlatform`, or a lambda parameter |
| Platform media/map/editor inside a note | `LocalNotePlatform` slot |
| `Intent` to another screen | the shared navigation in `commonsUI` (`AmethystApp`) |
| Per-window-size layout forks | `ScreenLayoutSpec` / `LocalScreenLayout` |
| `@StringRes Int` field | `StringResource` / `PluralStringResource` |
