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
package com.vitorpamplona.amethyst.ui.components

import android.util.Log
import android.util.Patterns
import com.vitorpamplona.amethyst.commons.RichTextParser
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import kotlinx.coroutines.CancellationException

class MarkdownParser {
    private fun getDisplayNameAndNIP19FromTag(
        tag: String,
        tags: ImmutableListOfLists<String>,
    ): Pair<String, String>? {
        val matcher = RichTextParser.tagIndex.matcher(tag)
        val (index, suffix) =
            try {
                matcher.find()
                Pair(matcher.group(1)?.toInt(), matcher.group(2) ?: "")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("Tag Parser", "Couldn't link tag $tag", e)
                Pair(null, null)
            }

        if (index != null && index >= 0 && index < tags.lists.size) {
            val tag = tags.lists[index]

            if (tag.size > 1) {
                if (tag[0] == "p") {
                    LocalCache.checkGetOrCreateUser(tag[1])?.let {
                        return Pair(it.toBestDisplayName(), it.pubkeyNpub())
                    }
                } else if (tag[0] == "e" || tag[0] == "a") {
                    LocalCache.checkGetOrCreateNote(tag[1])?.let {
                        return Pair(it.idDisplayNote(), it.toNEvent())
                    }
                }
            }
        }

        return null
    }

    private fun getDisplayNameFromNip19(nip19: Nip19Bech32.Return): Pair<String, String>? {
        if (nip19.type == Nip19Bech32.Type.USER) {
            LocalCache.getUserIfExists(nip19.hex)?.let {
                return Pair(it.toBestDisplayName(), it.pubkeyNpub())
            }
        } else if (nip19.type == Nip19Bech32.Type.NOTE) {
            LocalCache.getNoteIfExists(nip19.hex)?.let {
                return Pair(it.idDisplayNote(), it.toNEvent())
            }
        } else if (nip19.type == Nip19Bech32.Type.ADDRESS) {
            LocalCache.getAddressableNoteIfExists(nip19.hex)?.let {
                return Pair(it.idDisplayNote(), it.toNEvent())
            }
        } else if (nip19.type == Nip19Bech32.Type.EVENT) {
            LocalCache.getNoteIfExists(nip19.hex)?.let {
                return Pair(it.idDisplayNote(), it.toNEvent())
            }
        }

        return null
    }

    fun returnNIP19References(
        content: String,
        tags: ImmutableListOfLists<String>?,
    ): List<Nip19Bech32.Return> {
        checkNotInMainThread()

        val listOfReferences = mutableListOf<Nip19Bech32.Return>()
        content.split('\n').forEach { paragraph ->
            paragraph.split(' ').forEach { word: String ->
                if (RichTextParser.startsWithNIP19Scheme(word)) {
                    val parsedNip19 = Nip19Bech32.uriToRoute(word)
                    parsedNip19?.let { listOfReferences.add(it) }
                }
            }
        }

        tags?.lists?.forEach {
            if (it[0] == "p" && it.size > 1) {
                listOfReferences.add(Nip19Bech32.Return(Nip19Bech32.Type.USER, it[1], null, null, null, ""))
            } else if (it[0] == "e" && it.size > 1) {
                listOfReferences.add(Nip19Bech32.Return(Nip19Bech32.Type.NOTE, it[1], null, null, null, ""))
            } else if (it[0] == "a" && it.size > 1) {
                listOfReferences.add(Nip19Bech32.Return(Nip19Bech32.Type.ADDRESS, it[1], null, null, null, ""))
            }
        }

        return listOfReferences
    }

    fun returnMarkdownWithSpecialContent(
        content: String,
        tags: ImmutableListOfLists<String>?,
    ): String {
        var returnContent = ""
        content.split('\n').forEach { paragraph ->
            paragraph.split(' ').forEach { word: String ->
                if (RichTextParser.isValidURL(word)) {
                    if (RichTextParser.isImageUrl(word)) {
                        returnContent += "![]($word) "
                    } else {
                        returnContent += "[$word]($word) "
                    }
                } else if (Patterns.EMAIL_ADDRESS.matcher(word).matches()) {
                    returnContent += "[$word](mailto:$word) "
                } else if (Patterns.PHONE.matcher(word).matches() && word.length > 6) {
                    returnContent += "[$word](tel:$word) "
                } else if (RichTextParser.startsWithNIP19Scheme(word)) {
                    val parsedNip19 = Nip19Bech32.uriToRoute(word)
                    returnContent +=
                        if (parsedNip19 !== null) {
                            val pair = getDisplayNameFromNip19(parsedNip19)
                            if (pair != null) {
                                val (displayName, nip19) = pair
                                "[$displayName](nostr:$nip19) "
                            } else {
                                "$word "
                            }
                        } else {
                            "$word "
                        }
                } else if (word.startsWith("#")) {
                    if (RichTextParser.tagIndex.matcher(word).matches() && tags != null) {
                        val pair = getDisplayNameAndNIP19FromTag(word, tags)
                        if (pair != null) {
                            returnContent += "[${pair.first}](nostr:${pair.second}) "
                        } else {
                            returnContent += "$word "
                        }
                    } else if (RichTextParser.hashTagsPattern.matcher(word).matches()) {
                        val hashtagMatcher = RichTextParser.hashTagsPattern.matcher(word)

                        val (myTag, mySuffix) =
                            try {
                                hashtagMatcher.find()
                                Pair(hashtagMatcher.group(1), hashtagMatcher.group(2))
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.e("Hashtag Parser", "Couldn't link hashtag $word", e)
                                Pair(null, null)
                            }

                        if (myTag != null) {
                            returnContent += "[#$myTag](nostr:Hashtag?id=$myTag)$mySuffix "
                        } else {
                            returnContent += "$word "
                        }
                    } else {
                        returnContent += "$word "
                    }
                } else {
                    returnContent += "$word "
                }
            }
            returnContent += "\n"
        }
        return returnContent
    }
}
