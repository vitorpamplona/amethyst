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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bolt12_lightning_offer
import com.vitorpamplona.amethyst.commons.resources.bolt12_offers
import com.vitorpamplona.amethyst.commons.resources.kc_list_and_more
import com.vitorpamplona.amethyst.commons.resources.kc_list_badge_count
import com.vitorpamplona.amethyst.commons.resources.kc_list_badge_sets
import com.vitorpamplona.amethyst.commons.resources.kc_list_empty
import com.vitorpamplona.amethyst.commons.resources.kc_list_entry_count
import com.vitorpamplona.amethyst.commons.resources.kc_list_expires
import com.vitorpamplona.amethyst.commons.resources.kc_list_external_identities
import com.vitorpamplona.amethyst.commons.resources.kc_list_favorite_follow_sets
import com.vitorpamplona.amethyst.commons.resources.kc_list_gallery_entry
import com.vitorpamplona.amethyst.commons.resources.kc_list_geocache_hunt
import com.vitorpamplona.amethyst.commons.resources.kc_list_link
import com.vitorpamplona.amethyst.commons.resources.kc_list_mint
import com.vitorpamplona.amethyst.commons.resources.kc_list_nests_auth
import com.vitorpamplona.amethyst.commons.resources.kc_list_nutzap_info_off
import com.vitorpamplona.amethyst.commons.resources.kc_list_nutzap_info_on
import com.vitorpamplona.amethyst.commons.resources.kc_list_p2pk_key
import com.vitorpamplona.amethyst.commons.resources.kc_list_public_count_plus_private
import com.vitorpamplona.amethyst.commons.resources.kc_list_relays
import com.vitorpamplona.amethyst.commons.resources.kc_list_status_cleared
import com.vitorpamplona.amethyst.commons.resources.kc_list_status_general
import com.vitorpamplona.amethyst.commons.resources.kc_list_status_music
import com.vitorpamplona.amethyst.commons.resources.kc_list_status_other
import com.vitorpamplona.amethyst.commons.resources.kc_list_sticker_count
import com.vitorpamplona.amethyst.commons.resources.kc_list_sticker_pack
import com.vitorpamplona.amethyst.commons.resources.kc_list_stickers_sensitive_hidden
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_days
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_ended
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_ends
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_invalid
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_last_post
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_live
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_started
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_title
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_active
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_background
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_background_image
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_background_video
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_body_font
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_colors
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_created_by
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_definition
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_incomplete
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_title_font
import com.vitorpamplona.amethyst.commons.resources.kc_list_top_eight
import com.vitorpamplona.amethyst.commons.resources.nip51_list_has_private_members
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.components.util.LongPressCopyText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.AddressMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.LabelMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.LinkMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.PagedMemberColumn
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.UserMemberRow
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.GeocacheHuntRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.Bolt12OffersDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.PaymentTargetChip
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.ProfilePaymentChip
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.abbreviateBolt12Offer
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.badges.BadgeThumb
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.getIdentityClaimDescription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.getIdentityClaimIcon
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.postingStreak.PostingStreakEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.profileTheme.active.ActiveProfileThemeEvent
import com.vitorpamplona.quartz.experimental.profileTheme.definition.ThemeDefinitionEvent
import com.vitorpamplona.quartz.experimental.profileTheme.tags.BackgroundTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.FontTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ThemeColors
import com.vitorpamplona.quartz.experimental.topEight.TopEightEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.tags.references.HttpUrlFormatter
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiListEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.StickerPackEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.IdentityClaimTag
import com.vitorpamplona.quartz.nip39ExtIdentities.UnsupportedIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.identityClaims
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList.FavoriteFollowSetsListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServer
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent
import com.vitorpamplona.quartz.nipF4Podcasts.favorites.FavoritePodcastsListEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/*
 * Cards for the replaceable lists and profile extras a user keeps about themselves: the servers
 * they upload to, the ways to pay them, the badges and themes they wear, their status and streak.
 * These used to fall through to the text-note renderer, which printed an empty content (or a
 * stray description) and hid the tags that are the whole point of the event.
 *
 * Encrypted halves of NIP-51-style lists are never decrypted here: a feed card shows what the
 * author published in the clear and says when there is more it cannot show.
 */

private const val MAX_BADGE_THUMBS = 16
private const val MAX_STICKERS = 24
private val StickerSize = 64.dp

