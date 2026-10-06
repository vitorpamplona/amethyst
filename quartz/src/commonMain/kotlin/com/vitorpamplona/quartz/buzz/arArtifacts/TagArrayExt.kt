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
package com.vitorpamplona.quartz.buzz.arArtifacts

import com.vitorpamplona.quartz.buzz.arArtifacts.tags.ArtifactOp
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.OpTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.PrevTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.ReasonTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.RootTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.TitleTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.TypeTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.VersionTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag

/** The envelope version — the `ar` tag. */
fun TagArray.artifactVersion(): String? = fastFirstNotNullOfOrNull(VersionTag::parse)

/** The artifact's stable UUID — the `d` tag. */
fun TagArray.artifactId(): String? = firstTagValue(DTag.TAG_NAME)

/** The artifact's home channel UUID (the source channel on a removal) — the `h` tag. */
fun TagArray.artifactHome(): String? = fastFirstNotNullOfOrNull(GroupIdTag::parse)

/** The namespaced content type — the `type` tag. */
fun TagArray.artifactType(): String? = fastFirstNotNullOfOrNull(TypeTag::parse)

/** The display title — the `title` tag (absent on a delete). */
fun TagArray.artifactTitle(): String? = fastFirstNotNullOfOrNull(TitleTag::parse)

/** The lifecycle operation — the `op` tag. */
fun TagArray.artifactOp(): ArtifactOp? = fastFirstNotNullOfOrNull(OpTag::parse)

/** The conversation anchor — the `root` tag, only when it holds a well-formed 64-hex event id. */
fun TagArray.artifactRoot(): HexKey? = fastFirstNotNullOfOrNull(RootTag::parseId)

/** The replaced revision — the `prev` tag, only when it holds a well-formed 64-hex event id. */
fun TagArray.artifactPrev(): HexKey? = fastFirstNotNullOfOrNull(PrevTag::parseId)

/** Why a removal (`kind:45011`) was issued — the `reason` tag. */
fun TagArray.artifactRemovalReason(): String? = fastFirstNotNullOfOrNull(ReasonTag::parse)

/**
 * The client-defined tags: every tag that is neither envelope nor NIP-OA `auth`. NIP-AR
 * editors MUST preserve these (and unfamiliar content fields) or decline the edit.
 */
fun TagArray.artifactClientTags(): List<Array<String>> = filter { it.isNotEmpty() && it[0] !in ArtifactValidator.ENVELOPE_TAG_NAMES && it[0] != ArtifactValidator.AUTH_TAG_NAME }
