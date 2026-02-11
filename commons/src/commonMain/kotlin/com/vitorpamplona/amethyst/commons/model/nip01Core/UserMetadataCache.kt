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
package com.vitorpamplona.amethyst.commons.model.nip01Core

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip39ExtIdentities.IdentityClaimTag
import com.vitorpamplona.quartz.nip39ExtIdentities.identityClaims
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.containsAny
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@Stable
class UserInfo(
    val info: UserMetadata,
    val tags: ImmutableListOfLists<String>,
    val identities: List<IdentityClaimTag>,
    val createdAt: Long,
)

@Stable
class UserMetadataCache {
    val flow: MutableStateFlow<UserInfo?> = MutableStateFlow(null)

    fun newMetadata(
        userInfo: UserMetadata,
        metaEvent: MetadataEvent,
    ) {
        flow.update {
            UserInfo(
                info = userInfo,
                tags = metaEvent.tags.toImmutableListOfLists(),
                identities = metaEvent.identityClaims(),
                createdAt = metaEvent.createdAt,
            )
        }
    }

    fun shouldUpdateWith(event: MetadataEvent) = event.createdAt > (flow.value?.createdAt ?: 0)

    fun anyNameStartsWith(username: String): Boolean = flow.value?.info?.anyNameStartsWith(username) ?: false

    fun containsAny(hiddenWordsCase: List<DualCase>): Boolean {
        if (hiddenWordsCase.isEmpty()) return false

        flow.value?.let { userInfo ->
            val info = userInfo.info

            if (info.name?.containsAny(hiddenWordsCase) == true) {
                return true
            }

            if (info.displayName?.containsAny(hiddenWordsCase) == true) {
                return true
            }

            if (info.picture?.containsAny(hiddenWordsCase) == true) {
                return true
            }

            if (info.banner?.containsAny(hiddenWordsCase) == true) {
                return true
            }

            if (info.about?.containsAny(hiddenWordsCase) == true) {
                return true
            }

            if (info.lud06?.containsAny(hiddenWordsCase) == true) {
                return true
            }

            if (info.lud16?.containsAny(hiddenWordsCase) == true) {
                return true
            }

            if (info.nip05?.containsAny(hiddenWordsCase) == true) {
                return true
            }
        }

        return false
    }

    fun bestName(): String? = flow.value?.info?.bestName()

    fun nip05(): String? = flow.value?.info?.nip05

    fun profilePicture(): String? = flow.value?.info?.picture

    fun lnAddress(): String? = flow.value?.info?.lnAddress()
}
