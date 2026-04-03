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
package com.vitorpamplona.quartz.marmot.mip02Welcome

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Marmot Welcome Event (MIP-02) — kind 444.
 *
 * An unsigned rumor event (no sig) that carries an MLS Welcome message for
 * onboarding new members to a group. Delivered via NIP-59 gift wrap:
 *
 *   GiftWrap(kind:1059) → Seal(kind:13) → WelcomeEvent(kind:444, unsigned)
 *
 * Content: base64-encoded MLSMessage with wire_format = mls_welcome.
 *
 * CRITICAL timing requirement: The Commit that adds this member MUST be
 * confirmed by relays BEFORE the Welcome is sent, to prevent state forks.
 *
 * After processing a Welcome, the new member MUST:
 * 1. Rotate their KeyPackage (publish new kind:30443 under same d-tag)
 * 2. Securely delete the init_key private material
 * 3. Perform a self-update within 24 hours for forward secrecy
 */
@Immutable
class WelcomeEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** Base64-encoded MLSMessage (wire_format = mls_welcome) */
    fun welcomeBase64() = content

    /** Event ID of the KeyPackage that was consumed for this invitation */
    fun keyPackageEventId() = tags.keyPackageEventId()

    /** Relays where the new member should look for Group Events */
    fun relays() = tags.welcomeRelays()

    /** Content encoding (must be "base64") */
    fun encoding() = tags.welcomeEncoding()

    override fun isContentEncoded() = true

    companion object {
        const val KIND = 444
        const val ALT_DESCRIPTION = "MLS Welcome"

        fun build(
            welcomeBase64: String,
            keyPackageEventId: HexKey,
            relays: List<NormalizedRelayUrl>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WelcomeEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, welcomeBase64, createdAt) {
            keyPackageEventId(keyPackageEventId)
            welcomeRelays(relays)
            encoding()
            initializer()
        }
    }
}
