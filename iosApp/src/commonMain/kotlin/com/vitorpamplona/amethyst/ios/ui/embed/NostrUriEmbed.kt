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
package com.vitorpamplona.amethyst.ios.ui.embed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.toNpub

/**
 * Represents a parsed nostr: URI reference found in note content.
 */
sealed class NostrReference {
    data class Profile(
        val pubKeyHex: String,
    ) : NostrReference()

    data class Note(
        val eventIdHex: String,
    ) : NostrReference()

    data class Event(
        val eventIdHex: String,
        val relayHint: String?,
    ) : NostrReference()

    data class Address(
        val kind: Int,
        val pubKeyHex: String,
        val dTag: String,
    ) : NostrReference()
}

/**
 * Parses nostr: URIs from note content using the quartz Nip19Parser.
 */
fun parseNostrReferences(content: String): List<Pair<IntRange, NostrReference>> {
    val results = mutableListOf<Pair<IntRange, NostrReference>>()
    val regex = Regex("nostr:([a-zA-Z0-9]+)")

    for (match in regex.findAll(content)) {
        val bech32 = match.groupValues[1]
        val entities = Nip19Parser.parseAll("nostr:$bech32")
        if (entities.isNotEmpty()) {
            val entity = entities.first()
            val ref =
                when (entity) {
                    is com.vitorpamplona.quartz.nip19Bech32.entities.NProfile -> {
                        NostrReference.Profile(entity.hex)
                    }

                    is com.vitorpamplona.quartz.nip19Bech32.entities.NPub -> {
                        NostrReference.Profile(entity.hex)
                    }

                    is com.vitorpamplona.quartz.nip19Bech32.entities.NNote -> {
                        NostrReference.Note(entity.hex)
                    }

                    is com.vitorpamplona.quartz.nip19Bech32.entities.NEvent -> {
                        val relayHint = entity.relay.firstOrNull()?.url
                        NostrReference.Event(entity.hex, relayHint)
                    }

                    is com.vitorpamplona.quartz.nip19Bech32.entities.NAddress -> {
                        NostrReference.Address(entity.kind, entity.author, entity.dTag)
                    }

                    else -> {
                        null
                    }
                }
            if (ref != null) {
                results.add(match.range to ref)
            }
        }
    }
    return results
}

/**
 * Renders an embedded nostr: reference inline.
 * Shows a compact preview card for referenced notes/profiles.
 */
@Composable
fun NostrEmbedCard(
    reference: NostrReference,
    localCache: IosLocalCache,
    modifier: Modifier = Modifier,
    onProfileClick: ((String) -> Unit)? = null,
    onNoteClick: ((String) -> Unit)? = null,
) {
    when (reference) {
        is NostrReference.Profile -> {
            val user = localCache.getUserIfExists(reference.pubKeyHex)
            val displayName = user?.toBestDisplayName() ?: reference.pubKeyHex.take(12) + "..."

            Row(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .then(
                            if (onProfileClick != null) {
                                Modifier.clickable { onProfileClick(reference.pubKeyHex) }
                            } else {
                                Modifier
                            },
                        ).padding(8.dp),
            ) {
                Text("👤", modifier = Modifier.padding(end = 4.dp))
                Column {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    val npub =
                        try {
                            reference.pubKeyHex
                                .hexToByteArrayOrNull()
                                ?.toNpub()
                                ?.take(20) ?: ""
                        } catch (_: Exception) {
                            ""
                        }
                    if (npub.isNotBlank()) {
                        Text(
                            "$npub...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        is NostrReference.Note -> {
            val note = localCache.getNoteIfExists(reference.eventIdHex)
            val content = note?.event?.content?.take(120) ?: "Loading note..."
            val author = note?.event?.pubKey?.let { localCache.getUserIfExists(it)?.toBestDisplayName() }

            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .then(
                            if (onNoteClick != null) {
                                Modifier.clickable { onNoteClick(reference.eventIdHex) }
                            } else {
                                Modifier
                            },
                        ).padding(8.dp),
            ) {
                Row {
                    Text("📝 ", style = MaterialTheme.typography.bodySmall)
                    author?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        is NostrReference.Event -> {
            // Same as Note but with relay hint
            val note = localCache.getNoteIfExists(reference.eventIdHex)
            val content = note?.event?.content?.take(120) ?: "Loading event..."

            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .then(
                            if (onNoteClick != null) {
                                Modifier.clickable { onNoteClick(reference.eventIdHex) }
                            } else {
                                Modifier
                            },
                        ).padding(8.dp),
            ) {
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        is NostrReference.Address -> {
            Row(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
            ) {
                Text(
                    "📋 Addressable event (kind ${reference.kind})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
