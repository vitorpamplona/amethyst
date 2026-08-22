/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.accountTransfer

import com.vitorpamplona.amethyst.commons.model.account.transfer.MAX_ARCHIVED_MESSAGES_PER_GROUP
import com.vitorpamplona.amethyst.model.marmot.AndroidMarmotMessageStore
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.utils.Log
import java.io.File

/**
 * Carries Marmot (MLS) conversation history to a new device — and nothing else
 * from Marmot.
 *
 * The split is the whole point. `mls_groups/<id>/state`, `retained` and
 * `marmot_keypackages` are MLS crypto state: cloning a ratchet onto a second
 * device defeats the forward secrecy it exists to provide, and two devices
 * sharing one leaf can reuse a key at the same epoch. Those stay behind, and
 * the new phone rejoins as a new member — which, by MLS's design, cannot
 * decrypt anything sent before it joined. `mls_groups/<id>/messages` is the
 * other half: already-decrypted plaintext the user holds anyway, so copying it
 * costs the protocol nothing and is the only way the past conversation survives
 * the move.
 *
 * Read and written through [AndroidMarmotMessageStore] rather than copied as
 * bytes, because the file is sealed with an Android Keystore key that cannot
 * leave the device — a byte copy would arrive undecryptable.
 */
object MarmotArchiveTransfer {
    /** Every group's archive for [npub], keyed by hex group id. */
    suspend fun export(
        rootFilesDir: File,
        npub: String,
    ): Map<String, List<String>> {
        val accountDir = accountDir(rootFilesDir, npub) ?: return emptyMap()
        val store = AndroidMarmotMessageStore(accountDir)

        return groupIds(accountDir)
            .mapNotNull { groupId ->
                try {
                    val messages = store.loadMessages(groupId)
                    if (messages.isEmpty()) {
                        null
                    } else {
                        groupId to messages.take(MAX_ARCHIVED_MESSAGES_PER_GROUP)
                    }
                } catch (e: Exception) {
                    // One unreadable group shouldn't cost the user every other
                    // conversation in the export.
                    Log.w("MarmotArchive") { "Skipping unreadable Marmot archive for $groupId: ${e.message}" }
                    null
                }
            }.toMap()
    }

    /**
     * Restores archived conversations for [npub].
     *
     * Appends rather than replaces, which the store documents as idempotent, so
     * importing twice — or importing onto a device that already rejoined and
     * caught up — does not duplicate history.
     */
    suspend fun import(
        rootFilesDir: File,
        npub: String,
        archives: Map<String, List<String>>,
    ) {
        if (archives.isEmpty()) return

        val accountDir = accountDir(rootFilesDir, npub) ?: return
        accountDir.mkdirs()
        val store = AndroidMarmotMessageStore(accountDir)

        archives.forEach { (groupId, messages) ->
            // The store itself requires hex (it builds a directory name from
            // this), but fail here rather than on an exception deep inside it.
            if (!groupId.matches(HEX)) {
                Log.w("MarmotArchive") { "Refusing a Marmot archive whose group id is not hex" }
                return@forEach
            }

            try {
                messages.take(MAX_ARCHIVED_MESSAGES_PER_GROUP).forEach { store.appendMessage(groupId, it) }
            } catch (e: Exception) {
                Log.w("MarmotArchive") { "Could not restore the Marmot archive for $groupId: ${e.message}" }
            }
        }
    }

    /**
     * Where AccountCacheState keeps this account's Marmot data. Keyed by pubkey
     * HEX, while the transfer bundle is keyed by npub, so the npub is decoded
     * rather than used directly.
     */
    private fun accountDir(
        rootFilesDir: File,
        npub: String,
    ): File? {
        val pubKeyHex = decodePublicKeyAsHexOrNull(npub) ?: return null
        return File(rootFilesDir, "accounts/$pubKeyHex")
    }

    /** Hex group ids that have a directory under this account. */
    private fun groupIds(accountDir: File): List<String> {
        val groups = File(accountDir, "mls_groups")
        return groups.list()?.filter { it.matches(HEX) } ?: emptyList()
    }

    private val HEX = Regex("^[0-9a-fA-F]+$")
}
