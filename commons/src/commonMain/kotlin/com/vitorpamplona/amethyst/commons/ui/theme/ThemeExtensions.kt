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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

val ColorScheme.isLight: Boolean
    get() = primary.luminance() < 0.5f

val ColorScheme.onBackgroundColorFilter: ColorFilter
    get() = ColorFilter.tint(onBackground)

val ColorScheme.newItemBackgroundColor: Color
    get() = primary.copy(0.12f)

val ColorScheme.transparentBackground: Color
    get() = background.copy(0.32f)

val ColorScheme.selectedNote: Color
    get() = newItemBackgroundColor.compositeOver(background)

val ColorScheme.secondaryButtonBackground: Color
    get() = primary.copy(alpha = 0.32f).compositeOver(background)

val ColorScheme.lessImportantLink: Color
    get() = primary.copy(alpha = 0.52f)

val ColorScheme.mediumImportanceLink: Color
    get() = primary.copy(alpha = 0.32f)

val ColorScheme.placeholderText: Color
    get() = onSurface.copy(alpha = 0.42f)

val ColorScheme.nip05: Color
    get() = if (isLight) Nip05EmailColorLight else Nip05EmailColorDark

val ColorScheme.grayText: Color
    get() = onSurface.copy(alpha = 0.52f)

val ColorScheme.subtleBorder: Color
    get() = if (isLight) onSurface.copy(alpha = 0.05f) else onSurface.copy(alpha = 0.12f)

val ColorScheme.subtleButton: Color
    get() = onSurface.copy(alpha = 0.22f)

val ColorScheme.overPictureBackground: Color
    get() = background.copy(0.62f)

val ColorScheme.bitcoinColor: Color
    get() = if (isLight) BitcoinLight else BitcoinDark

val ColorScheme.chatBackground: Color
    get() = if (isLight) onSurface.copy(alpha = 0.08f) else onSurface.copy(alpha = 0.12f)

val ColorScheme.chatDraftBackground: Color
    get() = onSurface.copy(alpha = 0.15f)

val ColorScheme.warningColor: Color
    get() = if (isLight) LightWarningColor else DarkWarningColor

val ColorScheme.warningColorOnSecondSurface: Color
    get() = if (isLight) LightWarningColorOnSecondSurface else DarkWarningColorOnSecondSurface

val ColorScheme.redColorOnSecondSurface: Color
    get() = if (isLight) LightRedColorOnSecondSurface else DarkRedColorOnSecondSurface

val ColorScheme.allGoodColor: Color
    get() = if (isLight) LightAllGoodColor else DarkAllGoodColor

val ColorScheme.fundraiserProgressColor: Color
    get() = if (isLight) LightFundraiserProgressColor else DarkFundraiserProgressColor

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.imageModifier: Modifier
    get() =
        Modifier
            .fillMaxWidth()
            .clip(shape = QuoteBorder)
            .border(1.dp, subtleBorder, QuoteBorder)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.videoGalleryModifier: Modifier
    get() =
        Modifier
            .fillMaxWidth()
            .clip(shape = RectangleShape)
            .border(1.dp, subtleBorder, RectangleShape)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.profile35dpModifier: Modifier
    get() =
        if (isLight) {
            Modifier.fillMaxWidth().clip(shape = CircleShape)
        } else {
            Modifier.size(Size35dp).clip(shape = CircleShape)
        }

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.replyModifier: Modifier
    get() =
        Modifier
            .padding(top = 5.dp)
            .fillMaxWidth()
            .clip(shape = QuoteBorder)
            .border(1.dp, subtleBorder, QuoteBorder)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.innerPostModifier: Modifier
    get() =
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clip(shape = QuoteBorder)
            .border(1.dp, subtleBorder, QuoteBorder)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.maxWidthWithBackground: Modifier
    get() =
        Modifier
            .fillMaxWidth()
            .background(background)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.channelNotePictureModifier: Modifier
    get() =
        Modifier
            .size(20.dp)
            .clip(shape = CircleShape)
            .border(2.dp, background, CircleShape)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.userProfileBorderModifier: Modifier
    get() =
        Modifier.border(
            3.dp,
            if (isLight) Color.Black else Color.White,
            CircleShape,
        )

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.relayIconModifier: Modifier
    get() =
        Modifier
            .size(Size13dp)
            .clip(shape = CircleShape)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.selectedReactionBoxModifier: Modifier
    get() =
        Modifier
            .padding(horizontal = 5.dp, vertical = 5.dp)
            .size(Size40dp)
            .clip(shape = SmallBorder)
            .background(secondaryContainer)
            .padding(5.dp)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.largeProfilePictureModifier: Modifier
    get() =
        Modifier
            .size(120.dp)
            .clip(shape = CircleShape)
            .border(3.dp, onBackground, CircleShape)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.newItemBubbleModifier: Modifier
    get() =
        Modifier
            .size(10.dp)
            .clip(shape = CircleShape)
            .background(primary)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.blackTagModifier: Modifier
    get() =
        Modifier
            .clip(SmallestBorder)
            .background(onBackground)
            .padding(horizontal = 5.dp)
