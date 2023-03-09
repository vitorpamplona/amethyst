package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material.MaterialRichText
import com.halilibo.richtext.ui.resolveDefaults
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nostr.postr.toNsec

@Composable
fun AccountBackupDialog(account: Account, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colors.background)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = onClose)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    MaterialRichText(
                        style = RichTextStyle().resolveDefaults()
                    ) {
                        Markdown(
                            content = stringResource(R.string.account_backup_tips_md)
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    NSecCopyButton(account)
                }
            }
        }
    }
}

@Composable
private fun NSecCopyButton(
    account: Account
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                copyNSec(context, scope, account, clipboardManager)
            }
        }

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            authenticatedCopyNSec(context, scope, account, clipboardManager, keyguardLauncher)
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.primary
        ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Icon(
            tint = MaterialTheme.colors.onPrimary,
            imageVector = Icons.Default.Key,
            contentDescription = stringResource(R.string.copies_the_nsec_id_your_password_to_the_clipboard_for_backup),
            modifier = Modifier.padding(end = 5.dp)
        )
        Text(stringResource(id = R.string.copy_my_secret_key), color = MaterialTheme.colors.onPrimary)
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

private fun authenticatedCopyNSec(
    context: Context,
    scope: CoroutineScope,
    account: Account,
    clipboardManager: ClipboardManager,
    keyguardLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    val fragmentContext = context.getFragmentActivity()!!
    val keyguardManager =
        fragmentContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    if (!keyguardManager.isDeviceSecure) {
        copyNSec(context, scope, account, clipboardManager)
        return
    }

    fun keyguardPrompt() {
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            context.getString(R.string.app_name_release),
            context.getString(R.string.copy_my_secret_key)
        )

        keyguardLauncher.launch(intent)
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        keyguardPrompt()
        return
    }

    val biometricManager = BiometricManager.from(context)
    val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.app_name_release))
        .setSubtitle(context.getString(R.string.copy_my_secret_key))
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
                copyNSec(context, scope, account, clipboardManager)
            }
        }
    )

    when (biometricManager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> biometricPrompt.authenticate(promptInfo)
        else -> keyguardPrompt()
    }
}

private fun copyNSec(
    context: Context,
    scope: CoroutineScope,
    account: Account,
    clipboardManager: ClipboardManager
) {
    account.loggedIn.privKey?.let {
        clipboardManager.setText(AnnotatedString(it.toNsec()))
        scope.launch {
            Toast.makeText(
                context,
                context.getString(R.string.secret_key_copied_to_clipboard),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
