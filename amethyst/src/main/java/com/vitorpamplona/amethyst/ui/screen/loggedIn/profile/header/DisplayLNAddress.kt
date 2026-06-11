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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.util.LongPressCopyText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.LightningAddressIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.payment.ProfilePaymentMethod
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier

/**
 * Profile row showing the user's lightning address. Tapping it opens the
 * unified Send Payment screen (amount + message + zap type, paid on the spot)
 * preselecting the Lightning rail; long-press copies the address.
 */
@Composable
fun DisplayLNAddress(
    lud16: String?,
    user: User,
    nav: INav,
) {
    if (!lud16.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LightningAddressIcon(modifier = Size16Modifier, tint = BitcoinOrange)

            LongPressCopyText(
                displayText = lud16,
                copyValue = lud16,
                onClick = {
                    nav.nav(Route.SendPayment(user.pubkeyHex, ProfilePaymentMethod.LIGHTNING.routeKey))
                },
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(top = 1.dp, bottom = 1.dp, start = 5.dp),
            )
        }
    }
}
