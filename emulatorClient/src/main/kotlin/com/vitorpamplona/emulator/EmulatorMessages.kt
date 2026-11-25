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

import com.vitorpamplona.emulator.proto.ProtoReader
import com.vitorpamplona.emulator.proto.ProtoWriter
import com.vitorpamplona.emulator.proto.forEachField
import com.vitorpamplona.emulator.proto.forEachProtoField
import com.vitorpamplona.emulator.proto.protoMessage

// The messages of AOSP's `emulator_controller.proto` (package `android.emulation.control`,
// Apache-2.0, external/qemu/android/android-grpc/.../emulator_controller.proto) that this client
// uses, encoded and decoded by hand. Every field number and enum value below is the proto's.

/** `ImageFormat.ImgFormat`. */
enum class ImageEncoding(
    val wire: Int,
    val bytesPerPixel: Int,
) {
    PNG(0, 0),

    /** Four bytes per pixel: R, G, B, A. */
    RGBA8888(1, 4),

    /** Three bytes per pixel: R, G, B. */
    RGB888(2, 3),
    ;

    companion object {
        fun fromWire(value: Int): ImageEncoding = entries.firstOrNull { it.wire == value } ?: PNG
    }
}

/** `Rotation.SkinRotation`: how Android sees the device turned. */
enum class SkinRotation(
    val wire: Int,
    /** The angle the proto gives for this rotation. */
    val degrees: Int,
) {
    PORTRAIT(0, 0),
    LANDSCAPE(1, 90),
    REVERSE_PORTRAIT(2, -180),
    REVERSE_LANDSCAPE(3, -90),
    ;

    companion object {
        fun fromWire(value: Int): SkinRotation = entries.firstOrNull { it.wire == value } ?: PORTRAIT
    }
}

/**
 * One screen frame, from `Image`. [pixels] holds [width] x [height] pixels in [encoding]
 * (or a whole PNG file for [ImageEncoding.PNG]).
 *
 * Row order: the proto says rows run "left to right and bottom up"; check against a real
 * emulator before flipping, since Android Studio's own viewer has been seen to draw them as
 * received (top-down). Not verified here.
 */
class EmulatorFrame(
    val width: Int,
    val height: Int,
    val encoding: ImageEncoding,
    val pixels: ByteArray,
    /** Rises through a stream; gaps are frames the emulator dropped. 0 for a single screenshot. */
    val seq: Long,
    /** When the emulator produced the frame, Unix microseconds; 0 when absent. */
    val timestampUs: Long,
    val rotation: SkinRotation,
    val display: Int,
) {
    override fun toString(): String = "EmulatorFrame(${width}x$height $encoding, ${pixels.size} bytes, seq=$seq, $rotation)"
}

