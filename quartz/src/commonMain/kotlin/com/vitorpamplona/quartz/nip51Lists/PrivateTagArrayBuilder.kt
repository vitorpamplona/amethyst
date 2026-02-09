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
package com.vitorpamplona.quartz.nip51Lists

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent

class PrivateTagArrayBuilder {
    companion object {
        suspend fun create(
            tags: Array<Array<String>>,
            toPrivate: Boolean,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> =
            if (toPrivate) {
                val encryptedTags =
                    PrivateTagsInContent.encryptNip44(
                        privateTags = tags,
                        signer = signer,
                    )
                Pair(encryptedTags, arrayOf())
            } else {
                Pair("", tags)
            }

        suspend fun add(
            current: PrivateTagArrayEvent,
            newTag: Array<String>,
            toPrivate: Boolean,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> =
            if (toPrivate) {
                val privateTags = current.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                val encryptedTags =
                    PrivateTagsInContent.encryptNip44(
                        privateTags = privateTags.plus(newTag),
                        signer = signer,
                    )
                Pair(encryptedTags, current.tags)
            } else {
                Pair(current.content, current.tags.plus(newTag))
            }

        suspend fun addAll(
            current: PrivateTagArrayEvent,
            newTag: Array<Array<String>>,
            toPrivate: Boolean,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> =
            if (toPrivate) {
                val privateTags = current.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                val encryptedTags =
                    PrivateTagsInContent.encryptNip44(
                        privateTags = privateTags.plus(newTag),
                        signer = signer,
                    )
                Pair(encryptedTags, current.tags)
            } else {
                Pair(current.content, current.tags.plus(newTag))
            }

        suspend fun replaceAllToPrivateNewTag(
            dTag: String,
            current: PrivateTagArrayEvent?,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> =
            if (current == null) {
                createPrivate(dTag, newTag, signer)
            } else {
                replaceAllToPrivateNewTag(current, oldTagStartsWith, newTag, signer)
            }

        suspend fun replaceAllToPublicNewTag(
            dTag: String,
            current: PrivateTagArrayEvent?,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> =
            if (current == null) {
                createPublic(dTag, newTag, signer)
            } else {
                replaceAllToPublicNewTag(current, oldTagStartsWith, newTag, signer)
            }

        suspend fun replaceAllToPrivateNewTag(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> {
            val privateTags = current.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
            val encryptedTags =
                PrivateTagsInContent.encryptNip44(
                    privateTags = privateTags.replaceAll(oldTagStartsWith, newTag),
                    signer = signer,
                )
            return Pair(encryptedTags, current.tags.remove(oldTagStartsWith))
        }

        suspend fun replaceAllToPublicNewTag(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> {
            val privateTags = current.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
            val encryptedTags =
                PrivateTagsInContent.encryptNip44(
                    privateTags = privateTags.remove(oldTagStartsWith),
                    signer = signer,
                )
            return Pair(encryptedTags, current.tags.remove(oldTagStartsWith).plus(newTag))
        }

        suspend fun removeAllFromPrivate(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> {
            val privateTags = current.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
            val encryptedTags =
                PrivateTagsInContent.encryptNip44(
                    privateTags = privateTags.remove(oldTagStartsWith),
                    signer = signer,
                )
            return Pair(encryptedTags, current.tags)
        }

        fun removeAllFromPublic(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> = Pair(current.content, current.tags.remove(oldTagStartsWith))

        suspend fun removeAll(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> {
            val privateTags = current.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
            val encryptedTags =
                PrivateTagsInContent.encryptNip44(
                    privateTags = privateTags.remove(oldTagStartsWith),
                    signer = signer,
                )
            return Pair(encryptedTags, current.tags.remove(oldTagStartsWith))
        }

        suspend fun createPrivate(
            dTag: String,
            newTag: Array<String>,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> {
            val encryptedTags =
                PrivateTagsInContent.encryptNip44(
                    privateTags = arrayOf(newTag),
                    signer = signer,
                )
            return Pair(encryptedTags, arrayOf(arrayOf("d", dTag)))
        }

        fun createPublic(
            dTag: String,
            newTag: Array<String>,
            signer: NostrSigner,
        ): Pair<String, Array<Array<String>>> = Pair("", arrayOf(arrayOf("d", dTag), newTag))
    }
}
