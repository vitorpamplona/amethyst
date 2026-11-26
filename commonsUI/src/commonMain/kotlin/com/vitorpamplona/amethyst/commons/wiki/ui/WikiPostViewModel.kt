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
package com.vitorpamplona.amethyst.commons.wiki.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.forks.parseForkedAddress
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiRedirectEvent

/**
 * The wiki editor's state: a NIP-54 article, written in Djot. It starts empty for a new topic, from
 * the account's own version to edit it (same `d` tag, so the new one replaces it), or from another
 * author's version to fork it (the copy carries NIP-54's `a` and `e` tags marked `fork`).
 */
@Stable
class WikiPostViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel

    var title by mutableStateOf(TextFieldValue(""))
    var summary by mutableStateOf(TextFieldValue(""))
    var slug by mutableStateOf("")
    val message = TextFieldState()
    var showPreview by mutableStateOf(false)

    /** The version this one starts from: the account's own (an edit) or someone else's (a fork). */
    var base: WikiArticleEvent? by mutableStateOf(null)
        private set

    /** Whether the slug follows the title: until the author types one, or for good once the topic is set. */
    private var slugFollowsTitle = true

    val isFork: Boolean get() = base?.let { it.pubKey != accountViewModel.account.signer.pubKey } == true
    val isEdit: Boolean get() = base?.pubKey == accountViewModel.account.signer.pubKey

    /** A topic already chosen (a fork, an edit, or "write your version" from its page) keeps its slug. */
    var slugLocked by mutableStateOf(false)
        private set

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
    }

    /** Starts from [topic], and from [baseNote]'s article if there is one. Only once per screen. */
    fun load(
        topic: String?,
        baseNote: AddressableNote?,
    ) {
        val event = baseNote?.event as? WikiArticleEvent
        if (event != null) {
            base = event
            title = TextFieldValue(event.title() ?: "")
            summary = TextFieldValue(event.summary() ?: "")
            message.setTextAndPlaceCursorAtEnd(event.content)
            setSlugLocked(WikiRedirectEvent.normalizeSlug(event.dTag()))
        } else if (!topic.isNullOrBlank()) {
            setSlugLocked(WikiRedirectEvent.normalizeSlug(topic))
            if (title.text.isEmpty()) title = TextFieldValue(topic.replace('-', ' ').replaceFirstChar { it.uppercaseChar() })
        }
    }

    private fun setSlugLocked(value: String) {
        slug = value
        slugFollowsTitle = false
        slugLocked = true
    }

    fun onTitleChanged(value: TextFieldValue) {
        title = value
        if (slugFollowsTitle) slug = WikiRedirectEvent.normalizeSlug(value.text)
    }

    fun onSlugChanged(value: String) {
        slug = value
        slugFollowsTitle = false
    }

    /** The slug as it will be published: normalized, as NIP-54 asks of every `d` tag. */
    fun normalizedSlug() = WikiRedirectEvent.normalizeSlug(slug)

    fun canPost(): Boolean = title.text.isNotBlank() && normalizedSlug().isNotEmpty() && message.text.isNotBlank()

    /** Signs and sends the article; returns its address, or null when it could not be built. */
    suspend fun publish(): Address? {
        val template = template() ?: return null
        val account = accountViewModel.account
        account.signAndComputeBroadcast(template)
        return Address(WikiArticleEvent.KIND, account.signer.pubKey, normalizedSlug())
    }

    private suspend fun template(): EventTemplate<WikiArticleEvent>? {
        if (!canPost()) return null
        val tagger = NewMessageTagger(message.text.toString(), null, null, accountViewModel)
        tagger.run()
        val text = tagger.message
        val emojis = accountViewModel.account.emoji.findEmojiTags(text)
        val forkOf = base?.takeIf { isFork }

        return WikiArticleEvent.build(
            description = text,
            title = title.text.trim(),
            summary = summary.text.trim().ifBlank { null },
            image = base?.image(),
            dTag = normalizedSlug(),
        ) {
            hashtags(findHashtags(text))
            references(findURLs(text))
            quotes(findNostrUris(text))
            emojis(emojis)
            if (forkOf != null) {
                add(forkedAddressTag(forkOf.address()))
                add(MarkedETag.assemble(forkOf.id, null, MarkedETag.MARKER.FORK, forkOf.pubKey))
            }
        }
    }

    companion object {
        /**
         * NIP-54's fork pointer, `["a", <address>, "", "fork"]`: the tag [parseForkedAddress] reads.
         * quartz has the parser but no builder yet; this belongs beside it.
         */
        fun forkedAddressTag(address: Address): Array<String> = arrayOf("a", address.toValue(), "", MarkedETag.MARKER.FORK.code)
    }
}
