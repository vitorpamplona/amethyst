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
package com.vitorpamplona.amethyst.ui.note.types

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponsesCache
import com.vitorpamplona.amethyst.commons.model.nip88Polls.TallyResults
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.note.showCount
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BigPadding
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.SmallishBorder
import com.vitorpamplona.amethyst.ui.theme.SpacedBy10dp
import com.vitorpamplona.amethyst.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.collections.map

@Composable
fun RenderPoll(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? PollEvent ?: return

    if (makeItShort && accountViewModel.isLoggedUser(note.author)) {
        Text(
            text = noteEvent.content,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        SensitivityWarning(
            note = note,
            accountViewModel = accountViewModel,
        ) {
            InnerRenderPoll(noteEvent, note, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

@Composable
fun InnerRenderPoll(
    event: PollEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val tags = remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }
    val callbackUri = remember(note) { note.toNostrUri() }

    Column(
        verticalArrangement = SpacedBy5dp,
    ) {
        TranslatableRichTextViewer(
            content = event.content,
            canPreview = canPreview && !makeItShort,
            quotesLeft = quotesLeft,
            modifier = Modifier.fillMaxWidth(),
            tags = tags,
            backgroundColor = backgroundColor,
            id = note.idHex,
            callbackUri = callbackUri,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        RenderPollCard(
            event = event,
            pollState = note.pollState(),
            accountViewModel = accountViewModel,
            galleryUser = { user ->
                ClickableUserPicture(
                    user,
                    Size25dp,
                    accountViewModel,
                    onClick = {
                        nav.nav { routeFor(user) }
                    },
                )
            },
        ) { code, label ->
            TranslatableRichTextViewer(
                content = label,
                canPreview = canPreview,
                quotesLeft = 1,
                modifier = Modifier.fillMaxWidth(),
                tags = tags,
                backgroundColor = backgroundColor,
                id = note.idHex + code,
                callbackUri = callbackUri,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        if (event.hasHashtags()) {
            DisplayUncitedHashtags(event, event.content, callbackUri, accountViewModel, nav)
        }
    }
}

@Stable
class PollCard(
    val options: List<PollItemCard>,
    val type: PollType,
    val endsAt: Long?,
    val isMyPoll: Boolean = false,
    val haveIVotedFlow: Flow<Boolean>,
    val haveIVoted: () -> Boolean,
) {
    fun hasEnded() = endsAt != null && endsAt < TimeUtils.now()
}

@Stable
class PollItemCard(
    val code: String,
    val label: String,
    val results: Flow<TallyResults>,
    val currentResults: () -> TallyResults,
)

@Composable
fun RenderPollCard(
    event: PollEvent,
    pollState: PollResponsesCache,
    accountViewModel: AccountViewModel,
    galleryUser: @Composable RowScope.(user: User) -> Unit,
    labelContent: @Composable ColumnScope.(code: String, label: String) -> Unit,
) {
    val card =
        remember(event) {
            PollCard(
                options =
                    event.options().map { option ->
                        PollItemCard(
                            code = option.code,
                            label = option.label,
                            results =
                                pollState.tallyFlow(
                                    option.code,
                                    accountViewModel.account.pubKey,
                                    accountViewModel.account.allFollows.flow
                                        .map { it.authors },
                                ),
                            currentResults = {
                                pollState.currentTally(
                                    option.code,
                                    accountViewModel.account.pubKey,
                                    accountViewModel.account.allFollows.flow.value.authors,
                                )
                            },
                        )
                    },
                type = event.pollType(),
                endsAt = event.endsAt(),
                isMyPoll = event.pubKey == accountViewModel.account.pubKey,
                haveIVotedFlow = pollState.hasPubKeyVotedFlow(accountViewModel.account.userProfile()),
                haveIVoted = {
                    pollState.hasPubKeyVoted(accountViewModel.account.userProfile())
                },
            )
        }

    RenderPollCard(
        card = card,
        onRespond = { responses ->
            accountViewModel.launchSigner {
                accountViewModel.account.pollRespond(event, responses)
            }
        },
        resultContent = galleryUser,
        labelContent = labelContent,
    )
}

@Composable
fun RenderPollCard(
    card: PollCard,
    onRespond: (Set<String>) -> Unit,
    resultContent: @Composable RowScope.(user: User) -> Unit,
    labelContent: @Composable ColumnScope.(code: String, label: String) -> Unit,
) {
    Column(
        verticalArrangement = SpacedBy5dp,
    ) {
        if (card.isMyPoll) {
            RenderResults(card, resultContent, labelContent)
        } else {
            val haveIVoted = card.haveIVoted()
            if (haveIVoted) {
                RenderResults(card, resultContent, labelContent)
            } else {
                // waits for vote
                val haveIVoted by card.haveIVotedFlow.collectAsStateWithLifecycle(haveIVoted)
                if (haveIVoted) {
                    RenderResults(card, resultContent, labelContent)
                } else if (card.hasEnded()) {
                    RenderResults(card, resultContent, labelContent)
                } else {
                    when (card.type) {
                        PollType.SINGLE_CHOICE -> RenderSingleChoiceOptions(card, labelContent, onRespond)
                        PollType.MULTI_CHOICE -> RenderMultiChoiceOptions(card, labelContent, onRespond)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RenderSingleChoiceOptions(
    card: PollCard,
    labelContent: @Composable (ColumnScope.(String, String) -> Unit),
    onRespond: (Set<String>) -> Unit,
) {
    card.options.forEach {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(SmallishBorder)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.placeholderText,
                        shape = SmallishBorder,
                    ).clickable {
                        onRespond(setOf(it.code))
                    },
        ) {
            Row(
                modifier = BigPadding,
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                ) {
                    labelContent(it.code, it.label)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RenderMultiChoiceOptions(
    card: PollCard,
    labelContent: @Composable (ColumnScope.(String, String) -> Unit),
    onRespond: (Set<String>) -> Unit,
) {
    var multichoice by
        remember {
            mutableStateOf<Set<String>>(emptySet())
        }

    card.options.forEach { option ->
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(SmallishBorder)
                    .border(1.dp, MaterialTheme.colorScheme.grayText, SmallishBorder)
                    .clickable {
                        multichoice += option.code
                    },
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = option.code in multichoice,
                    onCheckedChange = { checked ->
                        if (checked) {
                            multichoice += option.code
                        } else {
                            multichoice -= option.code
                        }
                    },
                )

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    labelContent(option.code, option.label)
                }
            }
        }
    }

    Button(
        onClick = {
            onRespond(multichoice)
        },
        modifier = Modifier.align(Alignment.End),
        enabled = multichoice.isNotEmpty(),
    ) {
        Text("Submit")
    }
}

@Composable
private fun RenderResults(
    card: PollCard,
    resultContent: @Composable RowScope.(user: User) -> Unit,
    labelContent: @Composable (ColumnScope.(code: String, label: String) -> Unit),
) {
    card.options.forEach { pollItem ->
        RenderClosedItem(pollItem, resultContent) {
            labelContent(pollItem.code, pollItem.label)
        }
    }
}

@Composable
private fun RenderClosedItem(
    item: PollItemCard,
    resultContent: @Composable RowScope.(user: User) -> Unit,
    labelContent: @Composable ColumnScope.() -> Unit,
) {
    val tally by item.results.collectAsStateWithLifecycle(item.currentResults())

    RenderClosedItem(tally, resultContent, labelContent)
}

@Composable
private fun RenderClosedItem(
    tally: TallyResults,
    resultContent: @Composable RowScope.(user: User) -> Unit,
    labelContent: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(SmallishBorder)
                .border(
                    width = 1.dp,
                    color =
                        if (tally.isWinning) {
                            MaterialTheme.colorScheme.allGoodColor
                        } else {
                            MaterialTheme.colorScheme.grayText
                        },
                    shape = SmallishBorder,
                ).background(
                    if (tally.isWinning) {
                        MaterialTheme.colorScheme.allGoodColor.copy(0.2f)
                    } else {
                        MaterialTheme.colorScheme.subtleBorder
                    },
                ),
    ) {
        // Animate the progress bar when a vote is cast
        val animatedProgress by animateFloatAsState(
            targetValue = tally.percent,
            animationSpec = tween(durationMillis = 800),
        )

        val progressBarColor = if (tally.isWinning) MaterialTheme.colorScheme.allGoodColor else MaterialTheme.colorScheme.primary

        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .alpha(0.32f)
                    .drawWithContent {
                        // Clip the drawing area to show only the progress amount
                        clipRect(right = size.width * animatedProgress) {
                            drawRect(progressBarColor)
                        }
                        drawContent()
                    },
        )

        Row(
            modifier = Modifier.padding(15.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                content = labelContent,
            )

            Spacer(StdHorzSpacer)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = SpacedBy10dp,
            ) {
                UserGallery(tally, resultContent)

                Text(
                    text = "${(tally.percent * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun UserGallery(
    tally: TallyResults,
    galleryUser: @Composable RowScope.(user: User) -> Unit,
) {
    if (tally.users.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((-10).dp),
        ) {
            tally.users.take(6).forEach {
                key(it.pubkeyHex) {
                    galleryUser(it)
                }
            }

            if (tally.users.size > 6) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(Size25dp)
                            .clip(shape = CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Text(
                        text = "+" + showCount(tally.users.size - 6),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun RenderPollManualPreview() {
    val poll =
        PollCard(
            options =
                listOf(
                    PollItemCard(
                        code = "1",
                        label = "Yes",
                        results = flow {},
                        currentResults = {
                            TallyResults(
                                percent = 0.9f,
                                isWinning = true,
                            )
                        },
                    ),
                    PollItemCard(
                        code = "2",
                        label = "No",
                        results = flow {},
                        currentResults = {
                            TallyResults(
                                percent = 0.1f,
                                isWinning = false,
                            )
                        },
                    ),
                ),
            type = PollType.SINGLE_CHOICE,
            endsAt = null,
            isMyPoll = true,
            haveIVotedFlow = flow {},
            haveIVoted = { true },
        )

    ThemeComparisonColumn {
        Column(Modifier.padding(10.dp)) {
            RenderPollCard(poll, {}, {}) { _, label ->
                Text(
                    text = label,
                )
            }
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Preview
@Composable
fun RenderPollGameResultsPreview() {
    val event =
        PollEvent(
            id = "1fae24bb7e1673afc94da3878d051c0f4fd65599960aeb8a9a90642f8cad6299",
            pubKey = "c21b1a6cdb247ccbd938dcb16b15a4fa382d00ffd7b12d5cbbad172a0cd4d170",
            createdAt = 1770690664,
            content = "If there cpuld only be one: ",
            tags =
                arrayOf(
                    arrayOf("option", "dzv1u5do4", "GTA V"),
                    arrayOf("option", "8mr177irx", "Cyberpunk 2077"),
                    arrayOf("relay", "wss://relay.nostr.band/"),
                    arrayOf("relay", "wss://relay.primal.net/"),
                    arrayOf("relay", "wss://nos.lol/"),
                    arrayOf("relay", "wss://relay.damus.io/"),
                    arrayOf("relay", "wss://wot.nostr.party/"),
                    arrayOf("relay", "wss://relay.mostr.pub/"),
                    arrayOf("relay", "wss://offchain.pub/"),
                    arrayOf("polltype", "singlechoice"),
                ),
            sig = "7263d7e98348474c9f6670a98225e3594bffa813ff9c9084b7045126562dc352744450b5e667b0069e0276a63d3b9e14df3b1e1a3e6d5e958c3a7c74390345f5",
        )

    val note = LocalCache.getOrCreateNote("1fae24bb7e1673afc94da3878d051c0f4fd65599960aeb8a9a90642f8cad6299")

    LocalCache.justConsume(event, null, true)

    val response1 =
        PollResponseEvent(
            "a503bb8b1bd9062fab4acd003f015791faa04a9f321b91fd6d4649315e2a9307",
            pubKey = "592295cf2b09a7f9555f43adb734cbee8a84ee892ed3f9336e6a09b6413a0db9",
            createdAt = 1770729213,
            tags =
                arrayOf(
                    arrayOf("e", "1fae24bb7e1673afc94da3878d051c0f4fd65599960aeb8a9a90642f8cad6299"),
                    arrayOf("response", "dzv1u5do4"),
                ),
            content = "",
            sig = "0affc0a4f39bbae0802628282af871bbdab8d0338956ebd51cbb5453eac8f66f56013af35dafbe847762f6cfc6f9746be59260d2c462602501dad94d2fb67b63",
        )

    val response2 =
        PollResponseEvent(
            "1ad46eabd0044911e2c65ace22d4ad19640a74dc2b3daeea7bc2862fb0dcf5fb",
            pubKey = "c21b1a6cdb247ccbd938dcb16b15a4fa382d00ffd7b12d5cbbad172a0cd4d170",
            createdAt = 1770729213,
            tags =
                arrayOf(
                    arrayOf("e", "1fae24bb7e1673afc94da3878d051c0f4fd65599960aeb8a9a90642f8cad6299"),
                    arrayOf("response", "8mr177irx"),
                ),
            content = "",
            sig = "3efa83a899e1038fde76b8130f99881ab88c330adcd7d9f2578fa3c3e346eba05d627bd75ddb5780ffd460fcefb07839d0b379ba20db30772fa4d847b1dcfa6f",
        )

    LocalCache.justConsume(response1, null, true)
    LocalCache.justConsume(response2, null, true)

    ThemeComparisonColumn {
        Column(Modifier.padding(10.dp)) {
            RenderPoll(
                note,
                false,
                true,
                2,
                remember { mutableStateOf(Color.Transparent) },
                mockAccountViewModel(),
                EmptyNav(),
            )
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Preview
@Composable
fun RenderPollColorResultsPreview() {
    val event =
        PollEvent(
            id = "64d4acac073860d9080a51988bb1e9ffaef4ae8f3f9cf2d7b510e10d16a33b91",
            pubKey = "feb88e80a63d6a25b4b56eed4fac67342a97f2206e0a04fad89eb24d55ea303f",
            createdAt = 1769822711,
            content = "Color?",
            tags =
                arrayOf(
                    arrayOf("option", "hrwgtnzue", "Blue"),
                    arrayOf("option", "1cx1gldth", "Red"),
                    arrayOf("option", "5e29bzfjf", "Green"),
                    arrayOf("polltype", "singlechoice"),
                    arrayOf("endsAt", "1769844311"),
                    arrayOf("relay", "wss://relay.ditto.pub"),
                    arrayOf("relay", "wss://relay.primal.net"),
                    arrayOf("relay", "wss://relay.damus.io"),
                    arrayOf("I", "iso3166:BR"),
                    arrayOf("K", "iso3166"),
                    arrayOf("i", "iso3166:BR"),
                    arrayOf("k", "iso3166"),
                ),
            sig = "d65af73c3b652ea06df3dc00b05a6f155cf8936d645ca002d2c1a5d59b45e9b072e5217d3339711ad62f5b105859d8da95eb636681bd12ad04063d19359f48b5",
        )

    val note = LocalCache.getOrCreateNote(event)

    LocalCache.justConsume(event, null, true)

    val response1 =
        PollResponseEvent(
            id = "b756c6b3112d088355f7e3d288f5ccc0633522d86f770e1bdba67fae1b082719",
            pubKey = "3ff64bd7dde76af783a9545e2fb3ce84d921be6c8c04f3dc6c4fa734cf7eb812",
            createdAt = 1769841653,
            tags =
                arrayOf(
                    arrayOf("e", "64d4acac073860d9080a51988bb1e9ffaef4ae8f3f9cf2d7b510e10d16a33b91"),
                    arrayOf("response", "hrwgtnzue"),
                    arrayOf("client", "www.pollstr.site"),
                ),
            content = "",
            sig = "3a8cc549a5736774568e4b9a32e689ad5882fe2d030c4e05ecf78c6b1cfe5b5c9d3383067f525dfbddae600dc18b1266526e6d5d4e1f43e5723ebc7607b0377d",
        )

    val response2 =
        PollResponseEvent(
            id = "6aecc88b2acc8145c68553040da333d62085a6f75b874b1b7fceab2c8158069e",
            pubKey = "c21b1a6cdb247ccbd938dcb16b15a4fa382d00ffd7b12d5cbbad172a0cd4d170",
            createdAt = 1769827608,
            tags =
                arrayOf(
                    arrayOf("e", "64d4acac073860d9080a51988bb1e9ffaef4ae8f3f9cf2d7b510e10d16a33b91"),
                    arrayOf("response", "5e29bzfjf"),
                ),
            content = "",
            sig = "1ab865185fa7328d8b64f5c676a370d2dc4896678d6b13786ff142cfe8d7dc635f523740fad25a944de1b01def179548843a893a088f177dd011674a746d02a4",
        )

    val response3 =
        PollResponseEvent(
            id = "53a5ac0972b08276a0ea34a7dcd1d796b14453f4a76ca4626ed42ad19d67ade9",
            pubKey = "feb88e80a63d6a25b4b56eed4fac67342a97f2206e0a04fad89eb24d55ea303f",
            createdAt = 1769822723,
            tags =
                arrayOf(
                    arrayOf("e", "64d4acac073860d9080a51988bb1e9ffaef4ae8f3f9cf2d7b510e10d16a33b91"),
                    arrayOf("response", "5e29bzfjf"),
                ),
            content = "",
            sig = "d330c6f10d26674785d7e312929f4b4e01272840c087de45dceb0389ce14368908011b621dad24a9e438c1d757f9eac9940b6b9005721ff995c50258d1a7f6fb",
        )

    val response4 =
        PollResponseEvent(
            id = "5c6670f736f166dad6f1784ebe72bb851a9d82a9b8bb1355d8d74c2c7f1869fd",
            pubKey = "feb88e80a63d6a25b4b56eed4fac67342a97f2206e0a04fad89eb24d55ea303f",
            createdAt = 1769822720,
            tags =
                arrayOf(
                    arrayOf("e", "64d4acac073860d9080a51988bb1e9ffaef4ae8f3f9cf2d7b510e10d16a33b91"),
                    arrayOf("response", "hrwgtnzue"),
                ),
            content = "",
            sig = "d2feee481c0e84306fcb0815c7c671ab697948148b18307f864fe6c96a09485c12bf8f843d406b231914d60bd2735d3f3ed0b4e89377794bccd66e2e0acf2eb5",
        )

    LocalCache.justConsume(response1, null, true)
    LocalCache.justConsume(response2, null, true)
    LocalCache.justConsume(response3, null, true)
    LocalCache.justConsume(response4, null, true)

    ThemeComparisonColumn {
        Column(Modifier.padding(10.dp)) {
            RenderPoll(
                note,
                false,
                true,
                2,
                remember { mutableStateOf(Color.Transparent) },
                mockAccountViewModel(),
                EmptyNav(),
            )
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Preview
@Composable
fun RenderPollMultiChoicePreview() {
    val event =
        PollEvent(
            id = "ab1cad8d8f24aaa53b8cf34dd06ddedda5961c57da7a0df91349901fc15b5042",
            pubKey = "baeb862f3318390ec5af5c9db64ae5ddb2efc1a97db54c6550656bfa2dcc054b",
            createdAt = 1769053977,
            content = "Have you seen the simply Nostr/greeny show?",
            tags =
                arrayOf(
                    arrayOf("option", "py4d986jp", "Yes"),
                    arrayOf("option", "z0tipo9as", "No "),
                    arrayOf("option", "59ya8j3jo", "GFY"),
                    arrayOf("relay", "wss://nos.lol"),
                    arrayOf("relay", "wss://student.chadpolytechnic.com"),
                    arrayOf("relay", "wss://nostr-relay.wlvs.space"),
                    arrayOf("relay", "wss://nostr.ono.re"),
                    arrayOf("relay", "wss://nostr.bongbong.com"),
                    arrayOf("polltype", "multiplechoice"),
                    arrayOf("client", "www.pollstr.site"),
                ),
            sig = "aaddf12bc4b7947d8c57dd0fb4378c5f0daab6844cc3205bf1480e2a10e077b1abca67d071530e9fecac517fd6d052e49630a32596d9ecfb862967d0b2b2d899",
        )

    val note = LocalCache.getOrCreateNote(event)

    LocalCache.justConsume(event, null, true)

    ThemeComparisonColumn {
        Column(Modifier.padding(10.dp)) {
            RenderPoll(
                note,
                makeItShort = false,
                canPreview = true,
                quotesLeft = 2,
                backgroundColor = remember { mutableStateOf(Color.Transparent) },
                accountViewModel = mockAccountViewModel(),
                nav = EmptyNav(),
            )
        }
    }
}
