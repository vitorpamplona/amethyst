---
name: android-expert
description: Android platform patterns for the `amethyst/` module. Use when working with (1) Android navigation (Navigation 3, the app-owned back stack, type-safe routes, bottom nav), (2) runtime permissions (camera, notifications, biometrics), (3) platform APIs (Intent, Context, Activity, ContentResolver), (4) Material3 theming and edge-to-edge UI, (5) AndroidManifest.xml and intent filters, (6) Proguard/R8 and APK optimization, (7) Android lifecycle (ViewModel, collectAsStateWithLifecycle), (8) Coil image loading. Delegates shared composables to compose-expert, build files to gradle-expert, and KMP structure to kotlin-multiplatform.
---

# android-expert

Android platform expertise for Amethyst Multiplatform project. Covers Navigation 3, Material3, permissions, lifecycle, and Android-specific patterns in KMP architecture.

## When to Use

Auto-invoke when working with:
- Android navigation (Navigation 3, routes, bottom nav)
- Runtime permissions (camera, notifications, biometric)
- Platform APIs (Intent, Context, Activity)
- Material3 theming and edge-to-edge UI
- Android build configuration (Proguard, APK optimization)
- AndroidManifest.xml configuration
- Android lifecycle (ViewModel, collectAsStateWithLifecycle)

## Core Mental Model

**Single Activity Architecture + Navigation 3**

```
MainActivity (Single Entry Point)
    ├── enableEdgeToEdge()
    ├── AmethystTheme { }
    └── NavDisplay (renders Nav.stacks, an app-owned back stack)
        ├── Route.Home → HomeScreen
        ├── Route.Profile(id) → ProfileScreen
        └── Route.Settings → SettingsScreen

Intent Filters (11+)
    ├── ACTION_MAIN (launcher)
    ├── ACTION_SEND (share)
    ├── ACTION_VIEW (deep links: nostr://, https://...)
    └── NFC_ACTION_NDEF_DISCOVERED
```

**Key Principles:**
1. **Type-Safe Navigation** - @Serializable routes, no strings
2. **Declarative Permissions** - Request contextually with Accompanist
3. **Edge-to-Edge + Insets** - Scaffold handles system bars
4. **ViewModel + Flow → State** - Survive config changes
5. **Platform Isolation** - Android code in `amethyst/` module or `androidMain/`

## Architecture Overview

### Module Structure

```
amethyst/                    # Android app module
├── src/
│   ├── main/
│   │   ├── java/com/vitorpamplona/amethyst/
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt          # Entry point
│   │   │   │   ├── navigation/
│   │   │   │   │   ├── AppNavigation.kt     # NavDisplay + every destination
│   │   │   │   │   ├── NavigationEffects.kt # destination registry, builders, transitions
│   │   │   │   │   ├── navs/Nav.kt, NavBackStacks.kt  # INav over the back stack
│   │   │   │   │   ├── routes/RouteNavController.kt  # current-route helpers
│   │   │   │   │   └── bottombars/AppBottomBar.kt
│   │   │   │   ├── screen/                  # 80+ screens
│   │   │   │   └── theme/Theme.kt           # AmethystTheme (accent, prefs, window insets)
│   │   │   └── Amethyst.kt                  # Application class
│   │   └── AndroidManifest.xml              # Permissions, intent filters
│   └── androidMain/                         # KMP Android source set
│       └── kotlin/                          # Platform-specific code
└── build.gradle                             # Android config
```

The `@Serializable` `Route` catalog is headless and lives in
`commons/.../commons/model/navigation/Routes.kt`; `INav`/`EmptyNav` and the top bars are
in `commonsUI/.../commons/ui/navigation/`. The palettes, `ColorScheme.*` tokens, sizes and
typography are in `commonsUI/.../commons/ui/theme/` (markdown styles in its `jvmAndroid`).
Add a route to the commons `Routes.kt`; wire its destination in the app's `AppNavigation.kt`.

## 1. Navigation (Navigation 3)

The app owns its back stack; Navigation 3's `NavDisplay` only renders it. There is no
`NavController` and no graph.

- **Routes** are the `@Serializable sealed class Route` in commons `Routes.kt`. The back stack is
  saved through kotlinx-serialization, so every route and every argument must be serializable.
