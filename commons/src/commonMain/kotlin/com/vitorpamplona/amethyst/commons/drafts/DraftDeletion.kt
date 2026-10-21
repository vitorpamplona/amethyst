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
package com.vitorpamplona.amethyst.commons.drafts

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/** What deletes a draft: the empty version that replaces it, and the kind-5 that deletes it. */
class DraftDeletion(
    val emptied: DraftWrapEvent,
    val deletion: DeletionRequestEvent,
)

/**
 * Signs the deletion of [draft]. Both events are dated strictly after it: Post often lands in the
 * same second as the last auto-save, and on a `created_at` tie the lower id wins, so a relay could
 * keep the draft over its empty replacement. The kind-5 names the draft's own id as well as its
 * address, for relays that apply `e` deletions but not `a` ones.
 */
suspend fun signDraftDeletion(
    draft: DraftWrapEvent,
    signer: NostrSigner,
    now: Long = TimeUtils.now(),
): DraftDeletion {
    val createdAt = maxOf(now, draft.createdAt + 1)
    return DraftDeletion(
        emptied = DraftWrapEvent.createDeletedEvent(draft.dTag(), signer, createdAt),
        deletion = signer.sign(DeletionRequestEvent.build(listOf(draft), createdAt)),
    )
}
