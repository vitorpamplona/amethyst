# Android Runtime Permissions

Complete permission handling patterns for Amethyst using Accompanist Permissions library and Android best practices.

## Permission Categories in Amethyst

### Network Permissions (Normal - Auto-granted)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
```

### Media Permissions (Dangerous - Runtime request)

```xml
<!-- Camera -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- Audio -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Storage (version-specific) -->
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

### Notification Permissions (Android 13+)

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### Location Permissions

```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

### NFC Permissions

```xml
<uses-permission android:name="android.permission.NFC" />
```

### Foreground Service Permissions

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

## Accompanist Permissions Library

### Setup

```gradle
dependencies {
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")
}
```

### Single Permission Pattern

```kotlin
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraFeature() {
    val cameraPermissionState = rememberPermissionState(
        Manifest.permission.CAMERA
    )

    when {
        // Permission granted - show feature
        cameraPermissionState.status.isGranted -> {
            CameraPreview()
        }

        // Should show rationale - explain why permission is needed
        cameraPermissionState.status.shouldShowRationale -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Camera permission is needed to scan QR codes for login",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() }
                ) {
                    Text("Grant Permission")
                }
            }
        }

        // First time - request permission
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() }
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Camera")
                }
            }
        }
    }
}
```

### Multiple Permissions Pattern

```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaUploadFeature() {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    )

    when {
        // All permissions granted
        permissionsState.allPermissionsGranted -> {
            MediaUploadUI()
        }

        // Some permissions need rationale
        permissionsState.shouldShowRationale -> {
            RationaleDialog(
                title = "Permissions Required",
                message = "Camera and storage access are needed to upload photos",
                onConfirm = {
                    permissionsState.launchMultiplePermissionRequest()
                },
                onDismiss = { /* Handle dismissal */ }
            )
        }

        // Request all permissions
        else -> {
            PermissionRequestScreen(
                permissions = permissionsState.permissions,
                onRequestPermissions = {
                    permissionsState.launchMultiplePermissionRequest()
                }
            )
        }
    }
}

@Composable
fun RationaleDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

## Lifecycle-Aware Permission Requests

### Amethyst Pattern: POST_NOTIFICATIONS

**File:** `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/LoggedInPage.kt`

```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationRegistration(accountViewModel: AccountViewModel) {
    val context = LocalContext.current

    // Only request on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionState = rememberPermissionState(
            Manifest.permission.POST_NOTIFICATIONS
        )

        // Register for push notifications when permission is granted
        if (notificationPermissionState.status.isGranted) {
            LifecycleResumeEffect(
                key1 = accountViewModel,
                key2 = notificationPermissionState.status.isGranted
            ) {
                val scope = rememberCoroutineScope()
                scope.launch(Dispatchers.IO) {
                    PushNotificationUtils.checkAndInit(
                        context = context,
                        accountViewModel = accountViewModel
                    )
                }

                onPauseOrDispose {
                    // Cleanup when composable pauses or disposes
                }
            }
        } else {
            // Show prompt to enable notifications
            NotificationPermissionPrompt(
                onEnableClick = {
                    notificationPermissionState.launchPermissionRequest()
                }
            )
        }
    }
}

@Composable
fun NotificationPermissionPrompt(onEnableClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enable Notifications",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Get notified when someone mentions you or replies to your posts",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onEnableClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Notifications")
            }
        }
    }
}
```

## Permission Best Practices

### 1. Request Contextually

**Bad:**
```kotlin
// Requesting permission on app launch
@Composable
fun AppContent() {
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        // DON'T DO THIS - user doesn't know why
        permissionState.launchPermissionRequest()
    }
}
```

**Good:**
```kotlin
// Request when user explicitly wants to use camera
@Composable
fun QRScannerButton() {
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Button(
        onClick = {
            if (permissionState.status.isGranted) {
                // Open scanner
            } else {
                // Request permission
                permissionState.launchPermissionRequest()
            }
        }
    ) {
        Text("Scan QR Code")
    }
}
```

### 2. Show Rationale

```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationFeature() {
    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // Always show rationale first for sensitive permissions
    if (!locationPermissionState.status.isGranted) {
        LocationRationaleCard(
            onEnableClick = {
                locationPermissionState.launchPermissionRequest()
            }
        )
    } else {
        LocationMap()
    }
}

@Composable
fun LocationRationaleCard(onEnableClick: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Why location access?",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Location is used for geohashing your posts. " +
                      "This helps other users discover local content. " +
                      "Your exact location is never shared.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                OutlinedButton(onClick = { /* Skip */ }) {
                    Text("Skip")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onEnableClick) {
                    Text("Enable")
                }
            }
        }
    }
}
```

### 3. Handle Permanent Denial

