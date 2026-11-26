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
package com.vitorpamplona.amethyst.commons.devices

import androidx.compose.runtime.Immutable
import com.vitorpamplona.devicecore.crash.DeviceCrash
import java.io.File

enum class DevicePlatform { ANDROID, IOS }

enum class DeviceKind {
    /** An Android emulator. */
    EMULATOR,

    /** An iOS simulator. */
    SIMULATOR,

    /** A phone on USB: the user's own, which agents use only once allowed. */
    PHYSICAL,
}

enum class DeviceStatus {
    /** Starting here, not yet listed by its platform's tools. */
    STARTING,

    /** Listed, but not ready for input (Android still booting, a simulator's services coming up). */
    BOOTING,
    ONLINE,
    OFFLINE,

    /** A phone that hasn't accepted this computer's adb key yet: the user taps Allow on it. */
    UNAUTHORIZED,

    /** adb can't open the USB device (Linux udev rules). */
    NO_PERMISSION,
}

/** One device the panel lists, of either platform. */
@Immutable
data class DeviceInfo(
    val serial: String,
    val name: String,
    val platform: DevicePlatform,
    val kind: DeviceKind,
    val status: DeviceStatus,
    /** The [StartableDevice.id] this runs: an AVD's name, or the simulator it is (or copies). */
    val source: String? = null,
    /** "API 35", "iOS 26.0": what it runs, for the picker. */
    val osVersion: String? = null,
    /** Whether its screen streams to the live view (an emulator's gRPC, idb); else it is polled. */
    val liveStream: Boolean = false,
    /** Started by this app: it stops it when it quits. */
    val startedHere: Boolean = false,
    /** Whether agents may look at and drive it: every virtual device; a phone once the user allows it. */
    val agentAllowed: Boolean = false,
    /**
     * The workstream (or chat) whose own copy this is: an emulator's read-only instance, or a
     * simulator's clone. Null for a shared device.
     */
    val owner: String? = null,
    /** What to call [owner] in the picker: the workstream's title. */
    val ownerLabel: String? = null,
    /** Whether "Reset to its starting state" works on it. */
    val canReset: Boolean = false,
    /** Whether it can be started again without its saved state. */
    val canColdBoot: Boolean = false,
    /** Whether its crashes can be read (a simulator's need idb's companion). */
    val canReadCrashes: Boolean = true,
) {
    val isOnline: Boolean get() = status == DeviceStatus.ONLINE
    val isIos: Boolean get() = platform == DevicePlatform.IOS

    /** A virtual device this app can stop: an emulator or a simulator, not a phone. */
    val isVirtual: Boolean get() = kind != DeviceKind.PHYSICAL
}

/** A virtual device that can be started: an AVD, or an iOS simulator that is shut down. */
@Immutable
data class StartableDevice(
    /** What [Devices.start] takes: the AVD's name, or the simulator's udid. */
    val id: String,
    val platform: DevicePlatform,
    val name: String,
    /** "API 35 · x86_64", "iOS 26.0". */
    val detail: String,
    /** Orders the newest first: the API level, or the OS version zero-padded. */
    val rank: String,
    val isTablet: Boolean = false,
)

/** What a platform has to work with here, for the panel to say what is missing. */
sealed interface PlatformSetup

/** What the Android side has to work with. */
@Immutable
data class AndroidSetup(
    val sdkDir: File? = null,
    val adbFound: Boolean = false,
    val emulatorFound: Boolean = false,
    /** Why the last look failed, as adb said it. */
    val error: String? = null,
) : PlatformSetup

/** What the iOS side has to work with. */
@Immutable
data class IosSetup(
    /** A Mac with Xcode (or a stand-in, `AMETHYST_XCRUN`): simulators can be listed. */
    val xcodeFound: Boolean = false,
    /** idb's companion: the live screen, touches and the UI tree need it. */
    val idbFound: Boolean = false,
    /** Why the last look failed, as simctl said it. */
    val error: String? = null,
) : PlatformSetup

@Immutable
data class DevicesState(
    val devices: List<DeviceInfo> = emptyList(),
    /** Every virtual device that can be started, of every platform, newest first per platform. */
    val startable: List<StartableDevice> = emptyList(),
    val android: AndroidSetup = AndroidSetup(),
    val ios: IosSetup = IosSetup(),
    /** Whether the first look has finished: before it, "no devices" isn't known yet. */
    val loaded: Boolean = false,
    /** Why the last start of each [StartableDevice.id] failed. */
    val startFailures: Map<String, String> = emptyMap(),
) {
    /** Whether any platform can run devices here. */
    val anyPlatform: Boolean get() = android.adbFound || android.sdkDir != null || ios.xcodeFound

    fun startable(id: String): StartableDevice? = startable.firstOrNull { it.id == id }
}

/** What an agent just did on a device, for the panel to show where it touched. */
@Immutable
data class DeviceActivity(
    val serial: String,
    val kind: Kind,
    /** Where, as a fraction of the screen as shown (0..1); null for what has no place (a key). */
    val x: Float? = null,
    val y: Float? = null,
    val toX: Float? = null,
    val toY: Float? = null,
    val label: String = "",
    val at: Long = System.currentTimeMillis(),
) {
    enum class Kind { TAP, LONG_PRESS, SWIPE, KEY, TYPE, LOOK, APP }
}

/** A crash or ANR found after the app started watching a device; [seq] orders them across devices. */
@Immutable
data class SeenCrash(
    val seq: Long,
    val serial: String,
    val crash: DeviceCrash,
    /** When the app found it, in epoch milliseconds. */
    val at: Long = System.currentTimeMillis(),
)

/** How to start a virtual device. Android's emulator takes both; a simulator ignores them. */
data class StartOptions(
    /** Boot without the saved quick-boot state. */
    val coldBoot: Boolean = false,
    /** Start from factory state. */
    val wipeData: Boolean = false,
)
