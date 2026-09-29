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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag

/** The `ar` envelope-version tag. */
fun <T : Event> TagArrayBuilder<T>.artifactVersion(version: String = VersionTag.CURRENT) = addUnique(VersionTag.assemble(version))

/**
 * The artifact UUID as its `d` tag. Artifacts are regular (not addressable) events, so this
 * is a plain `d` tag, not the NIP-01 addressable identifier.
 */
fun <T : Event> TagArrayBuilder<T>.artifactId(artifactId: String) = addUnique(DTag.assemble(artifactId))

/** The home (or, on a removal, source) channel — the `h` tag. */
fun <T : Event> TagArrayBuilder<T>.artifactHome(channelId: String) = addUnique(GroupIdTag.assemble(channelId))

fun <T : Event> TagArrayBuilder<T>.artifactType(type: String) = addUnique(TypeTag.assemble(type))

fun <T : Event> TagArrayBuilder<T>.artifactTitle(title: String) = addUnique(TitleTag.assemble(title))

fun <T : Event> TagArrayBuilder<T>.artifactOp(op: ArtifactOp) = addUnique(OpTag.assemble(op))

fun <T : Event> TagArrayBuilder<T>.artifactRoot(anchorEventId: HexKey) = addUnique(RootTag.assemble(anchorEventId))

fun <T : Event> TagArrayBuilder<T>.artifactPrev(revisionId: HexKey) = addUnique(PrevTag.assemble(revisionId))

fun <T : Event> TagArrayBuilder<T>.artifactRemovalReason(reason: String = ReasonTag.MOVED) = addUnique(ReasonTag.assemble(reason))
