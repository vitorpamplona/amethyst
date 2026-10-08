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
package com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/** WalletScrutiny's verdicts, as `docs/verifications.md` lists them for the `status` tag. */
enum class BuildStatus(
    val code: String,
) {
    /** Built from source, and the result matches the official files. */
    REPRODUCIBLE("reproducible"),

    /** Built from source, and the result differs from the official files. */
    NOT_REPRODUCIBLE("not_reproducible"),

    /** "Failed to build from source." */
    FTBFS("ftbfs"),
    SPAM("spam"),

    /** The release has no source tag to build from. */
    NOTAG("notag"),

    /** No public source code. */
    NOSOURCE("nosource"),
    WARNING("warning"),
    OBFUSCATED("obfuscated"),
    ;

    companion object {
        fun fromCode(code: String): BuildStatus? = entries.firstOrNull { it.code == code }
    }
}

/**
 * `["status", "<verdict>"]` on a WalletScrutiny verification: the set-level verdict for every file
 * the event's `x` tags list. Its presence, with `i`, is what tells a verification apart from the
 * other apps that publish kind 30301.
 */
class BuildStatusTag {
    companion object {
        const val TAG_NAME = "status"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        /** The verdict as written, so a verdict this enum does not know is still readable. */
        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun assemble(status: BuildStatus) = arrayOf(TAG_NAME, status.code)
    }
}
