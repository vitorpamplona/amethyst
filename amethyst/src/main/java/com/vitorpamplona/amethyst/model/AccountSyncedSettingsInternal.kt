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
package com.vitorpamplona.amethyst.model

import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.serialization.Serializable
import java.util.Locale

val DefaultReactions =
    listOf(
        "\uD83D\uDE80",
        "\uD83E\uDEC2",
        "\uD83D\uDC40",
        "\uD83D\uDE02",
        "\uD83C\uDF89",
        "\uD83E\uDD14",
        "\uD83D\uDE31",
    )

val DefaultZapAmounts = listOf(100L, 500L, 1000L)
val DefaultReportWarningThreshold = 5

@Serializable
enum class ReactionRowAction {
    Reply,
    Boost,
    Like,
    Zap,
    Share,
    Pay,
}

@Serializable
data class ReactionRowItem(
    val action: ReactionRowAction,
    val enabled: Boolean = true,
    val showCounter: Boolean = true,
)

val DefaultReactionRowItems =
    listOf(
        ReactionRowItem(ReactionRowAction.Reply),
        ReactionRowItem(ReactionRowAction.Boost),
        ReactionRowItem(ReactionRowAction.Like),
        ReactionRowItem(ReactionRowAction.Zap),
        ReactionRowItem(ReactionRowAction.Pay, enabled = false, showCounter = false),
        ReactionRowItem(ReactionRowAction.Share, showCounter = false),
    )

// Existing accounts have a reaction-row list serialized before some actions
// existed (e.g. Pay was added later). Append any default items the saved list
// is missing so new actions surface without forcing a settings reset — the
// user's existing order/toggles for actions they already have are preserved.
fun mergeWithDefaultReactionRowItems(saved: List<ReactionRowItem>): List<ReactionRowItem> {
    val knownActions = saved.mapTo(mutableSetOf()) { it.action }
    val missing = DefaultReactionRowItems.filter { it.action !in knownActions }
    return if (missing.isEmpty()) saved else saved + missing
}

@Serializable
enum class VideoPlayerAction {
    Fullscreen,
    Mute,
    Quality,
    Share,
    Download,
    PictureInPicture,
    Cast,
}

@Serializable
enum class VideoButtonLocation {
    TopBar,
    OverflowMenu,
}

@Serializable
data class VideoPlayerButtonItem(
    val action: VideoPlayerAction,
    val location: VideoButtonLocation = VideoButtonLocation.OverflowMenu,
)

val DefaultVideoPlayerButtonItems =
    listOf(
        VideoPlayerButtonItem(VideoPlayerAction.Fullscreen, VideoButtonLocation.TopBar),
        VideoPlayerButtonItem(VideoPlayerAction.Mute, VideoButtonLocation.TopBar),
        VideoPlayerButtonItem(VideoPlayerAction.Quality, VideoButtonLocation.TopBar),
        VideoPlayerButtonItem(VideoPlayerAction.Cast, VideoButtonLocation.TopBar),
        VideoPlayerButtonItem(VideoPlayerAction.Share, VideoButtonLocation.OverflowMenu),
        VideoPlayerButtonItem(VideoPlayerAction.Download, VideoButtonLocation.OverflowMenu),
        VideoPlayerButtonItem(VideoPlayerAction.PictureInPicture, VideoButtonLocation.OverflowMenu),
    )

// Existing accounts have a button-items list serialized before some actions
// existed (e.g. Cast was added later). Append any default items the saved list
// is missing so new actions surface without forcing a settings reset — the
// user's existing order/locations for actions they already have are preserved.
fun mergeWithDefaultVideoPlayerButtons(saved: List<VideoPlayerButtonItem>): List<VideoPlayerButtonItem> {
    val knownActions = saved.mapTo(mutableSetOf()) { it.action }
    val missing = DefaultVideoPlayerButtonItems.filter { it.action !in knownActions }
    return if (missing.isEmpty()) saved else saved + missing
}

fun getLanguagesSpokenByUser(): Set<String> {
    val languageList = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration())
    val codedList = mutableSetOf<String>()
    for (i in 0 until languageList.size()) {
        languageList.get(i)?.let { codedList.add(it.language) }
    }
    return codedList
}

@Serializable
class AccountSyncedSettingsInternal(
    val reactions: AccountReactionPreferencesInternal = AccountReactionPreferencesInternal(),
    val zaps: AccountZapPreferencesInternal = AccountZapPreferencesInternal(),
    val languages: AccountLanguagePreferencesInternal = AccountLanguagePreferencesInternal(),
    val security: AccountSecurityPreferencesInternal = AccountSecurityPreferencesInternal(),
    val videoPlayer: AccountVideoPlayerPreferencesInternal = AccountVideoPlayerPreferencesInternal(),
)

@Serializable
class AccountVideoPlayerPreferencesInternal(
    var buttonItems: List<VideoPlayerButtonItem> = DefaultVideoPlayerButtonItems,
)

@Serializable
class AccountReactionPreferencesInternal(
    var reactionChoices: List<String> = DefaultReactions,
    var reactionRowItems: List<ReactionRowItem> = DefaultReactionRowItems,
)

@Serializable
class AccountZapPreferencesInternal(
    var zapAmountChoices: List<Long> = DefaultZapAmounts,
    val defaultZapType: LnZapEvent.ZapType = LnZapEvent.ZapType.PUBLIC,
)

@Serializable
class AccountLanguagePreferencesInternal(
    var dontTranslateFrom: Set<String> = getLanguagesSpokenByUser(),
    var languagePreferences: Map<String, String> = mapOf(),
    var translateTo: String = Locale.getDefault().language,
)

@Serializable
class AccountSecurityPreferencesInternal(
    val showSensitiveContent: Boolean? = null,
    var warnAboutPostsWithReports: Boolean = true,
    val reportWarningThreshold: Int = DefaultReportWarningThreshold,
    var filterSpamFromStrangers: Boolean = true,
    val maxHashtagLimit: Int = 8,
    var sendKind0EventsToLocalRelay: Boolean = false,
    var addClientTag: Boolean = true,
)
