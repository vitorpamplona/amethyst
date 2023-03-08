package com.vitorpamplona.amethyst.service.nip19

enum class TlvTypes(val id: Byte) {
    SPECIAL(0),
    RELAY(1),
    AUTHOR(2),
    KIND(3);
}
