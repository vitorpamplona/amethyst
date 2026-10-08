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
package com.vitorpamplona.quartz.experimental.decoupling.transfer.response

import com.vitorpamplona.quartz.experimental.decoupling.transfer.tags.ClientKeyTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag

fun TagArray.senderClientKey() = firstNotNullOfOrNull(ClientKeyTag::parse)

fun TagArray.recipientKeys() = mapNotNull(PTag::parseKey)

fun TagArray.recipientHints() = mapNotNull(PTag::parseAsHint)

/**
 * The requester's client key: the first `p` that is not [identity]. PsstPsst also tags the identity
 * itself "for account inbox routing"; other clients tag only the requester's client key.
 */
fun TagArray.requesterClientKey(identity: HexKey) = firstNotNullOfOrNull { tag -> PTag.parseKey(tag)?.takeIf { it != identity } }
