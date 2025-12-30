# android-expert

Android platform expertise for Amethyst Multiplatform project. Covers Compose Navigation, Material3, permissions, lifecycle, and Android-specific patterns in KMP architecture.

## When to Use

Auto-invoke when working with:
- Android navigation (Navigation Compose, routes, bottom nav)
- Runtime permissions (camera, notifications, biometric)
- Platform APIs (Intent, Context, Activity)
- Material3 theming and edge-to-edge UI
- Android build configuration (Proguard, APK optimization)
- AndroidManifest.xml configuration
- Android lifecycle (ViewModel, collectAsStateWithLifecycle)

## Core Mental Model

**Single Activity Architecture + Compose Navigation**

```
MainActivity (Single Entry Point)
    ├── enableEdgeToEdge()
    ├── AmethystTheme { }
    └── NavHost
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
│   │   │   │   │   ├── AppNavigation.kt     # NavHost
│   │   │   │   │   ├── routes/Routes.kt     # @Serializable routes
│   │   │   │   │   └── bottombars/AppBottomBar.kt
│   │   │   │   ├── screen/                  # 80+ screens
│   │   │   │   └── theme/Theme.kt           # Material3 theme
│   │   │   └── Amethyst.kt                  # Application class
│   │   └── AndroidManifest.xml              # Permissions, intent filters
│   └── androidMain/                         # KMP Android source set
│       └── kotlin/                          # Platform-specific code
└── build.gradle                             # Android config
```

## 1. Type-Safe Navigation

### Pattern: @Serializable Routes

**Best Practice (Navigation 2.8.0+):**
```kotlin
// Routes.kt - Define all routes with type safety
@Serializable
sealed class Route {
    @Serializable object Home : Route()
    @Serializable object Search : Route()
    @Serializable data class Profile(val pubkey: String) : Route()
    @Serializable data class Note(val noteId: String) : Route()
    @Serializable data class Thread(val noteId: String) : Route()
}

// AppNavigation.kt - NavHost setup
@Composable
fun AppNavigation(
    navController: NavHostController,
    accountViewModel: AccountViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home,
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        composable<Route.Home> {
            HomeScreen(accountViewModel, navController)
        }

        composable<Route.Profile> { backStackEntry ->
            val profile = backStackEntry.toRoute<Route.Profile>()
            ProfileScreen(profile.pubkey, accountViewModel, navController)
        }

        composable<Route.Note> { backStackEntry ->
            val note = backStackEntry.toRoute<Route.Note>()
            NoteScreen(note.noteId, accountViewModel, navController)
        }
    }
}
```

### Navigation Manager Pattern

**Amethyst Pattern (`Nav.kt`):**
```kotlin
class Nav(
    val controller: NavHostController,
    val drawerState: DrawerState,
    val scope: CoroutineScope
) {
    fun nav(route: Route) {
        scope.launch {
            controller.navigate(route)
            drawerState.close()
        }
    }

    fun newStack(route: Route) {
        scope.launch {
            controller.navigate(route) {
                popUpTo(Route.Home) { inclusive = false }
            }
            drawerState.close()
        }
    }

    fun popBack() {
        controller.popBackStack()
    }
}

// Usage in composables
@Composable
fun HomeScreen(nav: Nav) {
    Button(onClick = { nav.nav(Route.Profile("npub1...")) }) {
        Text("View Profile")
    }
}
```

### Bottom Navigation

**Material3 Pattern:**
```kotlin
@Composable
fun AppBottomBar(
    selectedRoute: Route,
    nav: Nav
) {
    NavigationBar {
        BottomBarItem.entries.forEach { item ->
            NavigationBarItem(
                selected = selectedRoute::class == item.route::class,
                onClick = { nav.nav(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}

enum class BottomBarItem(val route: Route, val icon: ImageVector, val label: String) {
    HOME(Route.Home, Icons.Default.Home, "Home"),
    MESSAGES(Route.Messages, Icons.Default.Message, "Messages"),
    NOTIFICATIONS(Route.Notifications, Icons.Default.Notifications, "Notifications"),
    SEARCH(Route.Search, Icons.Default.Search, "Search"),
    PROFILE(Route.Profile, Icons.Default.Person, "Profile")
}
```

