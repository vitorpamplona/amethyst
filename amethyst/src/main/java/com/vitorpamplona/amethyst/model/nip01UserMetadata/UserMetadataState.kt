/**
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
package com.vitorpamplona.amethyst.model.nip01UserMetadata

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User


import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserMetadataState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val user = cache.getOrCreateUser(signer.pubKey)

    // fun getEphemeralChatListAddress() = cache.getOrCreateUser(signer.pubKey)

    fun getUserMetadataFlow(): StateFlow<UserState> = user.flow().metadata.stateFlow

    fun getUserMetadataEvent(): MetadataEvent? = user.latestMetadata

    suspend fun sendNewUserMetadata(
        name: String? = null,
        picture: String? = null,
        banner: String? = null,
        website: String? = null,
        pronouns: String? = null,
        about: String? = null,
        nip05: String? = null,
        lnAddress: String? = null,
        lnURL: String? = null,
        twitter: String? = null,
        mastodon: String? = null,
        github: String? = null,
    ): MetadataEvent {
        val latest = getUserMetadataEvent()

        val template =
            if (latest != null) {
                MetadataEvent.updateFromPast(
                    latest = latest,
                    name = name,
                    displayName = name,
                    picture = picture,
                    banner = banner,
                    website = website,
                    pronouns = pronouns,
                    about = about,
                    nip05 = nip05,
                    lnAddress = lnAddress,
                    lnURL = lnURL,
                    twitter = twitter,
                    mastodon = mastodon,
                    github = github,
                )
            } else {
                MetadataEvent.createNew(
                    name = name,
                    displayName = name,
                    picture = picture,
                    banner = banner,
                    website = website,
                    pronouns = pronouns,
                    about = about,
                    nip05 = nip05,
                    lnAddress = lnAddress,
                    lnURL = lnURL,
                    twitter = twitter,
                    mastodon = mastodon,
                    github = github,
                )
            }

        return signer.sign(template)
    }

    init {
        settings.backupUserMetadata?.let {
            Log.d("AccountRegisterObservers", "Loading saved user metadata ${it.toJson()}")

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        // saves contact list for the next time.
        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Kind 0 Collector Start")
            getUserMetadataFlow().collect {
                Log.d("AccountRegisterObservers", "Updating Kind 0 ${it.user.toBestDisplayName()}")
                settings.updateUserMetadata(it.user.latestMetadata)
            }
        }
    }
}
