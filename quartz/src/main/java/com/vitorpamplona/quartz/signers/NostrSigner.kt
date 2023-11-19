package com.vitorpamplona.quartz.signers

import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventFactory
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class NostrSigner(val pubKey: HexKey) {

    abstract fun <T: Event> sign(createdAt: Long, kind: Int, tags: List<List<String>>, content: String, onReady: (T) -> Unit)

    abstract fun nip04Encrypt(decryptedContent: String, toPublicKey: HexKey, onReady: (String)-> Unit)
    abstract fun nip04Decrypt(encryptedContent: String, fromPublicKey: HexKey, onReady: (String)-> Unit)

    abstract fun nip44Encrypt(decryptedContent: String, toPublicKey: HexKey, onReady: (String)-> Unit)
    abstract fun nip44Decrypt(encryptedContent: String, fromPublicKey: HexKey, onReady: (String)-> Unit)

    abstract fun decryptZapEvent(event: LnZapRequestEvent, onReady: (Event)-> Unit)
}