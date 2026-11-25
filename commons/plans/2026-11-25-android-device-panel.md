# Android device panel and agent device tools

Status: built (desktop). Date: 2026-11-25.

## Goal

Developing agentic code and testing it on an Android device should be one loop inside Amethyst
Desktop, the way Android Studio's Running Devices window sits beside its editor: the coding chat
on the left, the emulator (or a phone) live on the right, and the agent able to build, install,
look, tap and read logcat on the same device the user is watching.

## Pieces

| Piece | Where | Reused / new |
|---|---|---|
| adb, AVDs, emulator discovery + launch, gRPC screen/input, UI tree | `androidDeviceClient` (new JVM module, no Amethyst types) | New. OkHttp for HTTP/2, no grpc-java/protobuf |
| SDK folder | `claudecode/AndroidSdk.home()` | Extended (it already found the SDK for builds) |
| Device list, start/stop, per-chat pick, agent leave for phones, activity feed | `androiddevices/AndroidDevices` | New, one per process like `ClaudeCodeService.shared` |
| Agent coordinate space, scaled screenshots | `androiddevices/DeviceConnection` | New |
| Build + install + open the chat's app | `androiddevices/AndroidAppRunner` | New; apksig (already a dependency) reads the package |
| Agent tools | `androiddevices/DeviceToolServer` (`android-device`) | New `HostToolServer`, beside `PublishToolServer` in `ClaudeCodeService.open` |
| Panel state | `androiddevices/DevicePanelViewModel` | New |
| Panel, live screen, Devices screen | `commonsUI/…/androiddevices/ui` | New; `PaneSplitter`, `paneWidthPrefs` reused |
| Route | `Route.AndroidDevices(chatKey?)` | New, desktop-only destination |

## Decisions

- **gRPC by hand.** The emulator's endpoint is h2c on localhost; OkHttp speaks it with prior
  knowledge, and the messages needed are a dozen fields. That keeps grpc-java, protobuf and the
  protobuf Gradle plugin out of the build.
- **Two screen paths.** An emulator this app starts gets `-grpc <port> -grpc-use-token`, so its
  frames stream (RGBA, scaled by the emulator to the view) and touches move live. Any other device
  (a phone, an emulator Studio started with JWT auth) is polled with `screencap` and driven with
  `input`: slower, same features.
- **Agents act through adb.** Their taps don't need the stream's latency, and adb's coordinates
  are the screen's current ones, the same as uiautomator's bounds and the screenshot's pixels.
- **One coordinate space for the agent.** Screenshots are shrunk to what the model sees without
  resizing (long edge ≤ 1568, ≤ 1.15 MP); the UI tree's bounds and every x/y are in those pixels.
- **Leave.** Emulators are the agent's; a phone only after the user flips "Let agents use this
  phone" in the panel. Looking, touching, typing, logcat and starting an emulator run without a
  prompt; building (`run_app`), installing, clearing data and shutting down ask.
- **Who gets the tools.** Coding chats with an agent that takes host tools, when an Android SDK is
  installed. The brief adds one paragraph about the panel.

## Not done

- JWT auth for emulators started elsewhere (Studio's): they fall back to adb.
- Multi-display, foldables, XR. The panel shows display 0.
- Audio, camera, sensors, location: the proto has them; nothing calls them yet.
- Verifying the gRPC path on a real emulator: this container has no KVM. The framing is tested
  against a real HTTP/2 server; the panel was checked with a scripted adb.
