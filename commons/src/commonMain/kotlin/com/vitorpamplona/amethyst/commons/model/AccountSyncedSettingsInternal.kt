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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.platform.getDefaultLanguage
import com.vitorpamplona.amethyst.commons.platform.getDeviceLanguages
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.serialization.Serializable

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
        ReactionRowItem(ReactionRowAction.Share, showCounter = false),
        ReactionRowItem(ReactionRowAction.Pay, showCounter = false),
    )

@Serializable
class AccountSyncedSettingsInternal(
    val reactions: AccountReactionPreferencesInternal = AccountReactionPreferencesInternal(),
    val zaps: AccountZapPreferencesInternal = AccountZapPreferencesInternal(),
    val languages: AccountLanguagePreferencesInternal = AccountLanguagePreferencesInternal(),
    val security: AccountSecurityPreferencesInternal = AccountSecurityPreferencesInternal(),
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
    var dontTranslateFrom: Set<String> = getDeviceLanguages(),
    var languagePreferences: Map<String, String> = mapOf(),
    var translateTo: String = getDefaultLanguage(),
)

@Serializable
class AccountSecurityPreferencesInternal(
    val showSensitiveContent: Boolean? = null,
    var warnAboutPostsWithReports: Boolean = true,
    var filterSpamFromStrangers: Boolean = true,
    val maxHashtagLimit: Int = 5,
    var sendKind0EventsToLocalRelay: Boolean = false,
)