- **`NavBackStacks`** (`navs/NavBackStacks.kt`) holds `stack` (what is on screen, never empty,
  starts at `Route.Home`) and `savedTabs` (the root entry of every bottom-bar tab the user left,
  which keeps that tab's ViewModels and scroll state alive). Each `NavStackEntry` has an `id`;
  its `contentKey` (`"nav-$id"`) is what saved state and ViewModels are keyed by, so the same
  route opened twice is two screens. `tabRoot` / `drawerRoot` mark tab roots and drawer screens.
- **`Nav`** (`navs/Nav.kt`) implements the shared `INav` over it: `nav`, `navDrawer`, `newStack`,
  `navBottomBar` (= `switchTab`), `popBack`, `popUpTo`, each after the keyboard settles.
  `nav.currentRoute` is snapshot state; read it (or `derivedStateOf` over it) instead of
  observing a controller. `LocalNavStackEntry` is the entry of the screen being composed.
- **Destinations** are registered in `AppNavigation.kt`'s `appDestinations` with the builders in
  `NavigationEffects.kt`. The builder picks the motion and the reading-column cap:

```kotlin
composableCapped<Route.Home> { HomeScreen(accountViewModel, nav) }          // fade, capped
composable<Route.Message> { MessagesScreen(accountViewModel, nav) }          // fade, full width
composableFromEnd<Route.Polls> { PollsScreen(accountViewModel, nav) }        // drill-in slide
composableFromEndArgs<Route.Note> { NoteScreen(it.id, accountViewModel, nav) }
composableFromBottomArgs<Route.NewPost> { NewPostScreen(it.message, accountViewModel, nav) } // modal
```

`BuildNavigation` decorates `stack + savedTabs` with the saveable-state and ViewModel-store
decorators, shows only `stack` in `NavDisplay`, and drives the push / pop / predictive-back
transitions from each entry's family and `tabRoot` flag.

**Adding a screen:** add the route to commons `Routes.kt` (`@Serializable`), then one builder line
in `appDestinations`. A route nothing registered fails the first time it is opened.

**Reference:** See `references/android-navigation.md` for the back-stack rules in detail.

## 2. Runtime Permissions

### Declarative Permission Handling

**Accompanist Pattern (Experimental API):**
```kotlin
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraFeature() {
    val cameraPermissionState = rememberPermissionState(
        Manifest.permission.CAMERA
    )

    when {
        cameraPermissionState.status.isGranted -> {
            // Permission granted - show camera UI
            CameraPreview()
        }

        cameraPermissionState.status.shouldShowRationale -> {
            // Show rationale and request again
            Column {
                Text("Camera permission is needed to scan QR codes")
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() }
                ) {
                    Text("Grant Permission")
                }
            }
        }

        else -> {
            // First time - request permission
            Button(
                onClick = { cameraPermissionState.launchPermissionRequest() }
            ) {
                Text("Enable Camera")
            }
        }
    }
}
```

### Multiple Permissions

```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaUploadFeature() {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    )

    when {
        permissionsState.allPermissionsGranted -> {
            MediaUploadUI()
        }

        permissionsState.shouldShowRationale -> {
            RationaleDialog(
                onConfirm = { permissionsState.launchMultiplePermissionRequest() },
                onDismiss = { /* Handle dismissal */ }
            )
        }

        else -> {
            PermissionRequestButton(
                onClick = { permissionsState.launchMultiplePermissionRequest() }
            )
        }
    }
}
```

### Lifecycle-Aware Permission Requests

**Amethyst Pattern (LoggedInPage.kt):**
```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationRegistration(accountViewModel: AccountViewModel) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionState = rememberPermissionState(
            Manifest.permission.POST_NOTIFICATIONS
        )

        if (notificationPermissionState.status.isGranted) {
            LifecycleResumeEffect(
                key1 = accountViewModel,
                notificationPermissionState.status.isGranted
            ) {
                val scope = rememberCoroutineScope()
                scope.launch {
                    PushNotificationUtils.checkAndInit(
                        context = context,
                        accountViewModel = accountViewModel
                    )
                }

                onPauseOrDispose {
                    // Cleanup when paused
                }
            }
        }
    }
}
```

### AndroidManifest Permission Declarations

**Key Permissions in Amethyst:**
```xml
<!-- AndroidManifest.xml -->
<manifest>
    <!-- Network -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />

    <!-- Media -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission
        android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />

    <!-- Android 13+ Notifications -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- NFC -->
    <uses-permission android:name="android.permission.NFC" />

    <!-- Location (for geohashing) -->
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- Foreground Services -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission
        android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
</manifest>
```

**Reference:** See `references/android-permissions.md` for complete permission patterns.

## 3. Material3 + Edge-to-Edge

### Edge-to-Edge Setup

**MainActivity Pattern:**
```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()  // Android 15+ immersive UI
        super.onCreate(savedInstanceState)

        setContent {
            AmethystTheme {
                AccountScreen()
            }
        }
    }
}
```

### Theme Configuration

**Material3 Color Schemes:**
```kotlin
// theme/Theme.kt
private val DarkColorPalette = darkColorScheme(
    primary = Purple200,
    secondary = Teal200,
    tertiary = Pink80,
    background = Color.Black,
    surface = Color.Black,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorPalette = lightColorScheme(
    primary = Purple500,
    secondary = Teal700,
    tertiary = Pink40,
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Composable
fun AmethystTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorPalette else LightColorPalette

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

### Scaffold with Insets

**Handling System Bars:**
```kotlin
@Composable
fun MainScreen(nav: Nav) {
    Scaffold(
        topBar = { AppTopBar(nav.currentRoute) },
        bottomBar = { AppBottomBar(nav.currentRoute, nav) },
        floatingActionButton = { NewPostFab() }
    ) { innerPadding ->
        // Scaffold automatically handles system bar insets
        Box(Modifier.padding(innerPadding)) {
            BuildNavigation(accountViewModel, nav)
        }
    }
}
```

**Custom Inset Handling:**
```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding

@Composable
fun CustomEdgeToEdgeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()  // Add padding for system bars
    ) {
        // Content draws edge-to-edge with safe padding
    }
}
```

## 4. ViewModel + Lifecycle

### ViewModel Pattern

**Standard Structure (80+ ViewModels in Amethyst):**
```kotlin
class FeedViewModel(
    private val accountStateViewModel: AccountStateViewModel
) : ViewModel() {

    private val _feedState = MutableStateFlow<FeedState>(FeedState.Loading)
    val feedState: StateFlow<FeedState> = _feedState.asStateFlow()

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            _feedState.value = FeedState.Loading
            try {
                val posts = repository.getFeed()
                _feedState.value = FeedState.Success(posts)
            } catch (e: Exception) {
                _feedState.value = FeedState.Error(e.message)
            }
        }
    }

    fun refresh() {
        loadFeed()
    }
}

