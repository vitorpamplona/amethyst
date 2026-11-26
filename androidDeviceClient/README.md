# androidDeviceClient

A Kotlin/JVM client for Android devices and the Android emulator, the way Android Studio drives
them. It starts the SDK tools the user installed (`adb`, `emulator`) and bundles nothing from them.
No Amethyst or Nostr types: `amy` or another tool can use it, and it can move to its own repository.

The desktop app uses it for the device panel beside a coding chat and for the agents' device tools
(`commons/…/androiddevices/`, see `commons/plans/2026-11-25-android-device-panel.md`).

## What it does

| Need | Where |
|---|---|
| Find `adb` and `emulator` in an SDK folder, or on the PATH | `AndroidSdkTools` |
| List devices, shell, install, launch, input, screenshots, logcat, rotation, `emu kill` | `adb/Adb` |
| The window hierarchy as numbered nodes, as text for an agent, find a node by text | `ui/UiTree` (uiautomator's XML) |
| The AVDs that can be started, read from their `.ini` files | `avd/Avds` |
| Every running emulator, whoever started it (Studio, a terminal, this app) | `emulator/EmulatorDiscovery` |
| Start an AVD hidden, with its own token-guarded gRPC port | `emulator/EmulatorLauncher` |
| The emulator's screen stream, touches, keys, typing, clipboard, rotation | `emulator/EmulatorController` over `grpc/GrpcChannel` |
| A signed token for an emulator Android Studio started (`-grpc-use-jwt`) | `emulator/EmulatorJwtAuth` |
| One live-view interface over both (frames + pointer + keys) | `device/DeviceScreen`: `EmulatorScreen`, `AdbScreen` |
| Crashes and ANRs in the device log (`crash` buffer, `ActivityManager` ANRs) | `adb/CrashLog`, `Adb.crashes` |
| Back to the AVD's saved state (`default_boot`) | `EmulatorController.loadSnapshot` (`SnapshotService`) |
| gRPC over a Unix socket; a client stream sent whole; a stream held open while answers come (idb's companion, for `iosSimulatorClient`) | `grpc/UnixSocketFactory`, `GrpcChannel.clientStream`, `GrpcChannel.openStream` |

## The emulator's gRPC endpoint, as observed

Read from the proto the emulator ships, `emulator/lib/emulator_controller.proto` (emulator 37.3),
and its `-help`:

- `emulator -avd X -grpc <port> -grpc-use-token -qt-hide-window` is how this client starts one:
  plain HTTP/2 (h2c) on localhost, every call carrying `authorization: Bearer <token>`. The token
  is `grpc.token` in the discovery file, or the console's `~/.emulator_console_auth_token`.
- Android Studio starts emulators with `-grpc-use-jwt`: the discovery file names `grpc.jwks`, a
  folder the emulator watches, and `grpc.jwk_active`, where it lists the keys it took.
  `EmulatorJwtAuth` writes an ES256 public key there as a JWK set and signs each call's token as
  `gradle-utp-emulator-control`, the issuer the emulator's allow list
  (`emulator/lib/emulator_access.json`) gives test tooling, naming the methods it calls in `aud`.
  Write the key file in place (a file renamed into the folder is never read) and leave `typ` out of
  the token's header (refused). That issuer may not call `SnapshotService`: PERMISSION_DENIED,
  "not on the allowlist", so only an emulator started here can be reset.
- Every emulator writes `pid_<pid>.ini` to `<dir>/avd/running`, where `<dir>` is
  `$XDG_RUNTIME_DIR`, else `~/.android` (Linux; emulator 37), `~/Library/Caches/TemporaryItems`
  (macOS) or `%LOCALAPPDATA%\Temp` (Windows). Keys: `port.serial` (adb's `emulator-<port>`),
  `port.adb`, `avd.id` (the AVD's id, what `-avd` takes), `avd.name` (its display name, "Test API
  21"), `avd.dir`, `grpc.port`, `grpc.token`, `grpc.jwks`, `grpc.jwk_active`, `cmdline`. The file
  can outlive its process: only a live pid counts.
- Messages are hand-encoded (`grpc/Protobuf`): no protobuf runtime or code generation. Field
  numbers come from the proto: `ImageFormat{format=1, width=3, height=4, display=5}`,
  `Image{format=1, image=4, seq=5}`, `TouchEvent{touches=1{x=1, y=2, identifier=3, pressure=4}}`,
  `KeyboardEvent{eventType=2, key=4, text=5}`, `ClipData{text=1}`.
- Touches are in the display panel's natural (unrotated) pixels; screenshots come turned the way
  the device is (`format.rotation`). `EmulatorScreen.toNatural` maps between them.
- `format.rotation` is the emulated device's posture, which only its rotation sensor changes
  (`setPhysicalModel`, ROTATION, z = 90 for landscape, -180, -90). A rotation locked through adb
  (`user_rotation`) turns Android's UI but not the frames, so a landscape app shows sideways:
  `EmulatorController.setRotation` turns the sensor instead (auto-rotate on).
- One AVD runs several times only when every instance is `-read-only` (`LaunchOptions.readOnly`):
  each boots from the AVD's quick-boot snapshot and drops its changes on exit. A writable instance
  beside them fails with "Another emulator instance is running…".
- `SnapshotService/LoadSnapshot` takes `SnapshotPackage{snapshot_id=1}` and answers
  `{success=3, err=4}`; `default_boot` is the quick-boot state.
- `KeyboardEvent.text` types printable ASCII, `\n` and `\t` only: other text goes in through the
  clipboard and a paste key.

## Checked against a real emulator

Emulator 37.3.10 on Linux, an API 21 x86 image, software CPU (no KVM: `-accel off` with
`ANDROID_I_WANT_MY_TCG=yes`), through the desktop app's device panel and two opt-in tests:
`EMULATOR_IT=1 ./gradlew :androidDeviceClient:test --tests '*RealEmulatorTest*'` (calls and their
timings) and `EMULATOR_IT=1 ./gradlew :commons:jvmTest --tests '*RealDeviceToolsTest*'` (every agent
tool; with `EMULATOR_IT_COPIES=<avd id>`, two read-only copies and a reset).

- Token and JWT auth; discovery under `~/.android`, matched to AVDs by `avd.id`.
- Calls: `getStatus` ~6 ms, a 480x800 RGBA `getScreenshot` ~30 ms, a touch ~6 ms; the guest sees a
  drag's moves at the pace they were sent, with the screen streaming meanwhile.
- RGBA rows run top-down. Touches land where clicked in portrait and landscape.
- Two `-read-only` copies of one AVD boot side by side from its snapshot; `LoadSnapshot` puts one
  back, after which adb sees it offline for a moment (`AndroidDevices.reset` waits).
- `-qt-hide-window` with no X display aborts the emulator (code 134): the launcher passes
  `-no-window` there.
- API 21 has no `pidof` (`ps` lists every process there) and `booted` stays false in `getStatus`
  (the proto says it is reliable from Android 9); boot is read from `sys.boot_completed`.

## adb, as used

- `input tap/swipe` and uiautomator's bounds are in the screen's current (rotated) pixels;
  `screencap -p` is full size in the same frame. So the adb path needs no rotation math.
- `input text` takes no spaces (sent as `%s`) and only ASCII; `Adb.type` refuses the rest.
- `uiautomator dump /dev/tty` fails on some images; `uiDump` falls back to a file.
