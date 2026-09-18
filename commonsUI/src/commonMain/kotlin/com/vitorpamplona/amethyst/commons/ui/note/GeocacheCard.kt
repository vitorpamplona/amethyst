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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_archived
import com.vitorpamplona.amethyst.commons.resources.geocache_claimed
import com.vitorpamplona.amethyst.commons.resources.geocache_difficulty
import com.vitorpamplona.amethyst.commons.resources.geocache_first_to_find
import com.vitorpamplona.amethyst.commons.resources.geocache_found_it
import com.vitorpamplona.amethyst.commons.resources.geocache_hint_tap_to_reveal
import com.vitorpamplona.amethyst.commons.resources.geocache_invalid_proof
import com.vitorpamplona.amethyst.commons.resources.geocache_is_art
import com.vitorpamplona.amethyst.commons.resources.geocache_mission
import com.vitorpamplona.amethyst.commons.resources.geocache_needs_verification
import com.vitorpamplona.amethyst.commons.resources.geocache_size_large
import com.vitorpamplona.amethyst.commons.resources.geocache_size_micro
import com.vitorpamplona.amethyst.commons.resources.geocache_size_other
import com.vitorpamplona.amethyst.commons.resources.geocache_size_regular
import com.vitorpamplona.amethyst.commons.resources.geocache_size_small
import com.vitorpamplona.amethyst.commons.resources.geocache_terrain
import com.vitorpamplona.amethyst.commons.resources.geocache_type_multi
import com.vitorpamplona.amethyst.commons.resources.geocache_type_mystery
import com.vitorpamplona.amethyst.commons.resources.geocache_type_traditional
import com.vitorpamplona.amethyst.commons.resources.geocache_unnamed
import com.vitorpamplona.amethyst.commons.resources.geocache_verified_find
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.quartz.nip01Core.tags.geohash.toGeoHash
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.HintObfuscation
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSize
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheType
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Renders the map hero at a geocache's location. The map is platform-specific (an Android tile
 * view today), so the host supplies it as this typed slot — the same arrangement
 * [RoadEventMap] uses.
 */
typealias GeocacheMap = @Composable (latitude: Double, longitude: Double, pinColor: Color, pinEmoji: String, pinAlpha: Float) -> Unit

/**
 * Whether a found log's embedded proof holds up.
 *
 * Deliberately not a boolean. "No verification attached" and "a verification that does not check
 * out" are different things and only one of them is suspicious, while "the listing has not been
 * fetched yet" must not render as either — a card that guesses here is the exact mistake
 * `GeocacheVerificationValidator` exists to prevent.
 */
enum class FoundLogProof {
    /** No `verification` tag, or the cache does not use verification at all. */
    NONE,

    /** A verification is attached but the listing needed to check it is not in hand. */
    UNKNOWN,

    /** Checked against the listing and it holds: the finder was physically at the cache. */
    VALID,

    /** Checked and rejected — wrong signer, wrong finder, wrong cache, or a bad signature. */
    INVALID,
}

private fun CacheType?.emoji(): String =
    when (this) {
        CacheType.TRADITIONAL -> "📦"
        CacheType.MULTI -> "🧭"
        CacheType.MYSTERY -> "❓"
        null -> "📍"
    }

private fun CacheType?.labelRes(): StringResource? =
    when (this) {
        CacheType.TRADITIONAL -> Res.string.geocache_type_traditional
        CacheType.MULTI -> Res.string.geocache_type_multi
        CacheType.MYSTERY -> Res.string.geocache_type_mystery
        null -> null
    }

private fun CacheSize.labelRes(): StringResource =
    when (this) {
        CacheSize.MICRO -> Res.string.geocache_size_micro
        CacheSize.SMALL -> Res.string.geocache_size_small
        CacheSize.REGULAR -> Res.string.geocache_size_regular
        CacheSize.LARGE -> Res.string.geocache_size_large
        CacheSize.OTHER -> Res.string.geocache_size_other
    }

/**
 * Self-contained card for a geocache listing (NIP-CC kind 37516).
 *
 * A [map] hero centred on the cache's finest published geohash, a floating pill carrying the
 * cache name, then the difficulty/terrain/size ratings and whatever badges the listing earns.
 * The description follows; the hint comes last, rotated, because a hint read by accident is a
 * hint wasted.
 *
 * [claimed] is passed in rather than derived here: deciding a first-to-find claim needs the
 * cache's found logs, which a card does not have.
 */
