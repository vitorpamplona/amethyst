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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.notices.ConcordNotice
import com.vitorpamplona.amethyst.commons.notices.SignerNotice
import com.vitorpamplona.amethyst.commons.notices.UserNoticeSink
import com.vitorpamplona.amethyst.commons.util.notifySignerErrors
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException

/**
 * Runs an invite-link [mint] for a screen, so a tap that makes no link always says why: a
 * read-only account hears so, a mint that came back empty (no CREATE_INVITE, or the link could
 * not be recorded) or threw past the signer (a relay, a store) reports
 * [ConcordNotice.InviteNotMinted], and the signer's own failures keep their notices. Never throws
 * but for cancellation, so a `rememberCoroutineScope` launch can call it bare.
 */
suspend fun mintConcordInviteReporting(
    notices: UserNoticeSink,
    writeable: Boolean,
    mint: suspend () -> String?,
): String? {
    if (!writeable) {
        notices.notify(SignerNotice.ReadOnly)
        return null
    }
    var url: String? = null
    var reported = false
    try {
        notifySignerErrors({
            reported = true
            notices.notify(it)
        }) { url = mint() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Concord", "Invite link not minted", e)
        notices.notify(ConcordNotice.InviteNotMinted)
        return null
    }
    if (url == null && !reported) notices.notify(ConcordNotice.InviteNotMinted)
    return url
}
