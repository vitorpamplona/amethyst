package com.vitorpamplona.amethyst.ui.elements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.quartz.events.EventInterface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayZapSplits(noteEvent: EventInterface, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val list = remember(noteEvent) { noteEvent.zapSplitSetup() }
    if (list.isEmpty()) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .height(20.dp)
                .width(25.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = stringResource(id = R.string.zaps),
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterStart),
                tint = BitcoinOrange
            )
            Icon(
                imageVector = Icons.Outlined.ArrowForwardIos,
                contentDescription = stringResource(id = R.string.zaps),
                modifier = Modifier
                    .size(13.dp)
                    .align(Alignment.CenterEnd),
                tint = BitcoinOrange
            )
        }

        Spacer(modifier = StdHorzSpacer)

        FlowRow {
            list.forEach {
                if (it.isLnAddress) {
                    ClickableText(
                        text = AnnotatedString(it.lnAddressOrPubKeyHex),
                        onClick = { },
                        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary)
                    )
                } else {
                    UserPicture(
                        userHex = it.lnAddressOrPubKeyHex,
                        size = Size25dp,
                        accountViewModel = accountViewModel,
                        nav = nav
                    )
                }
            }
        }
    }
}