sealed class FeedState {
    object Loading : FeedState()
    data class Success(val posts: List<Post>) : FeedState()
    data class Error(val message: String?) : FeedState()
}
```

### Compose Integration

**collectAsStateWithLifecycle Pattern:**
```kotlin
@Composable
fun FeedScreen(
    feedViewModel: FeedViewModel = viewModel()
) {
    val feedState by feedViewModel.feedState.collectAsStateWithLifecycle()

    when (feedState) {
        is FeedState.Loading -> {
            LoadingIndicator()
        }
        is FeedState.Success -> {
            val posts = (feedState as FeedState.Success).posts
            LazyColumn {
                items(posts) { post ->
                    PostCard(post)
                }
            }
        }
        is FeedState.Error -> {
            ErrorScreen(
                message = (feedState as FeedState.Error).message,
                onRetry = { feedViewModel.refresh() }
            )
        }
    }
}
```

### Lifecycle Effects

**LifecycleResumeEffect Pattern:**
```kotlin
@Composable
fun ChatScreen(chatViewModel: ChatViewModel) {
    LifecycleResumeEffect(key1 = chatViewModel) {
        // Called when composable resumes (onResume)
        chatViewModel.connectToRelay()

        onPauseOrDispose {
            // Called when composable pauses (onPause) or disposes
            chatViewModel.disconnectFromRelay()
        }
    }
}
```

**DisposableEffect for Cleanup:**
```kotlin
@Composable
fun VideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
        }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { PlayerView(it).apply { player = exoPlayer } }
    )
}
```

## 5. Platform APIs

### Activity & Context Access

**LocalContext Pattern:**
```kotlin
@Composable
fun ShareButton(text: String) {
    val context = LocalContext.current

    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "Share via"))
        }
    ) {
        Text("Share")
    }
}
```

**Activity Reference:**
```kotlin
// WindowUtils.kt pattern
@Composable
fun getActivity(): Activity? = LocalContext.current.getActivity()

tailrec fun Context.getActivity(): ComponentActivity =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.getActivity()
        else -> throw IllegalStateException("Context not an Activity")
    }

