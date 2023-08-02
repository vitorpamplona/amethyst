package com.vitorpamplona.amethyst.model

data class Nip47URI(val pubKeyHex: HexKey, val relayUri: String?, val secret: HexKey?)
