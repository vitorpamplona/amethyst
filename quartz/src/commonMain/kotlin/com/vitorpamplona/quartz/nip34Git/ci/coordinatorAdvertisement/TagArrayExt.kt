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
package com.vitorpamplona.quartz.nip34Git.ci.coordinatorAdvertisement

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip34Git.ci.tags.AdmissionTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.BillingTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiAdmissionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiBillingPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiExecutionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.ExecutionTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.SecretsKeyTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.SoftwareTag

fun TagArray.software() = fastFirstNotNullOfOrNull(SoftwareTag::parse)

/**
 * Reads a policy tag the spec allows once. Several tags that disagree are ambiguous and read as
 * [unknown] — the policy reader must never pick the more permissive of two claims.
 */
private inline fun <T : Any> TagArray.singlePolicy(
    parse: (Array<String>) -> T?,
    unknown: T,
): T? {
    val values = mapNotNull(parse).distinct()
    return when (values.size) {
        0 -> null
        1 -> values[0]
        else -> unknown
    }
}

/** Null when absent; [CiAdmissionPolicy.UNKNOWN] when unrecognized or ambiguous. */
fun TagArray.admission() = singlePolicy(AdmissionTag::parse, CiAdmissionPolicy.UNKNOWN)

/** Null when absent; [CiExecutionPolicy.UNKNOWN] when unrecognized or ambiguous. */
fun TagArray.execution() = singlePolicy(ExecutionTag::parse, CiExecutionPolicy.UNKNOWN)

/** Null when absent (the tag is optional); [CiBillingPolicy.UNKNOWN] when unrecognized or ambiguous. */
fun TagArray.billing() = singlePolicy(BillingTag::parse, CiBillingPolicy.UNKNOWN)

/** The secret-update recipient. The spec allows at most one; several read as none. */
fun TagArray.secretsKey() = mapNotNull(SecretsKeyTag::parse).singleOrNull()
