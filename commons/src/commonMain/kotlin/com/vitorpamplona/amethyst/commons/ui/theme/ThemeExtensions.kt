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

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * Determines if the color scheme is light mode.
 * Based on primary color luminance.
 */
val ColorScheme.isLight: Boolean
    get() = primary.luminance() < 0.5f

/**
 * Color filter for onBackground color (for tinting icons/images).
 */
val ColorScheme.onBackgroundColorFilter: ColorFilter
    get() = ColorFilter.tint(onBackground)

/**
 * Placeholder text color (onSurface with reduced alpha).
 */
val ColorScheme.placeholderText: Color
    get() = onSurface.copy(alpha = 0.42f)

/**
 * Less important link color (primary with reduced alpha).
 */
val ColorScheme.lessImportantLink: Color
    get() = primary.copy(alpha = 0.52f)

/**
 * Medium importance link color (primary with reduced alpha).
 */
val ColorScheme.mediumImportanceLink: Color
    get() = primary.copy(alpha = 0.32f)

/**
 * Gray text color (onSurface with reduced alpha).
 */
val ColorScheme.grayText: Color
    get() = onSurface.copy(alpha = 0.52f)

/**
 * Subtle border color (onSurface with low alpha).
 */
val ColorScheme.subtleBorder: Color
    get() = if (isLight) onSurface.copy(alpha = 0.05f) else onSurface.copy(alpha = 0.12f)

/**
 * Subtle button color (onSurface with reduced alpha).
 */
val ColorScheme.subtleButton: Color
    get() = onSurface.copy(alpha = 0.22f)

/**
 * NIP-05 email color.
 */
val ColorScheme.nip05: Color
    get() = if (isLight) Color(0xFFa770f3) else Color(0xFF6e5490)

/**
 * Over-picture background color (background with reduced alpha).
 */
val ColorScheme.overPictureBackground: Color
    get() = background.copy(alpha = 0.62f)

/**
 * Bitcoin color (orange, light/dark variant).
 */
val ColorScheme.bitcoinColor: Color
    get() = if (isLight) Color(0xFFB66605) else Color(0xFFF7931A)

/**
 * Background color for new/unread items.
 */
val ColorScheme.newItemBackgroundColor: Color
    get() = primary.copy(alpha = 0.12f)

/**
 * Transparent background overlay.
 */
val ColorScheme.transparentBackground: Color
    get() = background.copy(alpha = 0.32f)

/**
 * Background for selected notes (new item color composited over background).
 */
val ColorScheme.selectedNote: Color
    get() = newItemBackgroundColor.compositeOver(background)

/**
 * Secondary button background (primary composited over background).
 */
val ColorScheme.secondaryButtonBackground: Color
    get() = primary.copy(alpha = 0.32f).compositeOver(background)

/**
 * Chat message background.
 */
val ColorScheme.chatBackground: Color
    get() = if (isLight) onSurface.copy(alpha = 0.08f) else onSurface.copy(alpha = 0.12f)

/**
 * Chat draft message background.
 */
val ColorScheme.chatDraftBackground: Color
    get() = onSurface.copy(alpha = 0.15f)

/**
 * Warning color (yellow).
 */
val ColorScheme.warningColor: Color
    get() = if (isLight) LightWarningColor else DarkWarningColor

/**
 * Warning color on second surface.
 */
val ColorScheme.warningColorOnSecondSurface: Color
    get() = if (isLight) LightWarningColorOnSecondSurface else DarkWarningColorOnSecondSurface

/**
 * Red color on second surface.
 */
val ColorScheme.redColorOnSecondSurface: Color
    get() = if (isLight) LightRedColorOnSecondSurface else DarkRedColorOnSecondSurface

/**
 * All good / success color (green).
 */
val ColorScheme.allGoodColor: Color
    get() = if (isLight) LightAllGoodColor else DarkAllGoodColor

/**
 * Fundraiser progress bar color.
 */
val ColorScheme.fundraiserProgressColor: Color
    get() = if (isLight) LightFundraiserProgressColor else DarkFundraiserProgressColor
