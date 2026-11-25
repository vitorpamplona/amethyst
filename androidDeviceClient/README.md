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
| The emulator's screen stream, touches, keys, typing, clipboard | `emulator/EmulatorController` over `grpc/GrpcChannel` |
| One live-view interface over both (frames + pointer + keys) | `device/DeviceScreen`: `EmulatorScreen`, `AdbScreen` |
| Crashes and ANRs in the device log (`crash` buffer, `ActivityManager` ANRs) | `adb/CrashLog`, `Adb.crashes` |
| Back to the AVD's saved state (`default_boot`) | `EmulatorController.loadSnapshot` (`SnapshotService`) |

## The emulator's gRPC endpoint, as observed

Read from the proto the emulator ships, `emulator/lib/emulator_controller.proto` (emulator 37.3),
and its `-help`:

- `emulator -avd X -grpc <port> -grpc-use-token -qt-hide-window` is how this client starts one:
  plain HTTP/2 (h2c) on localhost, every call carrying `authorization: Bearer <token>`. The token
  is `grpc.token` in the discovery file, or the console's `~/.emulator_console_auth_token`.
- Without `-grpc`, the emulator defaults to `-grpc-use-jwt` (what Android Studio uses): only a
  client whose signing key sits in the `grpc.jwks` folder gets in. This client doesn't do that
  dance: such an emulator is driven through adb (slower screen, same everything else).
- Every emulator writes `pid_<pid>_info.ini` to `<dir>/avd/running`, where `<dir>` is
  `$XDG_RUNTIME_DIR` (Linux; a temp folder without it), `~/Library/Caches/TemporaryItems` (macOS) or
  `%LOCALAPPDATA%\Temp` (Windows). Keys: `port.serial` (adb's `emulator-<port>`), `port.adb`,
  `avd.name`, `avd.dir`, `grpc.port`, `grpc.token`, `grpc.jwks`, `cmdline`.
- Messages are hand-encoded (`grpc/Protobuf`): no protobuf runtime or code generation. Field
  numbers come from the proto: `ImageFormat{format=1, width=3, height=4, display=5}`,
  `Image{format=1, image=4, seq=5}`, `TouchEvent{touches=1{x=1, y=2, identifier=3, pressure=4}}`,
  `KeyboardEvent{eventType=2, key=4, text=5}`, `ClipData{text=1}`.
- Touches are in the display panel's natural (unrotated) pixels; screenshots come turned the way
  the device is (`format.rotation`). `EmulatorScreen.toNatural` maps between them.
- One AVD runs several times only when every instance is `-read-only` (`LaunchOptions.readOnly`):
  each boots from the AVD's quick-boot snapshot and drops its changes on exit. A writable instance
  beside them fails with "Another emulator instance is running…".
- `SnapshotService/LoadSnapshot` takes `SnapshotPackage{snapshot_id=1}` and answers
  `{success=3, err=4}`; `default_boot` is the quick-boot state.
- `KeyboardEvent.text` types printable ASCII, `\n` and `\t` only: other text goes in through the
  clipboard and a paste key.

Not checked against a running emulator here (the container has no KVM): the framing is tested
against a real HTTP/2 server (`GrpcChannelTest`), the rest against the proto's field numbers.

## adb, as used

- `input tap/swipe` and uiautomator's bounds are in the screen's current (rotated) pixels;
  `screencap -p` is full size in the same frame. So the adb path needs no rotation math.
- `input text` takes no spaces (sent as `%s`) and only ASCII; `Adb.type` refuses the rest.
- `uiautomator dump /dev/tty` fails on some images; `uiDump` falls back to a file.
