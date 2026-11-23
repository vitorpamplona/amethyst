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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.marmotDevices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.marmot.devices.SasSymbol
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_0
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_1
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_10
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_11
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_12
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_13
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_14
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_15
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_16
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_17
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_18
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_19
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_2
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_20
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_21
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_22
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_23
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_24
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_25
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_26
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_27
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_28
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_29
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_3
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_30
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_31
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_32
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_33
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_34
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_35
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_36
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_37
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_38
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_39
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_4
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_40
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_41
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_42
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_43
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_44
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_45
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_46
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_47
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_48
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_49
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_5
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_50
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_51
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_52
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_53
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_54
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_55
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_56
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_57
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_58
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_59
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_6
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_60
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_61
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_62
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_63
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_7
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_8
import com.vitorpamplona.amethyst.commons.resources.sas_emoji_9
import com.vitorpamplona.amethyst.commons.ui.stringRes
import org.jetbrains.compose.resources.StringResource

/**
 * The translated names of the 64 pairing emoji, by [SasSymbol.number] (the spec's table, which is
 * Matrix's SAS table). Users read the names aloud when the pictures render differently.
 */
val SAS_EMOJI_DESCRIPTIONS: List<StringResource> =
    listOf(
        Res.string.sas_emoji_0,
        Res.string.sas_emoji_1,
        Res.string.sas_emoji_2,
        Res.string.sas_emoji_3,
        Res.string.sas_emoji_4,
        Res.string.sas_emoji_5,
        Res.string.sas_emoji_6,
        Res.string.sas_emoji_7,
        Res.string.sas_emoji_8,
        Res.string.sas_emoji_9,
        Res.string.sas_emoji_10,
        Res.string.sas_emoji_11,
        Res.string.sas_emoji_12,
        Res.string.sas_emoji_13,
        Res.string.sas_emoji_14,
        Res.string.sas_emoji_15,
        Res.string.sas_emoji_16,
        Res.string.sas_emoji_17,
        Res.string.sas_emoji_18,
        Res.string.sas_emoji_19,
        Res.string.sas_emoji_20,
        Res.string.sas_emoji_21,
        Res.string.sas_emoji_22,
        Res.string.sas_emoji_23,
        Res.string.sas_emoji_24,
        Res.string.sas_emoji_25,
        Res.string.sas_emoji_26,
        Res.string.sas_emoji_27,
        Res.string.sas_emoji_28,
        Res.string.sas_emoji_29,
        Res.string.sas_emoji_30,
        Res.string.sas_emoji_31,
        Res.string.sas_emoji_32,
        Res.string.sas_emoji_33,
        Res.string.sas_emoji_34,
        Res.string.sas_emoji_35,
        Res.string.sas_emoji_36,
        Res.string.sas_emoji_37,
        Res.string.sas_emoji_38,
        Res.string.sas_emoji_39,
        Res.string.sas_emoji_40,
        Res.string.sas_emoji_41,
        Res.string.sas_emoji_42,
        Res.string.sas_emoji_43,
        Res.string.sas_emoji_44,
        Res.string.sas_emoji_45,
        Res.string.sas_emoji_46,
        Res.string.sas_emoji_47,
        Res.string.sas_emoji_48,
        Res.string.sas_emoji_49,
        Res.string.sas_emoji_50,
        Res.string.sas_emoji_51,
        Res.string.sas_emoji_52,
        Res.string.sas_emoji_53,
        Res.string.sas_emoji_54,
        Res.string.sas_emoji_55,
        Res.string.sas_emoji_56,
        Res.string.sas_emoji_57,
        Res.string.sas_emoji_58,
        Res.string.sas_emoji_59,
        Res.string.sas_emoji_60,
        Res.string.sas_emoji_61,
        Res.string.sas_emoji_62,
        Res.string.sas_emoji_63,
    )

/** [symbol]'s name in the user's language, or its English name when the number is unknown. */
@Composable
fun sasDescription(symbol: SasSymbol): String = SAS_EMOJI_DESCRIPTIONS.getOrNull(symbol.number)?.let { stringRes(it) } ?: symbol.englishDescription

/** The seven emoji with their names, in reading order, wrapping onto a second row on narrow windows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SasCodeGrid(
    code: List<SasSymbol>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        code.forEach { SasCell(it) }
    }
}

@Composable
private fun SasCell(symbol: SasSymbol) {
    val name = sasDescription(symbol)
    Column(
        modifier =
            Modifier
                .width(84.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 12.dp, horizontal = 4.dp)
                .clearAndSetSemantics { contentDescription = name },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = symbol.emoji, fontSize = 40.sp, textAlign = TextAlign.Center)
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
