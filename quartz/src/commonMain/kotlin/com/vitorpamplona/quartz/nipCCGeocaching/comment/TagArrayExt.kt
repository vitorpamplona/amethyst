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
package com.vitorpamplona.quartz.nipCCGeocaching.comment

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nipCCGeocaching.comment.tags.GeocacheLogType
import com.vitorpamplona.quartz.nipCCGeocaching.comment.tags.GeocacheLogTypeTag

/**
 * The log type of a kind 1111 comment on a geocache.
 *
 * NIP-CC: "If no `t` tag is present, the comment is assumed to be a general note." Hashtags in
 * `t` are skipped rather than mistaken for a type, because only the four defined codes parse.
 */
fun TagArray.geocacheLogType() = firstNotNullOfOrNull(GeocacheLogTypeTag::parse) ?: GeocacheLogType.NOTE

/** The declared log type, or null where the comment left it to the default. */
fun TagArray.declaredGeocacheLogType() = firstNotNullOfOrNull(GeocacheLogTypeTag::parse)
