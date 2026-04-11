# String Resource Migration Scope: Android R.string → Compose Multiplatform Resources

**Date:** 2026-04-11  
**Status:** Analysis / Scoping  
**Goal:** Migrate `R.string.xxx` usage from `amethyst/` to `Res.string.xxx` (Compose MP Resources) so composables can move to `commons/`.

---

## 1. Scale of the Problem

| Metric | Count |
|--------|-------|
| `.kt` files importing `amethyst.R` | **399** |
| `.kt` files referencing `R.string.` | **398** |
| Unique `R.string.xxx` keys used in code | **2,566** references across files |
| Total `<string>` entries in `strings.xml` | **1,978** |
| Parameterized strings (`%s`, `%d`, `%1$s`, etc.) | **172** |
| Translations (locale-specific `strings.xml` files) | **78** |
| `R.drawable.xxx` unique references | **78** |
| Files using `R.drawable.` | **42** |
| No plurals or string-arrays | **0** |

### Hotspot Directories (files with R.string usage)

| Directory (under `ui/`) | Files |
|--------------------------|-------|
| `note/types/` | 28 |
| `note/` | 22 |
| `screen/loggedIn/settings/` | 12 |
| `actions/uploads/` | 11 |
| `note/elements/` | 10 |
| `components/` | 10 |
| `screen/loggedIn/profile/header/` | 9 |
| `screen/loggedIn/home/` | 9 |
| `screen/loggedOff/login/` | 8 |
| `screen/loggedIn/relays/common/` | 8 |

---

## 2. Current State of Compose MP Resources in `commons/`

**Already set up and working.** The migration has already begun:

- `commons/build.gradle.kts` contains `compose.resources { }` configuration
- `commons/src/commonMain/composeResources/values/strings.xml` exists with **57 lines** (~30 string entries)
- **4 composables** in `commons/` already use `Res.string.xxx`:
  - `ChatroomHeader.kt` — `Res.string.accessibility_user_avatar`
  - `PlaceholderScreens.kt` — search/messages/notifications titles & descriptions
  - `LoadingState.kt` — refresh/try again labels
  - `UserSearchCard.kt` — user avatar accessibility

These use the standard pattern:
```kotlin
import org.jetbrains.compose.resources.stringResource
// ...
stringResource(Res.string.some_key)
```

---

## 3. The `stringRes()` Helper — Key Complexity

**Location:** `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/StringResourceCache.kt`

This is NOT just `stringResource()`. It's a **caching wrapper** using `LruCache<Int, String>(300)` that avoids repeated `stringResource()` calls (which are reportedly >1ms on some phones). It has **6 overloads**:

### Composable variants (used in UI)
1. `stringRes(id: Int): String` — simple cached lookup
2. `stringRes(id: Int, vararg args: String): String` — with String format args
3. `stringRes(id: Int, vararg args: Int?): String` — with Int format args

### Context-based variants (used outside composition, e.g. in formatters)
4. `stringRes(ctx: Context, id: Int): String` — cached via `ctx.getString()`
5. `stringRes(ctx: Context, id: Int, vararg args: String?): String` — with String args
6. `stringRes(ctx: Context, id: Int, vararg args: Int?): String` — with Int args

**186 call sites** use the `Context`-based variants (non-composable code like `TimeAgoFormatter`).

### Migration Implications
- The **composable variants** can be replaced with `stringResource(Res.string.xxx)` directly (Compose MP Resources handles caching at the framework level)
- The **Context-based variants** are harder — they're used outside `@Composable` scope. These need either:
  - A KMP-compatible string resolution mechanism (expect/actual)
  - Refactoring callers to pass pre-resolved strings
  - A `getString()` equivalent in Compose MP Resources (currently limited)

Also includes `painterRes()` (drawable caching) which will need similar treatment for `R.drawable` migration.

---

## 4. Android strings.xml Inventory

**Main file:** `amethyst/src/main/res/values/strings.xml` — **2,254 lines**, **1,978 string entries**

**Translations:** 78 locale files including:
- Major: es, ru, zh-TW, pl, fi, sv, lv, ne, ur
- Already in `commons/composeResources/values/strings.xml`: 30 entries (login, actions, errors, placeholders, accessibility)

**No plurals or string-arrays** — simplifies migration since Compose MP Resources handles `<string>` identically to Android resources.

**172 parameterized strings** — Compose MP Resources supports `%s`/`%d` format args the same way, so these should migrate cleanly.

---

## 5. Low-Hanging Fruit: Easiest Files to Migrate First

Files with **zero `android.*` imports** and **≤5 `R.string` references** — these only need string key migration to become KMP-ready.

### 1-ref files (25 files — trivial migration each)

| File | Location |
|------|----------|
| `AiWritingHelpButton.kt` | `note/creators/aihelp/` |
| `BadgeCompose.kt` | `note/` |
| `BoostedMark.kt` | `note/elements/` |
| `CalendarEvent.kt` | `note/types/` |
| `DisplayGeoHashExternalId.kt` | `note/nip22Comments/` |
| `DisplayHashtagExternalId.kt` | `note/nip22Comments/` |
| `DisplayReward.kt` | `note/elements/` |
| `ForkInfo.kt` | `note/elements/` |
| `InteractiveStory.kt` | `note/types/` |
| `LongForm.kt` | `note/types/` |
| `MultiSetCompose.kt` | `note/` |
| `Nip.kt` | `note/types/` |
| `Notifying.kt` | `note/creators/notify/` |
| `PostButton.kt` | `note/buttons/` |
| `PrivateMessage.kt` | `note/types/` |
| `RelayListBox.kt` | `note/` |
| `RenderPostApproval.kt` | `note/types/` |
| `SaveButton.kt` | `note/buttons/` |
| `ShowEmojiSuggestionList.kt` | `note/creators/emojiSuggestions/` |
| `ShowUserButton.kt` | `note/` |
| `TimeAgo.kt` | `note/elements/` |
| `TorrentComment.kt` | `note/types/` |
| `Video.kt` | `note/types/` |
| `Wiki.kt` | `note/types/` |
| `ZapPollField.kt` | `note/creators/zappolls/` |

