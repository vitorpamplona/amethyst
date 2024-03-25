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
package com.vitorpamplona.amethyst.ui.screen.loggedOff

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.Amethyst
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.service.PackageUtils
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ConnectOrbotDialog
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.launch

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun SignUpPage() {
    val accountViewModel: AccountStateViewModel = viewModel()

    ThemeComparisonRow(
        toPreview = {
            SignUpPage(accountViewModel) {}
        },
    )
}

@Composable
fun SignUpPage(
    accountStateViewModel: AccountStateViewModel,
    onWantsToLogin: () -> Unit,
) {
    val displayName = remember { mutableStateOf(TextFieldValue("")) }
    var errorMessage by remember { mutableStateOf("") }
    val acceptedTerms = remember { mutableStateOf(false) }
    var termsAcceptanceIsRequired by remember { mutableStateOf("") }

    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val useProxy = remember { mutableStateOf(false) }
    val proxyPort = remember { mutableStateOf("9050") }
    var connectOrbotDialogOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Size20dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            imageVector = CustomHashTagIcons.Amethyst,
            contentDescription = stringResource(R.string.app_logo),
            modifier = Modifier.size(150.dp),
            contentScale = ContentScale.Inside,
        )

        Spacer(modifier = Modifier.height(Size40dp))

        Text(text = stringResource(R.string.welcome), style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(Size20dp))

        Text(text = stringResource(R.string.how_should_we_call_you), style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(Size20dp))

        OutlinedTextField(
            value = displayName.value,
            onValueChange = { displayName.value = it },
            keyboardOptions =
                KeyboardOptions(
                    autoCorrect = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Go,
                ),
            placeholder = {
                Text(
                    text = stringResource(R.string.my_awesome_name),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            keyboardActions =
                KeyboardActions(
                    onGo = {
                        if (!acceptedTerms.value) {
                            termsAcceptanceIsRequired =
                                context.getString(R.string.acceptance_of_terms_is_required)
                        }

                        if (displayName.value.text.isBlank()) {
                            errorMessage = context.getString(R.string.name_is_required)
                        }

                        if (acceptedTerms.value && displayName.value.text.isNotBlank()) {
                            accountStateViewModel.login(displayName.value.text, useProxy.value, proxyPort.value.toInt()) {
                                errorMessage = context.getString(R.string.invalid_key)
                            }
                        }
                    },
                ),
        )
        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = acceptedTerms.value,
                onCheckedChange = { acceptedTerms.value = it },
            )

            val regularText = SpanStyle(color = MaterialTheme.colorScheme.onBackground)

            val clickableTextStyle = SpanStyle(color = MaterialTheme.colorScheme.primary)

            val annotatedTermsString =
                buildAnnotatedString {
                    withStyle(regularText) { append(stringResource(R.string.i_accept_the)) }

                    withStyle(clickableTextStyle) {
                        pushStringAnnotation("openTerms", "")
                        append(stringResource(R.string.terms_of_use))
                        pop()
                    }
                }

            ClickableText(
                text = annotatedTermsString,
            ) { spanOffset ->
                annotatedTermsString.getStringAnnotations(spanOffset, spanOffset).firstOrNull()?.also {
                        span ->
                    if (span.tag == "openTerms") {
                        runCatching {
                            uri.openUri("https://github.com/vitorpamplona/amethyst/blob/main/PRIVACY.md")
                        }
                    }
                }
            }
        }

        if (termsAcceptanceIsRequired.isNotBlank()) {
            Text(
                text = termsAcceptanceIsRequired,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (PackageUtils.isOrbotInstalled(context)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = useProxy.value,
                    onCheckedChange = {
                        if (it) {
                            connectOrbotDialogOpen = true
                        }
                    },
                )

                Text(stringResource(R.string.connect_via_tor))
            }

            if (connectOrbotDialogOpen) {
                ConnectOrbotDialog(
                    onClose = { connectOrbotDialogOpen = false },
                    onPost = {
                        connectOrbotDialogOpen = false
                        useProxy.value = true
                    },
                    onError = {
                        scope.launch {
                            Toast.makeText(
                                context,
                                it,
                                Toast.LENGTH_LONG,
                            )
                                .show()
                        }
                    },
                    proxyPort,
                )
            }
        }

        Spacer(modifier = Modifier.height(Size10dp))

        Box(modifier = Modifier.padding(Size40dp, 0.dp, Size40dp, 0.dp)) {
            Button(
                enabled = acceptedTerms.value,
                onClick = {
                    if (!acceptedTerms.value) {
                        termsAcceptanceIsRequired = context.getString(R.string.acceptance_of_terms_is_required)
                    }

                    if (displayName.value.text.isBlank()) {
                        errorMessage = context.getString(R.string.key_is_required)
                    }

                    if (acceptedTerms.value && displayName.value.text.isNotBlank()) {
                        accountStateViewModel.newKey(useProxy.value, proxyPort.value.toInt(), displayName.value.text)
                    }
                },
                shape = RoundedCornerShape(Size35dp),
                modifier = Modifier.height(50.dp),
            ) {
                Text(
                    text = stringResource(R.string.create_account),
                    modifier = Modifier.padding(horizontal = Size40dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(Size40dp))

        Text(text = stringResource(R.string.already_have_an_account))

        Spacer(modifier = Modifier.height(Size20dp))

        Box(modifier = Modifier.padding(Size40dp, 0.dp, Size40dp, 0.dp)) {
            OutlinedButton(
                onClick = onWantsToLogin,
                shape = RoundedCornerShape(Size35dp),
                modifier = Modifier.height(50.dp),
            ) {
                Text(
                    text = stringResource(R.string.login),
                    modifier = Modifier.padding(horizontal = Size40dp),
                )
            }
        }
    }
}
