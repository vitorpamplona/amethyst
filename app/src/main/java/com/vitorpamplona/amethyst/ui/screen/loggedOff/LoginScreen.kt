package com.vitorpamplona.amethyst.ui.screen.loggedOff

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.PackageUtils
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ConnectOrbotDialog
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.signers.ExternalSignerLauncher
import com.vitorpamplona.quartz.signers.SignerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

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
    var dialogOpen by remember {
        mutableStateOf(false)
    }
    val useProxy = remember { mutableStateOf(false) }
    val proxyPort = remember { mutableStateOf("9050") }
    var connectOrbotDialogOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var loginWithExternalSigner by remember { mutableStateOf(false) }

    if (loginWithExternalSigner) {
        val externalSignerLauncher = remember { ExternalSignerLauncher("", signerPackageName = "") }
        val id = remember { UUID.randomUUID().toString() }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = { result ->
                if (result.resultCode != Activity.RESULT_OK) {
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            Amethyst.instance,
                            "Sign request rejected",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    result.data?.let {
                        externalSignerLauncher.newResult(it)
                    }
                }
            }
        )

        val activity = getActivity() as MainActivity

        DisposableEffect(launcher, activity, externalSignerLauncher) {
            externalSignerLauncher.registerLauncher(
                launcher = {
                    try {
                        activity.prepareToLaunchSigner()
                        launcher.launch(it)
                    } catch (e: Exception) {
                        Log.e("Signer", "Error opening Signer app", e)
                        scope.launch(Dispatchers.Main) {
                            Toast.makeText(
                                Amethyst.instance,
                                R.string.error_opening_external_signer,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                contentResolver = { Amethyst.instance.contentResolver }
            )
            onDispose {
                externalSignerLauncher.clearLauncher()
            }
        }

        LaunchedEffect(loginWithExternalSigner, externalSignerLauncher) {
            externalSignerLauncher.openSignerApp(
                "",
                SignerType.GET_PUBLIC_KEY,
                "",
                id
            ) { result ->
                val split = result.split("-")
                val pubkey = split.first()
                val packageName = if (split.size > 1) split[1] else ""
                key.value = TextFieldValue(pubkey)
                if (!acceptedTerms.value) {
                    termsAcceptanceIsRequired =
                        context.getString(R.string.acceptance_of_terms_is_required)
                }

                if (key.value.text.isBlank()) {
                    errorMessage = context.getString(R.string.key_is_required)
                }

                if (acceptedTerms.value && key.value.text.isNotBlank()) {
                    accountViewModel.login(key.value.text, useProxy.value, proxyPort.value.toInt(), true, packageName) {
                        errorMessage = context.getString(R.string.invalid_key)
                    }
                }
            }
        }
    }

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
                        color = MaterialTheme.colorScheme.placeholderText
                    )
                },
                trailingIcon = {
                    Row {
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
                    }
                },
                leadingIcon = {
                    if (dialogOpen) {
                        SimpleQrCodeScanner {
                            dialogOpen = false
                            if (!it.isNullOrEmpty()) {
                                key.value = TextFieldValue(it)
                            }
                        }
                    }
                    IconButton(onClick = { dialogOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_qrcode),
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (!acceptedTerms.value) {
                            termsAcceptanceIsRequired = context.getString(R.string.acceptance_of_terms_is_required)
                        }

                        if (key.value.text.isBlank()) {
                            errorMessage = context.getString(R.string.key_is_required)
                        }

                        if (acceptedTerms.value && key.value.text.isNotBlank()) {
                            accountViewModel.login(key.value.text, useProxy.value, proxyPort.value.toInt()) {
                                errorMessage = context.getString(R.string.invalid_key)
                            }
                        }
                    }
                )
            )
            if (errorMessage.isNotBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
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
                        SpanStyle(color = MaterialTheme.colorScheme.onBackground)

                    val clickableTextStyle =
                        SpanStyle(color = MaterialTheme.colorScheme.primary)

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
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (PackageUtils.isOrbotInstalled(context)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useProxy.value,
                        onCheckedChange = {
                            if (it) {
                                connectOrbotDialogOpen = true
                            }
                        }
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
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        proxyPort
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.padding(40.dp, 0.dp, 40.dp, 0.dp)) {
                Button(
                    enabled = acceptedTerms.value,
                    onClick = {
                        if (!acceptedTerms.value) {
                            termsAcceptanceIsRequired = context.getString(R.string.acceptance_of_terms_is_required)
                        }

                        if (key.value.text.isBlank()) {
                            errorMessage = context.getString(R.string.key_is_required)
                        }

                        if (acceptedTerms.value && key.value.text.isNotBlank()) {
                            accountViewModel.login(key.value.text, useProxy.value, proxyPort.value.toInt()) {
                                errorMessage = context.getString(R.string.invalid_key)
                            }
                        }
                    },
                    shape = RoundedCornerShape(Size35dp),
                    modifier = Modifier
                        .height(50.dp)
                ) {
                    Text(
                        text = stringResource(R.string.login),
                        modifier = Modifier.padding(horizontal = 40.dp)
                    )
                }
            }

            if (PackageUtils.isAmberInstalled(context)) {
                Box(modifier = Modifier.padding(40.dp, 40.dp, 40.dp, 0.dp)) {
                    Button(
                        enabled = acceptedTerms.value,
                        onClick = {
                            if (!acceptedTerms.value) {
                                termsAcceptanceIsRequired = context.getString(R.string.acceptance_of_terms_is_required)
                                return@Button
                            }

                            loginWithExternalSigner = true
                            return@Button
                        },
                        shape = RoundedCornerShape(Size35dp),
                        modifier = Modifier
                            .height(50.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.login_with_external_signer),
                            modifier = Modifier.padding(horizontal = 40.dp)
                        )
                    }
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
                    accountViewModel.newKey(useProxy.value, proxyPort.value.toInt())
                } else {
                    termsAcceptanceIsRequired =
                        context.getString(R.string.acceptance_of_terms_is_required)
                }
            },
            style = TextStyle(
                fontSize = Font14SP,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        )
    }
}
