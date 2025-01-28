/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip01Core

import androidx.compose.runtime.Stable
import com.fasterxml.jackson.annotation.JsonProperty
import com.vitorpamplona.quartz.nip02FollowList.ImmutableListOfLists

@Stable
class UserMetadata {
    var name: String? = null

    @Deprecated("Use name instead", replaceWith = ReplaceWith("name"))
    var username: String? = null

    @JsonProperty("display_name")
    var displayName: String? = null
    var picture: String? = null
    var banner: String? = null
    var website: String? = null
    var about: String? = null
    var bot: Boolean? = null
    var pronouns: String? = null

    var nip05: String? = null
    var nip05Verified: Boolean = false
    var nip05LastVerificationTime: Long? = 0

    var domain: String? = null
    var lud06: String? = null
    var lud16: String? = null

    var twitter: String? = null

    @Transient
    var tags: ImmutableListOfLists<String>? = null

    fun anyName(): String? = displayName ?: name ?: username

    fun anyNameStartsWith(prefix: String): Boolean =
        listOfNotNull(name, username, displayName, nip05, lud06, lud16).any {
            it.contains(prefix, true)
        }

    fun lnAddress(): String? = lud16 ?: lud06

    fun bestName(): String? = displayName ?: name ?: username

    fun nip05(): String? = nip05

    fun profilePicture(): String? = picture

    fun cleanBlankNames() {
        if (pronouns == "null") pronouns = null

        if (picture?.isNotEmpty() == true) picture = picture?.trim()
        if (nip05?.isNotEmpty() == true) nip05 = nip05?.trim()
        if (displayName?.isNotEmpty() == true) displayName = displayName?.trim()
        if (name?.isNotEmpty() == true) name = name?.trim()
        if (username?.isNotEmpty() == true) username = username?.trim()
        if (lud06?.isNotEmpty() == true) lud06 = lud06?.trim()
        if (lud16?.isNotEmpty() == true) lud16 = lud16?.trim()
        if (pronouns?.isNotEmpty() == true) pronouns = pronouns?.trim()

        if (banner?.isNotEmpty() == true) banner = banner?.trim()
        if (website?.isNotEmpty() == true) website = website?.trim()
        if (domain?.isNotEmpty() == true) domain = domain?.trim()

        if (picture?.isBlank() == true) picture = null
        if (nip05?.isBlank() == true) nip05 = null
        if (displayName?.isBlank() == true) displayName = null
        if (name?.isBlank() == true) name = null
        if (username?.isBlank() == true) username = null
        if (lud06?.isBlank() == true) lud06 = null
        if (lud16?.isBlank() == true) lud16 = null

        if (banner?.isBlank() == true) banner = null
        if (website?.isBlank() == true) website = null
        if (domain?.isBlank() == true) domain = null
        if (pronouns?.isBlank() == true) pronouns = null
    }
}
