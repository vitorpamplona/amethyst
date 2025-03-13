/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip01Core.core

class TagArrayBuilder<T : IEvent> {
    /**
     * keeps a tag list by tag names to treat tags that must be unique
     */
    private val tagList = mutableMapOf<String, MutableList<Tag>>()

    fun remove(tagName: String): TagArrayBuilder<T> {
        tagList.remove(tagName)
        return this
    }

    fun remove(
        tagName: String,
        tagValue: String,
    ): TagArrayBuilder<T> {
        tagList[tagName]?.removeIf { it.valueOrNull() == tagValue }
        if (tagList[tagName]?.isEmpty() == true) {
            tagList.remove(tagName)
        }
        return this
    }

    fun removeIf(
        predicate: (Tag, Tag) -> Boolean,
        toCompare: Tag,
    ): TagArrayBuilder<T> {
        val tagName = toCompare.nameOrNull() ?: return this
        tagList[tagName]?.removeIf { predicate(it, toCompare) }
        if (tagList[tagName]?.isEmpty() == true) {
            tagList.remove(tagName)
        }
        return this
    }

    fun add(tag: Array<String>): TagArrayBuilder<T> {
        if (tag.isEmpty() || tag[0].isEmpty()) return this
        tagList.getOrPut(tag[0], ::mutableListOf).add(tag)
        return this
    }

    fun addUnique(tag: Array<String>): TagArrayBuilder<T> {
        if (tag.isEmpty() || tag[0].isEmpty()) return this
        tagList[tag[0]] = mutableListOf(tag)
        return this
    }

    fun addAll(tag: List<Array<String>>): TagArrayBuilder<T> {
        tag.forEach(::add)
        return this
    }

    fun addAll(tag: Array<Array<String>>): TagArrayBuilder<T> {
        tag.forEach(::add)
        return this
    }

    fun toTypedArray() = tagList.flatMap { it.value }.toTypedArray()

    fun build() = toTypedArray()
}

inline fun <T : Event> tagArray(initializer: TagArrayBuilder<T>.() -> Unit = {}): TagArray = TagArrayBuilder<T>().apply(initializer).build()
