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
package com.vitorpamplona.quartz.experimental.attestations.attestation

import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.AttestationStatus
import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.RequestTag
import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.StatusTag
import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.ValidFromTag
import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.ValidToTag
import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.Validity
import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.ValidityTag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

fun TagArrayBuilder<AttestationEvent>.validity(validity: Validity) = addUnique(ValidityTag.assemble(validity))

fun TagArrayBuilder<AttestationEvent>.status(status: AttestationStatus) = addUnique(StatusTag.assemble(status))

fun TagArrayBuilder<AttestationEvent>.validFrom(timestamp: Long) = addUnique(ValidFromTag.assemble(timestamp))

fun TagArrayBuilder<AttestationEvent>.validTo(timestamp: Long) = addUnique(ValidToTag.assemble(timestamp))

fun TagArrayBuilder<AttestationEvent>.request(requestAddress: String) = addUnique(RequestTag.assemble(requestAddress))
