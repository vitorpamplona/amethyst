# Android Navigation Patterns

Complete navigation implementation patterns for Amethyst Android app using Navigation Compose with type safety.

## Type-Safe Routes (Navigation 2.8.0+)

### Route Definitions

```kotlin
// Routes.kt - All 40+ routes in Amethyst
@Serializable
sealed class Route {
    // Bottom nav routes
    @Serializable object Home : Route()
    @Serializable object Messages : Route()
    @Serializable object Video : Route()
    @Serializable object Discover : Route()
    @Serializable object Notification : Route()

    // Content routes with parameters
    @Serializable data class Profile(val pubkey: String) : Route()
    @Serializable data class Note(val id: String) : Route()
    @Serializable data class Channel(val id: String) : Route()
    @Serializable data class Thread(
        val id: String,
        val replyTo: String? = null
    ) : Route()

    // New content routes
    @Serializable data class NewPost(
        val message: String? = null,
        val attachment: String? = null,
        val replyTo: String? = null
    ) : Route()

    // Settings
    @Serializable object Settings : Route()
    @Serializable object Security : Route()
    @Serializable object Relays : Route()

    // Search
    @Serializable data class Search(val query: String = "") : Route()

    // Media
    @Serializable data class Image(val url: String) : Route()
    @Serializable data class Video(val url: String) : Route()
}
```

## NavHost Configuration

### Basic Setup

```kotlin
@Composable
fun AppNavigation(
    navController: NavHostController,
    accountViewModel: AccountViewModel,
    drawerState: DrawerState
) {
    val scope = rememberCoroutineScope()
    val nav = remember {
        Nav(navController, drawerState, scope)
    }

    NavHost(
        navController = navController,
        startDestination = Route.Home,
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        // Define routes
        composable<Route.Home> {
            HomeScreen(accountViewModel, nav)
        }

        composable<Route.Profile> { backStackEntry ->
            val profile = backStackEntry.toRoute<Route.Profile>()
            ProfileScreen(
                pubkey = profile.pubkey,
                accountViewModel = accountViewModel,
                nav = nav
            )
        }

        composable<Route.Note> { backStackEntry ->
            val note = backStackEntry.toRoute<Route.Note>()
            NoteScreen(
                noteId = note.id,
                accountViewModel = accountViewModel,
                nav = nav
            )
        }

        composable<Route.NewPost> { backStackEntry ->
            val newPost = backStackEntry.toRoute<Route.NewPost>()
            NewPostScreen(
                initialMessage = newPost.message,
                initialAttachment = newPost.attachment,
                replyTo = newPost.replyTo,
                accountViewModel = accountViewModel,
                onPost = { nav.popBack() }
            )
        }
    }
}
```

### Custom Transitions

```kotlin
composable<Route.Profile>(
    enterTransition = {
        slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(300)
        )
    },
    exitTransition = {
        slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(300)
        )
    },
    popEnterTransition = {
        slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = tween(300)
        )
    },
    popExitTransition = {
        slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = tween(300)
        )
    }
) { backStackEntry ->
    val profile = backStackEntry.toRoute<Route.Profile>()
    ProfileScreen(profile.pubkey, accountViewModel, nav)
}
```

## Navigation Manager

### Nav Wrapper Class

```kotlin
class Nav(
    val controller: NavHostController,
    val drawerState: DrawerState,
    val scope: CoroutineScope
) {
    /**
     * Navigate to a route, closing drawer if open
     */
    fun nav(route: Route) {
        scope.launch {
            if (!controller.popBackStack(route, inclusive = false)) {
                controller.navigate(route) {
                    launchSingleTop = true
                }
            }
            drawerState.close()
        }
    }

    /**
     * Navigate with new stack (clear back stack to Home)
     */
    fun newStack(route: Route) {
        scope.launch {
            controller.navigate(route) {
                popUpTo(Route.Home) {
                    inclusive = false
                }
                launchSingleTop = true
            }
            drawerState.close()
        }
    }

    /**
     * Pop back stack
     */
    fun popBack() {
        controller.popBackStack()
    }

    /**
     * Pop up to specific route
     */
    inline fun <reified T : Route> popUpTo(inclusive: Boolean = false) {
        controller.popBackStack<T>(inclusive = inclusive)
    }

    /**
     * Get current route
     */
    fun currentRoute(): Route? {
        return controller.currentBackStackEntry?.toRoute<Route>()
    }
}
```

