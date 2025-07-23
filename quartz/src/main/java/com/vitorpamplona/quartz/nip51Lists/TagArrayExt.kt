/**
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
package com.vitorpamplona.quartz.nip51Lists

import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.utils.startsWith
import com.vitorpamplona.quartz.utils.startsWithAny
import kotlin.collections.ArrayList

inline fun TagArray.filterToArray(predicate: (Array<String>) -> Boolean): TagArray = filterTo(ArrayList(), predicate).toTypedArray()

inline fun TagArray.remove(predicate: (Array<String>) -> Boolean): TagArray = filterNotTo(ArrayList(this.size), predicate).toTypedArray()

fun TagArray.remove(startsWith: Array<String>): TagArray = filterNotTo(ArrayList(this.size), { it.startsWith(startsWith) }).toTypedArray()

fun <R> TagArray.removeParsing(
    transform: (Tag) -> R,
    equalsTo: R,
): TagArray =
    filterNotTo(
        destination = ArrayList(this.size),
        predicate = {
            transform(it) == equalsTo
        },
    ).toTypedArray()

fun TagArray.removeAny(startsWith: List<Array<String>>): TagArray =
    filterNotTo(
        ArrayList(this.size),
        {
            it.startsWithAny(startsWith)
        },
    ).toTypedArray()

fun TagArray.replaceAll(
    startsWith: Array<String>,
    newElement: Array<String>,
): TagArray = filterNotTo(ArrayList(this.size), { it.startsWith(startsWith) }).plusElement(newElement).toTypedArray()