// ---------------------------------------------------------------------------------------------
// Shared list frame
// ---------------------------------------------------------------------------------------------

/**
 * A [KindCard] whose body is a paged column of members: the icon and the list's name on top, how
 * many public entries it holds under it, then the first few members with a "Show more".
 */
@Composable
private fun <T> ListKindCard(
    icon: MaterialSymbol,
    title: String,
    items: List<T>,
    hasPrivateItems: Boolean,
    backgroundColor: MutableState<Color>,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    KindCard(
        icon = icon,
        title = title,
        subtitle = listCountLine(items.size, hasPrivateItems),
    ) {
        header?.invoke(this)

        if (items.isEmpty() && hasPrivateItems) {
            Text(
                text = stringRes(Res.string.nip51_list_has_private_members),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PagedMemberColumn(items, backgroundColor, Modifier.fillMaxWidth(), itemContent = itemContent)
    }
}

@Composable
private fun listCountLine(
    count: Int,
    hasPrivateItems: Boolean,
): String? =
    when {
        count == 0 && hasPrivateItems -> null
        count == 0 -> stringRes(Res.string.kc_list_empty)
        hasPrivateItems -> pluralStringRes(Res.plurals.kc_list_public_count_plus_private, count, count)
        else -> pluralStringRes(Res.plurals.kc_list_entry_count, count, count)
    }

// ---------------------------------------------------------------------------------------------
// Emoji and stickers
// ---------------------------------------------------------------------------------------------

/** NIP-51 kind 10030: the emoji packs this user picked, each drawn as the pack it points at. */
@Composable
fun RenderEmojiList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? EmojiListEvent ?: return
    val packs = remember(event) { event.emojiPacks() }

    ListKindCard(MaterialSymbols.EmojiEmotions, kindName(event.kind), packs, false, backgroundColor) { address ->
        key(address.toValue()) {
            AddressMemberRow(address, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

/** Kind 30031: a sticker pack — its name, description, and a grid of its safe-for-work stickers. */
@Composable
fun RenderStickerPack(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? StickerPackEvent ?: return

    val stickers = remember(event) { event.stickers() }
    val safe = remember(stickers) { stickers.filter { !it.isNsfw() } }
    val hiddenCount = stickers.size - safe.size
    val description = remember(event) { event.description() }

    KindCard(
        icon = MaterialSymbols.AddReaction,
        title = event.title()?.ifBlank { null } ?: event.dTag().ifBlank { null } ?: stringRes(Res.string.kc_list_sticker_pack),
        subtitle = pluralStringRes(Res.plurals.kc_list_sticker_count, stickers.size, stickers.size),
    ) {
        KindCardText(baseNote, description, backgroundColor, accountViewModel, nav)

        if (accountViewModel.settings.showImages()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                safe.take(MAX_STICKERS).forEach { sticker ->
                    AsyncImage(
                        model = sticker.url,
                        contentDescription = sticker.description ?: sticker.code,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(StickerSize).clip(RoundedCornerShape(8.dp)),
                    )
                }
                if (safe.size > MAX_STICKERS) {
                    MoreCountBox(safe.size - MAX_STICKERS, StickerSize)
                }
            }
        } else {
            KindCardChips(safe.take(MAX_STICKERS).map { ":${it.code}:" })
        }

        if (hiddenCount > 0) {
            Text(
                text = pluralStringRes(Res.plurals.kc_list_stickers_sensitive_hidden, hiddenCount, hiddenCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MoreCountBox(
    count: Int,
    size: Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringRes(Res.string.kc_list_and_more, count),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Badges
// ---------------------------------------------------------------------------------------------

/** NIP-58 kind 10008: the badges this user chose to show on their profile, as a strip of images. */
@Composable
fun RenderProfileBadges(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ProfileBadgesEvent ?: return

    val definitions = remember(event) { event.badgeAwardDefinitions() }
    val sets = remember(event) { event.badgeSets().map { it.dTag } }

    KindCard(
        icon = MaterialSymbols.MilitaryTech,
        title = kindName(event.kind),
        subtitle = pluralStringRes(Res.plurals.kc_list_badge_count, definitions.size, definitions.size),
    ) {
        BadgeThumbStrip(definitions, accountViewModel, nav)
        if (sets.isNotEmpty()) {
            KindCardField(stringRes(Res.string.kc_list_badge_sets), sets.joinToString(", "))
        }
    }
}

/**
 * NIP-58 kind 30008: either the legacy `profile_badges` list (the old form of kind 10008) or a
 * named NIP-51 badge set with its own title and description.
 */
@Composable
fun RenderAcceptedBadgeSet(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? AcceptedBadgeSetEvent ?: return

    val definitions = remember(event) { event.badgeAwardDefinitions() }
    val title =
        if (event.isLegacyProfileBadges()) {
            kindName(event.kind)
        } else {
            event.title()?.ifBlank { null } ?: event.dTag().ifBlank { null } ?: kindName(event.kind)
        }

    KindCard(
        icon = MaterialSymbols.MilitaryTech,
        title = title,
        subtitle = pluralStringRes(Res.plurals.kc_list_badge_count, definitions.size, definitions.size),
    ) {
        if (!event.isLegacyProfileBadges()) {
            KindCardText(baseNote, event.description(), backgroundColor, accountViewModel, nav)
        }
        BadgeThumbStrip(definitions, accountViewModel, nav)
    }
}

/** The badge definitions at [definitions] as clickable thumbnails, capped at [MAX_BADGE_THUMBS]. */
@Composable
private fun BadgeThumbStrip(
    definitions: List<Address>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (definitions.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        definitions.take(MAX_BADGE_THUMBS).forEach { address ->
            key(address.toValue()) {
                LoadAddressableNote(address) { note ->
                    note?.let { BadgeThumb(it, accountViewModel, nav) }
                }
            }
        }
        if (definitions.size > MAX_BADGE_THUMBS) {
            Text(
                text = stringRes(Res.string.kc_list_and_more, definitions.size - MAX_BADGE_THUMBS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Server and relay lists
// ---------------------------------------------------------------------------------------------

/** Kind 10063: the Blossom servers this user uploads media to. */
@Composable
fun RenderBlossomServers(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
) {
    val event = baseNote.event as? BlossomServersEvent ?: return
    val servers = remember(event) { event.servers() }
    ServerListCard(MaterialSymbols.CloudUpload, kindName(event.kind), servers, backgroundColor)
}

/** NIP-96 kind 10096: the HTTP file storage servers this user uploads to. */
@Composable
fun RenderFileServers(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
) {
    val event = baseNote.event as? FileServersEvent ?: return
    val servers = remember(event) { event.servers() }
    ServerListCard(MaterialSymbols.Storage, kindName(event.kind), servers, backgroundColor)
}

@Composable
private fun ServerListCard(
    icon: MaterialSymbol,
    title: String,
    servers: List<String>,
    backgroundColor: MutableState<Color>,
) {
    ListKindCard(icon, title, servers, false, backgroundColor) { server ->
        key(server) { LabelMemberRow(HttpUrlFormatter.displayUrl(server)) }
    }
}

/** Kind 10112: the audio-room (nests) MoQ servers this user hosts rooms on, each with its auth host. */
@Composable
fun RenderNestsServers(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
) {
    val event = baseNote.event as? NestsServersEvent ?: return
    val servers = remember(event) { event.servers() }

    ListKindCard(MaterialSymbols.Mic, kindName(event.kind), servers, false, backgroundColor) { server ->
        key(server.relay) { NestsServerRow(server) }
    }
}

@Composable
private fun NestsServerRow(server: NestsServer) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(
            text = HttpUrlFormatter.displayUrl(server.relay),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringRes(Res.string.kc_list_nests_auth, HttpUrlFormatter.displayUrl(server.auth)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** NIP-37 kind 10013: the relays this user stores drafts on. Usually all private, never decrypted here. */
@Composable
fun RenderPrivateOutboxRelays(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
) {
    val event = baseNote.event as? PrivateOutboxRelayListEvent ?: return
    val relays = remember(event) { event.publicRelays() }

    ListKindCard(MaterialSymbols.Drafts, kindName(event.kind), relays, event.content.isNotEmpty(), backgroundColor) { relay ->
        key(relay.url) { LabelMemberRow(relay.displayUrl()) }
    }
}

// ---------------------------------------------------------------------------------------------
// Payments
// ---------------------------------------------------------------------------------------------

/** NIP-61 kind 10019: the mints, relays and locking key a user accepts nutzaps with. */
@Composable
fun RenderNutzapInfo(baseNote: Note) {
    val event = baseNote.event as? NutzapInfoEvent ?: return

    val mints = remember(event) { event.mints() }
    val relays = remember(event) { event.relays().map { it.displayUrl() } }
    val p2pk = remember(event) { event.p2pkPubkey() }
    val accepts = mints.isNotEmpty() && p2pk != null

    KindCard(
        icon = MaterialSymbols.Paid,
        title = stringRes(if (accepts) Res.string.kc_list_nutzap_info_on else Res.string.kc_list_nutzap_info_off),
        iconTint = if (accepts) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        mints.forEach { mint ->
            val units = if (mint.units.isEmpty()) "" else " (${mint.units.joinToString(", ")})"
            KindCardField(stringRes(Res.string.kc_list_mint), HttpUrlFormatter.displayUrl(mint.mintUrl) + units, maxLines = 1)
        }
        if (relays.isNotEmpty()) {
            KindCardField(stringRes(Res.string.kc_list_relays), relays.joinToString(", "))
        }
        KindCardField(stringRes(Res.string.kc_list_p2pk_key), p2pk?.toShortDisplay(), monospace = true, maxLines = 1)
    }
}

/** NIP-A3 kind 10133: where to pay this user, as the same chips the profile shows. */
@Composable
fun RenderPaymentTargets(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? PaymentTargetsEvent ?: return
    val author = baseNote.author ?: return
    val targets = remember(event) { event.paymentTargets() }

    KindCard(
        icon = MaterialSymbols.Payment,
        title = kindName(event.kind),
        subtitle = listCountLine(targets.size, false),
    ) {
        if (targets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                targets.forEach { target -> PaymentTargetChip(author, target, accountViewModel, nav) }
            }
        }
    }
}

/** NIP-B1 kind 10058: the BOLT12 offers this user can be paid at; a chip opens the pay/copy dialog. */
@Composable
fun RenderBolt12OfferList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val event = baseNote.event as? Bolt12OfferListEvent ?: return
    val offers = remember(event) { event.offers() }
    var dialogOffer by remember { mutableStateOf<String?>(null) }

    KindCard(
        icon = MaterialSymbols.Bolt,
        title = stringRes(Res.string.bolt12_offers),
        subtitle = listCountLine(offers.size, false),
        iconTint = BitcoinOrange,
    ) {
        if (offers.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                offers.forEach { offer ->
                    ProfilePaymentChip(
                        color = BitcoinOrange,
                        label = stringRes(Res.string.bolt12_lightning_offer),
                        detail = remember(offer) { abbreviateBolt12Offer(offer) },
                        copyValue = offer,
                        onClick = { dialogOffer = offer },
                    ) {
                        Icon(
                            symbol = MaterialSymbols.Bolt,
                            contentDescription = null,
                            tint = BitcoinOrange,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }

    dialogOffer?.let { offer ->
        Bolt12OffersDialog(
            offers = listOf(offer),
            accountViewModel = accountViewModel,
            onDismiss = { dialogOffer = null },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Lists of things to follow
// ---------------------------------------------------------------------------------------------

/** NIP-F4 kind 10054: the podcasts this user listens to; the private half stays encrypted. */
@Composable
fun RenderFavoritePodcasts(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? FavoritePodcastsListEvent ?: return
    val podcasts = remember(event) { event.publicFavorites().map { it.pubKey } }
    PodcastListCard(kindName(event.kind), podcasts, event.content.isNotEmpty(), quotesLeft, backgroundColor, accountViewModel, nav)
}

/** NIP-F4 kind 10064: the podcasts this user says they make, confirming each show's author claim. */
@Composable
fun RenderAuthoredPodcasts(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? AuthoredPodcastsEvent ?: return
    val podcasts = remember(event) { event.authoredKeys() }
    PodcastListCard(kindName(event.kind), podcasts, false, quotesLeft, backgroundColor, accountViewModel, nav)
}

@Composable
private fun PodcastListCard(
    title: String,
    podcastKeys: List<HexKey>,
    hasPrivateItems: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ListKindCard(MaterialSymbols.Podcasts, title, podcastKeys, hasPrivateItems, backgroundColor) { podcast ->
        key(podcast) {
            AddressMemberRow(PodcastMetadataEvent.createAddress(podcast), quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

/** NIP-51 kind 10021: other people's (or the user's own) follow sets kept at hand. */
@Composable
fun RenderFavoriteFollowSets(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? FavoriteFollowSetsListEvent ?: return
    val sets = remember(event) { event.publicFavoriteFollowSets() }

    ListKindCard(
        MaterialSymbols.Groups,
        stringRes(Res.string.kc_list_favorite_follow_sets),
        sets,
        event.content.isNotEmpty(),
        backgroundColor,
    ) { set ->
        key(set.address.toValue()) {
            AddressMemberRow(set.address, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

/** NIP-72 kind 10004: the moderated communities this user follows. */
@Composable
fun RenderCommunityList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CommunityListEvent ?: return
    val communities = remember(event) { event.publicCommunities() }

    ListKindCard(MaterialSymbols.Forum, kindName(event.kind), communities, event.content.isNotEmpty(), backgroundColor) { community ->
        key(community.address.toValue()) {
            AddressMemberRow(community.address, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

/** Kind 18678: the eight people this user ranks first on their profile, in order. */
@Composable
fun RenderTopEight(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? TopEightEvent ?: return
    val ranked = remember(event) { event.rankedKeys().withIndex().toList() }

    ListKindCard(MaterialSymbols.Star, stringRes(Res.string.kc_list_top_eight), ranked, false, backgroundColor) { entry ->
        key(entry.value) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (entry.index + 1).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp),
                )
                Box(Modifier.weight(1f)) { UserMemberRow(entry.value, accountViewModel, nav) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Identity and presence
// ---------------------------------------------------------------------------------------------

/** NIP-39 kind 10011: accounts on other platforms this user claims, each linking to its proof. */
@Composable
fun RenderExternalIdentities(baseNote: Note) {
    val event = baseNote.event as? ExternalIdentitiesEvent ?: return
    val claims = remember(event) { event.identityClaims() }

    KindCard(
        icon = MaterialSymbols.AlternateEmail,
        title = stringRes(Res.string.kc_list_external_identities),
        subtitle = listCountLine(claims.size, false),
    ) {
        claims.forEach { claim -> IdentityClaimRow(claim) }
    }
}

@Composable
private fun IdentityClaimRow(claim: IdentityClaimTag) {
    val uri = LocalUriHandler.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (claim is UnsupportedIdentity) {
            Icon(
                symbol = MaterialSymbols.Link,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = claim.platformIdentity(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Icon(
                painter = painterRes(getIdentityClaimIcon(claim), 0),
                contentDescription = stringRes(getIdentityClaimDescription(claim)),
                tint = Color.Unspecified,
                modifier = Modifier.size(18.dp),
            )
            LongPressCopyText(
                displayText = claim.identity,
                copyValue = claim.identity,
                onClick = { runCatching { uri.openUri(claim.toProofUrl()) } },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}

/**
 * NIP-38 kind 30315: what the user is up to (general) or listening to (music), with the link or
 * the note, profile or address the status points at, and when it expires.
 */
@Composable
fun RenderUserStatus(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? UserStatusEvent ?: return

    val type = remember(event) { event.dTag() }
    val isCleared = remember(event) { event.isCleared() }
    val text =
        remember(event) {
            val emoji = event.statusEmoji()
            when {
                emoji == null -> event.content
                event.content.isBlank() -> emoji
                else -> "$emoji ${event.content}"
            }
        }
    val url = remember(event) { event.firstTaggedUrl()?.ifBlank { null } }
    val expiration = remember(event) { event.expiration() }
    val profile = remember(event) { event.referencedProfile() }
    val eventId = remember(event) { event.referencedEventId() }
    val address = remember(event) { event.referencedAddressId()?.let { Address.parse(it) } }

    val title =
        when {
            isCleared -> stringRes(Res.string.kc_list_status_cleared)
            type == UserStatusEvent.MUSIC -> stringRes(Res.string.kc_list_status_music)
            type == UserStatusEvent.GENERAL || type.isBlank() -> stringRes(Res.string.kc_list_status_general)
            else -> stringRes(Res.string.kc_list_status_other, type)
        }

    KindCard(
        icon = if (type == UserStatusEvent.MUSIC) MaterialSymbols.MusicNote else MaterialSymbols.Face,
        title = title,
    ) {
        if (!isCleared) {
            KindCardText(baseNote, text, backgroundColor, accountViewModel, nav)
        }
        KindCardField(stringRes(Res.string.kc_list_link), url, maxLines = 1)
        KindCardField(
            stringRes(Res.string.kc_list_expires),
            expiration?.let { formatDateTime(it * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT) },
        )
        profile?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        when {
            eventId != null -> KindCardQuoteEvent(eventId, quotesLeft, backgroundColor, accountViewModel, nav)
            address != null -> KindCardQuoteAddress(address, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

/** Kind 13473: the author's self-reported run of daily posts, live or broken. */
@Composable
fun RenderPostingStreak(baseNote: Note) {
    val event = baseNote.event as? PostingStreakEvent ?: return

    val now = remember(event) { TimeUtils.now() }
    val streak = remember(event) { event.streak(now) }

    if (streak == null) {
        KindCard(
            icon = MaterialSymbols.LocalFireDepartment,
            title = stringRes(Res.string.kc_list_streak_title),
            subtitle = stringRes(Res.string.kc_list_streak_invalid),
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val isLive = streak.isLive(now)
    val days = streak.days().toInt()

    KindCard(
        icon = MaterialSymbols.LocalFireDepartment,
        title = pluralStringRes(Res.plurals.kc_list_streak_days, days, days),
        subtitle = stringRes(if (isLive) Res.string.kc_list_streak_live else Res.string.kc_list_streak_ended),
        iconTint = if (isLive) BitcoinOrange else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        KindCardField(
            stringRes(Res.string.kc_list_streak_started),
            formatDateTime(streak.start * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE),
        )
        KindCardField(
            stringRes(Res.string.kc_list_streak_last_post),
            formatDateTime(streak.end * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT),
        )
        if (isLive) {
            KindCardField(
                stringRes(Res.string.kc_list_streak_ends),
                formatDateTime(streak.expiresAt() * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Profile themes
// ---------------------------------------------------------------------------------------------

/** What the two Ditto theme kinds share, read once so one card draws both. */
private class ThemeInfo(
    val colors: ThemeColors?,
    val title: String?,
    val description: String?,
    val titleFont: FontTag?,
    val bodyFont: FontTag?,
    val background: BackgroundTag?,
)

/** Kind 16767: the theme this user's profile is shown in, credited to its creator when worn from someone else. */
@Composable
fun RenderActiveProfileTheme(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ActiveProfileThemeEvent ?: return
    val info =
        remember(event) {
            ThemeInfo(event.colors(), event.title(), event.description(), event.titleFont(), event.bodyFont(), event.background())
        }
    val creator = remember(event) { if (event.isWornFromAnotherUser()) event.creatorPubKey() else null }

    ThemeCard(baseNote, stringRes(Res.string.kc_list_theme_active), info, creator, backgroundColor, accountViewModel, nav)
}

/** Kind 36767: a theme someone published for others to wear. */
@Composable
fun RenderThemeDefinition(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ThemeDefinitionEvent ?: return
    val info =
        remember(event) {
            ThemeInfo(event.colors(), event.title(), event.description(), event.titleFont(), event.bodyFont(), event.background())
        }
    val creator = remember(event) { event.creator()?.pubKey?.takeIf { it != event.pubKey } }

    ThemeCard(baseNote, stringRes(Res.string.kc_list_theme_definition), info, creator, backgroundColor, accountViewModel, nav)
}

@Composable
private fun ThemeCard(
    baseNote: Note,
    kindLabel: String,
    info: ThemeInfo,
    creator: HexKey?,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val themeTitle = info.title?.ifBlank { null }

    KindCard(
        icon = MaterialSymbols.BrightnessMedium,
        title = themeTitle ?: kindLabel,
        subtitle =
            if (info.colors == null) {
                stringRes(Res.string.kc_list_theme_incomplete)
            } else if (themeTitle != null) {
                kindLabel
            } else {
                null
            },
    ) {
        info.colors?.let { ThemeSwatch(it, themeTitle ?: kindLabel) }
        KindCardText(baseNote, info.description, backgroundColor, accountViewModel, nav)
        info.colors?.let {
            KindCardField(
                stringRes(Res.string.kc_list_theme_colors),
                "${it.background.toHex()}  ${it.text.toHex()}  ${it.primary.toHex()}",
                monospace = true,
                maxLines = 1,
            )
        }
        KindCardField(stringRes(Res.string.kc_list_theme_title_font), info.titleFont?.family, maxLines = 1)
        KindCardField(stringRes(Res.string.kc_list_theme_body_font), info.bodyFont?.family, maxLines = 1)
        info.background?.let {
            KindCardField(
                stringRes(Res.string.kc_list_theme_background),
                stringRes(if (it.isVideo()) Res.string.kc_list_theme_background_video else Res.string.kc_list_theme_background_image),
            )
        }
        if (creator != null) {
            Text(
                text = stringRes(Res.string.kc_list_theme_created_by),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KindCardUsers(listOf(creator), accountViewModel, nav)
        }
    }
}

/** A small preview of the theme: its title in the text color on the background, beside a primary-color pill. */
@Composable
private fun ThemeSwatch(
    colors: ThemeColors,
    label: String,
) {
    val shape = RoundedCornerShape(12.dp)
    val background = remember(colors) { Color(colors.background.toArgb()) }
    val text = remember(colors) { Color(colors.text.toArgb()) }
    val primary = remember(colors) { Color(colors.primary.toArgb()) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(background)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = text,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(width = 40.dp, height = 20.dp)
                .clip(RoundedCornerShape(50))
                .background(primary),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Gallery, bookmarks, hunts
// ---------------------------------------------------------------------------------------------

/**
 * Kind 1163: a picture the author pinned to their profile gallery. The picture and its caption;
 * the post it was taken from is quoted only when the entry carries no picture of its own, so the
 * same image is not drawn twice.
 */
@Composable
fun RenderProfileGalleryEntry(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ProfileGalleryEntryEvent ?: return

    val media = remember(event) { event.toMediaContents(baseNote) }
    val summary = remember(event) { event.summary() }
    val fromEvent = remember(event) { event.fromEvent() }

    KindCard(
        icon = MaterialSymbols.PhotoLibrary,
        title = stringRes(Res.string.kc_list_gallery_entry),
    ) {
        media.firstOrNull()?.let { first ->
            SensitivityWarning(note = baseNote, accountViewModel = accountViewModel) {
                ZoomableContentView(
                    content = first,
                    images = media,
                    roundedCorner = true,
                    contentScale = ContentScale.FillWidth,
                    accountViewModel = accountViewModel,
                )
            }
        }
        KindCardText(baseNote, summary, backgroundColor, accountViewModel, nav)
        if (media.isEmpty() && fromEvent != null) {
            KindCardQuoteEvent(fromEvent, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

private fun ProfileGalleryEntryEvent.toMediaContents(note: Note): ImmutableList<BaseMediaContent> {
    val mimeType = mimeType()
    val description = summary()
    val uri = note.toNostrUri()
    return urls()
        .map { url ->
            if (RichTextParser.classifyMedia(url, mimeType) == MediaContentKind.VIDEO) {
                MediaUrlVideo(
                    url = url,
                    description = description,
                    hash = hash(),
                    dim = dimensions(),
                    uri = uri,
                    blurhash = blurhash(),
                    mimeType = mimeType,
                    thumbhash = thumbhash(),
                )
            } else {
                MediaUrlImage(
                    url = url,
                    description = description,
                    hash = hash(),
                    blurhash = blurhash(),
                    dim = dimensions(),
                    uri = uri,
                    mimeType = mimeType,
                    thumbhash = thumbhash(),
                )
            }
        }.toImmutableList()
}

/** NIP-B0 kind 39701: a saved web page — its title, the link (with a preview), the note and labels. */
@Composable
fun RenderWebBookmark(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? WebBookmarkEvent ?: return

    val url = remember(event) { event.url() }
    val title = remember(event) { event.title()?.ifBlank { null } ?: HttpUrlFormatter.displayUrl(url) }
    val hashtags = remember(event) { event.hashtags().map { "#$it" } }

    KindCard(
        icon = MaterialSymbols.Bookmark,
        title = title,
        subtitle = kindName(event.kind),
    ) {
        LinkMemberRow(url, accountViewModel, nav)
        KindCardText(baseNote, event.description(), backgroundColor, accountViewModel, nav)
        KindCardChips(hashtags)
    }
}

/** NIP-CC kind 37517: a geocache hunt, drawn as the same row the Hunts tab uses, with progress. */
@Composable
fun RenderGeocacheCurationList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GeocacheCurationListEvent ?: return

    KindCard(
        icon = MaterialSymbols.Hiking,
        title = stringRes(Res.string.kc_list_geocache_hunt),
    ) {
        GeocacheHuntRow(baseNote, event, accountViewModel, nav)
    }
}
