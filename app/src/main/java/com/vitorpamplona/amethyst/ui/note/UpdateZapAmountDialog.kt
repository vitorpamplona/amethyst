package com.vitorpamplona.amethyst.ui.note

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.decodePublicKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.SaveButton
import com.vitorpamplona.amethyst.ui.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.getFragmentActivity
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope as rememberCoroutineScope

class UpdateZapAmountViewModel(val account: Account) : ViewModel() {
    var nextAmount by mutableStateOf(TextFieldValue(""))
    var amountSet by mutableStateOf(listOf<Long>())
    var walletConnectRelay by mutableStateOf(TextFieldValue(""))
    var walletConnectPubkey by mutableStateOf(TextFieldValue(""))
    var walletConnectSecret by mutableStateOf(TextFieldValue(""))
    var selectedZapType by mutableStateOf(LnZapEvent.ZapType.PRIVATE)

    fun load() {
        this.amountSet = account.zapAmountChoices
        this.walletConnectPubkey = account.zapPaymentRequest?.pubKeyHex?.let { TextFieldValue(it) } ?: TextFieldValue("")
        this.walletConnectRelay = account.zapPaymentRequest?.relayUri?.let { TextFieldValue(it) } ?: TextFieldValue("")
        this.walletConnectSecret = account.zapPaymentRequest?.secret?.let { TextFieldValue(it) } ?: TextFieldValue("")
        this.selectedZapType = account.defaultZapType
    }

    fun toListOfAmounts(commaSeparatedAmounts: String): List<Long> {
        return commaSeparatedAmounts.split(",").map { it.trim().toLongOrNull() ?: 0 }
    }

    fun addAmount() {
        val newValue = nextAmount.text.trim().toLongOrNull()
        if (newValue != null) {
            amountSet = amountSet + newValue
        }

        nextAmount = TextFieldValue("")
    }

    fun removeAmount(amount: Long) {
        amountSet = amountSet - amount
    }

    fun sendPost() {
        account?.changeZapAmounts(amountSet)
        account?.changeDefaultZapType(selectedZapType)

        if (walletConnectRelay.text.isNotBlank() && walletConnectPubkey.text.isNotBlank()) {
            val pubkeyHex = try {
                decodePublicKey(walletConnectPubkey.text.trim()).toHexKey()
            } catch (e: Exception) {
                null
            }

            val relayUrl = walletConnectRelay.text.ifBlank { null }?.let {
                var addedWSS =
                    if (!it.startsWith("wss://") && !it.startsWith("ws://")) "wss://$it" else it
                if (addedWSS.endsWith("/")) addedWSS = addedWSS.dropLast(1)

                addedWSS
            }

            val unverifiedPrivKey = walletConnectSecret.text.ifBlank { null }
            val privKeyHex = try {
                unverifiedPrivKey?.let { decodePublicKey(it).toHexKey() }
            } catch (e: Exception) {
                null
            }

            if (pubkeyHex != null) {
                account?.changeZapPaymentRequest(
                    Nip47URI(
                        pubkeyHex,
                        relayUrl,
                        privKeyHex
                    )
                )
            } else {
                account?.changeZapPaymentRequest(null)
            }
        } else {
            account?.changeZapPaymentRequest(null)
        }

        nextAmount = TextFieldValue("")
    }

    fun cancel() {
        nextAmount = TextFieldValue("")
    }

    fun hasChanged(): Boolean {
        return (
            selectedZapType != account?.defaultZapType ||
                amountSet != account?.zapAmountChoices ||
                walletConnectPubkey.text != (account?.zapPaymentRequest?.pubKeyHex ?: "") ||
                walletConnectRelay.text != (account?.zapPaymentRequest?.relayUri ?: "") ||
                walletConnectSecret.text != (account?.zapPaymentRequest?.secret ?: "")
            )
    }

