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
package com.vitorpamplona.amethyst.commons.marmot.devices

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceLinkEndReason
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceLinkPrompt
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevicesSnapshot
import com.vitorpamplona.amethyst.commons.model.marmot.devices.NewDeviceLinkState
import com.vitorpamplona.amethyst.commons.model.marmot.devices.SasSymbol
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** Which side of a link this device plays on the link screen. */
enum class DeviceLinkRole {
    /** This device joins the account's other devices: it sends the request. */
    NEW_DEVICE,

    /** This device is already one of the account's devices and approves the new one. */
    EXISTING_DEVICE,
}

/** What the link screen shows, for either role, read from [MarmotDevicesSnapshot]. */
@Immutable
sealed interface DeviceLinkScreenState {
    /** New device: publishing its KeyPackage and sending the request. */
    data object Starting : DeviceLinkScreenState

    /** Existing device: no new device of the account has asked yet. */
    data object WaitingForRequest : DeviceLinkScreenState

    /** New device: the request went out; no existing device answered yet. */
    data object WaitingForAnswer : DeviceLinkScreenState

    /** Existing device: fetching and checking the new device's KeyPackage. */
    data object Preparing : DeviceLinkScreenState

    /** Both screens show [code]; [canConfirm] is false while "They match" cannot be accepted yet. */
    data class Comparing(
        val code: List<SasSymbol>,
        val canConfirm: Boolean,
        /** The request being compared, on the existing device. */
        val sessionId: HexKey?,
    ) : DeviceLinkScreenState

    /** This user said the codes match; the other device has not yet. */
    data object WaitingForOtherDevice : DeviceLinkScreenState

    /** New device: both confirmed, waiting to be added to the account's device group. */
    data object Joining : DeviceLinkScreenState

    /** Existing device: two new devices asked at once, so neither is answered. */
    data object CompetingRequests : DeviceLinkScreenState

    /** Existing device: too many attempts; answers again at [retryAt]. */
    data class RateLimited(
        val retryAt: Long,
    ) : DeviceLinkScreenState

    /** Done. On a new device, [remainingChats] chats are still on their way. */
    data class Linked(
        val remainingChats: Long?,
    ) : DeviceLinkScreenState

    data class Ended(
        val reason: DeviceLinkEndReason,
    ) : DeviceLinkScreenState

    /** New device: this device is already linked, so there is nothing to link. */
    data object AlreadyLinked : DeviceLinkScreenState

    /** New device: the request could not be sent (no relay took the KeyPackage, say). */
    data object StartFailed : DeviceLinkScreenState
}

object DeviceLinkScreen {
    /**
     * The link screen for [role]. On the existing device [sessionId] is the request it follows (null
     * while none arrived); on a new device, [startFailed] says the last start threw.
     */
    fun state(
        snapshot: MarmotDevicesSnapshot,
        role: DeviceLinkRole,
        sessionId: HexKey?,
        startFailed: Boolean = false,
    ): DeviceLinkScreenState =
        when (role) {
            DeviceLinkRole.NEW_DEVICE -> newDevice(snapshot, startFailed)
            DeviceLinkRole.EXISTING_DEVICE -> existingDevice(snapshot, sessionId)
        }

    private fun newDevice(
        snapshot: MarmotDevicesSnapshot,
        startFailed: Boolean,
    ): DeviceLinkScreenState =
        when (val s = snapshot.newDevice) {
            null ->
                when {
                    startFailed -> DeviceLinkScreenState.StartFailed
                    snapshot.linked -> DeviceLinkScreenState.AlreadyLinked
                    else -> DeviceLinkScreenState.Starting
                }
            NewDeviceLinkState.WaitingForAnswer -> DeviceLinkScreenState.WaitingForAnswer
            is NewDeviceLinkState.Comparing -> DeviceLinkScreenState.Comparing(s.code, canConfirm = true, sessionId = null)
            NewDeviceLinkState.WaitingForOtherDevice -> DeviceLinkScreenState.WaitingForOtherDevice
            NewDeviceLinkState.Joining -> DeviceLinkScreenState.Joining
            NewDeviceLinkState.Linked -> DeviceLinkScreenState.Linked(snapshot.remainingChats?.takeIf { it > 0 })
            is NewDeviceLinkState.Ended -> if (startFailed) DeviceLinkScreenState.StartFailed else DeviceLinkScreenState.Ended(s.reason)
        }

    private fun existingDevice(
        snapshot: MarmotDevicesSnapshot,
        sessionId: HexKey?,
    ): DeviceLinkScreenState {
        val prompt = sessionId?.let { id -> snapshot.prompts.firstOrNull { it.sessionId == id } } ?: return DeviceLinkScreenState.WaitingForRequest
        return when (val s = prompt.state) {
            DeviceLinkPrompt.State.Preparing -> DeviceLinkScreenState.Preparing
            is DeviceLinkPrompt.State.Comparing -> DeviceLinkScreenState.Comparing(s.code, s.canLink, prompt.sessionId)
            DeviceLinkPrompt.State.CompetingRequests -> DeviceLinkScreenState.CompetingRequests
            is DeviceLinkPrompt.State.RateLimited -> DeviceLinkScreenState.RateLimited(s.retryAt)
            DeviceLinkPrompt.State.Linking -> DeviceLinkScreenState.WaitingForOtherDevice
            DeviceLinkPrompt.State.Linked -> DeviceLinkScreenState.Linked(null)
            is DeviceLinkPrompt.State.Ended -> DeviceLinkScreenState.Ended(s.reason)
        }
    }

    /**
     * The request an existing device's link screen, opened at [openedAt] without one, should follow:
     * the newest that is still in progress, or that arrived after the screen opened even if it ended
     * since. A link finished before the screen opened (linked or ended) is never followed, so a
     * second "Link a new device" does not open on the last one's outcome.
     */
    fun followablePrompt(
        prompts: List<DeviceLinkPrompt>,
        openedAt: Long,
    ): DeviceLinkPrompt? =
        prompts
            .filter { it.requestedAt >= openedAt || !it.state.isFinished() }
            .maxByOrNull { it.requestedAt }

    private fun DeviceLinkPrompt.State.isFinished() = this is DeviceLinkPrompt.State.Ended || this == DeviceLinkPrompt.State.Linked

    /** Whether [state] is one a user can still cancel. */
    fun isCancellable(state: DeviceLinkScreenState): Boolean =
        when (state) {
            DeviceLinkScreenState.Starting,
            DeviceLinkScreenState.WaitingForAnswer,
            DeviceLinkScreenState.Preparing,
            is DeviceLinkScreenState.Comparing,
            DeviceLinkScreenState.WaitingForOtherDevice,
            DeviceLinkScreenState.CompetingRequests,
            is DeviceLinkScreenState.RateLimited,
            -> true
            else -> false
        }
}
