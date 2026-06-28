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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.authoring

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.quartz.podcasts.PodcastValue
import com.vitorpamplona.quartz.podcasts.PodcastValueRecipient

/**
 * Editable state for a Podcasting-2.0 value-for-value (V4V) split, shared by the show and episode
 * composers. Holds one [RecipientDraft] per payee; [toPodcastValue] turns the drafts back into a
 * [PodcastValue] on save (or null when there are no payable recipients). The suggested amount /
 * currency / enabled flag on a loaded block are carried through untouched — the editor only manages
 * the recipient list.
 */
class V4VSplitEditorState {
    val recipients = mutableStateListOf<RecipientDraft>()

    private var amount: Long? = null
    private var currency: String? = null
    private var enabled: Boolean? = null

    fun load(value: PodcastValue?) {
        recipients.clear()
        amount = value?.amount
        currency = value?.currency
        enabled = value?.enabled
        value?.recipients?.forEach { recipients.add(RecipientDraft.from(it)) }
    }

    fun add() {
        recipients.add(RecipientDraft())
    }

    fun remove(draft: RecipientDraft) {
        recipients.remove(draft)
    }

    /** Sum of the weights of the payable recipients — used to show each as a percentage. */
    fun totalSplit(): Int = recipients.mapNotNull { it.toRecipient() }.sumOf { it.split }

    fun toPodcastValue(): PodcastValue? {
        val valid = recipients.mapNotNull { it.toRecipient() }
        if (valid.isEmpty()) return null
        return PodcastValue(
            enabled = enabled,
            amount = amount,
            currency = currency,
            recipients = valid,
        )
    }
}

/** One editable recipient row. All fields are Compose state so the editor recomposes as they change. */
class RecipientDraft {
    val name = mutableStateOf("")

    /** false = lnaddress (LNURL-pay), true = node (keysend to a raw node pubkey). */
    val isNode = mutableStateOf(false)
    val address = mutableStateOf("")
    val split = mutableStateOf("1")
    val fee = mutableStateOf(false)

    /** A recipient is payable once it has an address and a positive weight. */
    fun toRecipient(): PodcastValueRecipient? {
        val addr = address.value.trim()
        if (addr.isBlank()) return null
        val weight = split.value.trim().toIntOrNull() ?: return null
        if (weight <= 0) return null
        return PodcastValueRecipient(
            name = name.value.trim().ifBlank { null },
            type = if (isNode.value) PodcastValue.TYPE_NODE else PodcastValue.TYPE_LNADDRESS,
            address = addr,
            split = weight,
            fee = if (fee.value) true else null,
        )
    }

    companion object {
        fun from(recipient: PodcastValueRecipient): RecipientDraft =
            RecipientDraft().apply {
                name.value = recipient.name.orEmpty()
                isNode.value = recipient.type == PodcastValue.TYPE_NODE
                address.value = recipient.address.orEmpty()
                split.value = recipient.split.toString()
                fee.value = recipient.fee == true
            }
    }
}