**Reference:** See `references/android-navigation.md` for complete navigation patterns.

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
fun MainScreen(navController: NavHostController) {
    val currentRoute by navController.currentBackStackEntryAsState()

    Scaffold(
        topBar = { AppTopBar(currentRoute) },
        bottomBar = { AppBottomBar(currentRoute, navController) },
        floatingActionButton = { NewPostFab() }
    ) { innerPadding ->
        // Scaffold automatically handles system bar insets
        NavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Routes...
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
    navController: NavHostController,
    accountViewModel: AccountViewModel
) {
    val activity = LocalContext.current as? Activity

    LaunchedEffect(activity?.intent) {
        activity?.intent?.let { intent ->
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val sharedImage = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    navController.navigate(
                        Route.NewPost(message = sharedText, attachment = sharedImage.toString())
                    )
                }

                Intent.ACTION_VIEW -> {
                    val uri = intent.data
                    when (uri?.scheme) {
                        "nostr" -> handleNostrUri(uri, navController)
                        "https", "http" -> handleWebUri(uri, navController)
                    }
                }
            }
        }
    }

    NavHost(navController = navController) {
        // Routes...
    }
}

fun handleNostrUri(uri: Uri, navController: NavHostController) {
    // nostr:npub1... -> Profile
    // nostr:note1... -> Note
    // nostr:nevent1... -> Event
    when {
        uri.path?.startsWith("npub") == true -> {
            navController.navigate(Route.Profile(uri.path!!))
        }
        uri.path?.startsWith("note") == true -> {
            navController.navigate(Route.Note(uri.path!!))
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

### Android Block

**build.gradle (Amethyst pattern):**
```gradle
android {
    namespace = 'com.vitorpamplona.amethyst'
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vitorpamplona.amethyst"
        minSdk = 26          // Android 8.0 (Oreo)
        targetSdk = 36       // Android 15
        versionCode = 430
        versionName = "1.04.2"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true   // Enable BuildConfig access
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += '/META-INF/{AL2.0,LGPL2.1}'
        }
    }

    // Product flavors for Play Store vs F-Droid
    flavorDimensions = ["channel"]
    productFlavors {
        create("play") {
            dimension = "channel"
            // Firebase, Google services
        }
        create("fdroid") {
            dimension = "channel"
            // UnifiedPush, open-source alternatives
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}
```

### Dependencies

**Key Android Dependencies:**
```gradle
dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Activity
    implementation(libs.androidx.activity.compose)

    // Accompanist
    implementation(libs.accompanist.permissions)

    // Shared module
    implementation(project(":commons"))
    implementation(project(":quartz"))
}
```

### Proguard Rules

**Common Rules for Amethyst:**
```proguard
# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Nostr event classes
-keep class com.vitorpamplona.quartz.events.** { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
```

**Reference:** See `references/proguard-rules.md` for complete Proguard configuration.

### APK Optimization

**Reference:** See `scripts/analyze-apk-size.sh` for APK size analysis.

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

```kotlin
@Composable
fun LocalizedButton() {
    val context = LocalContext.current

    Button(
        onClick = {
            val message = context.getString(R.string.button_clicked)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    ) {
        Text(stringResource(R.string.button_label))
    }
}
```

## Testing Android Components

### Navigation Testing

```kotlin
@Test
fun testNavigationToProfile() {
    val navController = TestNavHostController(ApplicationProvider.getApplicationContext())

    composeTestRule.setContent {
        navController.navigatorProvider.addNavigator(ComposeNavigator())
        AppNavigation(navController, accountViewModel)
    }

    composeTestRule.onNodeWithText("Profile").performClick()

    assertEquals(
        Route.Profile::class,
        navController.currentBackStackEntry?.destination?.route::class
    )
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
| **Navigate** | `navController.navigate(Route.Profile(id))` |
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
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/routes/Routes.kt`
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/AppNavigation.kt`
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/theme/Theme.kt`
- `amethyst/src/main/AndroidManifest.xml`
- `amethyst/build.gradle`

## Additional Resources

- `references/android-navigation.md` - Complete navigation patterns and examples
- `references/android-permissions.md` - Permission handling patterns
- `references/proguard-rules.md` - Proguard configuration
- `scripts/analyze-apk-size.sh` - APK size optimization script

## When NOT to Use

- Desktop-specific features → Use `desktop-expert` skill
- iOS-specific features → Use `ios-expert` skill
- Shared KMP code → Use `kotlin-multiplatform` skill
- Nostr protocol → Use `nostr-expert` skill
- Compose UI components → Use `compose-expert` skill
