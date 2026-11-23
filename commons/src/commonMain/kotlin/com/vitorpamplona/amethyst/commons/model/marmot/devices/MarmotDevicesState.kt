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
package com.vitorpamplona.amethyst.commons.model.marmot.devices

import androidx.compose.runtime.Immutable
import com.vitorpamplona.marmot.appComponents.DeviceEntryV1
import com.vitorpamplona.marmot.appComponents.DeviceStatusV1
import com.vitorpamplona.marmot.features.deviceGroup.MarmotDeviceGroupManager
import com.vitorpamplona.marmot.features.devicePairing.CancelReason
import com.vitorpamplona.marmot.features.devicePairing.PairingEndReason
import com.vitorpamplona.marmot.features.devicePairing.SasEmoji
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * One of the seven pairing emoji. [number] is its row in the spec's 64-entry table
 * (`features/device-pairing.md`, Matrix's SAS table): the UI maps it to a translated
 * description; [englishDescription] is only the fallback a headless front end prints.
 */
@Immutable
data class SasSymbol(
    val number: Int,
    val emoji: String,
    val englishDescription: String,
) {
    companion object {
        fun of(emoji: SasEmoji) = SasSymbol(emoji.number, emoji.emoji, emoji.description)
    }
}

/** Why a link attempt ended, for a notice the UI words. */
enum class DeviceLinkEndReason {
    /** This device's user cancelled. */
    CANCELLED,

    /** The other device cancelled (or timed out on its side). */
    CANCELLED_ELSEWHERE,

    /** A user said the codes differ. */
    CODES_DIFFER,

    /** A commitment or MAC did not verify: someone else may be in the middle. */
    VERIFICATION_FAILED,

    /** 600 s passed. */
    TIMED_OUT,

    /** The new device took another device's answer. */
    ANSWERED_ELSEWHERE,

    /** The KeyPackage named in the request could not be fetched or does not match. */
    CANNOT_LINK,

    /** The link was confirmed but adding the device failed. */
    LINK_FAILED,
    ;

    companion object {
        fun of(reason: PairingEndReason): DeviceLinkEndReason =
            when (reason) {
                PairingEndReason.UserCancelled -> CANCELLED
                PairingEndReason.MismatchedCode -> CODES_DIFFER
                PairingEndReason.MismatchedCommitment, PairingEndReason.MismatchedConfirm, PairingEndReason.InvalidKey -> VERIFICATION_FAILED
                PairingEndReason.Timeout -> TIMED_OUT
                PairingEndReason.AnsweredElsewhere -> ANSWERED_ELSEWHERE
                is PairingEndReason.Cancelled ->
                    when (reason.reason) {
                        CancelReason.MISMATCHED_CODE -> CODES_DIFFER
                        CancelReason.TIMEOUT -> TIMED_OUT
                        CancelReason.MISMATCHED_COMMITMENT -> VERIFICATION_FAILED
                        else -> CANCELLED_ELSEWHERE
                    }
            }
    }
}

/** The new device's side of a link, as a screen shows it. */
@Immutable
sealed interface NewDeviceLinkState {
    /** The Request went out; no existing device answered yet. */
    data object WaitingForAnswer : NewDeviceLinkState

    /** Show [code] and ask whether it matches the other screen. */
    data class Comparing(
        val code: List<SasSymbol>,
    ) : NewDeviceLinkState

    /** This user confirmed; the existing device has not yet. */
    data object WaitingForOtherDevice : NewDeviceLinkState

    /** Both confirmed: waiting for the device group's Welcome and this device's first Commit. */
    data object Joining : NewDeviceLinkState

    /** In the device group, its first Commit applied. */
    data object Linked : NewDeviceLinkState

    data class Ended(
        val reason: DeviceLinkEndReason,
    ) : NewDeviceLinkState
}

/** An existing device's view of one link request from a new device of the account. */
@Immutable
data class DeviceLinkPrompt(
    val sessionId: HexKey,
    /** The new device's publication slot, once its KeyPackage was fetched. */
    val slot: HexKey?,
    val requestedAt: Long,
    val state: State,
) {
    @Immutable
    sealed interface State {
        /** Fetching the named KeyPackage and answering. */
        data object Preparing : State

        /** Show [code]; offer Link only when [canLink]. */
        data class Comparing(
            val code: List<SasSymbol>,
            val canLink: Boolean,
        ) : State

        /** Two or more requests are live: offer nothing until only one is. */
        data object CompetingRequests : State

        data class RateLimited(
            val retryAt: Long,
        ) : State

        /** This user approved; waiting for the new device's Confirm. */
        data object Linking : State

        /** Added to the device group. */
        data object Linked : State

        data class Ended(
            val reason: DeviceLinkEndReason,
        ) : State
    }
}

/** One device of the account's roster. */
@Immutable
data class LinkedDevice(
    val slot: HexKey,
    val label: String,
    val client: String,
    val platform: String,
    val isThisDevice: Boolean,
    val status: DeviceStatusV1,
    val linkedAt: Long,
    val linkedBy: HexKey?,
    val addToAllChats: Boolean,
    val bringHistory: Boolean,
    val lastActiveAt: Long?,
) {
    companion object {
        fun of(
            entry: DeviceEntryV1,
            ownSlot: HexKey,
            lastActiveAt: Long?,
        ) = LinkedDevice(
            slot = entry.slot,
            label = entry.label,
            client = entry.client,
            platform = entry.platform,
            isThisDevice = entry.slot == ownSlot,
            status = entry.status,
            linkedAt = entry.linkedAt,
            linkedBy = entry.linkedBy,
            addToAllChats = entry.addToAllChats,
            bringHistory = (entry.settings and DeviceEntryV1.BRING_HISTORY) != 0,
            lastActiveAt = lastActiveAt,
        )
    }
}

/** A KeyPackage slot of the account that is not a device of the roster: a sign-in to ask about. */
@Immutable
data class SignIn(
    val slot: HexKey,
    val publishedAt: Long,
    val kind: MarmotDeviceGroupManager.SignInClass,
    /** This device's user said it was theirs ("That was me"): listed, never asked about again. */
    val confirmed: Boolean = false,
)

/** Everything the devices screen, the sign-in prompt and the "unlinked" notice read. */
@Immutable
data class MarmotDevicesSnapshot(
    /** This device's publication slot; null before [MarmotDevices.start]. */
    val ownSlot: HexKey? = null,
    /** This device trusts a device group. */
    val linked: Boolean = false,
    /** A sibling removed this device from the device group ("This device was unlinked"). */
    val unlinked: Boolean = false,
    val devices: List<LinkedDevice> = emptyList(),
    val newDevice: NewDeviceLinkState? = null,
    val prompts: List<DeviceLinkPrompt> = emptyList(),
    /** While a fresh link catches up: how many chats the approver said to expect that have not arrived. */
    val remainingChats: Long? = null,
    val signIns: List<SignIn> = emptyList(),
)
