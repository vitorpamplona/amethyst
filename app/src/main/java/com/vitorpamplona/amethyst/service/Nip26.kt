package com.vitorpamplona.amethyst.service

object Nip26 {
    fun toTags(token: String, signature: String, hexKey: String): List<String> {
        val keys = token.split(":")

        return listOf(keys[1], hexKey, keys[3], signature)
    }
}
