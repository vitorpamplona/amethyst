# Shared composables catalog

A starting map of reusable composables in
`commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/` (paths below are relative
to it). It is not exhaustive: `ui/components/` alone has ~85 files. Before writing a new
component, `ls` the folder and grep for the concept.

## Avatars and images (`components/`)

| Composable | File | Notes |
|---|---|---|
| `UserAvatar(userHex, pictureUrl, size, modifier, ..., badge)` | `UserAvatar.kt` | The user avatar. Reads `LocalDisplaySettings` for picture / robohash / GIF autoplay; optional `BoxScope` badge slot |
| `RobohashFallbackAsyncImage(robot, model, ...)` | `RobohashAsyncImage.kt` | Coil image with a robohash fallback |
| `RobohashImage(robot, ...)` | `RobohashImage.kt` | Robohash only; see `icon-assets.md` |
| `MyAsyncImage(imageUrl, contentDescription, contentScale, mainImageModifier, loadedImageModifier, accountViewModel, onLoadingBackground, onError)` | `MyAsyncImage.kt` | Coil image with the app's loading / error slots |
| `ImageGallery(images, state, accountViewModel, ...)` | `ImageGallery.kt` | Renders an `ImageGalleryParagraph` from the rich-text parser |
| `UrlPreviewCard(url, previewInfo, ...)` | `UrlPreviewCard.kt` | Link preview card |

## Rich text and notes

| Composable | File | Notes |
|---|---|---|
| `RichTextViewer(content, canPreview, quotesLeft, modifier, tags, backgroundColor, ...)` | `components/RichTextViewer.kt` | Parses through `CachedRichTextParser`; see `rich-text-parsing.md` |
| `NoteCompose(...)` | `note/NoteCompose.kt` | The note card used by every feed; `ui/note/` holds its parts and per-kind cards |

## Feeds (`feeds/`)

| Composable | File | Notes |
|---|---|---|
| `RefresheableFeedContentStateView(feedContentState, routeForLastRead, ..., accountViewModel, nav)` | `FeedContentStateView.kt` | Standard pull-to-refresh feed over a `FeedContentState` |
| `RenderFeedContentState(...)`, `SaveableFeedContentState(...)`, `SaveableGridFeedContentState(...)` | `FeedContentStateView.kt` | Lower-level pieces: state switch, saved list / grid scroll |
| `RefresheableBox(invalidateableContent \| onRefresh, content: BoxScope.() -> Unit)` | `RefresheableBox.kt` | Pull-to-refresh container |
| `FeedEmpty(onRefresh)`, `FeedError(...)`, `LoadingFeed()` | `FeedEmpty.kt`, `FeedError.kt`, `LoadingFeed.kt` | What feeds show for empty / error / loading |
| `ShimmerPlaceholder(modifier)`, `NoteCardSkeleton(modifier)` | `components/ShimmerPlaceholder.kt` | Skeleton placeholders |

`components/LoadingState.kt` also has generic `LoadingState`, `EmptyState`, `ErrorState`,
`FeedEmptyState`, `FeedErrorState`; the app's feeds use the `feeds/` versions above.

## Containers, menus and dialogs (`components/`)

| Composable | File | Notes |
|---|---|---|
| `ClickableBox(modifier, onClick, [onLongClick], content)`, `ToggleableBox(...)` | `ClickableBox.kt` | Click / long-click / toggle containers |
| `RoundedDropdownMenu(expanded, onDismissRequest, ..., content: ColumnScope.() -> Unit)`, `RoundedMenuSection`, `RoundedMenuItemModifier` | `RoundedDropdownMenu.kt` | The app's dropdown menu style |
| `M3ActionDialog(title, onDismiss, content)`, `M3ActionSection`, `M3ActionRow(icon, text, ...)` | `M3ActionDialog.kt` | Action-sheet style dialog |
| `TextSpinner(...)`, `SpinnerSelectionDialog(...)` | `TextSpinner.kt` | Option pickers over `ImmutableList<TitleExplainer>` |
| `SwipeToDeleteContainer(...)`, `SwipeToDeleteWithConfirmation(...)` | `SwipeToDelete.kt` | Swipe-to-delete rows |
| `HoverTooltip(text, content)` | `HoverTooltip.kt` | Tooltip on pointer hover (desktop / laptops) |
| `InformationDialog(obj, ...)` | `InformationDialog.kt` | Shows a toast-manager error message |
| `AddButton(modifier, text: StringResource, ...)`, `RemoveButton(...)` | `ActionButtons.kt` | Outlined add / remove buttons |
| `LoadingAnimation(...)` | `LoadingAnimation.kt` | Small animated loading indicator |

## Screens and layout

- `screens/PlaceholderScreens.kt`: `PlaceholderScreen(title, description, modifier)` plus
  `SearchPlaceholder`, `MessagesPlaceholder`, `NotificationsPlaceholder`.
- `navigation/topbars/`: `TopBarWithBackButton`, `TopBarExtensibleWithBackButton`, `ActionTopBar`,
  `ShorterTopAppBar`, `UserDrawerSearchTopBar`, `FeedFilterSpinner`.
- `layouts/ScreenLayout.kt`: `ScreenLayoutSpec`, `LocalScreenLayout`, `FeedContentMaxWidth`.
