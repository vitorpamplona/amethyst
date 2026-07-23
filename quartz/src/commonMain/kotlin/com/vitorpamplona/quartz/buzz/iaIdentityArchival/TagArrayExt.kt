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
package com.vitorpamplona.quartz.buzz.iaIdentityArchival

import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.Consent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.ConsentTag
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.ReasonTag
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.ReplacedByTag
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.tags.AuthTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip70ProtectedEvts.tags.ProtectedTag

/** The archival target — the single `p` tag pubkey. */
fun TagArray.archivalTarget(): HexKey? = firstNotNullOfOrNull(PTag::parseKey)

/** The machine-readable `reason` code, if present. */
fun TagArray.archivalReason(): String? = firstNotNullOfOrNull(ReasonTag::parse)

/** The `replaced-by` rotation pointer (archive path only), if present. */
fun TagArray.archivalReplacedBy(): HexKey? = firstNotNullOfOrNull(ReplacedByTag::parse)

/** The NIP-OA owner attestation carried in the `auth` tag (owner-of-agent path), if present. */
fun TagArray.archivalAuth(): OwnerAttestation? = firstNotNullOfOrNull(AuthTag::parse)

/** The relay-written `consent` record (path + actor), present only on the 8002/8003 deltas. */
fun TagArray.archivalConsent(): Consent? = firstNotNullOfOrNull(ConsentTag::parse)

/** The `e` tag referencing the originating request event, present only on the 8002/8003 deltas. */
fun TagArray.archivalRequestId(): String? = firstNotNullOfOrNull(ETag::parseId)

/** Whether the event carries the NIP-70 `-` protection marker. */
fun TagArray.isProtected(): Boolean = firstNotNullOfOrNull(ProtectedTag::parse) == true

/** Every archived-identity pubkey listed as a bare `p` tag (used by the 13535 snapshot). */
fun TagArray.archivedIdentities(): List<HexKey> = mapNotNull(PTag::parseKey)
