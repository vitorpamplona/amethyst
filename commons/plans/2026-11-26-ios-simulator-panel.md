# iOS simulators in the device panel

Status: built (desktop), checked against stand-ins, not yet on a Mac. Date: 2026-11-26.

## Goal

The device panel beside a coding chat (`2026-11-25-android-device-panel.md`) shows an iOS
simulator the way it shows an Android emulator: booted headless from the panel or by the agent,
its screen live beside the chat, clicked and typed into, and the same agent tools (look, tap,
type, swipe, keys, rotate, run the app, read the log, crashes in every result) working on it.

## Pieces

| Piece | Where | Reused / new |
|---|---|---|
| simctl, idb's gRPC (screen stream, HID, accessibility tree, orientation, crash reports), Xcode builds | `iosSimulatorClient` (new JVM module, no Amethyst types) | New; reuses `androidDeviceClient`'s `GrpcChannel`, `DeviceScreen`, `UiTree`, `DeviceKey` |
| gRPC over a Unix socket, client streams, held-open (duplex) streams | `androidDeviceClient/grpc` (`UnixSocketFactory`, `GrpcChannel.clientStream`/`openStream`) | Extended |
| One device interface for the panel and the tools | `androiddevices/DeviceDriver` | New; `DeviceConnection` (Android) and `SimulatorConnection` (iOS) implement it |
| The simulators, workstream copies, companions | `androiddevices/IosSimulators`, merged into `AndroidDevices`' list | New |
| Run on a simulator | `androiddevices/IosAppRunner` (xcodebuild) | New, beside `AndroidAppRunner`, same `AppRunState` |
| Agent tools | `DeviceToolServer`, renamed `device` (was `android-device`) | Extended: `start_simulator`, `stop_device`, `install_app`, `device_log`; the rest take either platform |
| Panel | `commonsUI/…/androiddevices/ui` | Extended: simulators in the picker and the empty state, iPhone icon, iOS keys and log |
| Stand-ins for Linux | `tools/fake-ios-simulator` | New |

## Decisions

- **idb for what simctl can't do.** simctl has no input and no live screen. idb's companion (MIT)
  has both, plus the accessibility tree and crash reports, over gRPC: the hand-written channel the
  emulator already uses, extended with the two call shapes idb needs (a client stream sent whole,
  and a stream kept open). AXe is GPL-3.0 since 2.1, so it isn't used even as a subprocess.
- **A Unix socket, never TCP.** idb's TCP mode binds every interface without authentication, and
  the service installs apps and reads files; `--grpc-domain-sock` in a `rwx------` folder keeps it
  to this user. OkHttp reaches it through a `SocketFactory` over a JDK Unix `SocketChannel`.
- **Minicap frames.** The stream format is length-prefixed JPEGs, scaled on the Mac to the view:
  small, self-delimiting, and decoded by Skia like the emulator's PNG frames (`PixelFormat.JPEG`).
- **One device list.** Simulators are `DeviceInfo`s with `DevicePlatform.IOS` and
  `DeviceKind.SIMULATOR`, so selection per chat, the activity overlay, crash banners and every
  tool work unchanged. `connection()` returns a `DeviceDriver`; Android-only paths (Gradle runs,
  snapshots) use `androidConnection()`.
- **Copies are clones.** A chat's `start_simulator` makes its workstream's own `simctl clone` of
  the simulator (named `… (Amethyst copy)`), deleted when it stops; reset deletes it and clones
  again. As with the emulator's read-only copies, the source must be shut down to be copied: the
  user sets up the starting state on the simulator itself from the Devices screen. Copies an
  earlier run left behind are deleted once shut down.
- **Points and pixels.** The agent's coordinates stay the screenshot's pixels; touches go to idb in
  points (pixels / density). The accessibility tree's point frames are turned into pixels.
- **No back key.** iOS goes back with a swipe from the left edge: `press_key back` and the panel's
  Back make that swipe. Recents is two presses of Home; power is the side button.
- **Offered where there are devices.** The panel, the Devices screen and the agents' tools appear
  with an Android SDK or with Xcode (`AndroidDevices.supported`).

## Not done

- A run on a Mac against a real simulator. The stand-ins check the app's side of every call
  byte for byte, but not how a real companion version answers (which RPCs it has, the coordinate
  space of touches on a turned simulator, the accessibility tree's keys on each iOS version).
- Physical iPhones (idb can drive them; no panel path yet), Apple Watch, Apple TV, visionOS.
- Location, push notifications, permissions, appearance, the status bar: simctl and idb have them;
  no tool calls them yet.
- Renaming the `androiddevices` packages and `AndroidDevices` now that they hold both platforms.