## Bottom Navigation

### Material3 NavigationBar

```kotlin
@Composable
fun AppBottomBar(
    currentRoute: Route?,
    nav: Nav
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        BottomBarRoute.entries.forEach { item ->
            NavigationBarItem(
                selected = currentRoute?.let { it::class == item.route::class } ?: false,
                onClick = { nav.nav(item.route) },
                icon = {
                    Icon(
                        imageVector = if (currentRoute?.let { it::class == item.route::class } == true) {
                            item.selectedIcon
                        } else {
                            item.unselectedIcon
                        },
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                alwaysShowLabel = false
            )
        }
    }
}

enum class BottomBarRoute(
    val route: Route,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
) {
    HOME(
        route = Route.Home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        label = "Home"
    ),
    MESSAGES(
        route = Route.Messages,
        selectedIcon = Icons.Filled.Message,
        unselectedIcon = Icons.Outlined.Message,
        label = "Messages"
    ),
    VIDEOS(
        route = Route.Video,
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
        label = "Videos"
    ),
    DISCOVER(
        route = Route.Discover,
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        label = "Discover"
    ),
    NOTIFICATIONS(
        route = Route.Notification,
        selectedIcon = Icons.Filled.Notifications,
        unselectedIcon = Icons.Outlined.Notifications,
        label = "Notifications"
    )
}
```

### Observing Current Route

```kotlin
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.toRoute<Route>()

    Scaffold(
        topBar = {
            if (shouldShowTopBar(currentRoute)) {
                AppTopBar(currentRoute)
            }
        },
        bottomBar = {
            if (shouldShowBottomBar(currentRoute)) {
                AppBottomBar(currentRoute, nav)
            }
        }
    ) { paddingValues ->
        AppNavigation(
            navController = navController,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

fun shouldShowBottomBar(route: Route?): Boolean {
    return when (route) {
        is Route.Home,
        is Route.Messages,
        is Route.Video,
        is Route.Discover,
        is Route.Notification -> true
        else -> false
    }
}
```

## Navigation Drawer

### Material3 ModalDrawerSheet

```kotlin
@Composable
fun AppDrawer(
    drawerState: DrawerState,
    nav: Nav,
    accountViewModel: AccountViewModel
) {
    val scope = rememberCoroutineScope()

    ModalDrawerSheet {
        // User profile header
        DrawerHeader(accountViewModel.account)

        HorizontalDivider()

        // Menu items
        NavigationDrawerItem(
            label = { Text("Home") },
            selected = false,
            onClick = { nav.nav(Route.Home) },
            icon = { Icon(Icons.Default.Home, "Home") }
        )

        NavigationDrawerItem(
            label = { Text("Profile") },
            selected = false,
            onClick = { nav.nav(Route.Profile(accountViewModel.account.pubkey)) },
            icon = { Icon(Icons.Default.Person, "Profile") }
        )

        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = false,
            onClick = { nav.nav(Route.Settings) },
            icon = { Icon(Icons.Default.Settings, "Settings") }
        )

        HorizontalDivider()

        NavigationDrawerItem(
            label = { Text("Logout") },
            selected = false,
            onClick = {
                scope.launch {
                    accountViewModel.logout()
                    drawerState.close()
                }
            },
            icon = { Icon(Icons.Default.Logout, "Logout") }
        )
    }
}
```

### Main Scaffold with Drawer

```kotlin
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val nav = remember { Nav(navController, drawerState, scope) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(drawerState, nav, accountViewModel)
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Amethyst") },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } }
                        ) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    }
                )
            },
            bottomBar = { AppBottomBar(currentRoute, nav) }
        ) { paddingValues ->
            AppNavigation(
                navController = navController,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
```

## Deep Link Handling

### Intent Processing

