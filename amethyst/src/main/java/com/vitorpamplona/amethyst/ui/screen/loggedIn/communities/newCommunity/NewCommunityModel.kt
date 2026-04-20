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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.newCommunity

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.ModeratorTag
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.RelayTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Stable
class NewCommunityModel : ViewModel() {
    var account: Account? = null

    var isPublishing by mutableStateOf(false)

    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var imageUrl by mutableStateOf("")
    var rules by mutableStateOf("")
    var moderatorsText by mutableStateOf("")
    var relayRequestsUrl by mutableStateOf("")
    var relayApprovalsUrl by mutableStateOf("")

    fun init(account: Account) {
        if (this.account == account) return
        this.account = account
    }

    fun canPost(): Boolean =
        !isPublishing &&
            name.isNotBlank() &&
            description.isNotBlank()

    @OptIn(ExperimentalUuidApi::class)
    fun publish(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) = try {
        publishUnsafe(context, onSuccess, onError)
    } catch (e: SignerExceptions.ReadOnlyException) {
        onError(
            stringRes(context, R.string.read_only_user),
            stringRes(context, R.string.login_with_a_private_key_to_be_able_to_sign_events),
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun publishUnsafe(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        val myAccount = account ?: return

        viewModelScope.launch(Dispatchers.IO) {
            isPublishing = true
            try {
                val moderatorTags = parseModeratorTags(myAccount)
                val relayTags = parseRelayTags()

                val definition =
                    myAccount.sendCommunityDefinition(
                        name = name.trim(),
                        description = description.trim(),
                        moderators = moderatorTags,
                        image = imageUrl.trim().ifBlank { null },
                        rules = rules.trim().ifBlank { null },
                        relays = relayTags.ifEmpty { null },
                        dTag = Uuid.random().toString(),
                    )

                if (definition == null) {
                    onError(
                        stringRes(context, R.string.read_only_user),
                        stringRes(context, R.string.login_with_a_private_key_to_be_able_to_sign_events),
                    )
                    return@launch
                }

                // Also follow the community so it appears on the user's list.
                val communityNote = myAccount.cache.getOrCreateAddressableNote(definition.address())
                myAccount.follow(communityNote)

                reset()
                onSuccess()
            } finally {
                isPublishing = false
            }
        }
    }

    private fun parseModeratorTags(account: Account): List<ModeratorTag> {
        val hexes =
            moderatorsText
                .lineSequence()
                .map { it.trim() }
                .filter { it.length == 64 && it.all { ch -> ch.isDigit() || (ch in 'a'..'f') || (ch in 'A'..'F') } }
                .map { it.lowercase() }
                .toSet()

        val withOwner = hexes + account.signer.pubKey

        return withOwner.map { ModeratorTag(it, null, "moderator") }
    }

    private fun parseRelayTags(): List<RelayTag> {
        val tags = mutableListOf<RelayTag>()
        relayRequestsUrl.trim().takeIf { it.isNotEmpty() }?.let {
            RelayUrlNormalizer.normalizeOrNull(it)?.let { url ->
                tags += RelayTag(url, RelayTag.MARKER_REQUESTS)
            }
        }
        relayApprovalsUrl.trim().takeIf { it.isNotEmpty() }?.let {
            RelayUrlNormalizer.normalizeOrNull(it)?.let { url ->
                tags += RelayTag(url, RelayTag.MARKER_APPROVALS)
            }
        }
        return tags
    }

    fun reset() {
        name = ""
        description = ""
        imageUrl = ""
        rules = ""
        moderatorsText = ""
        relayRequestsUrl = ""
        relayApprovalsUrl = ""
    }
}
