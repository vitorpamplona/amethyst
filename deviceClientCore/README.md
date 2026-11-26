# deviceClientCore

What every device client shares, whatever the device: `androidDeviceClient` (adb, the emulator)
and `iosSimulatorClient` (simctl, idb) build on it, and it knows neither Android nor iOS. No
Amethyst or Nostr types.

| Need | Where |
|---|---|
| Run a tool to its end or leave it running, killed with a cancelled caller; a seam for tests | `CommandRunner`, `ProcessCommandRunner` |
| gRPC over HTTP/2 without grpc-java: unary, server streaming, a client stream sent whole, a stream held open (duplex) | `grpc/GrpcChannel` (OkHttp, prior knowledge) |
| Protobuf messages encoded and read by hand (varints, doubles, strings, nested messages) | `grpc/Protobuf` |
| The same channel to a local server on a Unix socket instead of a TCP port | `grpc/UnixSocketFactory` |
| The live view's contract: frames to draw, a pointer, keys, text | `screen/DeviceScreen`, `ScreenFrame`, `PointerAction`, `PixelFormat` |
| An image's size without decoding it | `screen/PngSize`, `screen/JpegSize` |
| A key or button, named once; each platform maps it to its own codes | `input/DeviceKey` |
| The screen as numbered nodes an agent reads and taps by number or text | `ui/UiTree`, `UiNode`, `Bounds` |
| A crash or ANR, whatever reported it (logcat, an `.ips` report) | `crash/DeviceCrash` |

`UnixSocketFactory` wraps a JDK Unix-domain `SocketChannel` in a `java.net.Socket` for OkHttp. Its
streams call the channel's `read`/`write` directly: `Channels.newInputStream` would hold the
channel's blocking lock through a read, and an HTTP/2 connection reads on one thread while it
writes on another. `UnixSocketGrpcTest` checks a unary call, a client stream and a held-open stream
through a real socket file.
