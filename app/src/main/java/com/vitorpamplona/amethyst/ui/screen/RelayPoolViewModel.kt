package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.service.relays.RelayPool

@Stable
class RelayPoolViewModel : ViewModel() {
    val connectionStatus = RelayPool.live.map {
        val connectedRelays = it.relays.connectedRelays()
        val availableRelays = it.relays.availableRelays()
        "$connectedRelays/$availableRelays"
    }.distinctUntilChanged()

    val isConnected = RelayPool.live.map {
        it.relays.connectedRelays() > 0
    }
}