### 2-ref files (20 files — still easy)

Including: `AddGeoHashButton`, `AddLnInvoiceButton`, `AddRemoveButtons`, `AnonymousPostButton`, `AppDefinition`, `Chess`, `DisplayLocationObserver`, `ErrorMessageDialog`, `ExpirationDateButton`, `FollowList`, `NewPostInvoiceRequest`, `RelayListRow`, `TextModification`, `UpdateReactionTypeDialog`, `UserReactionsRow`, etc.

### Also outside `note/` directory (zero android imports, 1 ref):
- `RecordVoiceButton.kt` (actions/uploads/)
- `ClickableWithdrawal.kt` (components/)
- `LoadingFeed.kt` (feeds/)
- `LeftPictureLayout.kt` (layouts/)
- `UserDrawerSearchTopBar.kt` (navigation/topbars/)

---

## 6. Recommended Migration Strategy

### Phase 0: Infrastructure (do once)
1. **Copy all strings from `amethyst/src/main/res/values/strings.xml` → `commons/src/commonMain/composeResources/values/strings.xml`**
   - Compose MP Resources uses the same XML format as Android
   - The 30 entries already in commons can stay; just merge/deduplicate
   - No need for plural support (none used)
   - Parameterized strings (`%s`/`%d`) work identically

2. **Copy all translation files** to `commons/src/commonMain/composeResources/values-XX/strings.xml`
   - 78 locale files → same folder structure under `composeResources/`
   - Convention: `values-es-rES` (Android) → `values-es-ES` (Compose MP) — verify exact naming

3. **Create a KMP `stringRes()` replacement in commons:**
   ```kotlin
   // In commons/src/commonMain/kotlin/.../StringResUtils.kt
   @Composable
   fun stringRes(res: StringResource): String = stringResource(res)
   
   @Composable
   fun stringRes(res: StringResource, vararg args: Any): String = 
       stringResource(res, *args)
   ```
   - Caching is less critical for Compose MP (recomposition handles this)
   - Drop the `LruCache` — or benchmark first if perf is a concern

4. **For non-composable `stringRes(Context, ...)` calls — expect/actual:**
   ```kotlin
   // commonMain
   expect fun getString(res: StringResource): String
   
   // androidMain
   actual fun getString(res: StringResource): String {
       return applicationContext.getString(res.resourceId) // or similar
   }
   ```
   - This is the hardest part; Compose MP Resources doesn't have a clean non-composable API
   - Alternative: refactor callers (like `TimeAgoFormatter`) to accept pre-resolved strings

### Phase 1: Migrate leaf composables (batch of 25-45 files)
- Start with the 25 one-ref files listed above
- For each: replace `stringRes(R.string.xxx)` → `stringRes(Res.string.xxx)` (or `stringResource(Res.string.xxx)`)
- Remove `import com.vitorpamplona.amethyst.R`
- Add `import org.jetbrains.compose.resources.stringResource` and `import (generated).Res`
- These files can then move to `commons/` as they have no other Android deps

### Phase 2: Migrate medium-complexity composables (2-5 refs, ~40 files)
- Same mechanical transformation, just more string keys per file
- Group by directory for easier review

### Phase 3: Migrate heavy composables and Context-based callers
- Settings screens, login screens, profile headers (~50+ R.string refs each)
- `TimeAgoFormatter` and other non-composable callers (186 call sites)
- `StringResourceCache.kt` itself can eventually be removed/replaced

### Phase 4: Drawables
- 78 unique `R.drawable` refs across 42 files
- Move drawables to `commons/src/commonMain/composeResources/drawable/`
- Replace `painterResource(R.drawable.xxx)` → `painterResource(Res.drawable.xxx)`

---

## 7. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Translation string name mismatches | Low | Same XML format; automated diff |
| Performance regression (losing LruCache) | Medium | Benchmark before removing cache |
| Non-composable string access (`Context`-based) | High | Need expect/actual or refactor; 186 call sites |
| Compose MP Resources generated code conflicts | Low | Namespace via `compose.resources { packageOfResClass }` |
| Build time increase (resource generation) | Low | Already set up in commons; incremental |
| Android string format differences (`values-xx-rYY`) | Medium | Verify Compose MP locale folder naming convention |

---

## 8. Effort Estimate

| Phase | Files | Effort |
|-------|-------|--------|
| Phase 0: Infrastructure | 1 build config + string copy + helper | 1-2 days |
| Phase 1: Leaf composables | ~25 files | 1 day (mechanical) |
| Phase 2: Medium composables | ~40 files | 1-2 days |
| Phase 3: Heavy + Context callers | ~333 files + refactoring | 3-5 days |
| Phase 4: Drawables | ~42 files | 1 day |
| **Total** | **~398 files** | **~7-11 days** |

Most of this is mechanical find-and-replace, suitable for scripted migration with manual review.

---

## 9. Quick Win: Script-Assisted Migration

A significant portion can be automated:
```bash
# Pseudocode for mechanical migration
# 1. For each R.string.xxx key, ensure it exists in commons strings.xml
# 2. sed -replace R.string.xxx → Res.string.xxx
# 3. sed-replace import amethyst.R → import (generated).Res
# 4. Add missing compose.resources imports
# 5. Run build to catch breakages
```

The `stringRes()` → `stringResource()` mapping needs care because of the overloaded variants, but the composable variants map 1:1 to Compose MP's `stringResource()`.
