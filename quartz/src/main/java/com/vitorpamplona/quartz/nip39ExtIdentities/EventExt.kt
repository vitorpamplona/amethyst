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
package com.vitorpamplona.quartz.nip39ExtIdentities

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.mapTagged
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent

fun MetadataEvent.identityClaims() =
    tags.mapTagged("i") {
        try {
            IdentityClaim.create(it[1], it[2])
        } catch (e: Exception) {
            Log.e("MetadataEvent", "Can't parse identity [${it.joinToString { "," }}]", e)
            null
        }
    }

fun MetadataEvent.updateClaims(
    twitter: String?,
    mastodon: String?,
    github: String?,
): TagArray {
    var claims = identityClaims()

    // null leave as is. blank deletes it.
    if (twitter != null) {
        // delete twitter
        claims = claims.filter { it !is TwitterIdentity }
        if (twitter.isNotBlank()) {
            TwitterIdentity.parseProofUrl(twitter)?.let { claims = claims + it }
        }
    }

    // null leave as is. blank deletes it.
    if (github != null) {
        // delete github
        claims = claims.filter { it !is GitHubIdentity }
        if (github.isNotBlank()) {
            GitHubIdentity.parseProofUrl(github)?.let { claims = claims + it }
        }
    }

    // null leave as is. blank deletes it.
    if (mastodon != null) {
        claims = claims.filter { it !is MastodonIdentity }
        if (mastodon.isNotBlank()) {
            MastodonIdentity.parseProofUrl(mastodon)?.let { claims = claims + it }
        }
    }

    return claims.map { arrayOf("i", it.platformIdentity(), it.proof) }.toTypedArray()
}
