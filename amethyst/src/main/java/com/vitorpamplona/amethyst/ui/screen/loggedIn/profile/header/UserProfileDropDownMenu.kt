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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import android.content.Intent
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.note.externalLinkForUser
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.quartz.nip56Reports.ReportType

@Composable
fun UserProfileDropDownMenu(
    user: User,
    popupExpanded: Boolean,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = onDismiss,
    ) {
        val clipboardManager = LocalClipboardManager.current

        DropdownMenuItem(
            text = { Text(stringRes(R.string.copy_user_id)) },
            onClick = {
                clipboardManager.setText(AnnotatedString(user.pubkeyNpub()))
                onDismiss()
            },
        )

        val context = LocalContext.current

        DropdownMenuItem(
            text = { Text(stringRes(R.string.quick_action_share)) },
            onClick = {
                val sendIntent =
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            externalLinkForUser(user),
                        )
                        putExtra(
                            Intent.EXTRA_TITLE,
                            stringRes(context, R.string.quick_action_share_browser_link),
                        )
                    }

                val shareIntent =
                    Intent.createChooser(sendIntent, stringRes(context, R.string.quick_action_share))
                context.startActivity(shareIntent)
                onDismiss()
            },
        )

        if (accountViewModel.userProfile() != user) {
            HorizontalDivider(thickness = DividerThickness)
            if (accountViewModel.account.isHidden(user)) {
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.unblock_user)) },
                    onClick = {
                        accountViewModel.show(user)
                        onDismiss()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringRes(id = R.string.block_hide_user)) },
                    onClick = {
                        accountViewModel.hide(user)
                        onDismiss()
                    },
                )
            }
            HorizontalDivider(thickness = DividerThickness)
            DropdownMenuItem(
                text = { Text(stringRes(id = R.string.report_spam_scam)) },
                onClick = {
                    accountViewModel.report(user, ReportType.SPAM)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(R.string.report_hateful_speech)) },
                onClick = {
                    accountViewModel.report(user, ReportType.PROFANITY)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(id = R.string.report_impersonation)) },
                onClick = {
                    accountViewModel.report(user, ReportType.IMPERSONATION)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(R.string.report_nudity_porn)) },
                onClick = {
                    accountViewModel.report(user, ReportType.NUDITY)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(id = R.string.report_illegal_behaviour)) },
                onClick = {
                    accountViewModel.report(user, ReportType.ILLEGAL)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(id = R.string.report_malware)) },
                onClick = {
                    accountViewModel.report(user, ReportType.MALWARE)
                    onDismiss()
                },
            )
        }
    }
}
