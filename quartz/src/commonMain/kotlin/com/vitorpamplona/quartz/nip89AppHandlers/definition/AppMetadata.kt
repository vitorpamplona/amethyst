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
package com.vitorpamplona.quartz.nip89AppHandlers.definition

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Stable
@Serializable
class AppMetadata {
    var name: String? = null
    var username: String? = null

    @SerialName("display_name")
    var displayName: String? = null
    var picture: String? = null

    var banner: String? = null
    var image: String? = null
    var website: String? = null
    var about: String? = null
    var subscription: Boolean? = false
    var acceptsNutZaps: Boolean? = false
    var supportsEncryption: Boolean? = false
    var personalized: Boolean? = false
    var amount: String? = null

    var nip05: String? = null
    var domain: String? = null
    var lud06: String? = null
    var lud16: String? = null

    fun anyName(): String? = displayName ?: name ?: username

    fun anyNameStartsWith(prefix: String): Boolean =
        listOfNotNull(name, username, displayName, nip05, lud06, lud16).any {
            it.contains(prefix, true)
        }

    fun lnAddress(): String? = lud16 ?: lud06

    fun bestName(): String? = displayName ?: name ?: username

    fun nip05(): String? = nip05

    fun profilePicture(): String? = picture ?: image

    fun cleanBlankNames() {
        if (picture?.isNotEmpty() == true) picture = picture?.trim()
        if (nip05?.isNotEmpty() == true) nip05 = nip05?.trim()

        if (displayName?.isNotEmpty() == true) displayName = displayName?.trim()
        if (name?.isNotEmpty() == true) name = name?.trim()
        if (username?.isNotEmpty() == true) username = username?.trim()
        if (lud06?.isNotEmpty() == true) lud06 = lud06?.trim()
        if (lud16?.isNotEmpty() == true) lud16 = lud16?.trim()

        if (website?.isNotEmpty() == true) website = website?.trim()
        if (domain?.isNotEmpty() == true) domain = domain?.trim()

        if (picture?.isBlank() == true) picture = null
        if (nip05?.isBlank() == true) nip05 = null
        if (displayName?.isBlank() == true) displayName = null
        if (name?.isBlank() == true) name = null
        if (username?.isBlank() == true) username = null
        if (lud06?.isBlank() == true) lud06 = null
        if (lud16?.isBlank() == true) lud16 = null

        if (website?.isBlank() == true) website = null
        if (domain?.isBlank() == true) domain = null
    }

    fun toJson() = assemble(this)

    companion object {
        fun assemble(data: AppMetadata): String = JsonMapper.toJson(data)

        fun parse(content: String): AppMetadata = JsonMapper.fromJson<AppMetadata>(content)
    }
}
