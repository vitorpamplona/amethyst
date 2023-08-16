package com.vitorpamplona.amethyst.ui.actions

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.EventInterface
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import kotlinx.coroutines.launch

fun openAmber(
    event: EventInterface,
    intentResult: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    val json = event.toJson()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$json"))
    if (event is PrivateDmEvent) {
        intent.putExtra("type", "nip04_decrypt")
    }
    intent.`package` = "com.greenart7c3.nostrsigner.debug"
    intentResult.launch(intent)
}

@Composable
fun SignerDialog(
    onClose: () -> Unit,
    onPost: (signedEvent: Event) -> Unit,
    event: EventInterface
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
            if (event is PrivateDmEvent) {
                onPost(
                    Event(
                        event.id(),
                        event.pubKey(),
                        event.createdAt(),
                        event.kind(),
                        event.tags(),
                        signature,
                        event.sig()
                    )
                )
            }
        }
    )

    LaunchedEffect(Unit) {
        openAmber(event, intentResult)
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
                        onCancel = {
                            onClose()
                        }
                    )

                    PostButton(
                        onPost = {
                            val signedEvent = if (event is PrivateDmEvent) {
                                Event(
                                    event.id(),
                                    event.pubKey(),
                                    event.createdAt(),
                                    event.kind(),
                                    event.tags(),
                                    signature,
                                    event.sig()
                                )
                            } else {
                                Event(
                                    event.id(),
                                    event.pubKey(),
                                    event.createdAt(),
                                    event.kind(),
                                    event.tags(),
                                    event.content(),
                                    signature
                                )
                            }
                            if (!signedEvent.hasValidSignature() && event !is PrivateDmEvent) {
                                scope.launch {
                                    Toast.makeText(
                                        context,
                                        "Invalid signature",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@PostButton
                            }
                            onPost(signedEvent)
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
                    onClick = { openAmber(event, intentResult) }
                ) {
                    Text("Open Amber")
                }
            }
        }
    }
}

@Preview
@Composable
fun Test() {
    SignerDialog(
        onClose = { },
        onPost = { },
        event = TextNoteEvent("", "", TimeUtils.now(), emptyList(), "test", "")
    )
}
