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
package com.vitorpamplona.amethyst.commons.feeds.custom

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.collections.immutable.toImmutableList

object FeedDefinitionSerializer {
    private val mapper = ObjectMapper()

    fun serializeList(feeds: List<FeedDefinition>): String {
        val array = mapper.createArrayNode()
        feeds.forEach { feed -> array.add(serializeFeed(feed)) }
        return mapper.writeValueAsString(array)
    }

    fun deserializeList(json: String): List<FeedDefinition> {
        if (json.isBlank()) return emptyList()
        val array = mapper.readTree(json) as? ArrayNode ?: return emptyList()
        return array.mapNotNull { node -> deserializeFeed(node) }
    }

    private fun serializeFeed(feed: FeedDefinition): ObjectNode =
        mapper.createObjectNode().apply {
            put("id", feed.id)
            put("name", feed.name)
            put("emoji", feed.emoji)
            put("pinned", feed.pinned)
            put("pinOrder", feed.pinOrder)
            put("refreshMode", feed.refreshMode.name)
            put("createdAt", feed.createdAt)
            set<ObjectNode>("source", serializeSource(feed.source))
        }

    private fun deserializeFeed(node: JsonNode): FeedDefinition? {
        val id = node.get("id")?.asText() ?: return null
        val name = node.get("name")?.asText() ?: return null
        val emoji = node.get("emoji")?.asText() ?: ""
        val pinned = node.get("pinned")?.asBoolean() ?: false
        val pinOrder = node.get("pinOrder")?.asInt() ?: Int.MAX_VALUE
        val refreshMode =
            node.get("refreshMode")?.asText()?.let {
                try {
                    RefreshMode.valueOf(it)
                } catch (_: Exception) {
                    RefreshMode.LIVE_STREAM
                }
            } ?: RefreshMode.LIVE_STREAM
        val createdAt = node.get("createdAt")?.asLong() ?: 0L
        val source = node.get("source")?.let { deserializeSource(it) } ?: return null

        return FeedDefinition(
            id = id,
            name = name,
            emoji = emoji,
            pinned = pinned,
            pinOrder = pinOrder,
            source = source,
            refreshMode = refreshMode,
            createdAt = createdAt,
        )
    }

    private fun serializeSource(source: FeedSource): ObjectNode =
        mapper.createObjectNode().apply {
            when (source) {
                is FeedSource.Filter -> {
                    put("type", "filter")
                    set<ArrayNode>("hashtags", mapper.valueToTree(source.hashtags.toList()))
                    set<ArrayNode>("authors", mapper.valueToTree(source.authors.toList()))
                    set<ArrayNode>("relays", mapper.valueToTree(source.relays.toList()))
                    set<ArrayNode>("excludeAuthors", mapper.valueToTree(source.excludeAuthors.toList()))
                    set<ArrayNode>("excludeKeywords", mapper.valueToTree(source.excludeKeywords.toList()))
                    set<ArrayNode>("kinds", mapper.valueToTree(source.kinds.toList()))
                }

                is FeedSource.PeopleList -> {
                    put("type", "people_list")
                    put("kind", source.kind)
                    put("pubkey", source.pubkey)
                    put("dTag", source.dTag)
                }

                is FeedSource.InterestSet -> {
                    put("type", "interest_set")
                    put("kind", source.kind)
                    put("pubkey", source.pubkey)
                    put("dTag", source.dTag)
                }

                is FeedSource.DVM -> {
                    put("type", "dvm")
                    put("kind", source.kind)
                    put("pubkey", source.pubkey)
                    put("dTag", source.dTag)
                }

                is FeedSource.SingleRelay -> {
                    put("type", "single_relay")
                    put("url", source.url)
                }

                FeedSource.Global -> {
                    put("type", "global")
                }

                FeedSource.Following -> {
                    put("type", "following")
                }
            }
        }

    private fun deserializeSource(node: JsonNode): FeedSource? {
        val type = node.get("type")?.asText() ?: return null
        return when (type) {
            "filter" -> {
                FeedSource.Filter(
                    hashtags = node.get("hashtags")?.map { it.asText() }?.toImmutableList() ?: return null,
                    authors = node.get("authors")?.map { it.asText() }?.toImmutableList() ?: return null,
                    relays = node.get("relays")?.map { it.asText() }?.toImmutableList() ?: return null,
                    excludeAuthors = node.get("excludeAuthors")?.map { it.asText() }?.toImmutableList() ?: return null,
                    excludeKeywords = node.get("excludeKeywords")?.map { it.asText() }?.toImmutableList() ?: return null,
                    kinds = node.get("kinds")?.map { it.asInt() }?.toImmutableList() ?: return null,
                )
            }

            "people_list" -> {
                FeedSource.PeopleList(
                    kind = node.get("kind")?.asInt() ?: 30000,
                    pubkey = node.get("pubkey")?.asText() ?: return null,
                    dTag = node.get("dTag")?.asText() ?: return null,
                )
            }

            "interest_set" -> {
                FeedSource.InterestSet(
                    kind = node.get("kind")?.asInt() ?: 30015,
                    pubkey = node.get("pubkey")?.asText() ?: return null,
                    dTag = node.get("dTag")?.asText() ?: return null,
                )
            }

            "dvm" -> {
                FeedSource.DVM(
                    kind = node.get("kind")?.asInt() ?: 31990,
                    pubkey = node.get("pubkey")?.asText() ?: return null,
                    dTag = node.get("dTag")?.asText() ?: return null,
                )
            }

            "single_relay" -> {
                FeedSource.SingleRelay(
                    url = node.get("url")?.asText() ?: return null,
                )
            }

            "global" -> {
                FeedSource.Global
            }

            "following" -> {
                FeedSource.Following
            }

            else -> {
                null
            }
        }
    }
}
