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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.backups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.diff.ValueChange
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataDiff
import com.vitorpamplona.quartz.nip02FollowList.ContactListDiff
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListDiff
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListDiff
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType

/**
 * The review list of a conflict, laid out for the kind of event that changed: a profile is
 * compared field by field with its images side by side, a NIP-65 list as a relay table with
 * read/write columns, follow and mute lists get a count header. Everything else uses the
 * generic removed / added / changed sections. All layouts contribute lazy items, so lists
 * with hundreds of entries scroll smoothly.
 */
internal fun LazyListScope.eventDiffItems(
    conflict: ReplaceableBackupConflict,
    presentation: DiffPresentation,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (val diff = conflict.diff) {
        is MetadataDiff -> profileDiffItems(diff, presentation, accountViewModel, nav)
        is AdvertisedRelayListDiff -> nip65DiffItems(diff, conflict, nav)
        is ContactListDiff -> {
            countHeader(
                (conflict.saved as? ContactListEvent)?.followCount(),
                (conflict.incoming as? ContactListEvent)?.followCount(),
                R.string.backup_review_follow_counts,
            )
            genericDiffItems(buildRows(presentation), accountViewModel, nav)
        }
        is MuteListDiff -> {
            countHeader(
                (conflict.saved as? MuteListEvent)?.countMutes(),
                (conflict.incoming as? MuteListEvent)?.countMutes(),
                R.string.backup_review_public_mute_counts,
            )
            genericDiffItems(buildRows(presentation), accountViewModel, nav)
        }
        else -> genericDiffItems(buildRows(presentation), accountViewModel, nav)
    }
}

