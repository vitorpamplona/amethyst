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
package com.vitorpamplona.amethyst.ui.components.toasts

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.actions.InformationDialog
import com.vitorpamplona.amethyst.ui.components.toasts.multiline.MultiErrorToastMsg
import com.vitorpamplona.amethyst.ui.components.toasts.multiline.MultiUserErrorMessageDialog
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import org.jetbrains.compose.resources.stringResource

@Composable
fun DisplayErrorMessages(
    toastManager: ToastManager,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val openDialogMsg = toastManager.toasts.collectAsStateWithLifecycle(null)

    openDialogMsg.value?.let { obj ->
        when (obj) {
            // New StringResource-based types
            is ResourceToastMsg -> {
                if (obj.params != null) {
                    InformationDialog(
                        stringResource(obj.titleRes),
                        stringResource(obj.descRes, *obj.params),
                    ) {
                        toastManager.clearToasts()
                    }
                } else {
                    InformationDialog(
                        stringResource(obj.titleRes),
                        stringResource(obj.descRes),
                    ) {
                        toastManager.clearToasts()
                    }
                }
            }

            is ThrowableToastMsg -> {
                InformationDialog(obj) {
                    toastManager.clearToasts()
                }
            }

            is ThrowableToastMsg2 -> {
                InformationDialog(obj) {
                    toastManager.clearToasts()
                }
            }

            is MultiErrorToastMsg -> {
                MultiUserErrorMessageDialog(obj, accountViewModel, nav)
            }

            // Legacy Int-based types (will be removed after full migration)
            is LegacyResourceToastMsg -> {
                @Suppress("DEPRECATION")
                if (obj.params != null) {
                    InformationDialog(
                        stringRes(obj.titleResId),
                        stringRes(obj.resourceId, *obj.params),
                    ) {
                        toastManager.clearToasts()
                    }
                } else {
                    InformationDialog(
                        stringRes(obj.titleResId),
                        stringRes(obj.resourceId),
                    ) {
                        toastManager.clearToasts()
                    }
                }
            }

            is LegacyThrowableToastMsg -> {
                @Suppress("DEPRECATION")
                InformationDialog(obj) {
                    toastManager.clearToasts()
                }
            }

            is LegacyThrowableToastMsg2 -> {
                @Suppress("DEPRECATION")
                InformationDialog(obj) {
                    toastManager.clearToasts()
                }
            }

            is LegacyMultiErrorToastMsg -> {
                @Suppress("DEPRECATION")
                MultiUserErrorMessageDialog(obj, accountViewModel, nav)
            }

            // String-based types
            is StringToastMsg -> {
                InformationDialog(
                    obj.title,
                    obj.msg,
                ) {
                    toastManager.clearToasts()
                }
            }

            is ActionableStringToastMsg -> {
                InformationDialog(
                    obj.title,
                    obj.msg,
                ) {
                    obj.action()
                    toastManager.clearToasts()
                }
            }
        }
    }
}