// Usage
@Composable
fun FullscreenToggle() {
    val activity = getActivity()

    Button(
        onClick = {
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    ) {
        Text("Go Fullscreen")
    }
}
```

### Intent Handling

**Deep Links (AppNavigation.kt pattern):**
```kotlin
@Composable
fun AppNavigation(
    nav: Nav,
    accountViewModel: AccountViewModel
) {
    val activity = LocalContext.current as? Activity

    LaunchedEffect(activity?.intent) {
        activity?.intent?.let { intent ->
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val sharedImage = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    nav.newStack(
                        Route.NewPost(message = sharedText, attachment = sharedImage.toString())
                    )
                }

                Intent.ACTION_VIEW -> {
                    val uri = intent.data
                    when (uri?.scheme) {
                        "nostr" -> handleNostrUri(uri, nav)
                        "https", "http" -> handleWebUri(uri, nav)
                    }
                }
            }
        }
    }

    BuildNavigation(accountViewModel, nav)
}

fun handleNostrUri(uri: Uri, nav: Nav) {
    // nostr:npub1... -> Profile
    // nostr:note1... -> Note
    // nostr:nevent1... -> Event
    when {
        uri.path?.startsWith("npub") == true -> {
            nav.newStack(Route.Profile(uri.path!!))
        }
        uri.path?.startsWith("note") == true -> {
            nav.newStack(Route.Note(uri.path!!))
        }
    }
}
```

### File Sharing with FileProvider

**ShareHelper Pattern:**
```kotlin
fun shareImage(context: Context, imageUri: Uri) {
    try {
        // Get file from cache
        val cachedFile = getCachedFile(context, imageUri)

        // Create content URI via FileProvider
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            cachedFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
```

**FileProvider Configuration (AndroidManifest.xml):**
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### Activity Results

**External Signer Integration (Amethyst pattern):**
```kotlin
@Composable
fun SignerIntegration(accountViewModel: AccountViewModel) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                accountViewModel.account.signer.newResponse(data)
            }
        }
    }

    Button(
        onClick = {
            val signerIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("nostrsigner:...")
            }
            launcher.launch(signerIntent)
        }
    ) {
        Text("Sign with External App")
    }
}
```

## 6. Build Configuration

Build files — the `android {}` block, the version catalog, dependencies,
Proguard/R8, and Desktop packaging — are **gradle-expert's** domain. Use
`/gradle-expert` instead of duplicating that guidance here. In particular, the
app version and the Android `versionCode` both live in
`gradle/libs.versions.toml` (`app` / `appCode`); `amethyst/build.gradle.kts`
reads both from the catalog, so a release bump is a single-file edit.

The one build detail that is genuinely Android-specific — not generic Gradle —
is the **product-flavor split** that ships two channels from one codebase:

```gradle
flavorDimensions = ["channel"]
productFlavors {
    create("play")   { dimension = "channel" } // Firebase, Google services
    create("fdroid") { dimension = "channel" } // UnifiedPush, open-source only
}
```

`play` carries Firebase/Google services; `fdroid` swaps them for UnifiedPush and
open-source alternatives so the F-Droid build stays proprietary-free.

Proguard/R8 rules: see `references/proguard-rules.md`. APK size analysis:
`scripts/analyze-apk-size.sh`.

## 7. KMP Android Source Sets

### Android Module Layout

**Amethyst Structure:**
```
amethyst/
├── src/
│   ├── main/                    # Standard Android
│   │   ├── java/com/.../        # Compose UI code
│   │   ├── res/                 # Android resources
│   │   └── AndroidManifest.xml
│   └── androidMain/             # KMP Android source set (if needed)
│       └── kotlin/              # Platform-specific utilities
└── build.gradle
```

**Platform-Specific Code:**
```kotlin
// commons/src/androidMain/kotlin/Platform.android.kt
actual fun openExternalUrl(url: String, context: Any) {
    val ctx = context as Context
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    ctx.startActivity(intent)
}

actual fun shareText(text: String, context: Any) {
    val ctx = context as Context
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(intent, "Share"))
}
```

### Build Configuration for KMP

```gradle
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
}

