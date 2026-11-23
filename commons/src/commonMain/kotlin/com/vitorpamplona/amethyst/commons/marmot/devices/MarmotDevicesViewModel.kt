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
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceGroupSwitch
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevices
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevicesSnapshot
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/** A device-group action that failed, for a notice the UI words. */
enum class DeviceActionFailure {
    SWITCH,

    /**
     * The switch cannot go off while this device is linked: the next launch would show the device
     * group as a chat and the siblings would keep this device. It signs out (logs out) first.
     */
    SWITCH_WHILE_LINKED,
    REMOVE,
    RENAME,
    SIGN_IN,
    LINK,
}

/**
 * The devices screen and the app-wide device prompts of one account: the roster and the pending
 * link requests come from [MarmotDevices.state]; the switch from [DeviceGroupSwitch].
 *
 * [devices] is null while device groups are off for this run, and [switch] is null on a front end
 * that keeps no switch. The switch is read when the account is built, so [switchOn] and
 * [runtimeOn] differ until the next launch: the screen says which way it will go.
 */
class MarmotDevicesViewModel(
    private val devices: MarmotDevices?,
    private val switch: DeviceGroupSwitch?,
    /** This account can sign, so it can link devices at all. */
    val canSign: Boolean,
) : ViewModel() {
    constructor(account: Account) : this(account.marmotDevices, account.marmotDeviceGroupSwitch, account.isWriteable())

    /** Device groups run in this launch. */
    val runtimeOn: Boolean = devices != null

    /** Whether the switch can be flipped here at all. */
    val switchAvailable: Boolean = switch != null

    private val _switchOn = MutableStateFlow(readSwitch())

    /** The switch as saved: what the next launch runs. */
    val switchOn: StateFlow<Boolean> = _switchOn.asStateFlow()

    val snapshot: StateFlow<MarmotDevicesSnapshot> = devices?.state ?: MutableStateFlow(MarmotDevicesSnapshot()).asStateFlow()

    private val _failure = MutableStateFlow<DeviceActionFailure?>(null)
    val failure: StateFlow<DeviceActionFailure?> = _failure.asStateFlow()

    private fun readSwitch(): Boolean =
        try {
            switch?.isEnabled() ?: runtimeOn
        } catch (e: Exception) {
            Log.w(TAG, "could not read the device-group switch", e)
            runtimeOn
        }

    fun setSwitch(enabled: Boolean) {
        val target = switch ?: return
        if (!enabled && snapshot.value.linked) {
            _failure.value = DeviceActionFailure.SWITCH_WHILE_LINKED
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                target.setEnabled(enabled)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "could not change the device-group switch", e)
                _failure.value = DeviceActionFailure.SWITCH
            }
            _switchOn.value = readSwitch()
        }
    }

    fun removeDevice(slot: HexKey) = run(DeviceActionFailure.REMOVE) { check(it.removeDevice(slot)) { "No relay confirmed the removal" } }

    /** Renames the device in [slot], this one included, on every device of the account. */
    fun renameDevice(
        slot: HexKey,
        label: String,
    ) = run(DeviceActionFailure.RENAME) { check(it.renameDevice(slot, label)) { "No relay confirmed the rename" } }

    /** "That was me": the sign-in in [slot] stays listed as this user's and is never asked about again. */
    fun acceptSignIn(slot: HexKey) = run(DeviceActionFailure.SIGN_IN) { check(it.acceptSignIn(slot)) { "Could not save the answer" } }

    /** "Not me": records that the sign-in in [slot] was not this user. */
    fun rejectSignIn(slot: HexKey) = run(DeviceActionFailure.SIGN_IN) { it.rejectSignIn(slot) }

    /** "They don't match", or "Ignore" on the prompt. */
    fun rejectLink(sessionId: HexKey) = run(DeviceActionFailure.LINK) { it.rejectLink(sessionId) }

    fun clearFailure() {
        _failure.value = null
    }

    private fun run(
        failure: DeviceActionFailure,
        block: suspend (MarmotDevices) -> Unit,
    ) {
        val d = devices ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block(d)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "device-group action failed: $failure", e)
                _failure.value = failure
            }
        }
    }

    class Factory(
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = MarmotDevicesViewModel(account) as T
    }

    companion object {
        private const val TAG = "MarmotDevicesViewModel"
    }
}
