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
package com.vitorpamplona.amethyst.ui.screen.loggedOff.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.DefaultSignerPermissions
import com.vitorpamplona.amethyst.ui.theme.Size0dp
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.quartz.nip55AndroidSigner.api.PubKeyResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.client.ExternalSignerLogin
import com.vitorpamplona.quartz.nip55AndroidSigner.client.getExternalSignersInstalled
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExternalSignerButton(loginViewModel: LoginViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val installedSigners = getExternalSignersInstalled(context)
    var shouldSelectSigner by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            scope.launch {
                val resultData = result.data
                if (result.resultCode == Activity.RESULT_OK && resultData != null) {
                    val loginInfo = ExternalSignerLogin.parseResult(resultData)
                    if (loginInfo is SignerResult.RequestAddressed.Successful<PubKeyResult>) {
                        loginViewModel.updateKey(TextFieldValue(loginInfo.result.pubkey), false)
                        loginViewModel.loginWithExternalSigner(loginInfo.result.packageName)
                    }
                } else {
                    loginViewModel.errorManager.error(R.string.sign_request_rejected2)
                }
            }
        }

    if (shouldSelectSigner) {
        Dialog(
            onDismissRequest = {
                shouldSelectSigner = false
            },
            content = {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(R.string.select_signer),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyColumn {
                            items(installedSigners) {
                                val appName = it.loadLabel(context.packageManager).toString()
                                val appIcon = it.loadIcon(context.packageManager)
                                val iconBitmap = appIcon.toBitmap()

                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 8.dp)
                                        .clickable {
                                            if (!loginViewModel.acceptedTerms) {
                                                loginViewModel.termsAcceptanceIsRequiredError = true
                                            } else {
                                                try {
                                                    launcher.launch(ExternalSignerLogin.createIntent(DefaultSignerPermissions, it.activityInfo.packageName))
                                                } catch (e: Exception) {
                                                    if (e is CancellationException) throw e
                                                    Log.e("ExternalSigner", "Error opening Signer app", e)
                                                    loginViewModel.errorManager.error(R.string.error_opening_external_signer)
                                                } finally {
                                                    shouldSelectSigner = false
                                                }
                                            }
                                        },
                                ) {
                                    val painter =
                                        rememberAsyncImagePainter(
                                            iconBitmap,
                                        )

                                    Image(
                                        painter = painter,
                                        contentDescription = appName,
                                        modifier =
                                            Modifier
                                                .size(48.dp)
                                                .padding(end = 16.dp),
                                    )
                                    Column {
                                        Text(appName)
                                        Text(
                                            it.activityInfo.packageName,
                                            fontSize = 14.sp,
                                            color = Color.Gray,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    Box(modifier = Modifier.padding(Size40dp, Size20dp, Size40dp, Size0dp)) {
        LoginWithAmberButton(
            enabled = loginViewModel.acceptedTerms,
            onClick = {
                if (!loginViewModel.acceptedTerms) {
                    loginViewModel.termsAcceptanceIsRequiredError = true
                } else {
                    try {
                        if (installedSigners.size == 1) {
                            launcher.launch(ExternalSignerLogin.createIntent(DefaultSignerPermissions))
                        } else {
                            shouldSelectSigner = true
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("ExternalSigner", "Error opening Signer app", e)
                        loginViewModel.errorManager.error(R.string.error_opening_external_signer)
                    }
                }
            },
        )
    }
}