```kotlin
@Composable
fun AppNavigation(
    navController: NavHostController,
    accountViewModel: AccountViewModel
) {
    val activity = LocalContext.current as? Activity

    // Handle incoming intents
    LaunchedEffect(activity?.intent) {
        activity?.intent?.let { intent ->
            handleIntent(intent, navController)
        }
    }

    NavHost(navController = navController) {
        // Routes...
    }
}

fun handleIntent(intent: Intent, navController: NavHostController) {
    when (intent.action) {
        Intent.ACTION_SEND -> {
            // Share text/image
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val sharedUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

            navController.navigate(
                Route.NewPost(
                    message = sharedText,
                    attachment = sharedUri?.toString()
                )
            )
        }

        Intent.ACTION_VIEW -> {
            // Deep link
            intent.data?.let { uri ->
                when (uri.scheme) {
                    "nostr" -> handleNostrUri(uri, navController)
                    "https", "http" -> handleWebUri(uri, navController)
                }
            }
        }
    }
}

fun handleNostrUri(uri: Uri, navController: NavHostController) {
    val path = uri.pathSegments.firstOrNull() ?: return

    when {
        path.startsWith("npub") -> {
            navController.navigate(Route.Profile(path))
        }
        path.startsWith("note") -> {
            navController.navigate(Route.Note(path))
        }
        path.startsWith("nevent") -> {
            // Decode and navigate to event
            val eventId = decodeNevent(path)
            navController.navigate(Route.Note(eventId))
        }
    }
}

fun handleWebUri(uri: Uri, navController: NavHostController) {
    // Handle web-based deep links
    // https://njump.me/npub1...
    // https://primal.net/profile/npub1...
    when (uri.host) {
        "njump.me" -> {
            val id = uri.pathSegments.lastOrNull()
            if (id?.startsWith("npub") == true) {
                navController.navigate(Route.Profile(id))
            }
        }
        "primal.net" -> {
            // Parse primal.net URLs
        }
    }
}
```

### AndroidManifest Intent Filters

```xml
<!-- MainActivity -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="nostr" />
</intent-filter>

<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="njump.me" />
    <data android:scheme="https" android:host="primal.net" />
    <data android:scheme="https" android:host="iris.to" />
</intent-filter>

<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
    <data android:mimeType="image/*" />
</intent-filter>
```

## Nested Navigation

### Tab Navigation Inside Screen

```kotlin
@Composable
fun ProfileScreen(
    pubkey: String,
    nav: Nav
) {
    val nestedNavController = rememberNavController()

    Column {
        ProfileHeader(pubkey)

        // Tab row
        TabRow(selectedTabIndex = currentTab) {
            Tab(selected = currentTab == 0, onClick = { /* Notes */ })
            Tab(selected = currentTab == 1, onClick = { /* Replies */ })
            Tab(selected = currentTab == 2, onClick = { /* Likes */ })
        }

        // Nested NavHost for tabs
        NavHost(
            navController = nestedNavController,
            startDestination = ProfileTab.Notes
        ) {
            composable<ProfileTab.Notes> {
                NotesTabContent(pubkey)
            }
            composable<ProfileTab.Replies> {
                RepliesTabContent(pubkey)
            }
            composable<ProfileTab.Likes> {
                LikesTabContent(pubkey)
            }
        }
    }
}

@Serializable
sealed class ProfileTab {
    @Serializable object Notes : ProfileTab()
    @Serializable object Replies : ProfileTab()
    @Serializable object Likes : ProfileTab()
}
```

## Testing Navigation

### Navigation Test Example

```kotlin
@Test
fun testNavigationToProfile() {
    val navController = TestNavHostController(
        ApplicationProvider.getApplicationContext()
    )

    composeTestRule.setContent {
        navController.navigatorProvider.addNavigator(
            ComposeNavigator()
        )
        AppNavigation(navController, accountViewModel)
    }

    // Navigate to profile
    composeTestRule.onNodeWithText("Profile").performClick()

    // Verify navigation
    val currentRoute = navController.currentBackStackEntry?.toRoute<Route>()
    assertTrue(currentRoute is Route.Profile)
}
```

## File Locations

- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/routes/Routes.kt`
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/AppNavigation.kt`
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/Nav.kt`
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/bottombars/AppBottomBar.kt`
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/drawer/DrawerContent.kt`
