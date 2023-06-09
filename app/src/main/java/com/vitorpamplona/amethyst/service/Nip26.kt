package com.vitorpamplona.amethyst.service

object Nip26 {
    fun toTags(token: String, signature: String, hexKey: String): List<String> {
        val keys = token.split(":")

        return listOf(keys[1], hexKey, keys[3], signature)
    }

    fun isValidDelegation(token: String): Boolean {
        val keys = token.split(":")

        if (keys.size != 4 || keys[0] != "nostr" || keys[1] != "delegation") {
            return false
        }
        val delegatee = keys[2]
        if (delegatee.length != 64) {
            return false
        }
        val params = keys[3]
        val paramsSplit = params.split("&")
        if (paramsSplit.isEmpty()) return false
        for (param in paramsSplit) {
            if (!paramsSplit.contains("kind")) return false
            if (!paramsSplit.contains("created_at")) return false
            val paramSplit = param.split(Regex("[<>=]"))
            if (paramSplit.isEmpty()) return false
            when (paramSplit[0]) {
                "created_at" -> {
                    val op = param.substring(10, 11)
                    if (op != "<" && op != ">") {
                        return false
                    }
                }
                else -> {
                    if (paramSplit[0] != "kind") {
                        return false
                    }
                }
            }
        }
        return true
    }
}
