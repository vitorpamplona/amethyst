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
package com.vitorpamplona.quartz.nip90Dvms.tags

import com.vitorpamplona.quartz.nip01Core.core.TagArray

fun TagArray.inputs() = mapNotNull(InputTag::parse)

fun TagArray.firstInputByType(type: String) = inputs().firstOrNull { it.type == type }

fun TagArray.outputMimeType() = firstNotNullOfOrNull(OutputTag::parse)?.mimeType

fun TagArray.dvmParams(): List<Pair<String, List<String>>> =
    filter { it.size >= 3 && it[0] == "param" }
        .map { it[1] to it.drop(2) }

fun TagArray.dvmParam(key: String): String? = firstOrNull { it.size >= 3 && it[0] == "param" && it[1] == key }?.getOrNull(2)

fun TagArray.dvmParamValues(key: String): List<String>? = firstOrNull { it.size >= 3 && it[0] == "param" && it[1] == key }?.drop(2)

fun TagArray.dvmParamAll(key: String): List<String> =
    filter { it.size >= 3 && it[0] == "param" && it[1] == key }
        .mapNotNull { it.getOrNull(2) }