/** `VmConfiguration.VmHypervisorType`. */
enum class Hypervisor(
    val wire: Int,
) {
    UNKNOWN(0),
    NONE(1),
    KVM(2),
    HAXM(3),
    HVF(4),
    WHPX(5),
    AEHD(6),
    ;

    companion object {
        fun fromWire(value: Int): Hypervisor = entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

/** `VmConfiguration`. */
data class VmConfiguration(
    val hypervisor: Hypervisor = Hypervisor.UNKNOWN,
    val cpuCores: Int = 0,
    val ramSizeBytes: Long = 0,
)

/** `EmulatorStatus`. */
data class EmulatorStatus(
    val version: String = "",
    val uptimeMs: Long = 0,
    /** True once the guest has finished booting (accurate from Android P on, says the proto). */
    val booted: Boolean = false,
    val vmConfig: VmConfiguration = VmConfiguration(),
    /** The AVD hardware configuration as key/value pairs, in the order the emulator sent them. */
    val hardwareConfig: Map<String, String> = emptyMap(),
    val heartbeat: Long = 0,
    val guestConfig: Map<String, String> = emptyMap(),
) {
    /**
     * The display size from `hw.lcd.width` / `hw.lcd.height`, the AVD `config.ini` keys. That the
     * emulator reports them under these names here is an assumption; when absent, read the size
     * off a screenshot instead.
     */
    val displayWidth: Int? get() = hardwareConfig["hw.lcd.width"]?.toIntOrNull()
    val displayHeight: Int? get() = hardwareConfig["hw.lcd.height"]?.toIntOrNull()
}

/**
 * One contact point, from `Touch`. A [pressure] above zero means touching; send 0 for the same
 * [identifier] to lift it, or the emulator keeps the slot (up to 120 s unless [neverExpire]).
 */
data class Touch(
    val x: Int,
    val y: Int,
    val identifier: Int = 0,
    val pressure: Int = 1,
    val touchMajor: Int = 0,
    val touchMinor: Int = 0,
    /** `EventExpiration.NEVER_EXPIRE`: the slot stays until a pressure-0 touch closes it. */
    val neverExpire: Boolean = false,
    val orientation: Int = 0,
)

/** `KeyboardEvent.KeyEventType`. */
enum class KeyEventType(
    val wire: Int,
) {
    KEY_DOWN(0),
    KEY_UP(1),

    /** Down, then up straight away. */
    KEY_PRESS(2),
}

/** `KeyboardEvent.KeyCodeType`: how [KeyboardEvent.keyCode] is to be read. */
enum class KeyCodeType(
    val wire: Int,
) {
    USB(0),
    EVDEV(1),
    XKB(2),
    WIN(3),
    MAC(4),
}

/**
 * `KeyboardEvent`. Set one of [key], [text] or [keyCode]: the emulator reads [text] first (and then
 * ignores the rest), then [key], then [keyCode].
 *
 * [key] takes W3C `KeyboardEvent.key` values. Ones Android maps to its own buttons: `GoBack`,
 * `GoHome`, `AppSwitch` (Overview), `Power`; plus the usual `Enter`, `Backspace`, `Tab`,
 * `ArrowLeft`... and single printable ASCII characters.
 */
data class KeyboardEvent(
    val eventType: KeyEventType = KeyEventType.KEY_PRESS,
    val key: String? = null,
    val text: String? = null,
    val keyCode: Int = 0,
    val codeType: KeyCodeType = KeyCodeType.USB,
) {
    companion object {
        const val GO_BACK = "GoBack"
        const val GO_HOME = "GoHome"
        const val APP_SWITCH = "AppSwitch"
        const val POWER = "Power"
        const val ENTER = "Enter"
        const val BACKSPACE = "Backspace"

        fun press(key: String) = KeyboardEvent(KeyEventType.KEY_PRESS, key = key)

        fun type(text: String) = KeyboardEvent(text = text)
    }
}

/** `VmRunState.RunState`. */
enum class VmRunState(
    val wire: Int,
) {
    UNKNOWN(0),
    RUNNING(1),
    RESTORE_VM(2),
    PAUSED(3),
    SAVE_VM(4),
    SHUTDOWN(5),
    TERMINATE(7),
    RESET(9),
    INTERNAL_ERROR(10),
    RESTART(11),
    START(12),
    STOP(13),
    ;

    companion object {
        fun fromWire(value: Int): VmRunState = entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

/** Encoders and decoders, kept apart from the public types. */
internal object EmulatorProto {
    // ImageFormat
    private const val IMAGE_FORMAT_FORMAT = 1
    private const val IMAGE_FORMAT_ROTATION = 2
    private const val IMAGE_FORMAT_WIDTH = 3
    private const val IMAGE_FORMAT_HEIGHT = 4
    private const val IMAGE_FORMAT_DISPLAY = 5

    // Image
    private const val IMAGE_FORMAT = 1
    private const val IMAGE_WIDTH = 2
    private const val IMAGE_HEIGHT = 3
    private const val IMAGE_IMAGE = 4
    private const val IMAGE_SEQ = 5
    private const val IMAGE_TIMESTAMP_US = 6

    // Rotation
    private const val ROTATION_ROTATION = 1

    // PhysicalModelValue
    const val PHYSICAL_TYPE_ROTATION = 1
    private const val PHYSICAL_TARGET = 1
    private const val PHYSICAL_VALUE = 3
    private const val PARAMETER_DATA = 1

    fun imageFormat(
        encoding: ImageEncoding,
        width: Int,
        height: Int,
        display: Int,
    ): ByteArray =
        protoMessage {
            enum(IMAGE_FORMAT_FORMAT, encoding.wire)
            uint32(IMAGE_FORMAT_WIDTH, width)
            uint32(IMAGE_FORMAT_HEIGHT, height)
            uint32(IMAGE_FORMAT_DISPLAY, display)
        }

    private class Format(
        var encoding: ImageEncoding = ImageEncoding.PNG,
        var rotation: SkinRotation = SkinRotation.PORTRAIT,
        var width: Int = 0,
        var height: Int = 0,
        var display: Int = 0,
    )

    private fun readFormat(r: ProtoReader): Format {
        val f = Format()
        r.forEachField { field ->
            when (field) {
                IMAGE_FORMAT_FORMAT -> f.encoding = ImageEncoding.fromWire(readEnum())
                IMAGE_FORMAT_ROTATION -> f.rotation = readRotation(readMessage())
                IMAGE_FORMAT_WIDTH -> f.width = readUInt32()
                IMAGE_FORMAT_HEIGHT -> f.height = readUInt32()
                IMAGE_FORMAT_DISPLAY -> f.display = readUInt32()
                else -> skip()
            }
        }
        return f
    }

    private fun readRotation(r: ProtoReader): SkinRotation {
        var rotation = SkinRotation.PORTRAIT
        r.forEachField { field ->
            if (field == ROTATION_ROTATION) rotation = SkinRotation.fromWire(readEnum()) else skip()
        }
        return rotation
    }

    fun decodeImage(bytes: ByteArray): EmulatorFrame {
        var format = Format()
        var legacyWidth = 0
        var legacyHeight = 0
        var pixels = ByteArray(0)
        var seq = 0L
        var timestamp = 0L
        bytes.forEachProtoField { field ->
            when (field) {
                IMAGE_FORMAT -> format = readFormat(readMessage())
                IMAGE_WIDTH -> legacyWidth = readUInt32()
                IMAGE_HEIGHT -> legacyHeight = readUInt32()
                IMAGE_IMAGE -> pixels = readBytes()
                IMAGE_SEQ -> seq = readUInt32().toLong() and 0xFFFFFFFFL
                IMAGE_TIMESTAMP_US -> timestamp = readUInt64()
                else -> skip()
            }
        }
        return EmulatorFrame(
            width = if (format.width != 0) format.width else legacyWidth,
            height = if (format.height != 0) format.height else legacyHeight,
            encoding = format.encoding,
            pixels = pixels,
            seq = seq,
            timestampUs = timestamp,
            rotation = format.rotation,
            display = format.display,
        )
    }

    /** Test helper and documentation of the shape: an `Image` as the emulator sends it. */
    fun encodeImage(frame: EmulatorFrame): ByteArray =
        protoMessage {
            message(IMAGE_FORMAT) {
                enum(IMAGE_FORMAT_FORMAT, frame.encoding.wire)
                message(IMAGE_FORMAT_ROTATION) { enum(ROTATION_ROTATION, frame.rotation.wire) }
                uint32(IMAGE_FORMAT_WIDTH, frame.width)
                uint32(IMAGE_FORMAT_HEIGHT, frame.height)
                uint32(IMAGE_FORMAT_DISPLAY, frame.display)
            }
            bytes(IMAGE_IMAGE, frame.pixels)
            uint32(IMAGE_SEQ, frame.seq.toInt())
            uint64(IMAGE_TIMESTAMP_US, frame.timestampUs)
        }

    // EmulatorStatus
    private const val STATUS_VERSION = 1
    private const val STATUS_UPTIME = 2
    private const val STATUS_BOOTED = 3
    private const val STATUS_VM_CONFIG = 4
    private const val STATUS_HARDWARE_CONFIG = 5
    private const val STATUS_HEARTBEAT = 6
    private const val STATUS_GUEST_CONFIG = 7
    private const val VM_HYPERVISOR = 1
    private const val VM_CORES = 2
    private const val VM_RAM = 3
    private const val ENTRY_LIST_ENTRY = 1

    // Entry, and a map<string, string> entry: key = 1, value = 2.
    private const val ENTRY_KEY = 1
    private const val ENTRY_VALUE = 2

    fun decodeStatus(bytes: ByteArray): EmulatorStatus {
        var version = ""
        var uptime = 0L
        var booted = false
        var vm = VmConfiguration()
        val hardware = LinkedHashMap<String, String>()
        var heartbeat = 0L
        val guest = LinkedHashMap<String, String>()
        bytes.forEachProtoField { field ->
            when (field) {
                STATUS_VERSION -> version = readString()
                STATUS_UPTIME -> uptime = readUInt64()
                STATUS_BOOTED -> booted = readBool()
                STATUS_VM_CONFIG -> vm = readVm(readMessage())
                STATUS_HARDWARE_CONFIG ->
                    readMessage().forEachField { f ->
                        if (f == ENTRY_LIST_ENTRY) readEntry(readMessage(), hardware) else skip()
                    }
                STATUS_HEARTBEAT -> heartbeat = readUInt64()
                STATUS_GUEST_CONFIG -> readEntry(readMessage(), guest)
                else -> skip()
            }
        }
        return EmulatorStatus(version, uptime, booted, vm, hardware, heartbeat, guest)
    }

    private fun readVm(r: ProtoReader): VmConfiguration {
        var hypervisor = Hypervisor.UNKNOWN
        var cores = 0
        var ram = 0L
        r.forEachField { field ->
            when (field) {
                VM_HYPERVISOR -> hypervisor = Hypervisor.fromWire(readEnum())
                VM_CORES -> cores = readInt32()
                VM_RAM -> ram = readInt64()
                else -> skip()
            }
        }
        return VmConfiguration(hypervisor, cores, ram)
    }

    private fun readEntry(
        r: ProtoReader,
        into: MutableMap<String, String>,
    ) {
        var key = ""
        var value = ""
        r.forEachField { field ->
            when (field) {
                ENTRY_KEY -> key = readString()
                ENTRY_VALUE -> value = readString()
                else -> skip()
            }
        }
        into[key] = value
    }

    /** Test helper: an `EmulatorStatus` as the emulator sends it. */
    fun encodeStatus(status: EmulatorStatus): ByteArray =
        protoMessage {
            string(STATUS_VERSION, status.version)
            uint64(STATUS_UPTIME, status.uptimeMs)
            bool(STATUS_BOOTED, status.booted)
            message(STATUS_VM_CONFIG) {
                enum(VM_HYPERVISOR, status.vmConfig.hypervisor.wire)
                int32(VM_CORES, status.vmConfig.cpuCores)
                int64(VM_RAM, status.vmConfig.ramSizeBytes)
            }
            message(STATUS_HARDWARE_CONFIG) {
                status.hardwareConfig.forEach { (k, v) -> message(ENTRY_LIST_ENTRY) { entry(k, v) } }
            }
            uint64(STATUS_HEARTBEAT, status.heartbeat)
            status.guestConfig.forEach { (k, v) -> message(STATUS_GUEST_CONFIG) { entry(k, v) } }
        }

    private fun ProtoWriter.entry(
        key: String,
        value: String,
    ) {
        string(ENTRY_KEY, key)
        string(ENTRY_VALUE, value)
    }

    // MouseEvent
    fun mouseEvent(
        x: Int,
        y: Int,
        buttons: Int,
        display: Int,
    ): ByteArray =
        protoMessage {
            int32(1, x)
            int32(2, y)
            int32(3, buttons)
            int32(4, display)
        }

    // TouchEvent { repeated Touch touches = 1; int32 display = 2; }
    fun touchEvent(
        touches: List<Touch>,
        display: Int,
    ): ByteArray =
        protoMessage {
            touches.forEach { t ->
                message(1) {
                    int32(1, t.x)
                    int32(2, t.y)
                    int32(3, t.identifier)
                    int32(4, t.pressure)
                    int32(5, t.touchMajor)
                    int32(6, t.touchMinor)
                    // EventExpiration: EVENT_EXPIRATION_UNSPECIFIED = 0, NEVER_EXPIRE = 1
                    enum(7, if (t.neverExpire) 1 else 0)
                    int32(8, t.orientation)
                }
            }
            int32(2, display)
        }

    // KeyboardEvent
    fun keyboardEvent(e: KeyboardEvent): ByteArray =
        protoMessage {
            enum(1, e.codeType.wire)
            enum(2, e.eventType.wire)
            int32(3, e.keyCode)
            string(4, e.key)
            string(5, e.text)
        }

    // WheelEvent
    fun wheelEvent(
        dx: Int,
        dy: Int,
        display: Int,
    ): ByteArray =
        protoMessage {
            int32(1, dx)
            int32(2, dy)
            int32(3, display)
        }

    // PhysicalModelValue { PhysicalType target = 1; State status = 2; ParameterValue value = 3; }
    fun physicalModel(
        target: Int,
        values: List<Float>,
    ): ByteArray =
        protoMessage {
            enum(PHYSICAL_TARGET, target)
            message(PHYSICAL_VALUE) { packedFloats(PARAMETER_DATA, values) }
        }

    fun decodePhysicalModelValues(bytes: ByteArray): List<Float> {
        val out = ArrayList<Float>()
        bytes.forEachProtoField { field ->
            if (field == PHYSICAL_VALUE) {
                readMessage().forEachField { f -> if (f == PARAMETER_DATA) readFloats(out) else skip() }
            } else {
                skip()
            }
        }
        return out
    }

    // VmRunState { RunState state = 1; }
    fun vmRunState(state: VmRunState): ByteArray = protoMessage { enum(1, state.wire) }

    fun decodeVmRunState(bytes: ByteArray): VmRunState {
        var state = VmRunState.UNKNOWN
        bytes.forEachProtoField { field -> if (field == 1) state = VmRunState.fromWire(readEnum()) else skip() }
        return state
    }
}
