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
package com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AppIdTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.VersionTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hasTagWithContent

/**
 * NIP-82 reuses kind 30063 which was already taken by NIP-51's
 * `ReleaseArtifactSetEvent`. Both are valid; they coexist on the wire.
 *
 * We disambiguate by inspecting the tag set: a NIP-82 Software Release MUST
 * carry both `i` (app identifier) and `version` tags. NIP-51 release artifact
 * sets do not.
 */
fun Event.isNip82SoftwareRelease() =
    kind == SoftwareReleaseEvent.KIND &&
        tags.hasTagWithContent(AppIdTag.TAG_NAME) &&
        tags.hasTagWithContent(VersionTag.TAG_NAME)

/**
 * Reinterprets `this` as a [SoftwareReleaseEvent]. Use [isNip82SoftwareRelease]
 * to gate the call.
 */
fun Event.asSoftwareRelease() = SoftwareReleaseEvent(id, pubKey, createdAt, tags, content, sig)
