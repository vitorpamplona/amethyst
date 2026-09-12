# `commonsUI` — the Compose half of the shared layer

`commonsUI` holds every piece of shared code that needs **Compose UI** and is
therefore useless to the headless `cli`:

- composables (`ui/<area>`, `<feature>/ui`, and the historical flat feature
  packages like `nip64Chess`, `audio`),
- `ImageVector` icons (`icons`, `hashtags`, `robohash`) and the icon fonts,
- theme, layouts, markdown rendering (`ui/markdown`, jvmAndroid),
- Coil (`service/image`: `CoilImageBridge` + BlurHash/ThumbHash/Base64/Blossom
  fetchers),
- the `composeResources` tree (translated strings, fonts, the napplet shell +
  shim files) and the generated `com.vitorpamplona.amethyst.commons.resources.Res`,
- the `@Composable` relay-client entry points (`observeUser*`,
  `*FilterAssemblerSubscription`, `KeyDataSourceSubscription`),
- state holders that carry a `foundation`/`ui` type (`LevelFeedViewModel` with
  its `LazyListState`, `ChatNewMessageState` with `TextFieldValue`,
  `EmojiSuggestionState` with `TextFieldState`).

It depends on `:commons` (and `:quartz`) as **`api`**, so a consumer that adds
`:commonsUI` sees the headless layer transitively. `amethyst`, `desktopApp`,
`nappletHost` and `benchmark` depend on it; `cli`, `geode`, `marmotBench`
must never.

## Same packages as `commons`

Every file keeps its `com.vitorpamplona.amethyst.commons.*` package. The split
is a **build-graph boundary, not a package rename**: nothing in the apps had to
change an import, and a file can move between the two modules with a `git mv`.
The rule for which module a file lives in is mechanical:

> imports `androidx.compose.ui` / `foundation` / `material3` / `animation`,
> Coil, `org.jetbrains.compose.resources`, `Res`, `org.jetbrains.skia`, or
> declares a `@Composable` → **`commonsUI`**. Otherwise → **`commons`**.

The only cross-module gotcha is `internal`: a `commonsUI` file cannot see an
`internal` declaration in `commons`. Make it public (or move the caller).

The full package taxonomy — including which packages are "mixed" (logic in
`commons`, composables here, same package name) — is the table in
`commons/ARCHITECTURE.md` §2. Read that first; this file only documents what
is specific to the UI module.

## Source sets

| Source set    | For |
|---------------|-----|
| `commonMain`  | Composables, icons, theme, Coil fetchers, `composeResources`. Gated by `verifyKmpPurity` like `commons`. |
| `jvmAndroid`  | Markdown renderer (`ui/markdown`), Coil-OkHttp + Blossom read-auth fetcher, the `viewModel()` helper. |
| `jvmMain`     | Desktop Coil bridge (`CoilImageBridge.jvm.kt`), `compose.desktop.currentOs`. `dependsOn(jvmAndroid)` + `skikoMain`. |
| `androidMain` | Android Coil bridge. `dependsOn(jvmAndroid)`. |
| `skikoMain`   | `org.jetbrains.skia` pixel helpers shared by desktop JVM + iOS (`SkiaBitmapConverter`). |
| `iosMain`     | iOS Coil bridge. Compile-only spike today. |

## Tooling that points here

- Icon fonts: `tools/material-symbols-subset/subset.sh` and
  `tools/icon-font/build_icon_font.py` read `icons/` and write
  `composeResources/font/` in this module (see root `.claude/CLAUDE.md`, "Icons").
- Translations: `crowdin.yml` and `tools/strings-migrate/` target
  `commonsUI/src/commonMain/composeResources/`.
- CI: `.github/workflows/build.yml` runs `:commonsUI:jvmTest`,
  `:commonsUI:verifyKmpPurity` and the iOS compile/test tasks next to the
  `:commons` ones.
