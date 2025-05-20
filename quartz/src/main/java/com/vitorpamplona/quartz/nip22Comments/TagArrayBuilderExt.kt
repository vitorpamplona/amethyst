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
package com.vitorpamplona.quartz.nip22Comments

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyIdentifierTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyKindTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootIdentifierTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootKindTag
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId

fun TagArrayBuilder<CommentEvent>.rootAddress(
    addressId: String,
    relayHint: String?,
) = addUnique(RootAddressTag.assemble(addressId, relayHint))

fun TagArrayBuilder<CommentEvent>.rootEvent(
    eventId: String,
    relayHint: String?,
    pubkey: String?,
) = addUnique(RootEventTag.assemble(eventId, relayHint, pubkey))

fun TagArrayBuilder<CommentEvent>.rootExternalIdentity(id: ExternalId) = add(RootIdentifierTag.assemble(id))

fun TagArrayBuilder<CommentEvent>.rootExternalIdentities(ids: List<ExternalId>) = addAll(ids.map { RootIdentifierTag.assemble(it) })

fun TagArrayBuilder<CommentEvent>.rootKind(kind: String) = addUnique(RootKindTag.assemble(kind))

fun TagArrayBuilder<CommentEvent>.rootKind(kind: Int) = addUnique(RootKindTag.assemble(kind))

fun TagArrayBuilder<CommentEvent>.rootKind(id: ExternalId) = addUnique(RootKindTag.assemble(id))

fun TagArrayBuilder<CommentEvent>.rootAuthor(
    pubKey: HexKey,
    relay: String?,
) = add(RootAuthorTag.assemble(pubKey, relay))

fun TagArrayBuilder<CommentEvent>.replyAddress(
    addressId: String,
    relayHint: String?,
) = addUnique(ReplyAddressTag.assemble(addressId, relayHint))

fun TagArrayBuilder<CommentEvent>.replyEvent(
    eventId: String,
    relayHint: String?,
    pubkey: String?,
) = addUnique(ReplyEventTag.assemble(eventId, relayHint, pubkey))

fun TagArrayBuilder<CommentEvent>.replyExternalIdentity(id: ExternalId) = add(ReplyIdentifierTag.assemble(id))

fun TagArrayBuilder<CommentEvent>.replyExternalIdentities(ids: List<ExternalId>) = addAll(ids.map { ReplyIdentifierTag.assemble(it) })

fun TagArrayBuilder<CommentEvent>.replyKind(kind: String) = addUnique(ReplyKindTag.assemble(kind))

fun TagArrayBuilder<CommentEvent>.replyKind(kind: Int) = addUnique(ReplyKindTag.assemble(kind))

fun TagArrayBuilder<CommentEvent>.replyKind(id: ExternalId) = addUnique(ReplyKindTag.assemble(id))

fun TagArrayBuilder<CommentEvent>.replyAuthor(
    pubKey: HexKey,
    relay: String?,
) = add(ReplyAuthorTag.assemble(pubKey, relay))

fun TagArrayBuilder<CommentEvent>.notify(list: List<PTag>) = pTags(list)

fun TagArrayBuilder<CommentEvent>.notify(pubkey: PTag) = pTag(pubkey)
