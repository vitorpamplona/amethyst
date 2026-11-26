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
| Running tools, gRPC (TCP or a Unix socket; unary, server, client and duplex streams), `DeviceScreen`, `DeviceKey`, `UiTree`, `DeviceCrash` | `deviceClientCore` (new JVM module, no platform) | Moved out of `androidDeviceClient`; the duplex stream and Unix socket are new |
| simctl, idb's gRPC (screen stream, HID, accessibility tree, orientation, crash reports), Xcode builds | `iosSimulatorClient` (new JVM module, no Amethyst types) | New |
| adb, AVDs, the emulator | `androidDeviceClient` | Reused; its keycodes and frame numbers became extensions (`AndroidKeys`, `PixelFormat.wire`) |
| One list of every platform's devices, each chat's pick, activity, crashes, leave for phones | `devices/Devices` | Was `androiddevices/AndroidDevices`, now platform-free |
| A platform's devices: list, start (copies), stop, reset, connection | `devices/DeviceBackend`: `android/AndroidBackend`, `ios/IosBackend` | Split out of `AndroidDevices`; iOS new |
| One device for the panel and the tools | `devices/DeviceDriver`: `android/AndroidConnection`, `ios/SimulatorConnection` | `DeviceConnection` renamed; iOS new |
| Run the chat's app | `devices/AppRunner`: `android/AndroidAppRunner`, `ios/IosAppRunner` (xcodebuild) | Interface new |
| Agent tools | `devices/DeviceToolServer`, named `device` (was `android-device`) | `start_device` (either platform), `stop_device`, `install_app`, `device_log`; the rest take either |
| Panel, live screen, Devices screen | `commonsUI/…/devices/ui`, `Route.Devices` | Renamed; one list of startable devices, flags instead of platform checks |
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
- **One device list over a backend per platform.** Simulators are `DeviceInfo`s with
  `DevicePlatform.IOS`, so selection per chat, the activity overlay, crash banners and every tool
  work on them unchanged. What differs between platforms is behind `DeviceBackend`; `Devices`
  never asks which platform a device is. A device says what it can do (`canReset`, `canColdBoot`,
  `liveScreen` on its driver), and what can start is one list (`StartableDevice`: an AVD or a
  shut-down simulator), so the panel has no platform branches but labels and icons.
- **A platform never holds up or empties another.** `Devices.refresh` asks every backend at once,
  each under a timeout; one that fails or hangs keeps its last list, with the error on its setup
  (a `simctl` that stumbles doesn't make simulators vanish, nor delay the emulators). Each backend
  has its own lock. Measured with stand-ins (adb answering in 120 ms, one emulator booting; 15
  looks each, median): with `simctl list` taking 250 ms, as on a Mac with several runtimes, a look
  went from 523 ms (one platform after the other) to 278 ms; with an instant `simctl`, from 270 ms
  to 243 ms (booting emulators are now asked at once too).
- **Copies are clones.** A chat's `start_device` on a simulator makes its workstream's own `simctl clone` of
  the simulator (named `… (Amethyst copy)`), deleted when it stops; reset deletes it and clones
  again. As with the emulator's read-only copies, the source must be shut down to be copied: the
  user sets up the starting state on the simulator itself from the Devices screen. Copies an
  earlier run left behind are deleted once shut down.
- **Points and pixels.** The agent's coordinates stay the screenshot's pixels; touches go to idb in
  points (pixels / density). The accessibility tree's point frames are turned into pixels.
- **No back key.** iOS goes back with a swipe from the left edge: `press_key back` and the panel's
  Back make that swipe. Recents is two presses of Home; power is the side button.
- **Offered where there are devices.** The panel, the Devices screen and the agents' tools appear
  with an Android SDK or with Xcode (`Devices.supported`).

## Not done

- A run on a Mac against a real simulator. The stand-ins check the app's side of every call
  byte for byte, but not how a real companion version answers (which RPCs it has, the coordinate
  space of touches on a turned simulator, the accessibility tree's keys on each iOS version).
- Physical iPhones (idb can drive them; no panel path yet), Apple Watch, Apple TV, visionOS.
- Location, push notifications, permissions, appearance, the status bar: simctl and idb have them;
  no tool calls them yet.
- Physical iPhones would be another `DeviceBackend` (idb drives devices too), with no change to the
  panel or the tools.
