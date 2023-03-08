package com.vitorpamplona.amethyst.ui.screen

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.service.relays.RelayPool

class RelayPoolViewModel : ViewModel() {
    val connectedRelaysLiveData: LiveData<Int> = RelayPool.live.map {
        it.relays.connectedRelays()
    }
    val availableRelaysLiveData: LiveData<Int> = RelayPool.live.map {
        it.relays.availableRelays()
    }
}