```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraFeatureWithSettings() {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(
        Manifest.permission.CAMERA
    )

    when {
        cameraPermissionState.status.isGranted -> {
            CameraPreview()
        }

        cameraPermissionState.status.shouldShowRationale -> {
            // User denied once, show rationale
            RationaleDialog(
                onConfirm = { cameraPermissionState.launchPermissionRequest() }
            )
        }

        else -> {
            // Might be permanently denied - offer settings
            PermanentlyDeniedDialog(
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun PermanentlyDeniedDialog(onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Permission Denied") },
        text = {
            Text(
                "Camera permission is required for QR scanning. " +
                "Please enable it in Settings."
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = { /* Cancel */ }) {
                Text("Cancel")
            }
        }
    )
}
```

### 4. Version-Specific Permissions

```kotlin
@Composable
fun StoragePermissionRequest() {
    val permissions = remember {
        buildList {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                    add(Manifest.permission.READ_MEDIA_VIDEO)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    // Android 10-12: No permission needed for scoped storage
                }
                else -> {
                    // Android 9 and below
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    if (permissions.isNotEmpty()) {
        val permissionsState = rememberMultiplePermissionsState(permissions)

        if (!permissionsState.allPermissionsGranted) {
            StoragePermissionUI(
                onRequest = { permissionsState.launchMultiplePermissionRequest() }
            )
        } else {
            MediaPickerUI()
        }
    } else {
        // No permission needed
        MediaPickerUI()
    }
}
```

## Permission Groups

### Camera + Storage (Media Upload)

```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaCaptureFeature() {
    val mediaPermissions = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permissionsState = rememberMultiplePermissionsState(mediaPermissions)

    when {
        permissionsState.allPermissionsGranted -> {
            MediaCaptureUI()
        }
        else -> {
            MediaPermissionScreen(
                permissions = permissionsState.permissions,
                onRequest = { permissionsState.launchMultiplePermissionRequest() }
            )
        }
    }
}
```

### Audio + Storage (Voice Recording)

```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceRecordingFeature() {
    val audioPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permissionsState = rememberMultiplePermissionsState(audioPermissions)

    if (permissionsState.allPermissionsGranted) {
        AudioRecorderUI()
    } else {
        AudioPermissionScreen(
            onRequest = { permissionsState.launchMultiplePermissionRequest() }
        )
    }
}
```

## Testing Permissions

### Grant Permission in Tests

```kotlin
@get:Rule
val permissionRule = GrantPermissionRule.grant(
    Manifest.permission.CAMERA,
    Manifest.permission.READ_EXTERNAL_STORAGE
)

@Test
fun testCameraFeatureWithPermission() {
    composeTestRule.setContent {
        CameraFeature()
    }

    // Permission is already granted by rule
    composeTestRule.onNodeWithText("Take Photo").assertExists()
}
```

### Test Permission Request Flow

```kotlin
@Test
fun testPermissionRequestFlow() {
    composeTestRule.setContent {
        CameraFeature()
    }

    // Initially shows permission request button
    composeTestRule.onNodeWithText("Enable Camera").assertExists()

    // Click to request
    composeTestRule.onNodeWithText("Enable Camera").performClick()

    // System permission dialog appears (can't test dialog itself)
    // Would need UiAutomator to interact with system dialog
}
```

## Permission State Checking

### Check Permission Before Action

```kotlin
fun checkAndRequestCameraPermission(
    context: Context,
    permissionState: PermissionState,
    onGranted: () -> Unit
) {
    when {
        permissionState.status.isGranted -> {
            onGranted()
        }
        permissionState.status.shouldShowRationale -> {
            // Show rationale dialog
        }
        else -> {
            permissionState.launchPermissionRequest()
        }
    }
}
```

### Manual Permission Check (Non-Compose)

```kotlin
fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

fun requestCameraPermission(activity: ComponentActivity) {
    ActivityCompat.requestPermissions(
        activity,
        arrayOf(Manifest.permission.CAMERA),
        REQUEST_CAMERA_PERMISSION
    )
}

// In Activity
override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    when (requestCode) {
        REQUEST_CAMERA_PERMISSION -> {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                // Permission denied
            }
        }
    }
}

companion object {
    private const val REQUEST_CAMERA_PERMISSION = 100
}
```

## File Locations

- `amethyst/src/main/AndroidManifest.xml` - Permission declarations
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/LoggedInPage.kt` - Notification permission pattern
- `amethyst/build.gradle` - Accompanist dependency

## Resources

- [Accompanist Permissions Documentation](https://google.github.io/accompanist/permissions/)
- [Android Permissions Guide](https://developer.android.com/guide/topics/permissions/overview)
- [Request Runtime Permissions](https://developer.android.com/training/permissions/requesting)
