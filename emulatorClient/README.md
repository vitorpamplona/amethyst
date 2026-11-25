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

## Not verified (no emulator where this was built)

Check these on a machine with an emulator before building on them:

- **Launch flags**: `-no-window`, `-grpc <port>`, `-grpc-use-token`, `-no-boot-anim`, `-no-audio`,
  `-no-snapshot-save`, `-read-only`, `-gpu`. Compare with `emulator -help` of the installed version.
- **Discovery directory**: Linux `$XDG_RUNTIME_DIR/avd/running`, else `/tmp/android-$USER/avd/running`;
  macOS `~/Library/Caches/TemporaryItems/avd/running`; Windows `%LOCALAPPDATA%\Temp\avd\running`.
  Whether `ANDROID_EMULATOR_HOME` / `ANDROID_AVD_HOME` move it is unknown, so they are not read
  (pass your own directories to `EmulatorDiscovery`).
- **Discovery keys**: `grpc.port`, `grpc.token`, `port.serial`, `port.adb`, `avd.name`, `avd.id`,
  `avd.dir`, `cmdline`. The parser keeps every key and treats any of them as optional.
- **Auth**: emulators started by Android Studio use JWT auth (`grpc.jwks...` in the discovery file),
  which this client does not do; those answer UNAUTHENTICATED. `RunningEmulator.usesJwt` flags them.
  Start the emulator with `-grpc-use-token` (what `EmulatorLauncher` does) to get a token.
- **Pixel row order**: the proto says rows run bottom up; check before flipping.
- **Rotation sign**: `rotateTo` sets the z angle the proto lists per `SkinRotation` (LANDSCAPE 90,
  REVERSE_LANDSCAPE -90); the sign the emulator expects is not confirmed.
- **Display size keys**: `EmulatorStatus.displayWidth/Height` read `hw.lcd.width`/`hw.lcd.height`
  from the hardware config; the width and height of a screenshot are the sure source.
- **Launcher pid**: `EmulatorLauncher.awaitRunning` matches the discovery file's pid against the
  started process and its descendants, then falls back to the AVD name.

## Tests

`./gradlew :emulatorClient:test`: protobuf encoding against the spec's examples, unknown-field
skipping, gRPC framing split across reads, discovery and AVD files in temp directories, the launch
command line, and end-to-end unary, trailers-only error, server-streaming and cancellation calls
against OkHttp's `mockwebserver3` running HTTP/2 with prior knowledge.
