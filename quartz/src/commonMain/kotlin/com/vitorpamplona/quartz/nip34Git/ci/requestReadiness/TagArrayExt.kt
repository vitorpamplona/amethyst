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
package com.vitorpamplona.quartz.nip34Git.ci.requestReadiness

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.RepositoryTag

// "Items have two fields or an optional relay-hint field, MUST NOT have a fourth marker."
private fun isItem(tag: Array<String>) = tag.size <= 3

fun TagArray.readyRepositories(): List<ATag> = mapNotNull { if (isItem(it)) RepositoryTag.parse(it) else null }.distinctBy { it.toTag() }

fun TagArray.readyRepositoryAddressIds(): List<String> = mapNotNull { if (isItem(it)) RepositoryTag.parseAddressId(it) else null }.distinct()

fun TagArray.readyRepositoryHints() = mapNotNull { if (isItem(it)) RepositoryTag.parseAsHint(it) else null }

fun TagArray.readyMaintainers(): List<PTag> = mapNotNull { if (isItem(it)) PTag.parse(it) else null }.distinctBy { it.pubKey }

fun TagArray.readyMaintainerHints() = mapNotNull { if (isItem(it)) PTag.parseAsHint(it) else null }
