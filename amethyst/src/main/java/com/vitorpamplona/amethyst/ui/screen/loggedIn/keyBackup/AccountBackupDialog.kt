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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.keyBackup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.resolveDefaults
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.authenticate
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.BackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AccountBackupDialog(
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogContents(accountViewModel, onClose)
    }
}

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun DialogContentsPreview() {
    ThemeComparisonRow {
        DialogContents(
            mockAccountViewModel(),
        ) {}
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DialogContents(
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier =
            Modifier,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier,
                ) {
                    ArrowBackIcon()
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val content1 = stringRes(R.string.account_backup_tips2_md)

                val astNode1 =
                    remember {
                        CommonmarkAstNodeParser(CommonMarkdownParseOptions.MarkdownWithLinks).parse(content1)
                    }

                RichText(
                    style = RichTextStyle().resolveDefaults(),
                    renderer = null,
                ) {
                    BasicMarkdown(astNode1)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row {
                    Column {
                        NSecCopyButton(accountViewModel)
                    }

                    Column {
                        QrCodeButton(accountViewModel)
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                val content = stringRes(R.string.account_backup_tips3_md)

                val astNode =
                    remember {
                        CommonmarkAstNodeParser(CommonMarkdownParseOptions.MarkdownWithLinks).parse(content)
                    }

                RichText(
                    style = RichTextStyle().resolveDefaults(),
                    renderer = null,
                ) {
                    BasicMarkdown(astNode)
                }

                val password = remember { mutableStateOf(TextFieldValue("")) }
                var errorMessage by remember { mutableStateOf("") }
                var showCharsPassword by remember { mutableStateOf(false) }

                val autofillNode =
                    AutofillNode(
                        autofillTypes = listOf(AutofillType.Password),
                        onFill = { password.value = TextFieldValue(it) },
                    )
                val autofill = LocalAutofill.current
                LocalAutofillTree.current += autofillNode

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    modifier =
                        Modifier
                            .onGloballyPositioned { coordinates ->
                                autofillNode.boundingBox = coordinates.boundsInWindow()
                            }.onFocusChanged { focusState ->
                                autofill?.run {
                                    if (focusState.isFocused) {
                                        requestAutofillForNode(autofillNode)
                                    } else {
                                        cancelAutofillForNode(autofillNode)
                                    }
                                }
                            },
                    value = password.value,
                    onValueChange = {
                        password.value = it
                        if (errorMessage.isNotEmpty()) {
                            errorMessage = ""
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go,
                        ),
                    placeholder = {
                        Text(
                            text = stringRes(R.string.ncryptsec_password),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showCharsPassword = !showCharsPassword }) {
                                Icon(
                                    imageVector =
                                        if (showCharsPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription =
                                        if (showCharsPassword) {
                                            stringRes(R.string.show_password)
                                        } else {
                                            stringRes(
                                                R.string.hide_password,
                                            )
                                        },
                                )
                            }
                        }
                    },
                    visualTransformation =
                        if (showCharsPassword) VisualTransformation.None else PasswordVisualTransformation(),
                )

                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                EncryptNSecCopyButton(accountViewModel, password)

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun NSecCopyButton(accountViewModel: AccountViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                copyNSec(context, scope, accountViewModel.account, clipboardManager)
            }
        }

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            authenticate(
                title = stringRes(context, R.string.copy_my_secret_key),
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = { copyNSec(context, scope, accountViewModel.account, clipboardManager) },
                onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
            )
        },
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        contentPadding = ButtonPadding,
    ) {
        Icon(
            tint = MaterialTheme.colorScheme.onPrimary,
            imageVector = Icons.Default.Key,
            contentDescription =
                stringRes(R.string.copies_the_nsec_id_your_password_to_the_clipboard_for_backup),
            modifier = Modifier.padding(end = 5.dp),
        )
        Text(
            stringRes(id = R.string.copy_my_secret_key),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun EncryptNSecCopyButton(
    accountViewModel: AccountViewModel,
    password: MutableState<TextFieldValue>,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                encryptCopyNSec(password, context, scope, accountViewModel, clipboardManager)
            }
        }

    Row {
        Column {
            OutlinedButton(
                modifier = Modifier.padding(horizontal = 3.dp),
                onClick = {
                    authenticate(
                        title = stringRes(context, R.string.copy_my_secret_key),
                        context = context,
                        keyguardLauncher = keyguardLauncher,
                        onApproved = { encryptCopyNSec(password, context, scope, accountViewModel, clipboardManager) },
                        onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
                    )
                },
                shape = ButtonBorder,
                contentPadding = ButtonPadding,
                enabled = password.value.text.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription =
                        stringRes(R.string.copies_the_nsec_id_your_password_to_the_clipboard_for_backup),
                    modifier = Modifier.padding(end = 5.dp),
                )
                Text(
                    stringRes(id = R.string.encrypt_and_copy_my_secret_key),
                )
            }
        }

        Column {
            QrCodeButtonEncrypted(accountViewModel, password)
        }
    }
}

fun Context.getFragmentActivity(): FragmentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is FragmentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

private fun copyNSec(
    context: Context,
    scope: CoroutineScope,
    account: Account,
    clipboardManager: ClipboardManager,
) {
    account.settings.keyPair.privKey?.let {
        clipboardManager.setText(AnnotatedString(it.toNsec()))
        scope.launch {
            Toast
                .makeText(
                    context,
                    stringRes(context, R.string.secret_key_copied_to_clipboard),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }
}

private fun encryptCopyNSec(
    password: MutableState<TextFieldValue>,
    context: Context,
    scope: CoroutineScope,
    accountViewModel: AccountViewModel,
    clipboardManager: ClipboardManager,
) {
    if (password.value.text.isBlank()) {
        scope.launch {
            Toast
                .makeText(
                    context,
                    stringRes(context, R.string.password_is_required),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    } else {
        accountViewModel.account.settings.keyPair.privKey?.let {
            val key = runCatching { Nip49().encrypt(it.toHexKey(), password.value.text) }.getOrNull()
            if (key != null) {
                clipboardManager.setText(AnnotatedString(key))
                scope.launch {
                    Toast
                        .makeText(
                            context,
                            stringRes(context, R.string.secret_key_copied_to_clipboard),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            } else {
                scope.launch {
                    Toast
                        .makeText(
                            context,
                            stringRes(context, R.string.failed_to_encrypt_key),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }
}

@Composable
private fun QrCodeButtonBase(
    accountViewModel: AccountViewModel,
    isEnabled: Boolean = true,
    contentDescription: Int,
    onDialogShow: () -> String?,
) {
    val context = LocalContext.current

    // store the dialog open or close state
    var dialogOpen by remember { mutableStateOf(false) }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                dialogOpen = true
            }
        }

    IconButton(
        enabled = isEnabled,
        onClick = {
            authenticate(
                title = stringRes(context, R.string.copy_my_secret_key),
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = { dialogOpen = true },
                onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
            )
        },
    ) {
        Icon(
            painter = painterRes(R.drawable.ic_qrcode, 4),
            contentDescription = stringRes(id = contentDescription),
            modifier = Modifier.size(24.dp),
            tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText,
        )
    }

    if (dialogOpen) {
        ShowKeyQRDialog(
            onDialogShow(),
            onClose = { dialogOpen = false },
        )
    }
}

@Composable
private fun QrCodeButton(accountViewModel: AccountViewModel) {
    QrCodeButtonBase(
        accountViewModel = accountViewModel,
        contentDescription = R.string.show_private_key_qr_code,
        onDialogShow = {
            accountViewModel.account.settings.keyPair.privKey
                ?.toNsec()
        },
    )
}

@Composable
private fun QrCodeButtonEncrypted(
    accountViewModel: AccountViewModel,
    password: MutableState<TextFieldValue>,
) {
    QrCodeButtonBase(
        accountViewModel = accountViewModel,
        isEnabled = password.value.text.isNotBlank(),
        contentDescription = R.string.show_encrypted_private_key_qr_code,
        onDialogShow = {
            accountViewModel.account.settings.keyPair.privKey
                ?.toHexKey()
                ?.let { Nip49().encrypt(it, password.value.text) }
        },
    )
}

@Composable
private fun ShowKeyQRDialog(
    qrCode: String?,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(10.dp),
            ) {
                // Back button at the top
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton(onPress = onClose)
                }

                // QR Code content
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QrCodeDrawer(qrCode ?: "error")
                }
            }
        }
    }
}
