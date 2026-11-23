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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevices
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevicesSnapshot
import com.vitorpamplona.amethyst.commons.model.marmot.devices.NewDeviceLinkState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * One link screen: [role] says whether this device is the new one (it sends the request when the
 * screen opens) or an existing one (it follows [initialSessionId], or the first request that
 * arrives). The seven emoji, both buttons and the outcome all come from [state].
 */
class MarmotDeviceLinkViewModel(
    private val devices: MarmotDevices?,
    val role: DeviceLinkRole,
    initialSessionId: HexKey?,
    private val attention: DeviceGroupAttentionState?,
    /**
     * Where the user's decisions (match, mismatch, cancel) run: the account's scope, because the
     * screen closes right after Cancel and its own scope would take the cancel with it.
     */
    private val decisionScope: CoroutineScope? = null,
    private val clock: () -> Long = { TimeUtils.now() },
) : ViewModel() {
    private val openedAt = clock()
    private val snapshots = devices?.state ?: MutableStateFlow(MarmotDevicesSnapshot())
    private val followed = MutableStateFlow(initialSessionId)
    private val startFailed = MutableStateFlow(false)

    val state: StateFlow<DeviceLinkScreenState> =
        combine(snapshots, followed, startFailed) { snapshot, session, failed ->
            DeviceLinkScreen.state(snapshot, role, session, failed)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            DeviceLinkScreen.state(snapshots.value, role, initialSessionId, false),
        )

    init {
        attention?.linkScreenOpened()
        initialSessionId?.let { attention?.dismissRequest(it) }
        if (role == DeviceLinkRole.NEW_DEVICE) {
            val current = snapshots.value
            val inProgress = current.newDevice.let { it != null && it !is NewDeviceLinkState.Ended && it != NewDeviceLinkState.Linked }
            if (!current.linked && !inProgress) start()
        } else if (initialSessionId == null) {
            // The existing device follows the first request it sees, and keeps following it once it ends.
            viewModelScope.launch {
                val prompt = snapshots.mapNotNull { DeviceLinkScreen.followablePrompt(it.prompts, openedAt) }.first()
                attention?.dismissRequest(prompt.sessionId)
                followed.value = prompt.sessionId
            }
        }
    }

    /** New device: (again) publish a KeyPackage and send the request. */
    fun start() {
        val d = devices ?: return
        startFailed.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                d.startLinkAsNewDevice()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "could not start linking this device", e)
                startFailed.value = true
            }
        }
    }

    /** "They match". The existing device names the new one [label]. */
    fun confirm(label: String) {
        val d = devices ?: return
        launch {
            when (role) {
                DeviceLinkRole.NEW_DEVICE -> d.confirmMatch()
                DeviceLinkRole.EXISTING_DEVICE -> followed.value?.let { d.approveLink(it, label) }
            }
        }
    }

    /** "They don't match". */
    fun reject() {
        val d = devices ?: return
        launch {
            when (role) {
                DeviceLinkRole.NEW_DEVICE -> d.rejectMatch()
                DeviceLinkRole.EXISTING_DEVICE -> followed.value?.let { d.rejectLink(it) }
            }
        }
    }

    fun cancel() {
        val d = devices ?: return
        launch {
            when (role) {
                DeviceLinkRole.NEW_DEVICE -> d.cancelLink()
                DeviceLinkRole.EXISTING_DEVICE -> followed.value?.let { d.rejectLink(it) }
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        (decisionScope ?: viewModelScope).launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "link step failed", e)
            }
        }
    }

    override fun onCleared() {
        attention?.linkScreenClosed()
        super.onCleared()
    }

    class Factory(
        val account: Account,
        val role: DeviceLinkRole,
        val sessionId: HexKey?,
        val attention: DeviceGroupAttentionState?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = MarmotDeviceLinkViewModel(account.marmotDevices, role, sessionId, attention, account.scope) as T
    }

    companion object {
        private const val TAG = "MarmotDeviceLinkVM"
    }
}
