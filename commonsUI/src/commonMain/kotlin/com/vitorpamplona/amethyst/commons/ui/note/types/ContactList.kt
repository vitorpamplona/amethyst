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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeForUser
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.contact_list_containing_label
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GalleryUnloaded
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayContactList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? ContactListEvent ?: return

    // NIP-02 defines only `p` tags: `["p", <key>, <relay>, <petname>]`.
    val follows = remember(noteEvent) { noteEvent.verifiedFollowKeySet().toImmutableList() }
    val petnames =
        remember(noteEvent) {
            noteEvent
                .follows()
                .mapNotNull { tag ->
                    tag.petname
                        ?.trim()
                        ?.ifBlank { null }
                        ?.let { tag.pubKey to it }
                }.distinctBy { it.first }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.ContactListUsers(baseNote.idHex)) }
                .padding(10.dp),
        verticalArrangement = SpacedBy5dp,
    ) {
        Text(
            text = pluralStringRes(Res.plurals.contact_list_containing_label, follows.size, follows.size),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        GalleryUnloaded(follows, Modifier, accountViewModel, nav)

        // The local names the author gave some of them (NIP-02's petname scheme).
        if (petnames.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                petnames.take(MAX_PETNAMES).forEach { (pubKey, petname) -> PetnameChip(pubKey, petname, accountViewModel, nav) }
            }
            KindCardMore(petnames.size - MAX_PETNAMES)
        }
    }
}

private const val MAX_PETNAMES = 6

/** A follow under the petname the list's author gave them: their picture and that name, opening their profile. */
@Composable
private fun PetnameChip(
    pubKey: HexKey,
    petname: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .clickable { nav.nav(routeForUser(pubKey)) }
                .padding(start = 2.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
    ) {
        UserPicture(pubKey, 22.dp, Modifier.clip(CircleShape), accountViewModel, nav)
        Text(
            text = petname,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
fun DisplayContactListPreview() {
    val accountViewModel = mockAccountViewModel()

    val contactList =
        ContactListEvent(
            id = "1d2f70ad06b65c4d3582d05dbc1e58fcf5b1d4b39a4ce4d54f3eeb1cb1b9b9aa",
            pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
            createdAt = 1761736286,
            tags =
                arrayOf(
                    arrayOf("p", "3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da"),
                    arrayOf("p", "9a9a4aa0e43e57873380ab22e8a3df12f3c4cf5bb3a804c6e3fed0069a6e2740"),
                    arrayOf("p", "4f5dd82517b11088ce00f23d99f06fe8f3e2e45ecf47bc9c2f90f34d5c6f7382"),
                    arrayOf("p", "ac92102a2ecb873c488e0125354ef5a97075a16198668c360eda050007ed42cd"),
                    arrayOf("p", "47f54409a4620eb35208a3bc1b53555bf3d0656b246bf0471a93208e20672f6f"),
                    arrayOf("p", "2624911545afb7a2b440cf10f5c69308afa33aae26fca664d8c94623dc0f1baf"),
                    arrayOf("p", "6641f26f5c59f7010dbe3e42e4593398e27c087497cb7d20e0e7633a17e48a94"),
                    arrayOf("p", "ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b"),
                    arrayOf("p", "7eb29c126b3628077e2e3d863b917a56b74293aa9d8a9abc26a40ba3f2866baf"),
                ),
            content = "",
            sig = "",
        )

    LocalCache.justConsume(contactList, null, false)

    ThemeComparisonColumn {
        DisplayContactList(
            baseNote = LocalCache.getOrCreateAddressableNote(contactList.address()),
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}
