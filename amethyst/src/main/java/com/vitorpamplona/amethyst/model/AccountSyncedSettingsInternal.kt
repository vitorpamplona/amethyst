/**
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
import com.vitorpamplona.quartz.experimental.tipping.TipEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
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

val DefaultTipAmounts = listOf(0.1, 0.5, 1.0)
val DefaultTipType = TipEvent.TipType.ANONYMOUS

fun getLanguagesSpokenByUser(): Set<String> {
    val languageList = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration())
    val codedList = mutableSetOf<String>()
    for (i in 0 until languageList.size()) {
        languageList.get(i)?.let { codedList.add(it.language) }
    }
    return codedList
}

class AccountSyncedSettingsInternal(
    val reactions: AccountReactionPreferencesInternal = AccountReactionPreferencesInternal(),
    val zaps: AccountZapPreferencesInternal = AccountZapPreferencesInternal(),
    val languages: AccountLanguagePreferencesInternal = AccountLanguagePreferencesInternal(),
    val security: AccountSecurityPreferencesInternal = AccountSecurityPreferencesInternal(),
    val tips: AccountTipPreferencesInternal = AccountTipPreferencesInternal(),
)

class AccountReactionPreferencesInternal(
    var reactionChoices: List<String> = DefaultReactions,
)

class AccountTipPreferencesInternal(
    var tipAmountChoices: List<Double> = DefaultTipAmounts,
    var defaultTipType: TipEvent.TipType = DefaultTipType,
)

class AccountZapPreferencesInternal(
    var zapAmountChoices: List<Long> = DefaultZapAmounts,
    val defaultZapType: LnZapEvent.ZapType = LnZapEvent.ZapType.PUBLIC,
)

class AccountLanguagePreferencesInternal(
    var dontTranslateFrom: Set<String> = getLanguagesSpokenByUser(),
    var languagePreferences: Map<String, String> = mapOf(),
    var translateTo: String = Locale.getDefault().language,
)

class AccountSecurityPreferencesInternal(
    val showSensitiveContent: Boolean? = null,
    var warnAboutPostsWithReports: Boolean = true,
    var filterSpamFromStrangers: Boolean = true,
)