@Composable
fun GeocacheCard(
    noteEvent: GeocacheListingEvent,
    claimed: Boolean = false,
    map: GeocacheMap,
) {
    val name = remember(noteEvent) { noteEvent.cacheName()?.trim().orEmpty() }
    val description = remember(noteEvent) { noteEvent.content.trim() }
    val point = remember(noteEvent) { noteEvent.cachePoint() }
    val type = remember(noteEvent) { noteEvent.cacheType() }
    val size = remember(noteEvent) { noteEvent.cacheSize() }
    val difficulty = remember(noteEvent) { noteEvent.difficulty() }
    val terrain = remember(noteEvent) { noteEvent.terrain() }
    val hint = remember(noteEvent) { noteEvent.hintOnWire()?.trim()?.ifBlank { null } }
    val mission = remember(noteEvent) { noteEvent.mission()?.trim()?.ifBlank { null } }
    val isArchived = remember(noteEvent) { noteEvent.isArchived() }
    val isFirstToFind = remember(noteEvent) { noteEvent.isFirstToFind() }
    val isArt = remember(noteEvent) { noteEvent.hasTypeModifier(TypeModifier.ART) }
    val photo =
        remember(noteEvent) {
            noteEvent
                .images()
                .firstOrNull()
                ?.trim()
                ?.ifBlank { null }
        }
    val needsVerification = remember(noteEvent) { noteEvent.requiresVerification() }

    // An archived cache, or one whose single claim is taken, is history rather than an
    // invitation — the map pin is dimmed to say so without hiding the cache.
    val pinAlpha = if (isArchived || claimed) 0.45f else 1f
    val accent = MaterialTheme.colorScheme.primary

    Column(MaterialTheme.colorScheme.replyModifier) {
        if (point != null) {
            Box(Modifier.fillMaxWidth()) {
                map(point.first, point.second, accent, type.emoji(), pinAlpha)
                CachePill(
                    color = accent,
                    emoji = type.emoji(),
                    label = name.ifEmpty { stringResource(Res.string.geocache_unnamed) },
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                )
            }
        } else {
            CachePill(
                color = accent,
                emoji = type.emoji(),
                label = name.ifEmpty { stringResource(Res.string.geocache_unnamed) },
                modifier = Modifier.padding(12.dp),
            )
        }

        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            CacheBadges(
                type = type,
                size = size,
                difficulty = difficulty,
                terrain = terrain,
                isArchived = isArchived,
                claimed = claimed,
                isFirstToFind = isFirstToFind,
                isArt = isArt,
                hasMission = mission != null,
                needsVerification = needsVerification,
            )

            if (photo != null) {
                // 87 `image` tags across 60 sampled listings — a cache's photo is usually the
                // hint that actually gets someone to the right tree, and both other clients lead
                // with it. Only the first: the rest belong on a detail screen.
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                )
            }

            if (description.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (mission != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "🗝  $mission",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (hint != null) {
                Spacer(Modifier.height(8.dp))
                SpoilerHint(hint)
            }
        }
    }
}

/**
 * Self-contained card for a found log (NIP-CC kind 7516).
 *
 * The log's own message with a "Found it!" pill, and a proof badge only where [proof] has
 * actually been checked. A log is a claim until something verifies it, so nothing here upgrades
 * an attached verification into a verified one.
 *
 * [cacheName] is what the log is *about*, and without it the card says only "Found it!" — the
 * one thing a reader scrolling a feed already assumed. The cache is named by an `a` tag, so the
 * name costs a lookup the caller has to do anyway; pass null while it is still loading.
 */
