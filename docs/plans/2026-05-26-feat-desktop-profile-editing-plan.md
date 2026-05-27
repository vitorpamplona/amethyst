---
title: "feat: Desktop Profile Editing — Full Parity + Desktop Polish"
type: feat
status: active
date: 2026-05-26
origin: docs/brainstorms/2026-05-26-desktop-profile-editing-brainstorm.md
deepened: 2026-05-26
---

# Desktop Profile Editing — Full Parity + Desktop Polish

## Enhancement Summary

**Deepened on:** 2026-05-26
**Skills applied:** compose-state-holder-ui-split, desktop-expert, kotlin-flow-state-event-modeling, compose-side-effects, nostr-expert, kotlin-multiplatform

### Key Improvements from Deepening
1. **State holder uses `MutableStateFlow`** not `mutableStateOf` — matches codebase convention (`ChatNewMessageState` pattern), KMP-safe, testable without Compose
2. **Separate `EditProfileFields` from wiring** — pure state class + state-holder composable that wires signer/broadcast
3. **Side effects clarified** — NIP-05: `snapshotFlow` + `debounce` + `collectLatest`; Upload/Save: `rememberCoroutineScope`
4. **Dialog + Card(600dp)** confirmed as correct container — NOT AlertDialog, NOT full screen
5. **`updateFromPast()` confirmed correct** — preserves unknown metadata fields in kind 0

## Overview

Desktop profile editing currently only supports display name via a single-field AlertDialog
(`UserProfileScreen.kt:961-1004`). Android supports 13 fields + image upload + NIP-39 social proofs.
This plan brings desktop to full feature parity and adds desktop-native polish (keyboard shortcuts,
drag-and-drop, URL preview, NIP-05 live verification).

## Problem Statement

Desktop users can only edit their display name. All other profile fields (bio, avatar, banner,
NIP-05, lightning address, social proofs, etc.) require switching to Android or another client.

## Proposed Solution

`Dialog + Card(600dp)` edit profile form replacing the AlertDialog. Extract shared state holder to
commons using `MutableStateFlow` (matching `ChatNewMessageState` pattern). Desktop-native UI with
keyboard shortcuts, drag-and-drop, and live NIP-05 verification.

## Technical Approach

### Architecture

```
quartz/commonMain/
├── MetadataEvent          ✅ Reuse (kind 0, all fields)
├── ExternalIdentitiesEvent ✅ Reuse (kind 10011, NIP-39)
├── Nip05Id / Nip05Client  ✅ Reuse (verification)
└── UserMetadata           ✅ Reuse (data model)

commons/jvmMain/
├── UploadOrchestrator     ✅ Reuse (Blossom upload, File-based)
├── BlossomClient          ✅ Reuse (HTTP upload)
├── BlossomAuth            ✅ Reuse (auth headers)
└── MediaCompressor        ✅ Reuse (EXIF stripping)

commons/commonMain/
├── ProfileBroadcastStatus ✅ Reuse
├── ProfileBroadcastBanner ✅ Reuse
└── EditProfileFields      🆕 NEW — @Stable state holder (13 fields, load/isDirty)

desktopApp/jvmMain/
├── EditProfileScreen      🆕 NEW — Dialog+Card form + wiring composable
├── DesktopFilePicker      ✅ Reuse (already exists)
└── UserProfileScreen      📦 MODIFY — replace AlertDialog, open EditProfileScreen
```

### Implementation Phases

#### Phase 1: Shared State Holder (commons/commonMain)

**Insight from skills:** Use `MutableStateFlow` (not `mutableStateOf`) to match codebase convention.
Every shared state holder in commons uses `MutableStateFlow` (`ChatNewMessageState`,
`AdvancedSearchBarState`, `FeedContentState`). Annotate `@Stable` for Compose skipping.
Separate pure state from infra wiring (compose-state-holder-ui-split skill).

**New file:** `commons/src/commonMain/kotlin/.../commons/profile/EditProfileFields.kt`

```kotlin
@Stable
class EditProfileFields {
    val name = MutableStateFlow("")
    val displayName = MutableStateFlow("")
    val about = MutableStateFlow("")
    val picture = MutableStateFlow("")
    val banner = MutableStateFlow("")
    val website = MutableStateFlow("")
    val pronouns = MutableStateFlow("")
    val nip05 = MutableStateFlow("")
    val lnAddress = MutableStateFlow("")
    val lnURL = MutableStateFlow("")
    val twitter = MutableStateFlow("")
    val github = MutableStateFlow("")
    val mastodon = MutableStateFlow("")

    // Snapshot of initial values for isDirty comparison
    private var snapshot = emptyMap<String, String>()

    val isDirty: Boolean get() = currentValues() != snapshot

    fun loadFrom(metadata: MetadataEvent?, identities: ExternalIdentitiesEvent?) {
        // Populate fields from events, take snapshot
    }

    fun clear() { /* reset all to "" */ }

    private fun currentValues(): Map<String, String> = mapOf(
        "name" to name.value, "displayName" to displayName.value, /* ... */
    )
}
```

