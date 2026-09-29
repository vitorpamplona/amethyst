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
package com.vitorpamplona.quartz.nip5aStaticWebsites

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.each
import com.vitorpamplona.quartz.nip01Core.links.props.NoProps
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.AppTag
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.OriginTag

/**
 * The references an nsite manifest carries (NIP-5A, and the NIP-5D napplets that reuse its tag
 * set): [AppTag] is "an addressable event reference to an app descriptor" ([Relation.APP]), and
 * the copy lineage is a lowercase `a` ([ATag]) to "the immediate parent nsite from which it was
 * copied" ([Relation.COPIED]) plus an uppercase `A` ([OriginTag]) to "the origin nsite of the
 * copy lineage" ([Relation.ORIGIN]).
 *
 * [parent] names the lowercase `a`: a manifest snapshot's single `a` is instead the root or
 * named site it snapshots ([Relation.SNAPSHOTTED]). `path`, `x`, `server` and `source` are
 * blob hashes and URLs, not links.
 */
fun LinkBuilder.siteManifestLinks(
    tags: TagArray,
    parent: Relation<NoProps> = Relation.COPIED,
) {
    each(tags, ATag::parse) { address(parent, it, ATag.TAG_NAME) }
    each(tags, OriginTag::parse) { address(Relation.ORIGIN, it, OriginTag.TAG_NAME) }
    each(tags, AppTag::parse) { address(Relation.APP, it, AppTag.TAG_NAME) }
}
