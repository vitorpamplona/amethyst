package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey

interface EventInterface {
  fun id(): HexKey

  fun pubKey(): HexKey

  fun createdAt(): Long

  fun kind(): Int

  fun tags(): List<List<String>>

  fun content(): String

  fun sig(): HexKey

  fun toJson(): String

  fun checkSignature()

  fun hasValidSignature(): Boolean

  fun isTaggedUser(loggedInUser: String): Boolean
}
