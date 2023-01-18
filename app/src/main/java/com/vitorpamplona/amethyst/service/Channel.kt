package com.vitorpamplona.amethyst.service

import java.util.UUID
import nostr.postr.JsonFilter

data class Channel (
  val id: String = UUID.randomUUID().toString().substring(0,4)
) {
  var filter: List<JsonFilter>? = null // Inactive when null
}