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
package com.vitorpamplona.quartz.buzz.oaOwnerAttestation

/**
 * The `conditions` string of a Buzz Owner Attestation (NIP-OA).
 *
 * The conditions restrict what the attested agent key is authorized to publish. The
 * string is part of the signed commitment, so it must be reproduced **byte for byte**
 * — this helper only builds/validates canonical strings, it never rewrites them.
 *
 * Grammar (from `buzz-sdk` `nip_oa.rs`): either empty, or one or more clauses joined
 * by `&`, with no whitespace and no empty clauses (no leading/trailing/double `&`):
 * - `kind=<0-65535>` — restrict to a single event kind (u16)
 * - `created_at<<0-4294967295>` — event must be older than this unix timestamp (u32)
 * - `created_at><0-4294967295>` — event must be newer than this unix timestamp (u32)
 *
 * All integers are canonical decimals: no leading zeros except the standalone `0`.
 */
data class AttestationConditions(
    val kind: Int? = null,
    val createdAtBefore: Long? = null,
    val createdAtAfter: Long? = null,
) {
    /** Serializes to the canonical conditions string in a fixed clause order. */
    fun encode(): String =
        buildList {
            kind?.let { add("kind=$it") }
            createdAtBefore?.let { add("created_at<$it") }
            createdAtAfter?.let { add("created_at>$it") }
        }.joinToString("&")

    companion object {
        private val CANONICAL_UINT = Regex("^(0|[1-9][0-9]*)$")

        private const val U16_MAX = 65535L
        private const val U32_MAX = 4294967295L

        private fun parseCanonicalUInt(
            value: String,
            max: Long,
        ): Long? {
            if (!CANONICAL_UINT.matches(value)) return null
            val parsed = value.toLongOrNull() ?: return null
            return if (parsed in 0..max) parsed else null
        }

        /**
         * Parses a conditions string. Returns null when the string is not canonical.
         * The empty string parses to an all-null (unrestricted) instance.
         */
        fun parse(conditions: String): AttestationConditions? {
            if (conditions.isEmpty()) return AttestationConditions()

            var kind: Int? = null
            var before: Long? = null
            var after: Long? = null

            for (clause in conditions.split("&")) {
                when {
                    clause.startsWith("kind=") -> {
                        val v = parseCanonicalUInt(clause.removePrefix("kind="), U16_MAX) ?: return null
                        kind = v.toInt()
                    }
                    clause.startsWith("created_at<") -> {
                        before = parseCanonicalUInt(clause.removePrefix("created_at<"), U32_MAX) ?: return null
                    }
                    clause.startsWith("created_at>") -> {
                        after = parseCanonicalUInt(clause.removePrefix("created_at>"), U32_MAX) ?: return null
                    }
                    else -> return null
                }
            }

            return AttestationConditions(kind, before, after)
        }

        /** True when [conditions] is a syntactically valid canonical conditions string. */
        fun isValid(conditions: String): Boolean = parse(conditions) != null
    }
}
