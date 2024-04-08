/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.MarkdownParseOptions
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.resolveDefaults
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.note.authenticate
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNsec
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
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .fillMaxSize(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(onPress = onClose)
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val content1 = stringResource(R.string.account_backup_tips2_md)

                    val astNode1 =
                        remember {
                            CommonmarkAstNodeParser(MarkdownParseOptions.MarkdownWithLinks).parse(content1)
                        }

                    RichText(
                        style = RichTextStyle().resolveDefaults(),
                        renderer = null,
                    ) {
                        BasicMarkdown(astNode1)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    NSecCopyButton(accountViewModel)

                    Spacer(modifier = Modifier.height(30.dp))

                    val content = stringResource(R.string.account_backup_tips3_md)

                    val astNode =
                        remember {
                            CommonmarkAstNodeParser(MarkdownParseOptions.MarkdownWithLinks).parse(content)
                        }

                    RichText(
                        style = RichTextStyle().resolveDefaults(),
                        renderer = null,
                    ) {
                        BasicMarkdown(astNode)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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

                    OutlinedTextField(
                        modifier =
                            Modifier
                                .onGloballyPositioned { coordinates ->
                                    autofillNode.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
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
                                autoCorrect = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Go,
                            ),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.ncryptsec_password),
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
                                                stringResource(R.string.show_password)
                                            } else {
                                                stringResource(
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
                }
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
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                copyNSec(context, scope, accountViewModel.account, clipboardManager)
            }
        }

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            authenticate(
                title = context.getString(R.string.copy_my_secret_key),
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = { copyNSec(context, scope, accountViewModel.account, clipboardManager) },
                onError = { title, message -> accountViewModel.toast(title, message) },
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
                stringResource(R.string.copies_the_nsec_id_your_password_to_the_clipboard_for_backup),
            modifier = Modifier.padding(end = 5.dp),
        )
        Text(
            stringResource(id = R.string.copy_my_secret_key),
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
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                encryptCopyNSec(password, context, scope, accountViewModel, clipboardManager)
            }
        }

    OutlinedButton(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            authenticate(
                title = context.getString(R.string.copy_my_secret_key),
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = { encryptCopyNSec(password, context, scope, accountViewModel, clipboardManager) },
                onError = { title, message -> accountViewModel.toast(title, message) },
            )
        },
        shape = ButtonBorder,
        contentPadding = ButtonPadding,
        enabled = password.value.text.isNotBlank(),
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription =
                stringResource(R.string.copies_the_nsec_id_your_password_to_the_clipboard_for_backup),
            modifier = Modifier.padding(end = 5.dp),
        )
        Text(
            stringResource(id = R.string.encrypt_and_copy_my_secret_key),
        )
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
    account.keyPair.privKey?.let {
        clipboardManager.setText(AnnotatedString(it.toNsec()))
        scope.launch {
            Toast.makeText(
                context,
                context.getString(R.string.secret_key_copied_to_clipboard),
                Toast.LENGTH_SHORT,
            )
                .show()
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
            Toast.makeText(
                context,
                context.getString(R.string.password_is_required),
                Toast.LENGTH_SHORT,
            )
                .show()
        }
    } else {
        accountViewModel.account.keyPair.privKey?.let {
            val key = CryptoUtils.encryptNIP49(it.toHexKey(), password.value.text)
            if (key != null) {
                clipboardManager.setText(AnnotatedString(key))
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.secret_key_copied_to_clipboard),
                        Toast.LENGTH_SHORT,
                    )
                        .show()
                }
            } else {
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.failed_to_encrypt_key),
                        Toast.LENGTH_SHORT,
                    )
                        .show()
                }
            }
        }
    }
}
