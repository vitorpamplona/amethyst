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
package com.vitorpamplona.amethyst.commons.privacy

data class FeatureTransportChoices(
    val image: TransportChoice = TransportChoice.DIRECT,
    val video: TransportChoice = TransportChoice.DIRECT,
    val urlPreview: TransportChoice = TransportChoice.DIRECT,
    val profilePic: TransportChoice = TransportChoice.DIRECT,
    val nip05: TransportChoice = TransportChoice.DIRECT,
    val money: TransportChoice = TransportChoice.DIRECT,
    val upload: TransportChoice = TransportChoice.DIRECT,
) {
    fun choiceFor(role: FeatureRole): TransportChoice =
        when (role) {
            FeatureRole.IMAGE -> image
            FeatureRole.VIDEO -> video
            FeatureRole.URL_PREVIEW -> urlPreview
            FeatureRole.PROFILE_PIC -> profilePic
            FeatureRole.NIP05 -> nip05
            FeatureRole.MONEY -> money
            FeatureRole.UPLOAD -> upload
        }

    fun with(
        role: FeatureRole,
        choice: TransportChoice,
    ): FeatureTransportChoices =
        when (role) {
            FeatureRole.IMAGE -> copy(image = choice)
            FeatureRole.VIDEO -> copy(video = choice)
            FeatureRole.URL_PREVIEW -> copy(urlPreview = choice)
            FeatureRole.PROFILE_PIC -> copy(profilePic = choice)
            FeatureRole.NIP05 -> copy(nip05 = choice)
            FeatureRole.MONEY -> copy(money = choice)
            FeatureRole.UPLOAD -> copy(upload = choice)
        }
}
