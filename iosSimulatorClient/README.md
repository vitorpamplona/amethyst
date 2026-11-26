# iosSimulatorClient

A Kotlin/JVM client for the iOS Simulator, the way Xcode and idb drive it. It starts the tools the
user installed (`xcrun simctl`, idb's `idb_companion`) and bundles nothing from them. No Amethyst or
Nostr types: `amy` or another tool can use it, and it can move to its own repository.

It shares `androidDeviceClient`'s device vocabulary (`DeviceScreen`, `ScreenFrame`, `DeviceKey`,
`UiTree`, the hand-written gRPC channel), so the desktop app's device panel and the agents' device
tools drive a simulator the way they drive an emulator (`commons/…/androiddevices/`, see
`commons/plans/2026-11-26-ios-simulator-panel.md`).

## What it does

| Need | Where |
|---|---|
| Find `xcrun` and `idb_companion` (Homebrew's folders too: the app has no shell PATH) | `XcodeTools` |
| List simulators and runtimes; boot headless, shut down, clone, delete, erase | `simctl/Simctl` |
| Install, uninstall, launch, terminate, open a URL, the pid of an app, the unified log | `simctl/Simctl` |
| Start a companion for one simulator, on a private Unix socket | `idb/IdbCompanion` |
| Describe the screen, screenshots, the minicap screen stream, HID touches/buttons/keys/text, orientation, crash reports, the accessibility tree, the installed apps | `idb/IdbClient` over `GrpcChannel` |
| The accessibility tree as numbered nodes in pixels, as text for an agent | `ax/AxTree` (→ `UiTree`) |
| A crash report (`.ips`) as a `DeviceCrash`: exception, what the app said, the crashed thread | `crash/IosCrashReport` |
| One live-view `DeviceScreen` over idb: JPEG frames, pointer, keys, text | `device/SimulatorScreen`, `device/HidKeys` |
| Find an Xcode project or workspace (and a KMP build's `iosApp/`), build a scheme for one simulator | `xcode/XcodeAppTargets`, `xcode/XcodeBuilder` |

## Why both simctl and idb

`simctl` is Apple's and public: it is the right tool for everything it does (a simulator's life,
apps, URLs, logs). It has no input at all, and no live screen: no taps, swipes, keys or text, and
`simctl io … recordVideo` writes a file. Every tool that adds those goes through the Simulator's
private frameworks (`SimulatorKit`'s HID, the framebuffer's `IOSurface`). idb is Meta's, MIT
licensed, and maintained; its companion exposes all of it as one gRPC service. AXe, the other
common choice, is GPL-3.0 since 2.1 (earlier releases were MIT), so it is not used.

Install idb with Homebrew:

```bash
brew tap meta/fb
brew trust meta/fb
brew install meta/fb/idb
```

## idb's gRPC service, as this client uses it

From `proto/idb.proto` in facebook/idb (service `idb.CompanionService`):

- `idb_companion --udid <udid> --grpc-domain-sock <path>` serves one simulator on a Unix socket and
  prints `{"grpc_path": "<path>"}` on stdout when it listens (a TCP companion prints
  `{"grpc_port": N}`, and binds every interface with no authentication, which is why this client
  never asks for TCP). The socket's folder is made `rwx------` under `/tmp`: macOS limits a socket
  path to 104 bytes, and the user's temporary folder takes half of that. `GrpcChannel` reaches the
  socket through `UnixSocketFactory`.
- `describe` → `TargetDescriptionResponse{target_description=1{udid=1, name=2,
  screen_dimensions=3{width=1, height=2, density=3 (double), width_points=4, height_points=5},
  state=4, os_version=6}, companion=2{hid_text=13}}`.
- `video_stream` is bidirectional: the client sends `Start{fps=2, format=3, compression_quality=4,
  scale_factor=5}` and keeps its side open; the companion streams until it reads `Stop` or the
  client's side ends, so `GrpcChannel.openStream` holds a duplex request body. Format `MINICAP`
  (3): a 24-byte header (its second byte is its length), then each frame as a little-endian
  32-bit length and a JPEG. The companion's messages don't line up with frames (`MinicapReader`).
  `RBGA` (raw) is unframed, padded to the pixel buffer's row stride, and sent at full size when a
  frame fails to scale, so it is not used.
- `hid` is client streaming, and the companion applies each event as it reads it, delays
  included: an agent's swipe is one call (`Touch` down at each point, `Delay` between, `Touch` up).
  A finger "down" at a new point is a move: the simulator's digitizer has nothing else. Touches are
  in points, in the screen's orientation as it is shown (the companion unrotates them).
- `HIDText` (field 9) types any text as a hardware keyboard would, when `describe` says `hid_text`;
  older companions get US key presses (`HidKeys`), ASCII only.
- `accessibility_info{format=NESTED(1)}` answers JSON: an array of elements with `type`,
  `AXLabel`, `AXValue`, `AXUniqueId`, `frame` {x, y, width, height} in points (older companions
  only `AXFrame` as `"{{x, y}, {w, h}}"`), `enabled`, `focused`, `hidden`, `traits`, `children`.
- `get_orientation` / `set_orientation` (a companion without `set_orientation` takes the HID
  orientation event, field 5). `crash_list{since=1}` → `CrashLogInfo{name=1, bundle_id=2,
  process_name=3, pid=5, timestamp=7}`; `crash_show{name=1}` → `{contents=2}`.

## simctl, as this client uses it

- `simctl list -j` (everything: a second kind after the first is read as a search term).
- `simctl bootstatus <udid> -b` boots when needed and waits for the simulator's services: booted
  this way, no Simulator.app window opens.
- `simctl clone <udid> <name>` needs the source shut down ("Unable to clone device in current
  state: Booted"); the device panel's workstream copies follow from that.
- `simctl spawn <udid> launchctl list` names running apps as `UIKitApplication:<bundle id>[…]`
  with their pid; `simctl spawn <udid> log show --style compact --last <n>m --predicate …` is the
  log (`processID == <pid>` for one app's).

## Checked

- Unit tests (`./gradlew :iosSimulatorClient:test`): simctl's JSON and arguments, minicap framing,
  JPEG sizes, HID encoding, `describe` over h2c, the accessibility tree, crash reports, Xcode
  project discovery. `androidDeviceClient`'s `UnixSocketGrpcTest` runs gRPC over a real Unix
  socket, a client stream, and a held-open stream that sends its stop when collected no more.
- End to end against `tools/fake-ios-simulator` (a stand-in `xcrun` and an h2c companion on its
  Unix socket): `commons`'s `FakeSimulatorToolsTest`, and the desktop app's device panel.
- **Not yet against a real simulator**: no Mac was at hand. Before relying on it, run the panel on a
  Mac with Xcode and idb; the parts most likely to differ from the proto are the coordinate space
  of touches on a turned simulator and what each companion version answers in `describe`.
