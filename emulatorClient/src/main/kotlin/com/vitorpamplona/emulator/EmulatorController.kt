/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.emulator

import com.vitorpamplona.emulator.grpc.GrpcChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

/**
 * A client for one running emulator's `android.emulation.control.EmulatorController` gRPC
 * service, the one Android Studio's Running Devices window drives.
 *
 * The emulator serves it in cleartext on loopback at [port] (`grpc.port` in its discovery file,
 * see [EmulatorDiscovery]). Started with `-grpc-use-token`, it wants `authorization: Bearer
 * <token>` with the `grpc.token` from that file; pass it as [token]. Emulators that Android Studio
 * starts use JWT auth instead (`grpc.jwks` in the discovery file), which this client does not
 * implement: such an emulator answers [com.vitorpamplona.emulator.grpc.GrpcStatusException] with
 * status UNAUTHENTICATED.
 *
 * Every call throws [java.io.IOException] (a [com.vitorpamplona.emulator.grpc.GrpcStatusException]
 * when the server answered with an error) and is cancellable.
 *
 * @param client a shared OkHttp client; a derived one is used with HTTP/2 prior knowledge, no
 *   proxy and, for streams, no read timeout.
 */
class EmulatorController(
    val port: Int,
    val token: String?,
    client: OkHttpClient,
    host: String = "127.0.0.1",
) {
    constructor(emulator: RunningEmulator, client: OkHttpClient) : this(
        port = requireNotNull(emulator.grpcPort) { "emulator ${emulator.pid} has no gRPC port" },
        token = emulator.grpcToken,
        client = client,
    )

    private val channel =
        GrpcChannel(
            client = client,
            baseUrl = "http://$host:$port",
            service = SERVICE,
            authorization = { token?.let { "Bearer $it" } },
        )

    /** Version, uptime, whether the guest has booted, and the hardware configuration. */
    suspend fun getStatus(): EmulatorStatus = EmulatorProto.decodeStatus(channel.unary("getStatus", EMPTY))

    /**
     * One screenshot. [width] and [height] of 0 mean the device's own size; otherwise the image
     * is scaled to fit within them, keeping the aspect ratio. [ImageEncoding.PNG] returns a PNG
     * file in [EmulatorFrame.pixels].
     */
    suspend fun getScreenshot(
        encoding: ImageEncoding = ImageEncoding.RGBA8888,
        width: Int = 0,
        height: Int = 0,
        display: Int = 0,
    ): EmulatorFrame = EmulatorProto.decodeImage(channel.unary("getScreenshot", EmulatorProto.imageFormat(encoding, width, height, display)))

    /**
     * The screen as it changes (`streamScreenshot`). The emulator sends a frame when the screen
     * changes, so a still screen sends nothing. Only the newest frame is kept when the collector
     * falls behind; [EmulatorFrame.seq] gaps show what was skipped. Cancel the collection to
     * close the stream.
     */
    fun screenshots(
        encoding: ImageEncoding = ImageEncoding.RGBA8888,
        width: Int = 0,
        height: Int = 0,
        display: Int = 0,
    ): Flow<EmulatorFrame> =
        rawScreenshots(encoding, width, height, display)
            .conflate()
            .map { EmulatorProto.decodeImage(it) }

    /** The undecoded `Image` messages of `streamScreenshot`, every one (no conflation). */
    internal fun rawScreenshots(
        encoding: ImageEncoding,
        width: Int,
        height: Int,
        display: Int,
    ): Flow<ByteArray> = channel.serverStreaming("streamScreenshot", EmulatorProto.imageFormat(encoding, width, height, display))

    /**
     * A mouse event at ([x], [y]) in device pixels. [buttons]: 0 none, 1 primary, 2 secondary.
     * Android sees the primary button as a touch. Move with the button held to drag.
     */
    suspend fun sendMouse(
        x: Int,
        y: Int,
        buttons: Int,
        display: Int = 0,
    ) {
        channel.unary("sendMouse", EmulatorProto.mouseEvent(x, y, buttons, display))
    }

    /** Touches in device pixels, delivered in order (multi-touch "protocol B"). */
    suspend fun sendTouch(
        touches: List<Touch>,
        display: Int = 0,
    ) {
        channel.unary("sendTouch", EmulatorProto.touchEvent(touches, display))
    }

    suspend fun sendKey(event: KeyboardEvent) {
        channel.unary("sendKey", EmulatorProto.keyboardEvent(event))
    }

    /** Presses and releases one W3C key name (`GoBack`, `GoHome`, `AppSwitch`, `Enter`...). */
    suspend fun pressKey(key: String) = sendKey(KeyboardEvent.press(key))

    /** Types [text]; meant for short ASCII strings (the proto suggests the clipboard for more). */
    suspend fun typeText(text: String) = sendKey(KeyboardEvent.type(text))

    /**
     * Scrolls with the mouse wheel through `injectWheel`, a client-streaming call: each call here
     * opens a stream, sends these events and closes it. 120 units are one wheel click; positive
     * [dy] is the wheel turned away from the user. Wheel events go where the mouse last was, so
     * [sendMouse] there first. A long-lived duplex stream would save a round trip per tick; this
     * keeps to the simple form.
     */
    suspend fun sendWheel(
        dx: Int,
        dy: Int,
        display: Int = 0,
    ) {
        channel.clientStreaming("injectWheel", listOf(EmulatorProto.wheelEvent(dx, dy, display)))
    }

    /**
     * Sets the device's physical rotation sensor (`setPhysicalModel`, target ROTATION), angles in
     * degrees around each axis; Android then turns its screen as a real phone would (if
     * auto-rotate is on).
     */
    suspend fun setRotation(
        xDegrees: Float,
        yDegrees: Float,
        zDegrees: Float,
    ) {
        channel.unary(
            "setPhysicalModel",
            EmulatorProto.physicalModel(EmulatorProto.PHYSICAL_TYPE_ROTATION, listOf(xDegrees, yDegrees, zDegrees)),
        )
    }

    /**
     * Turns the device to [rotation] around the z axis, using the angle the proto gives for it
     * (LANDSCAPE 90, REVERSE_LANDSCAPE -90, REVERSE_PORTRAIT -180). The sign convention of the
     * z angle has not been checked against a real emulator.
     */
    suspend fun rotateTo(rotation: SkinRotation) = setRotation(0f, 0f, rotation.degrees.toFloat())

    /** The rotation sensor's x, y, z in degrees (`getPhysicalModel`, target ROTATION). */
    suspend fun getRotation(): List<Float> =
        EmulatorProto.decodePhysicalModelValues(
            channel.unary("getPhysicalModel", EmulatorProto.physicalModel(EmulatorProto.PHYSICAL_TYPE_ROTATION, emptyList())),
        )

    suspend fun getVmState(): VmRunState = EmulatorProto.decodeVmRunState(channel.unary("getVmState", EMPTY))

    /** PAUSED, RUNNING (from paused), SHUTDOWN (clean close), TERMINATE, RESET, RESTART... */
    suspend fun setVmState(state: VmRunState) {
        channel.unary("setVmState", EmulatorProto.vmRunState(state))
    }

    companion object {
        const val SERVICE = "android.emulation.control.EmulatorController"
        private val EMPTY = ByteArray(0)
    }
}