@Composable
fun GeocacheFoundLogCard(
    noteEvent: GeocacheFoundLogEvent,
    proof: FoundLogProof = FoundLogProof.NONE,
    cacheName: String? = null,
) {
    val message = remember(noteEvent) { noteEvent.content.trim() }
    val photo =
        remember(noteEvent) {
            noteEvent
                .images()
                .firstOrNull()
                ?.trim()
                ?.ifBlank { null }
        }
    val accent = MaterialTheme.colorScheme.primary

    Column(MaterialTheme.colorScheme.replyModifier) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CachePill(
                color = accent,
                emoji = "🎯",
                label = stringResource(Res.string.geocache_found_it),
            )
            when (proof) {
                FoundLogProof.VALID -> OutlineBadge("✅  " + stringResource(Res.string.geocache_verified_find))
                FoundLogProof.INVALID -> OutlineBadge("⚠️  " + stringResource(Res.string.geocache_invalid_proof))
                FoundLogProof.NONE, FoundLogProof.UNKNOWN -> Unit
            }
        }

        if (cacheName != null || message.isNotEmpty() || photo != null) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (cacheName != null) {
                    Text(
                        text = cacheName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (message.isNotEmpty()) {
                    if (cacheName != null) Spacer(Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (photo != null) {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CacheBadges(
    type: CacheType?,
    size: CacheSize?,
    difficulty: Int?,
    terrain: Int?,
    isArchived: Boolean,
    claimed: Boolean,
    isFirstToFind: Boolean,
    isArt: Boolean,
    hasMission: Boolean,
    needsVerification: Boolean,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        type.labelRes()?.let { OutlineBadge(stringResource(it)) }
        size?.let { OutlineBadge(stringResource(it.labelRes())) }
        difficulty?.let { OutlineBadge(stringResource(Res.string.geocache_difficulty, it)) }
        terrain?.let { OutlineBadge(stringResource(Res.string.geocache_terrain, it)) }

        if (isFirstToFind) OutlineBadge("🥇  " + stringResource(Res.string.geocache_first_to_find))
        if (isArt) OutlineBadge("🎨  " + stringResource(Res.string.geocache_is_art))
        if (hasMission) OutlineBadge("🗝  " + stringResource(Res.string.geocache_mission))
        if (needsVerification) OutlineBadge("🔐  " + stringResource(Res.string.geocache_needs_verification))
        if (claimed) OutlineBadge("🏁  " + stringResource(Res.string.geocache_claimed))
        if (isArchived) OutlineBadge("🗃️  " + stringResource(Res.string.geocache_archived))
    }
}

/**
 * The hint: an invitation until tapped, then the text.
 *
 * Shows *no* hint text while hidden — not even the encoded form. Geocaching clients have always
 * done it this way (Lightning Piggy's detail screen offers "Stuck? Tap to reveal the hint"), and
 * it is the stronger design for two reasons: a wall of ROT13 reads as corruption rather than as
 * a hint, and nothing on screen can spoil a find no matter which way round the publisher
 * encoded it.
 *
 * That last point matters here more than it does for them. Publishers disagree about whether
 * `hint` goes on the wire plain or rotated (see [HintObfuscation]), so picking the readable form
 * is a guess — and with nothing rendered until the tap, a wrong guess can only ever show the
 * wrong text *after* the reader asked for it, never before.
 */
@Composable
private fun SpoilerHint(hint: String) {
    val revealedText = remember(hint) { HintObfuscation.revealed(hint) }
    var revealed by remember(hint) { mutableStateOf(false) }

    Column(Modifier.clickable { revealed = !revealed }) {
        if (revealed) {
            Text(
                text = revealedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = stringResource(Res.string.geocache_hint_tap_to_reveal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}

/** A floating rounded pill: [emoji] + [label] on a [color] background, text auto-contrasted. */
@Composable
private fun CachePill(
    color: Color,
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = color,
        contentColor = if (color.luminance() > 0.55f) Color.Black else Color.White,
        shape = RoundedCornerShape(50),
        shadowElevation = 3.dp,
    ) {
        Text(
            text = "$emoji  $label",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A quiet outlined chip for one rating or state fact. */
@Composable
private fun OutlineBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.subtleBorder,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * The centre of the cache's finest published geohash, or null when it published none.
 *
 * A listing has no `lat`/`lon` tags — unlike a road event — so the geohash is the only location
 * there is, and the finest one is the only one precise enough to walk to.
 */
private fun GeocacheListingEvent.cachePoint(): Pair<Double, Double>? {
    val finest = location() ?: return null
    val decoded = runCatching { finest.toGeoHash() }.getOrNull() ?: return null
    return decoded.centerLat to decoded.centerLon
}
