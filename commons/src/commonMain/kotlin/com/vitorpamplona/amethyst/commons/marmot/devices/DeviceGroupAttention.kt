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
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceLinkPrompt
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevicesSnapshot
import com.vitorpamplona.amethyst.commons.model.marmot.devices.NewDeviceLinkState
import com.vitorpamplona.amethyst.commons.model.marmot.devices.SignIn
import com.vitorpamplona.marmot.features.deviceGroup.MarmotDeviceGroupManager
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** What the app shows outside the devices screen: one prompt at a time, the most urgent first. */
@Immutable
data class DeviceGroupAttention(
    /** Another device of this account asks to link, and this device can approve it. */
    val linkRequest: DeviceLinkPrompt? = null,
    /** A sign-in to this account this device does not know: "Was this you?" */
    val signIn: SignIn? = null,
    /** A sibling removed this device from the account's devices. */
    val unlinked: Boolean = false,
) {
    companion object {
        val NONE = DeviceGroupAttention()

        /** The prompt states that still need this user; the rest are done or in hand. */
        fun needsAnswer(state: DeviceLinkPrompt.State): Boolean =
            state is DeviceLinkPrompt.State.Preparing ||
                state is DeviceLinkPrompt.State.Comparing ||
                state is DeviceLinkPrompt.State.CompetingRequests ||
                state is DeviceLinkPrompt.State.RateLimited

        fun of(
            snapshot: MarmotDevicesSnapshot,
            dismissedRequests: Set<HexKey>,
            dismissedSignIns: Set<HexKey>,
            unlinkedDismissed: Boolean,
            linkScreenOpen: Boolean,
        ): DeviceGroupAttention {
            // A device that was removed approves nothing and asks about nothing: it only says so.
            if (snapshot.unlinked) return DeviceGroupAttention(unlinked = !unlinkedDismissed)
            val linkingItself = snapshot.newDevice.let { it != null && it !is NewDeviceLinkState.Ended && it != NewDeviceLinkState.Linked }
            val request =
                if (linkScreenOpen || linkingItself) {
                    null
                } else {
                    snapshot.prompts
                        .filter { it.sessionId !in dismissedRequests && needsAnswer(it.state) }
                        .maxByOrNull { it.requestedAt }
                }
            val signIn =
                snapshot.signIns
                    .filter { it.kind == MarmotDeviceGroupManager.SignInClass.NEW_SIGN_IN && it.slot !in dismissedSignIns }
                    .maxByOrNull { it.publishedAt }
            return DeviceGroupAttention(linkRequest = request, signIn = signIn)
        }
    }
}

/**
 * What this app session already showed or the user waved off, shared by the prompt host and the
 * devices screens of one account. In memory: a restart asks again about what is still pending.
 */
class DeviceGroupAttentionState {
    private val _dismissedRequests = MutableStateFlow<PersistentSet<HexKey>>(persistentSetOf())
    val dismissedRequests: StateFlow<PersistentSet<HexKey>> = _dismissedRequests.asStateFlow()

    private val _dismissedSignIns = MutableStateFlow<PersistentSet<HexKey>>(persistentSetOf())
    val dismissedSignIns: StateFlow<PersistentSet<HexKey>> = _dismissedSignIns.asStateFlow()

    private val _unlinkedDismissed = MutableStateFlow(false)
    val unlinkedDismissed: StateFlow<Boolean> = _unlinkedDismissed.asStateFlow()

    private val _openLinkScreens = MutableStateFlow(0)

    /** How many link screens are open: one on top shows incoming requests itself, so no prompt. */
    val openLinkScreens: StateFlow<Int> = _openLinkScreens.asStateFlow()

    fun dismissRequest(sessionId: HexKey) = _dismissedRequests.update { it.add(sessionId) }

    fun dismissSignIn(slot: HexKey) = _dismissedSignIns.update { it.add(slot) }

    fun dismissUnlinked() {
        _unlinkedDismissed.value = true
    }

    fun linkScreenOpened() = _openLinkScreens.update { it + 1 }

    fun linkScreenClosed() = _openLinkScreens.update { (it - 1).coerceAtLeast(0) }

    /** What the app should prompt for, as [snapshots] and what was dismissed change. */
    fun attention(snapshots: Flow<MarmotDevicesSnapshot>): Flow<DeviceGroupAttention> =
        combine(snapshots, _dismissedRequests, _dismissedSignIns, _unlinkedDismissed, _openLinkScreens) { snapshot, requests, signIns, unlinked, screens ->
            DeviceGroupAttention.of(snapshot, requests, signIns, unlinked, screens > 0)
        }
}