    fun updateNIP47(uri: String) {
        val contact = Nip47.parse(uri)
        if (contact != null) {
            walletConnectPubkey =
                TextFieldValue(contact.pubKeyHex)
            walletConnectRelay =
                TextFieldValue(contact.relayUri ?: "")
            walletConnectSecret =
                TextFieldValue(contact.secret ?: "")
        }
    }

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <UpdateZapAmountViewModel : ViewModel> create(modelClass: Class<UpdateZapAmountViewModel>): UpdateZapAmountViewModel {
            return UpdateZapAmountViewModel(account) as UpdateZapAmountViewModel
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateZapAmountDialog(onClose: () -> Unit, nip47uri: String? = null, accountViewModel: AccountViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val postViewModel: UpdateZapAmountViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex,
        factory = UpdateZapAmountViewModel.Factory(accountViewModel.account)
    )

    val uri = LocalUriHandler.current

    val zapTypes = listOf(
        Triple(LnZapEvent.ZapType.PUBLIC, stringResource(id = R.string.zap_type_public), stringResource(id = R.string.zap_type_public_explainer)),
        Triple(LnZapEvent.ZapType.PRIVATE, stringResource(id = R.string.zap_type_private), stringResource(id = R.string.zap_type_private_explainer)),
        Triple(LnZapEvent.ZapType.ANONYMOUS, stringResource(id = R.string.zap_type_anonymous), stringResource(id = R.string.zap_type_anonymous_explainer)),
        Triple(LnZapEvent.ZapType.NONZAP, stringResource(id = R.string.zap_type_nonzap), stringResource(id = R.string.zap_type_nonzap_explainer))
    )

    val zapOptions = remember { zapTypes.map { it.second }.toImmutableList() }
    val zapOptionExplainers = remember { zapTypes.map { it.third }.toImmutableList() }

    LaunchedEffect(accountViewModel) {
        postViewModel.load()
        if (nip47uri != null) {
            try {
                postViewModel.updateNIP47(nip47uri)
            } catch (e: IllegalArgumentException) {
                scope.launch {
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp).imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = {
                        postViewModel.cancel()
                        onClose()
                    })

                    SaveButton(
                        onPost = {
                            postViewModel.sendPost()
                            onClose()
                        },
                        isActive = postViewModel.hasChanged()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.animateContentSize()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    postViewModel.amountSet.forEach { amountInSats ->
                                        Button(
                                            modifier = Modifier.padding(horizontal = 3.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = MaterialTheme.colors.primary
                                            ),
                                            onClick = {
                                                postViewModel.removeAmount(amountInSats)
                                            }
                                        ) {
                                            Text(
                                                "⚡ ${
                                                showAmount(
                                                    amountInSats.toBigDecimal().setScale(1)
                                                )
                                                } ✖",
                                                color = Color.White,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                label = { Text(text = stringResource(R.string.new_amount_in_sats)) },
                                value = postViewModel.nextAmount,
                                onValueChange = {
                                    postViewModel.nextAmount = it
                                },
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    capitalization = KeyboardCapitalization.None,
                                    keyboardType = KeyboardType.Number
                                ),
                                placeholder = {
                                    Text(
                                        text = "100, 1000, 5000",
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .weight(1f)
                            )

                            Button(
                                onClick = { postViewModel.addAmount() },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = MaterialTheme.colors.primary
                                )
                            ) {
                                Text(text = stringResource(R.string.add), color = Color.White)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextSpinner(
                                label = stringResource(id = R.string.zap_type_explainer),
                                placeholder = zapTypes.filter { it.first == accountViewModel.defaultZapType() }
                                    .first().second,
                                options = zapOptions,
                                explainers = zapOptionExplainers,
                                onSelect = {
                                    postViewModel.selectedZapType = zapTypes[it].first
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 5.dp)
                            )
                        }

                        Divider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            thickness = 0.25.dp
                        )

                        var qrScanning by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(id = R.string.wallet_connect_service),
                                Modifier.weight(1f)
                            )

                            IconButton(onClick = {
                                runCatching { uri.openUri("https://nwc.getalby.com/apps/new?c=Amethyst") }
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.alby),
                                    null,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                            }

                            IconButton(onClick = {
                                qrScanning = true
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_qrcode),
                                    null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colors.primary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(id = R.string.wallet_connect_service_explainer),
                                Modifier.weight(1f),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                fontSize = 14.sp
                            )
                        }

                        if (qrScanning) {
                            SimpleQrCodeScanner {
                                qrScanning = false
                                if (!it.isNullOrEmpty()) {
                                    try {
                                        postViewModel.updateNIP47(it)
                                    } catch (e: IllegalArgumentException) {
                                        scope.launch {
                                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                label = { Text(text = stringResource(R.string.wallet_connect_service_pubkey)) },
                                value = postViewModel.walletConnectPubkey,
                                onValueChange = {
                                    postViewModel.walletConnectPubkey = it
                                },
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    capitalization = KeyboardCapitalization.None
                                ),
                                placeholder = {
                                    Text(
                                        text = "npub, hex",
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                label = { Text(text = stringResource(R.string.wallet_connect_service_relay)) },
                                modifier = Modifier.weight(1f),
                                value = postViewModel.walletConnectRelay,
                                onValueChange = { postViewModel.walletConnectRelay = it },
                                placeholder = {
                                    Text(
                                        text = "wss://relay.server.com",
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                        maxLines = 1
                                    )
                                },
                                singleLine = true
                            )
                        }

                        var showPassword by remember {
                            mutableStateOf(false)
                        }

                        val scope = rememberCoroutineScope()
                        val context = LocalContext.current

                        val keyguardLauncher =
                            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                                if (result.resultCode == Activity.RESULT_OK) {
                                    showPassword = true
                                }
                            }

                        val authTitle =
                            stringResource(id = R.string.wallet_connect_service_show_secret)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                label = { Text(text = stringResource(R.string.wallet_connect_service_secret)) },
                                modifier = Modifier.weight(1f),
                                value = postViewModel.walletConnectSecret,
                                onValueChange = { postViewModel.walletConnectSecret = it },
                                keyboardOptions = KeyboardOptions(
                                    autoCorrect = false,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Go
                                ),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.wallet_connect_service_secret_placeholder),
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (!showPassword) {
                                            authenticate(
                                                authTitle,
                                                context,
                                                scope,
                                                keyguardLauncher
                                            ) {
                                                showPassword = true
                                            }
                                        } else {
                                            showPassword = false
                                        }
                                    }) {
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
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
                            )
                        }
                    }
                }
            }
        }
    }
}

fun authenticate(
    title: String,
    context: Context,
    scope: CoroutineScope,
    keyguardLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    onApproved: () -> Unit
) {
    val fragmentContext = context.getFragmentActivity()!!
    val keyguardManager =
        fragmentContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    if (!keyguardManager.isDeviceSecure) {
        onApproved()
        return
    }

    @Suppress("DEPRECATION")
    fun keyguardPrompt() {
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            context.getString(R.string.app_name_release),
            title
        )

        keyguardLauncher.launch(intent)
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        keyguardPrompt()
        return
    }

    val biometricManager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.app_name_release))
        .setSubtitle(title)
        .setAllowedAuthenticators(authenticators)
        .build()

    val biometricPrompt = BiometricPrompt(
        fragmentContext,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)

                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> keyguardPrompt()
                    BiometricPrompt.ERROR_LOCKOUT -> keyguardPrompt()
                    else ->
                        scope.launch {
                            Toast.makeText(
                                context,
                                "${context.getString(R.string.biometric_error)}: $errString",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.biometric_authentication_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onApproved()
            }
        }
    )

    when (biometricManager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> biometricPrompt.authenticate(promptInfo)
        else -> keyguardPrompt()
    }
}
