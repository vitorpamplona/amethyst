package com.vitorpamplona.amethyst.ui.screen.loggedOff

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import java.util.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginPage(
    accountViewModel: AccountStateViewModel,
    isFirstLogin: Boolean
) {
    val key = remember { mutableStateOf(TextFieldValue("")) }
    var errorMessage by remember { mutableStateOf("") }
    val acceptedTerms = remember { mutableStateOf(!isFirstLogin) }
    var termsAcceptanceIsRequired by remember { mutableStateOf("") }
    val uri = LocalUriHandler.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // The first child is glued to the top.
        // Hence we have nothing at the top, an empty box is used.
        Box(modifier = Modifier.height(0.dp))

        // The second child, this column, is centered vertically.
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painterResource(id = R.drawable.amethyst),
                contentDescription = stringResource(R.string.app_logo),
                modifier = Modifier.size(200.dp),
                contentScale = ContentScale.Inside
            )

            Spacer(modifier = Modifier.height(40.dp))

            var showPassword by remember {
                mutableStateOf(false)
            }

            val autofillNode = AutofillNode(
                autofillTypes = listOf(AutofillType.Password),
                onFill = { key.value = TextFieldValue(it) }
            )
            val autofill = LocalAutofill.current
            LocalAutofillTree.current += autofillNode

            OutlinedTextField(
                modifier = Modifier
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
                value = key.value,
                onValueChange = { key.value = it },
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go
                ),
                placeholder = {
                    Text(
                        text = stringResource(R.string.nsec_npub_hex_private_key),
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showPassword) {
                                stringResource(R.string.show_password)
                            } else {
                                stringResource(
                                    R.string.hide_password
                                )
                            }
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardActions = KeyboardActions(
                    onGo = {
                        try {
                            accountViewModel.login(key.value.text)
                        } catch (e: Exception) {
                            errorMessage = context.getString(R.string.invalid_key)
                        }
                    }
                )
            )
            if (errorMessage.isNotBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isFirstLogin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acceptedTerms.value,
                        onCheckedChange = { acceptedTerms.value = it }
                    )

                    val regularText =
                        SpanStyle(color = MaterialTheme.colors.onBackground)

                    val clickableTextStyle =
                        SpanStyle(color = MaterialTheme.colors.primary)

                    val annotatedTermsString = buildAnnotatedString {
                        withStyle(regularText) {
                            append(stringResource(R.string.i_accept_the))
                        }

                        withStyle(clickableTextStyle) {
                            pushStringAnnotation("openTerms", "")
                            append(stringResource(R.string.terms_of_use))
                        }
                    }

                    ClickableText(
                        text = annotatedTermsString
                    ) { spanOffset ->
                        annotatedTermsString.getStringAnnotations(spanOffset, spanOffset)
                            .firstOrNull()
                            ?.also { span ->
                                if (span.tag == "openTerms") {
                                    runCatching { uri.openUri("https://github.com/vitorpamplona/amethyst/blob/main/PRIVACY.md") }
                                }
                            }
                    }
                }

                if (termsAcceptanceIsRequired.isNotBlank()) {
                    Text(
                        text = termsAcceptanceIsRequired,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.padding(40.dp, 0.dp, 40.dp, 0.dp)) {
                Button(
                    onClick = {
                        if (!acceptedTerms.value) {
                            termsAcceptanceIsRequired =
                                context.getString(R.string.acceptance_of_terms_is_required)
                        }

                        if (key.value.text.isBlank()) {
                            errorMessage = context.getString(R.string.key_is_required)
                        }

                        if (acceptedTerms.value && key.value.text.isNotBlank()) {
                            try {
                                accountViewModel.login(key.value.text)
                            } catch (e: Exception) {
                                errorMessage = context.getString(R.string.invalid_key)
                            }
                        }
                    },
                    shape = RoundedCornerShape(35.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults
                        .buttonColors(
                            backgroundColor = if (acceptedTerms.value) MaterialTheme.colors.primary else Color.Gray
                        )
                ) {
                    Text(text = stringResource(R.string.login))
                }
            }
        }

        // The last child is glued to the bottom.
        ClickableText(
            text = AnnotatedString(stringResource(R.string.generate_a_new_key)),
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            onClick = {
                if (acceptedTerms.value) {
                    accountViewModel.newKey()
                } else {
                    termsAcceptanceIsRequired =
                        context.getString(R.string.acceptance_of_terms_is_required)
                }
            },
            style = TextStyle(
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )
        )
    }
}