private fun LazyListScope.countHeader(
    saved: Int?,
    incoming: Int?,
    textRes: Int,
) {
    if (saved == null || incoming == null) return
    item(key = "counts", contentType = "counts") {
        Text(
            text = stringRes(textRes, saved.toString(), incoming.toString()),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Profile: each changed field, saved vs new, images shown as images.
// ---------------------------------------------------------------------------------------

private class ProfileField(
    val labelRes: Int,
    val change: ValueChange<String>,
    val isImage: Boolean = false,
)

private fun profileFields(diff: MetadataDiff): List<ProfileField> =
    listOfNotNull(
        diff.picture?.let { ProfileField(R.string.backup_profile_field_picture, it, isImage = true) },
        diff.banner?.let { ProfileField(R.string.backup_profile_field_banner, it, isImage = true) },
        diff.name?.let { ProfileField(R.string.backup_profile_field_name, it) },
        diff.displayName?.let { ProfileField(R.string.backup_profile_field_display_name, it) },
        diff.about?.let { ProfileField(R.string.backup_profile_field_about, it) },
        diff.nip05?.let { ProfileField(R.string.backup_profile_field_nip05, it) },
        diff.website?.let { ProfileField(R.string.backup_profile_field_website, it) },
        diff.lud16?.let { ProfileField(R.string.backup_profile_field_lud16, it) },
        diff.lud06?.let { ProfileField(R.string.backup_profile_field_lud06, it) },
        diff.clinkOffer?.let { ProfileField(R.string.backup_profile_field_clink_offer, it) },
        diff.pronouns?.let { ProfileField(R.string.backup_profile_field_pronouns, it) },
        diff.bot?.let { ProfileField(R.string.backup_profile_field_bot, ValueChange(it.before?.toString(), it.after?.toString())) },
        diff.birthday?.let {
            ProfileField(
                R.string.backup_profile_field_birthday,
                ValueChange(
                    it.before?.let { b -> listOfNotNull(b.year, b.month, b.day).joinToString("-") },
                    it.after?.let { a -> listOfNotNull(a.year, a.month, a.day).joinToString("-") },
                ),
            )
        },
    )

private fun LazyListScope.profileDiffItems(
    diff: MetadataDiff,
    presentation: DiffPresentation,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val fields = profileFields(diff)
    if (fields.isNotEmpty()) {
        item(key = "profile-fields", contentType = "group") { GroupLabel(R.string.backup_entry_profile_field) }
        items(fields, key = { "field-" + it.labelRes }, contentType = { if (it.isImage) "image-field" else "text-field" }) {
            FieldComparison(it, accountViewModel)
        }
    }
    // Identity claims and fields this app doesn't model use the generic sections.
    genericDiffItems(buildRows(presentation, skipGroups = setOf(R.string.backup_entry_profile_field)), accountViewModel, nav)
}

@Composable
private fun FieldComparison(
    field: ProfileField,
    accountViewModel: AccountViewModel,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringRes(field.labelRes), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FieldSide(R.string.backup_review_saved, field.change.before, field.isImage, removed = field.change.after == null, Modifier.weight(1f), accountViewModel)
            FieldSide(R.string.backup_review_new, field.change.after, field.isImage, removed = false, Modifier.weight(1f), accountViewModel)
        }
    }
}

@Composable
private fun FieldSide(
    captionRes: Int,
    value: String?,
    isImage: Boolean,
    removed: Boolean,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val background = if (removed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(8.dp),
    ) {
        Text(stringRes(captionRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.placeholderText)
        when {
            value == null ->
                Text(
                    stringRes(R.string.backup_review_not_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            isImage ->
                MyAsyncImage(
                    imageUrl = value,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    mainImageModifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)),
                    loadedImageModifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)),
                    accountViewModel = accountViewModel,
                    onLoadingBackground = null,
                    onError = { Text(clip(value), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                )
            else -> Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------------------
// NIP-65: a relay table with the saved and new read/write access side by side.
// ---------------------------------------------------------------------------------------

private class RelayRowData(
    val url: String,
    val saved: AdvertisedRelayType?,
    val new: AdvertisedRelayType?,
)

private fun LazyListScope.nip65DiffItems(
    diff: AdvertisedRelayListDiff,
    conflict: ReplaceableBackupConflict,
    nav: INav,
) {
    val rows =
        diff.relays.removed.map { RelayRowData(it.relayUrl.url, it.type, null) } +
            diff.relays.changed.map { RelayRowData(it.before.relayUrl.url, it.before.type, it.after.type) } +
            diff.relays.added.map { RelayRowData(it.relayUrl.url, null, it.type) }

    val savedCount = (conflict.saved as? AdvertisedRelayListEvent)?.relays()?.size ?: 0
    val unchanged = savedCount - diff.relays.removed.size - diff.relays.changed.size

    item(key = "relay-header", contentType = "relay-header") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringRes(R.string.backup_entry_relay), Modifier.weight(2f), style = MaterialTheme.typography.labelLarge)
            Text(stringRes(R.string.backup_review_saved), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(stringRes(R.string.backup_review_new), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        }
    }
    items(rows, key = { "relay-" + it.url }, contentType = { "relay-row" }) { RelayTableRow(it, nav) }
    if (unchanged > 0) {
        item(key = "relay-unchanged", contentType = "note") {
            DetailText(
                stringRes(R.string.backup_review_unchanged_relays, unchanged.toString()),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun RelayTableRow(
    row: RelayRowData,
    nav: INav,
) {
    val removed = row.new == null
    val added = row.saved == null
    val urlColor =
        when {
            removed -> MaterialTheme.colorScheme.error
            added -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.tertiary
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.RelayInfo(row.url)) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.url.removePrefix("wss://").removeSuffix("/"),
            Modifier.weight(2f),
            color = urlColor,
            textDecoration = if (removed) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AccessCell(row.saved, Modifier.weight(1f))
        AccessCell(row.new, Modifier.weight(1f), highlight = row.saved != row.new)
    }
}

@Composable
private fun AccessCell(
    type: AdvertisedRelayType?,
    modifier: Modifier,
    highlight: Boolean = false,
) {
    val text =
        when (type) {
            null -> "—"
            AdvertisedRelayType.BOTH -> stringRes(R.string.backup_review_access_read_write)
            AdvertisedRelayType.READ -> stringRes(R.string.backup_review_access_read)
            AdvertisedRelayType.WRITE -> stringRes(R.string.backup_review_access_write)
        }
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (highlight) FontWeight.Bold else null,
        color = if (type == null) MaterialTheme.colorScheme.placeholderText else Color.Unspecified,
    )
}
