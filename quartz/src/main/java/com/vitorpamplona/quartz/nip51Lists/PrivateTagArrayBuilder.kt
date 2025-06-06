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
package com.vitorpamplona.quartz.nip51Lists

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent

class PrivateTagArrayBuilder {
    companion object {
        fun create(
            tags: Array<Array<String>>,
            toPrivate: Boolean,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            if (toPrivate) {
                PrivateTagsInContent.encryptNip04(
                    privateTags = tags,
                    signer = signer,
                ) { encryptedTags ->
                    onReady(encryptedTags, arrayOf())
                }
            } else {
                onReady("", tags)
            }
        }

        fun add(
            current: PrivateTagArrayEvent,
            newTag: Array<String>,
            toPrivate: Boolean,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            if (toPrivate) {
                current.privateTags(signer) { privateTags ->
                    PrivateTagsInContent.encryptNip04(
                        privateTags = privateTags.plus(newTag),
                        signer = signer,
                    ) { encryptedTags ->
                        onReady(encryptedTags, current.tags)
                    }
                }
            } else {
                onReady(current.content, current.tags.plus(newTag))
            }
        }

        fun addAll(
            current: PrivateTagArrayEvent,
            newTag: Array<Array<String>>,
            toPrivate: Boolean,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            if (toPrivate) {
                current.privateTags(signer) { privateTags ->
                    PrivateTagsInContent.encryptNip04(
                        privateTags = privateTags.plus(newTag),
                        signer = signer,
                    ) { encryptedTags ->
                        onReady(encryptedTags, current.tags)
                    }
                }
            } else {
                onReady(current.content, current.tags.plus(newTag))
            }
        }

        fun replaceAllToPrivateNewTag(
            dTag: String,
            current: PrivateTagArrayEvent?,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            if (current == null) {
                createPrivate(dTag, newTag, signer, onReady)
            } else {
                replaceAllToPrivateNewTag(current, oldTagStartsWith, newTag, signer, onReady)
            }
        }

        fun replaceAllToPublicNewTag(
            dTag: String,
            current: PrivateTagArrayEvent?,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            if (current == null) {
                createPublic(dTag, newTag, signer, onReady)
            } else {
                replaceAllToPublicNewTag(current, oldTagStartsWith, newTag, signer, onReady)
            }
        }

        fun replaceAllToPrivateNewTag(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            current.privateTags(signer) { privateTags ->
                PrivateTagsInContent.encryptNip04(
                    privateTags = privateTags.replaceAll(oldTagStartsWith, newTag),
                    signer = signer,
                ) { encryptedTags ->
                    onReady(encryptedTags, current.tags.remove(oldTagStartsWith))
                }
            }
        }

        fun replaceAllToPublicNewTag(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            current.privateTags(signer) { privateTags ->
                PrivateTagsInContent.encryptNip04(
                    privateTags = privateTags.remove(oldTagStartsWith),
                    signer = signer,
                ) { encryptedTags ->
                    onReady(encryptedTags, current.tags.remove(oldTagStartsWith).plus(newTag))
                }
            }
        }

        fun removeAllFromPrivate(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            current.privateTags(signer) { privateTags ->
                PrivateTagsInContent.encryptNip04(
                    privateTags = privateTags.remove(oldTagStartsWith),
                    signer = signer,
                ) { encryptedTags ->
                    onReady(encryptedTags, current.tags)
                }
            }
        }

        fun removeAllFromPublic(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) = onReady(current.content, current.tags.remove(oldTagStartsWith))

        fun removeAll(
            current: PrivateTagArrayEvent,
            oldTagStartsWith: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            current.privateTags(signer) { privateTags ->
                PrivateTagsInContent.encryptNip04(
                    privateTags = privateTags.remove(oldTagStartsWith),
                    signer = signer,
                ) { encryptedTags ->
                    onReady(encryptedTags, current.tags.remove(oldTagStartsWith))
                }
            }
        }

        fun createPrivate(
            dTag: String,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            PrivateTagsInContent.encryptNip04(
                privateTags = arrayOf(newTag),
                signer = signer,
            ) { encryptedTags ->
                onReady(encryptedTags, arrayOf(arrayOf("d", dTag)))
            }
        }

        fun createPublic(
            dTag: String,
            newTag: Array<String>,
            signer: NostrSigner,
            onReady: (content: String, tags: Array<Array<String>>) -> Unit,
        ) {
            onReady("", arrayOf(arrayOf("d", dTag), newTag))
        }
    }
}
