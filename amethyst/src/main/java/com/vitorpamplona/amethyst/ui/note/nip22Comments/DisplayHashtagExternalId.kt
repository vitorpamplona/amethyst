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
package com.vitorpamplona.amethyst.ui.note.nip22Comments

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId

@Composable
fun DisplayHashtagExternalId(
    externalId: HashtagId,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisplayHashtagExternalId(externalId.topic) {
        nav.nav(Route.Hashtag(externalId.topic.lowercase()))
    }
}

@Composable
fun DisplayHashtagExternalId(
    topic: String,
    linkInteractionListener: LinkInteractionListener,
) {
    Row(modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Tag,
            contentDescription = stringRes(id = R.string.hashtag_exclusive),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(StdHorzSpacer)

        Text(
            text =
                buildAnnotatedString {
                    withLink(
                        LinkAnnotation.Clickable("hashtag", null, linkInteractionListener),
                    ) {
                        append(topic)
                    }
                },
            style =
                LocalTextStyle.current.copy(
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )
    }
}
