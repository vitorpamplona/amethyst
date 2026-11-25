# emulatorClient

An **unofficial** Kotlin/JVM client for the Android Emulator's gRPC `EmulatorController` service
(`android.emulation.control`, the service Android Studio's Running Devices window drives). It is
for showing an emulator's screen inside an app and sending it touch, mouse and key input.

It bundles nothing from the Android SDK, and it has no gRPC, protobuf or codegen dependency: it
speaks gRPC itself over OkHttp's cleartext HTTP/2 (`Protocol.H2_PRIOR_KNOWLEDGE`) and encodes the
few messages it needs by hand, with every field number and enum value taken from AOSP's
`emulator_controller.proto` (Apache-2.0). Dependencies: OkHttp and kotlinx.coroutines. No Amethyst
or Nostr types, so it can move to its own repository.

## What it does

| Need | Where |
|---|---|
| Find the emulators running now (gRPC port, token, adb serial, AVD name) | `EmulatorDiscovery`, `RunningEmulator` |
| Find the SDK, the `emulator` binary and the AVDs | `AndroidSdk` |
| Start an AVD headless with gRPC on, and wait for it to appear | `EmulatorLauncher`, `EmulatorLaunchOptions` |
| Status (version, uptime, booted, hardware config) | `EmulatorController.getStatus` |
| One screenshot / a stream of frames (RGBA8888, RGB888 or PNG; rotation, seq, timestamp) | `getScreenshot`, `screenshots` |
| Mouse, multi-touch, keys (W3C names: `GoBack`, `GoHome`, `AppSwitch`, `Enter`...), text | `sendMouse`, `sendTouch`, `sendKey`, `pressKey`, `typeText` |
| Mouse wheel (`injectWheel`, one short client stream per call) | `sendWheel` |
| Rotate the device (rotation sensor through `setPhysicalModel`) | `setRotation`, `rotateTo`, `getRotation` |
| Pause, resume, shut down, reset | `getVmState`, `setVmState` |

```kotlin
val emulator = EmulatorDiscovery().running().first { it.grpcPort != null }
val controller = EmulatorController(emulator, OkHttpClient())
controller.screenshots(ImageEncoding.RGBA8888, width = 540).collect { frame -> draw(frame) }
controller.sendTouch(listOf(Touch(x, y, identifier = 0, pressure = 1)))
controller.sendTouch(listOf(Touch(x, y, identifier = 0, pressure = 0))) // lift
controller.pressKey(KeyboardEvent.GO_BACK)
```

## The wire, in short

`POST http://127.0.0.1:<grpc.port>/android.emulation.control.EmulatorController/<method>` over
h2c, with `content-type: application/grpc`, `te: trailers` and, for an emulator started with
`-grpc-use-token`, `authorization: Bearer <grpc.token>`. The body is length-prefixed messages (a
zero compressed-flag byte, a four-byte big-endian length, the protobuf). The answer is the same
framing; `grpc-status` arrives in the trailers, or in the headers of a trailers-only answer, and a
non-zero one becomes `GrpcStatusException`.

## Checked against a real emulator

Emulator 37.3.10 on Linux (no KVM, so software CPU), with API 21 images, through
`RealEmulatorTest` and the desktop app's devices panel:

- **Launch flags**: every flag `EmulatorLauncher` passes is in that emulator's `-help`; the panel's
  Start launches an AVD, and `awaitRunning` finds it. `emulator` execs into QEMU, so the started
  process's pid is the discovery file's.
- **Discovery**: without `XDG_RUNTIME_DIR` the emulator falls back to `~/.android/avd/running`
  (its log says so), which is searched too. Keys as listed above; `avd.name` is the display name
  ("Test API 21"), `avd.id` the AVD's id; `cmdline` is quoted.
- **Token auth**: `-grpc-use-token`, the token from `grpc.token`.
- **JWT auth** (`-grpc-use-jwt`, Android Studio's way): the discovery file names `grpc.jwks` (a
  directory the emulator watches) and `grpc.jwk_active`. `EmulatorJwtAuth` writes its ES256 key
  there as a JWK set and signs tokens as the issuer the default allow list gives test tooling
  (`gradle-utp-emulator-control`), naming in `aud` the methods it calls. Two traps, both seen: a
  key file renamed into place is never read (write it in place), and a token with a `typ` header
  is refused. That issuer may not call `injectWheel` or `setVmState` (PERMISSION_DENIED).
- **Display size**: `hw.lcd.width`/`hw.lcd.height` are in `getStatus`'s hardware config.
- **Frames**: PNG and RGBA8888 at the asked size, fitted with the aspect kept (asked 518 x 728:
  436 x 728 upright, 518 x 310 turned). Raw RGBA rows run **top-down**, not bottom-up as the proto
  says.
- **Rotation**: `rotateTo` turns the device (LANDSCAPE is +90 on z, REVERSE_LANDSCAPE -90); a frame
  reports it after a moment and comes turned, its width and height swapped.
- **Touches**: mouse and touch coordinates are in the device's **upright** frame however it is
  turned; a client mapping a turned frame must turn its point back (the devices panel does).
- **Keys**: `GoHome`, `GoBack`, `AudioVolumeUp` act.
- **Wheel**: `injectWheel` is answered OK but did nothing on the API 21 image; the devices panel
  scrolls with a touch swipe instead.
- **Status**: `booted` stays false on API 21 (the proto says it is reliable from Android 9).

Not checked: macOS and Windows (discovery paths there), physical devices.

## Tests

`./gradlew :emulatorClient:test`: protobuf encoding against the spec's examples, unknown-field
skipping, gRPC framing split across reads, discovery and AVD files in temp directories, the launch
command line, and end-to-end unary, trailers-only error, server-streaming and cancellation calls
against OkHttp's `mockwebserver3` running HTTP/2 with prior knowledge.

`EMULATOR_IT=1 ./gradlew :emulatorClient:test --tests '*RealEmulatorTest*'` runs the same calls against
an emulator that runs now; `EMULATOR_IT_LAUNCH=<avd id>` also starts one (`EMULATOR_IT_ARGS` adds
flags such as `-accel off`, `EMULATOR_IT_KEEP=1` leaves it running).
