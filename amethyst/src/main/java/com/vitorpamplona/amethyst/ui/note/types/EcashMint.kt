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
@file:Suppress("unused")

package com.vitorpamplona.amethyst.ui.note.types

// Re-export from commons for backwards compatibility
import androidx.compose.runtime.Composable
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.fedimint.FedimintEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCashuMint as CommonsRenderCashuMint
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderFedimint as CommonsRenderFedimint
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMintRecommendation as CommonsRenderMintRecommendation

@Composable
fun RenderCashuMint(noteEvent: CashuMintEvent) {
    CommonsRenderCashuMint(noteEvent)
}

@Composable
fun RenderFedimint(noteEvent: FedimintEvent) {
    CommonsRenderFedimint(noteEvent)
}

@Composable
fun RenderMintRecommendation(noteEvent: MintRecommendationEvent) {
    CommonsRenderMintRecommendation(noteEvent)
}