android {
    sourceSets {
        // Link androidMain source set
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDirs("src/main/java", "src/androidMain/kotlin")
        }
    }
}
```

## Common Patterns

### 1. Single Activity Architecture

**All screens in one activity, navigation via Compose:**
```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AmethystTheme {
                val accountViewModel: AccountStateViewModel = viewModel()
                AccountScreen(accountViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DEFAULT_MUTED_SETTING.value = true
    }

    override fun onPause() {
        super.onPause()
        LanguageTranslatorService.clear()
    }
}
```

### 2. Configuration Changes

**ViewModels survive rotation:**
```kotlin
// ViewModel persists across config changes
@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel = viewModel()
) {
    val profile by profileViewModel.profile.collectAsStateWithLifecycle()

    // UI rebuilds on rotation, but ViewModel data persists
    ProfileContent(profile)
}
```

### 3. Resource Access

**Strings do not go in `amethyst/src/main/res`.** New user-visible strings live in
`commonsUI/src/commonMain/composeResources/values/strings.xml` and are read as
`Res.string.x`, even on an Android-only screen. See "Strings" in `.claude/CLAUDE.md`
for the rule and the small platform tier that is the only exception.

```kotlin
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.button_clicked
import com.vitorpamplona.amethyst.commons.resources.button_label
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun LocalizedButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            // No blocking accessor outside composition: read it from a coroutine,
            // or resolve it in composition and capture the String.
            scope.launch {
                Toast.makeText(context, loadStringRes(Res.string.button_clicked), Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Text(stringRes(Res.string.button_label))
    }
}
```

## Testing Android Components

### Navigation Testing

The back stack is plain state, so navigation rules are unit-tested without Compose or mocks
(see `amethyst/src/test/.../ui/navigation/NavBackStacksTest.kt` and `NavBottomBarStackTest.kt`):

```kotlin
@Test
fun aTabTapDropsWhatWasPushedOnTopOfTheTab() =
    runTest {
        val stacks = NavBackStacks()
        val nav = Nav(stacks, this)

        nav.nav(Route.Note("n"))
        nav.navBottomBar(Route.Message)
        advanceUntilIdle()

        assertEquals(listOf(Route.Home, Route.Message), stacks.stack.map { it.route })
    }
```

### Permission Testing

```kotlin
@Test
fun testPermissionRequest() {
    val scenario = launchActivity<MainActivity>()

    scenario.onActivity { activity ->
        // Grant permission via UiAutomator
        grantPermissionViaUi(Manifest.permission.CAMERA)
    }

    composeTestRule.onNodeWithText("Camera Ready").assertExists()
}
```

## Anti-Patterns to Avoid

1. **String-based navigation** - Use type-safe @Serializable routes
2. **Requesting permissions eagerly** - Request contextually before feature use
3. **Ignoring edge-to-edge** - Handle insets properly with Scaffold
4. **Using GlobalScope** - Use viewModelScope or rememberCoroutineScope
5. **Not handling config changes** - Use ViewModel + collectAsStateWithLifecycle
6. **Hardcoded system bar heights** - Use WindowInsets APIs
7. **Blocking main thread** - Use viewModelScope.launch(Dispatchers.IO)

## Quick Reference

| Task | Pattern |
|------|---------|
| **Navigate** | `nav.nav(Route.Profile(id))` |
| **Request Permission** | `rememberPermissionState().launchPermissionRequest()` |
| **Access Context** | `val context = LocalContext.current` |
| **Get Activity** | `val activity = context.getActivity()` |
| **Open URL** | `Intent(ACTION_VIEW, Uri.parse(url))` |
| **Share Text** | `Intent(ACTION_SEND).putExtra(EXTRA_TEXT, text)` |
| **Observe Flow** | `flow.collectAsStateWithLifecycle()` |
| **Lifecycle Effect** | `LifecycleResumeEffect { ... }` |
| **Handle Insets** | `Modifier.systemBarsPadding()` |
| **Theme** | `MaterialTheme(colorScheme = ...) { }` |

## File Locations

**Key Android Files:**
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/MainActivity.kt`
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/model/navigation/Routes.kt` (route catalog)
- `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/navigation/navs/INav.kt`
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/AppNavigation.kt`
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/theme/Theme.kt` (`AmethystTheme`)
- `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/theme/AmethystColorScheme.kt` (palettes + `ColorScheme.*` tokens)
- `amethyst/src/main/AndroidManifest.xml`
- `amethyst/build.gradle`

## Additional Resources

- `references/android-navigation.md` - Complete navigation patterns and examples
- `references/android-permissions.md` - Permission handling patterns
- `references/proguard-rules.md` - Proguard configuration
- `references/image-loading.md` - Coil 3.x setup, custom fetchers (Blossom/Base64/BlurHash/ThumbHash), `MyAsyncImage`, `RobohashAsyncImage`
- `scripts/analyze-apk-size.sh` - APK size optimization script

## When NOT to Use

- Desktop-specific features → Use `desktop-expert` skill
- iOS-specific features → Use `ios-expert` skill
- Shared KMP code → Use `kotlin-multiplatform` skill
- Nostr protocol → Use `nostr-expert` skill
- Compose UI components → Use `compose-expert` skill
