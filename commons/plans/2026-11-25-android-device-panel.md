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
- **Two screen paths.** An emulator this app starts gets `-grpc <port> -grpc-use-token`; one
  Android Studio started answers a signed JWT (`EmulatorJwtAuth`). Either way its frames stream
  (RGBA, scaled by the emulator to the view) and touches move live. A phone, or an emulator whose
  port refuses, is polled with `screencap` and driven with `input`: slower, same features.
- **Agents' gestures over gRPC where there is one.** `adb shell input` starts a JVM on the device
  for each touch; the emulator's `sendTouch` takes milliseconds. adb stays the fallback, and the
  coordinates the agent sees are the same either way.
- **A dock panel, docked beside the chat.** The panel is the dock's reserved Devices panel
  (`DevicesDockPanel`) and follows the coding chat in focus (`DeviceChatFocus`). Coding screens
  take the whole width (`FullWidthRoutes`), where other panels open over the screen; this one is
  marked `besideFullWidth`, so it stays docked there, since it is what the chat works on.
- **Rotation by the emulator's sensor.** A rotation locked through adb turns Android but not the
  emulator's frames, so the stream would show the app sideways; turning the emulated device
  (`setPhysicalModel`) turns both, as the emulator's own rotate button does.
- **One coordinate space for the agent.** Screenshots are shrunk to what the model sees without
  resizing (long edge ≤ 1568, ≤ 1.15 MP); the UI tree's bounds and every x/y are in those pixels.
- **Leave.** Emulators are the agent's; a phone only after the user flips "Let agents use this
  phone" in the panel. Looking, touching, typing, logcat and starting an emulator run without a
  prompt; building (`run_app`), installing, clearing data and shutting down ask.
- **Who gets the tools.** Coding chats with an agent that takes host tools, when an Android SDK is
  installed. The brief adds one paragraph about the panel.

## Crashes reach the agent without asking

Every device tool (but `select_device`, `stop_emulator`, `logcat`) reads the device's crash log
before and after it acts (`Adb.crashes`: the `crash` buffer, plus `ActivityManager` errors in the
system buffer for ANRs, parsed by `CrashLog`) and puts any crash or ANR the chat hasn't heard of at
the top of its result: "AFTER THIS ACTION, AN APP CRASHED: …" with the exception and the top of
the trace. A crash the last action caused after its result went back leads the next result. The
app a chat opened (`launch_app`, `run_app`) is also watched: gone with nothing in the crash log, the
next result says so once. `AndroidDevices.crashes` keeps one list; each reader (each chat's tools,
the panel) keeps its own cursor, so both hear of every crash. The first read of a device only marks
what is there: crashes from before anyone watched aren't news. The panel shows a crash banner (Copy,
Dismiss) for crashes the user causes by hand too: it polls every 3 s while open.

## Each workstream gets its own emulator

The emulator runs several instances of one AVD only when every one is `-read-only` (its own message:
"Another emulator instance is running. Please close it or run all emulators with -read-only
flag."). So:

- **A chat starts copies.** `start_emulator` and the panel's "Start a copy" pass an owner: the
  chat's workstream id (`ClaudeCodeService.deviceOwner`), or the chat's key outside a workstream.
  The copy is read-only: it boots from the AVD's saved quick-boot state and drops its changes when
  it stops. The same owner starting again gets the copy it has; another owner gets its own. A
  workstream's chats share its copy and its device pick.
- **The Devices screen starts the AVD itself**, writable: that is where the user sets up the
  starting state (log in, install, seed data), and its quick-boot save on shutdown becomes every
  copy's start. It can't run while copies do, and copies can't start while it runs: the error says
  which to stop.
- **Reset.** `reset_device` and the panel's "Reset to its starting state" load `default_boot` over
  the emulator's `SnapshotService` (gRPC): the copy goes back to its start without a restart.
- **Cleanup.** Deleting a workstream, or freeing its clones when it idles, stops its copies
  (`AndroidDevices.releaseOwner`). They also stop when the app quits.
- Another workstream's copy is never a chat's device by default; the picker labels copies ("This
  workstream's copy", "Copy for <title>") and `list_devices` tells the agent the same.

## Not done

- Multi-display, foldables, XR. The panel shows display 0.
- Audio, camera, sensors, location: the proto has them; nothing calls them yet.
- Resetting an emulator Android Studio started: the JWT issuer the emulator gives tools may not load
  snapshots, so the panel offers reset only on emulators started here.
- A run on a GPU machine. The real-emulator checks (`androidDeviceClient/README.md`) ran in a
  container that renders in software, where each full-window redraw takes ~150 ms; the gRPC calls
  themselves measured in milliseconds.
