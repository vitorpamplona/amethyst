package com.vitorpamplona.amethyst.ui.note

import nostr.postr.toHex

fun ByteArray.toDisplayHex(): String {
  return toHex().toDisplayHex()
}

fun String.toDisplayHex(): String {
  return replaceRange(6, length-6, ":")
}

