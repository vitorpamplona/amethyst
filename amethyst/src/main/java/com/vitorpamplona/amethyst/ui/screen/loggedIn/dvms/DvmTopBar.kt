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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.SimpleImage35Modifier

@Composable
fun DvmTopBar(
    appDefinitionId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TopBarExtensibleWithBackButton(
        title = {
            LoadNote(baseNoteHex = appDefinitionId, accountViewModel = accountViewModel) { appDefinitionNote ->
                if (appDefinitionNote != null) {
                    val card = observeAppDefinition(appDefinitionNote, accountViewModel)

                    card.cover?.let {
                        MyAsyncImage(
                            imageUrl = it,
                            contentDescription = card.name,
                            contentScale = ContentScale.Crop,
                            mainImageModifier = Modifier,
                            loadedImageModifier = SimpleImage35Modifier,
                            accountViewModel = accountViewModel,
                            onLoadingBackground = {
                                appDefinitionNote.author?.let { author ->
                                    BannerImage(author, SimpleImage35Modifier, accountViewModel)
                                }
                            },
                            onError = {
                                appDefinitionNote.author?.let { author ->
                                    BannerImage(author, SimpleImage35Modifier, accountViewModel)
                                }
                            },
                        )
                    } ?: run {
                        appDefinitionNote.author?.let { author ->
                            BannerImage(author, SimpleImage35Modifier, accountViewModel)
                        }
                    }

                    Spacer(modifier = DoubleHorzSpacer)

                    Text(
                        text = card.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        popBack = nav::popBack,
    )
}
