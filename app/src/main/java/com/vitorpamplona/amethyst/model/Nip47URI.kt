package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.encoders.HexKey

data class Nip47URI(val pubKeyHex: HexKey, val relayUri: String?, val secret: HexKey?)
