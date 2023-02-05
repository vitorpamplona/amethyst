package com.vitorpamplona.amethyst.service.relays

import nostr.postr.JsonFilter

class TypedFilter(
  val types: Set<FeedType>,
  val filter: JsonFilter
)