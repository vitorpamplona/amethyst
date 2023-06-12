package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.HexKey
import fr.acinq.secp256k1.Hex
import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest

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
        if (!params.contains("kind")) return false
        if (!params.contains("created_at")) return false
        val paramsSplit = params.split("&")
        if (paramsSplit.isEmpty()) return false
        for (param in paramsSplit) {
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

    fun checkSignature(token: List<String>, delegatee: HexKey): Boolean {
        val signature = token[3]
        val delegator = token[1]
        val condition = token[2]
        val delegationString = "nostr:delegation:$delegatee:$condition"
        return Secp256k1.verifySchnorr(
            Hex.decode(signature),
            MessageDigest.getInstance("SHA-256").digest(delegationString.toByteArray()),
            Hex.decode(delegator)
        )
    }

    fun checkSignature(signature: String, delegationString: String, delegator: HexKey): Boolean {
        return Secp256k1.verifySchnorr(
            Hex.decode(signature),
            MessageDigest.getInstance("SHA-256").digest(delegationString.toByteArray()),
            Hex.decode(delegator)
        )
    }
}
