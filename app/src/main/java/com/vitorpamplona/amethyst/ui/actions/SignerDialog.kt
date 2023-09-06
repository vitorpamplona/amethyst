package com.vitorpamplona.amethyst.ui.actions

import android.app.Activity.RESULT_OK
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.service.AmberUtils
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.Event
import kotlinx.coroutines.launch

enum class SignerType {
    SIGN_EVENT,
    NIP04_ENCRYPT,
    NIP04_DECRYPT,
    NIP44_ENCRYPT,
    NIP44_DECRYPT,
    GET_PUBLIC_KEY
}

@Composable
fun SignerDialog(
    onClose: () -> Unit,
    onPost: (content: String) -> Unit,
    data: String,
    type: SignerType = SignerType.SIGN_EVENT,
    pubKey: HexKey = ""
) {
    var signature by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val intentResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            if (it.resultCode != RESULT_OK) {
                scope.launch {
                    Toast.makeText(
                        context,
                        "Sign request rejected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@rememberLauncherForActivityResult
            }

            signature = it.data?.getStringExtra("signature") ?: ""
            if (type != SignerType.SIGN_EVENT) {
                onPost(
                    signature
                )
            }
            ServiceManager.shouldPauseService = true
        }
    )

    LaunchedEffect(Unit) {
        AmberUtils.openAmber(data, type, intentResult, pubKey, "")
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
                .fillMaxHeight()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(
                        onPress = {
                            onClose()
                        }
                    )

                    PostButton(
                        onPost = {
                            if (type == SignerType.SIGN_EVENT) {
                                val event = Event.fromJson(data)
                                val signedEvent = Event(
                                    event.id(),
                                    event.pubKey(),
                                    event.createdAt(),
                                    event.kind(),
                                    event.tags(),
                                    event.content(),
                                    signature
                                )

                                if (!signedEvent.hasValidSignature()) {
                                    scope.launch {
                                        Toast.makeText(
                                            context,
                                            "Invalid signature",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    return@PostButton
                                }
                                onPost(signedEvent.toJson())
                            } else {
                                onPost(signature)
                            }
                        },
                        isActive = true
                    )
                }

                Spacer(modifier = DoubleVertSpacer)

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = signature,
                    onValueChange = {
                        signature = it
                    },
                    placeholder = { Text("Signature (hex)") },
                    trailingIcon = {
                        Row {
                            IconButton(
                                onClick = { }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = stringResource(R.string.paste_from_clipboard)
                                )
                            }
                        }
                    }
                )
                Button(
                    shape = ButtonBorder,
                    onClick = { AmberUtils.openAmber(data, type, intentResult, pubKey, "") }
                ) {
                    Text("Open Amber")
                }
            }
        }
    }
}
