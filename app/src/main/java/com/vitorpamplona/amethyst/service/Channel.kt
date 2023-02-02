package com.vitorpamplona.amethyst.service

import java.util.UUID
import nostr.postr.JsonFilter

data class Channel (
  val id: String = UUID.randomUUID().toString().substring(0,4),
  val onEOSE: ((Long) -> Unit)? = null
) {
  var filter: List<JsonFilter>? = null // Inactive when null

  fun updateEOSE(l: Long) {
    onEOSE?.let { it(l) }
  }
}