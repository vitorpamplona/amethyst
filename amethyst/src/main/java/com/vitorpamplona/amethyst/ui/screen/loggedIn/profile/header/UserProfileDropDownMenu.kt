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

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.IAccountViewModel
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.note.externalLinkForUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip56Reports.ReportType
import kotlinx.coroutines.launch

@Composable
fun UserProfileDropDownMenu(
    user: User,
    popupExpanded: Boolean,
    onDismiss: () -> Unit,
    accountViewModel: IAccountViewModel,
) {
    if (!popupExpanded) return

    M3ActionDialog(
        title = stringRes(R.string.profile_actions_dialog_title),
        onDismiss = onDismiss,
    ) {
        val clipboardManager = LocalClipboard.current
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        // Share section
        M3ActionSection {
            M3ActionRow(
                icon = Icons.Outlined.ContentCopy,
                text = stringRes(R.string.copy_user_id),
            ) {
                scope.launch {
                    clipboardManager.setText(user.pubkeyNpub())
                    onDismiss()
                }
            }
            M3ActionRow(
                icon = Icons.Outlined.Share,
                text = stringRes(R.string.quick_action_share),
            ) {
                val sendIntent =
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, externalLinkForUser(user))
                        putExtra(
                            Intent.EXTRA_TITLE,
                            stringRes(context, R.string.quick_action_share_browser_link),
                        )
                    }
                val shareIntent = Intent.createChooser(sendIntent, stringRes(context, R.string.quick_action_share))
                context.startActivity(shareIntent)
                onDismiss()
            }
        }

        // Moderation section (if not self)
        if (accountViewModel.userProfile() != user) {
            M3ActionSection {
                if (accountViewModel.account.isHidden(user)) {
                    M3ActionRow(
                        icon = Icons.Outlined.CheckCircle,
                        text = stringRes(R.string.unblock_user),
                    ) {
                        accountViewModel.show(user)
                        onDismiss()
                    }
                } else {
                    M3ActionRow(
                        icon = Icons.Outlined.Block,
                        text = stringRes(R.string.block_hide_user),
                        isDestructive = true,
                    ) {
                        accountViewModel.hide(user)
                        onDismiss()
                    }
                }
            }

            // Report section
            M3ActionSection {
                M3ActionRow(icon = Icons.Outlined.Report, text = stringRes(R.string.report_spam_scam), isDestructive = true) {
                    accountViewModel.report(user, ReportType.SPAM)
                    onDismiss()
                }
                M3ActionRow(icon = Icons.Outlined.Report, text = stringRes(R.string.report_hateful_speech), isDestructive = true) {
                    accountViewModel.report(user, ReportType.PROFANITY)
                    onDismiss()
                }
                M3ActionRow(icon = Icons.Outlined.Report, text = stringRes(R.string.report_impersonation), isDestructive = true) {
                    accountViewModel.report(user, ReportType.IMPERSONATION)
                    onDismiss()
                }
                M3ActionRow(icon = Icons.Outlined.Report, text = stringRes(R.string.report_nudity_porn), isDestructive = true) {
                    accountViewModel.report(user, ReportType.NUDITY)
                    onDismiss()
                }
                M3ActionRow(icon = Icons.Outlined.Report, text = stringRes(R.string.report_illegal_behaviour), isDestructive = true) {
                    accountViewModel.report(user, ReportType.ILLEGAL)
                    onDismiss()
                }
                M3ActionRow(icon = Icons.Outlined.Report, text = stringRes(R.string.report_malware), isDestructive = true) {
                    accountViewModel.report(user, ReportType.MALWARE)
                    onDismiss()
                }
            }
        }
    }
}
