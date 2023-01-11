package com.vitorpamplona.amethyst.ui.screen

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.service.relays.RelayPool

class RelayPoolViewModel: ViewModel() {
  val relayPoolLiveData: LiveData<String> = Transformations.map(RelayPool.live) {
    it.relays.report()
  }
}