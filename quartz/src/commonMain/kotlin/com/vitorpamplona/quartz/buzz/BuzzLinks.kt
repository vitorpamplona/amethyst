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
package com.vitorpamplona.quartz.buzz

import com.vitorpamplona.quartz.graph.LinkBuilder
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.props.NoProps
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag

/**
 * The channels a Buzz event's `h` tags ([GroupIdTag]) name, as [Relation.GROUP] (or [relation]).
 * Buzz scopes nearly every kind to a channel this way, and a channel id is a NIP-29 group id, so
 * the target is the same `h` value node the NIP-29 kinds link to.
 */
fun LinkBuilder.buzzChannels(
    tags: TagArray,
    relation: Relation<NoProps> = Relation.GROUP,
) = each(tags, GroupIdTag::parse) { tag(relation, GroupIdTag.TAG_NAME, it) }