**Key decisions (deepened):**
- `MutableStateFlow` — KMP-safe, testable without Compose, matches commons convention
- `@Stable` — truthful contract (all public properties are StateFlow)
- No `signer`/`broadcastEvent` in constructor — those go in the wiring composable
- `isDirty` as simple property (not `derivedStateOf` since we're using StateFlow)
- `loadFrom()` accepts events directly + takes snapshot for dirty tracking

**Files:**
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/profile/EditProfileFields.kt` — NEW

#### Phase 2: Desktop Edit Profile Screen

**Insight from desktop-expert:** Use `Dialog { Card(Modifier.width(600.dp)) }` — established pattern
for rich forms (`ComposeNoteDialog`, `ImportFollowListDialog`). NOT `AlertDialog` (too simple) or
full screen (reserved for editors like `ArticleEditorScreen`).

**New file:** `desktopApp/src/jvmMain/kotlin/.../desktop/ui/profile/EditProfileScreen.kt`

**Structure — two composables (state-holder/UI split):**

```kotlin
// 1. State-holder composable — wires infra
@Composable
fun EditProfileDialog(
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    latestMetadata: MetadataEvent?,
    latestIdentities: ExternalIdentitiesEvent?,
    onDismiss: () -> Unit,
) {
    val fields = remember { EditProfileFields() }
    LaunchedEffect(Unit) { fields.loadFrom(latestMetadata, latestIdentities) }
    val scope = rememberCoroutineScope()

    EditProfileContent(
        fields = fields,
        onSave = { scope.launch { save(fields, account.signer, relayManager) } },
        onCancel = onDismiss,
        // ... upload callbacks wired here
    )
}

// 2. Pure UI composable — previewable, no infra
@Composable
fun EditProfileContent(
    fields: EditProfileFields,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    isUploadingAvatar: Boolean,
    isUploadingBanner: Boolean,
    nip05Status: Nip05VerificationStatus,
    broadcastStatus: ProfileBroadcastStatus,
    modifier: Modifier = Modifier,
) { /* Dialog + Card(600dp) layout */ }
```

**Layout:**
```
┌──────────────────────────────────────────────────┐
│  Edit Profile                     [Cancel] [Save]│
├──────────────────────────────────────────────────┤
│  ┌─────────────┐  Display Name [_______________] │
│  │   Avatar    │  Name (@)     [_______________] │
│  │  [Upload]   │  Pronouns     [_______________] │
│  └─────────────┘                                 │
│                                                  │
│  Banner [drag image here or click to upload]     │
│  ┌──────────────────────────────────────────┐    │
│  │          banner preview                   │    │
│  └──────────────────────────────────────────┘    │
│                                                  │
│  About [___________________________________]     │
│        [___________________________________]     │
│                                                  │
│  Website       [_______________]                 │
│  NIP-05        [_______________] ✓ verified      │
│  LN Address    [_______________]                 │
│  LNURL (legacy)[_______________]                 │
│                                                  │
│  ▶ Social Proofs (NIP-39)                        │
│    Twitter   [_______________]                   │
│    GitHub    [_______________]                   │
│    Mastodon  [_______________]                   │
│                                                  │
│  [ProfileBroadcastBanner — shows on save]        │
└──────────────────────────────────────────────────┘
```

**Keyboard shortcuts (OS-aware):**
```kotlin
val isMacOS = System.getProperty("os.name").contains("Mac", ignoreCase = true)
Modifier.onPreviewKeyEvent { event ->
    when {
        event.type == KeyEventType.KeyDown && event.key == Key.Escape -> { onCancel(); true }
        event.type == KeyEventType.KeyDown && event.key == Key.S &&
            (if (isMacOS) event.isMetaPressed else event.isCtrlPressed) -> { onSave(); true }
        else -> false
    }
}
```

**Files:**
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/profile/EditProfileScreen.kt` — NEW

#### Phase 3: Image Upload Integration

**Insight from compose-side-effects:** Use `rememberCoroutineScope().launch` from click callback —
NOT `LaunchedEffect` (upload is a discrete user action, not reactive state). Cancellation is free
via structured concurrency — scope dies when dialog leaves composition.

**Flow:**
1. User clicks upload button or drops image onto avatar/banner area
2. `DesktopFilePicker.pickMediaFiles()` opens → returns `File` (runs on `Dispatchers.IO` thread)
3. `UploadOrchestrator.upload(file, alt, serverBaseUrl, signer)` uploads via Blossom
4. Result URL written to `fields.picture.value` or `fields.banner.value`
5. `AsyncImage` shows live preview of the URL

**Upload callback (in wiring composable):**
```kotlin
val scope = rememberCoroutineScope()
var isUploadingAvatar by remember { mutableStateOf(false) }

fun onPickAvatar() {
    scope.launch(Dispatchers.IO) {
        val files = DesktopFilePicker.pickMediaFiles()
        val file = files.firstOrNull() ?: return@launch
        isUploadingAvatar = true
        try {
            val result = orchestrator.upload(file, "Profile picture", serverBaseUrl, account.signer)
            fields.picture.value = result.blossom.url
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { /* error toast */ }
        finally { isUploadingAvatar = false }
    }
}
```

**Drag-and-drop (from `ComposeNoteDialog.kt:136-149`):**
```kotlin
val dropTarget = remember {
    object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val dropEvent = event.nativeEvent as? DropTargetDropEvent ?: return false
            dropEvent.acceptDrop(DnDConstants.ACTION_COPY)
            val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            val imageFile = files.firstOrNull { it.extension.lowercase() in IMAGE_EXTENSIONS }
            imageFile?.let { onPickAvatar(it) } // or onPickBanner
            return true
        }
    }
}
```

#### Phase 4: NIP-05 Live Verification

**Insight from compose-side-effects:** Use `LaunchedEffect(Unit)` + `snapshotFlow` + `debounce(500)`
\+ `collectLatest`. `snapshotFlow` bridges Compose/Flow state reads. `collectLatest` auto-cancels
stale network calls. Do NOT key LaunchedEffect by nip05 value (defeats debounce).

```kotlin
sealed class Nip05Status {
    data object Idle : Nip05Status()
    data object Checking : Nip05Status()
    data object Verified : Nip05Status()
    data object NotVerified : Nip05Status()
    data class Error(val message: String) : Nip05Status()
}

// In the wiring composable:
var nip05Status by remember { mutableStateOf<Nip05Status>(Nip05Status.Idle) }

LaunchedEffect(Unit) {
    snapshotFlow { fields.nip05.value }   // or collectAsState + snapshotFlow
        .debounce(500)
        .mapNotNull { Nip05Id.parse(it) }
        .collectLatest { nip05Id ->
            nip05Status = Nip05Status.Checking
            try {
                val result = Nip05Client().load(nip05Id)
                nip05Status = if (result?.names?.containsValue(account.pubKeyHex) == true)
                    Nip05Status.Verified else Nip05Status.NotVerified
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { nip05Status = Nip05Status.Error(e.message ?: "Failed") }
        }
}
```

**Note:** Since `fields.nip05` is `MutableStateFlow`, we use `.collectLatest` directly instead of
`snapshotFlow`. The `snapshotFlow` is only needed for `mutableStateOf` Compose state.

#### Phase 5: Wire Navigation + Replace AlertDialog

**Changes to `UserProfileScreen.kt`:**
- Remove lines 960-1004 (AlertDialog)
- Remove `updateProfileDisplayName()` function (lines 1060-1114)
- Remove `editingDisplayName` state variable
- Add `var showEditProfile by remember { mutableStateOf(false) }`
- On edit button click: `showEditProfile = true`
- When `showEditProfile`: show `EditProfileDialog(...)` passing account, relayManager, latestMetadata

**Protocol correctness (nostr-expert):**
- `MetadataEvent.updateFromPast()` preserves unknown fields — correct
- Metadata (kind 0) and identities (kind 10011) are separate events, broadcast independently
- Both broadcast to all connected relays via `relayManager.broadcastToAll()`

#### Phase 6: Desktop Polish

- **Keyboard shortcuts:** Ctrl+S / Cmd+S save, Esc cancel — OS-aware via `isMetaPressed` vs `isCtrlPressed`
- **Drag-and-drop:** Drop zones on avatar and banner areas — image-only filter
- **URL preview:** `AsyncImage` for avatar (circular) and banner (landscape) as user types/uploads
- **Banner aspect guidance:** `supportingText` "Recommended: landscape image (~3:1 aspect ratio)"
- **Unsaved changes warning:** If `fields.isDirty` and user presses Esc/Cancel, show confirmation AlertDialog
- **Tab navigation:** Natural tab order (Display Name → Name → Pronouns → About → ...)
- **FocusRequester:** Auto-focus display name on open (pattern from `NewDmDialog.kt:82`)
- **Section composables:** Break form into `BasicInfoSection`, `MediaSection`, `VerificationSection`,
  `LightningSection`, `SocialProofsSection` to limit recomposition scope

## Extraction Matrix

| Component | Status | Location | Action |
|-----------|--------|----------|--------|
| `MetadataEvent` | ✅ Exists | `quartz/commonMain/` | Reuse |
| `ExternalIdentitiesEvent` | ✅ Exists | `quartz/commonMain/` | Reuse |
| `Nip05Id` / `Nip05Client` | ✅ Exists | `quartz/commonMain/` | Reuse |
| `ProfileBroadcastStatus` | ✅ Exists | `commons/commonMain/` | Reuse |
| `ProfileBroadcastBanner` | ✅ Exists | `commons/commonMain/` | Reuse |
| `UploadOrchestrator` | ✅ Exists | `commons/jvmMain/` | Reuse |
| `BlossomClient` | ✅ Exists | `commons/jvmMain/` | Reuse |
| `DesktopFilePicker` | ✅ Exists | `desktopApp/jvmMain/` | Reuse |
| `EditProfileFields` | 🆕 New | `commons/commonMain/` | Create — `@Stable`, `MutableStateFlow` |
| `EditProfileScreen` | 🆕 New | `desktopApp/jvmMain/` | Create — Dialog+Card, state-holder/UI split |
| `NewUserMetadataViewModel` | ⚠️ Android | `amethyst/` | Reference only |

## Acceptance Criteria

### Functional
- [x] All 13 profile fields editable
- [x] Avatar upload via file picker (drag-and-drop deferred)
- [x] Banner upload via file picker (drag-and-drop deferred)
- [x] Live URL preview for avatar and banner
- [x] NIP-05 live verification with status indicator
- [x] Social proofs section (NIP-39) — collapsible
- [x] Broadcast status feedback via ProfileBroadcastBanner
- [x] Keyboard shortcuts: Ctrl+S/Cmd+S save, Esc cancel
- [x] Unsaved changes confirmation
- [x] Auto-focus on display name field

### Non-Functional
- [x] `EditProfileFields` in commons/commonMain using `MutableStateFlow`
- [x] No Android dependencies in shared code
- [x] Desktop file picker (not Android gallery)
- [x] Upload via existing `UploadOrchestrator` (Blossom)
- [x] Compiles: `./gradlew :desktopApp:compileKotlin` and `./gradlew :commons:compileKotlinJvm`
- [x] Spotless: `./gradlew spotlessApply` passes

## Sources & References

### Origin
- **Brainstorm:** [docs/brainstorms/2026-05-26-desktop-profile-editing-brainstorm.md](docs/brainstorms/2026-05-26-desktop-profile-editing-brainstorm.md)
- Key decisions: Option A (full form), extract ViewModel, all extras worth it

### Skill Insights Applied
- **compose-state-holder-ui-split**: Separate `EditProfileFields` (pure) from wiring composable
- **kotlin-flow-state-event-modeling**: Use `MutableStateFlow` matching commons convention
- **compose-side-effects**: `snapshotFlow`+debounce for NIP-05, `rememberCoroutineScope` for upload/save
- **desktop-expert**: Dialog+Card(600dp) container, OS-aware shortcuts
- **nostr-expert**: `updateFromPast()` correct, broadcast metadata+identities separately
- **kotlin-multiplatform**: commons/commonMain correct placement for `EditProfileFields`

### Internal References
- Android ViewModel: `amethyst/.../ui/actions/NewUserMetadataViewModel.kt`
- Desktop current edit: `desktopApp/.../ui/UserProfileScreen.kt:960-1114`
- Upload orchestrator: `commons/jvmMain/.../service/upload/UploadOrchestrator.kt`
- File picker: `desktopApp/.../ui/media/DesktopFilePicker.kt`
- Drag-and-drop pattern: `desktopApp/.../ui/ComposeNoteDialog.kt:136-149`
- Keyboard shortcut pattern: `desktopApp/.../ui/ArticleEditorScreen.kt:198-200`
- Dialog pattern: `desktopApp/.../ui/account/AddAccountDialog.kt:56-127`
- Collapsible section: `desktopApp/.../ui/settings/LocalRelaySettingsScreen.kt:115-146`
- Chat state holder pattern: `commons/commonMain/.../chats/ChatNewMessageState.kt`
- NIP-05 client: `quartz/commonMain/.../nip05DnsIdentifiers/Nip05Client.kt`
- MetadataEvent: `quartz/commonMain/.../nip01Core/metadata/MetadataEvent.kt:116-222`
- ExternalIdentitiesEvent: `quartz/commonMain/.../nip39ExtIdentities/ExternalIdentitiesEvent.kt:52-84`
