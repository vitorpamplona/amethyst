package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Report
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserMetadata
import com.vitorpamplona.amethyst.service.Nip05Verifier
import com.vitorpamplona.amethyst.ui.theme.Nip05
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

@Composable
fun nip05VerificationAsAState(user: UserMetadata, pubkeyHex: String): State<Boolean?> {
    val nip05Verified = remember(user.nip05) {
        // starts with null if must verify or already filled in if verified in the last hour
        val default = if ((user.nip05LastVerificationTime ?: 0) > (Date().time / 1000 - 60 * 60)) { // 1hour
            user.nip05Verified
        } else {
            null
        }

        mutableStateOf(default)
    }

    LaunchedEffect(key1 = user.nip05) {
        if (nip05Verified.value == null) {
            launch(Dispatchers.IO) {
                user.nip05?.ifBlank { null }?.let { nip05 ->
                    Nip05Verifier().verifyNip05(
                        nip05,
                        onSuccess = {
                            // Marks user as verified
                            if (it == pubkeyHex) {
                                user.nip05Verified = true
                                user.nip05LastVerificationTime = Date().time / 1000

                                if (nip05Verified.value != true) {
                                    nip05Verified.value = true
                                }
                            } else {
                                user.nip05Verified = false
                                user.nip05LastVerificationTime = 0

                                if (nip05Verified.value != false) {
                                    nip05Verified.value = false
                                }
                            }
                        },
                        onError = {
                            user.nip05LastVerificationTime = 0
                            user.nip05Verified = false

                            if (nip05Verified.value != false) {
                                nip05Verified.value = false
                            }
                        }
                    )
                }
            }
        }
    }

    return nip05Verified
}

@Composable
fun ObserveDisplayNip05Status(baseNote: Note, columnModifier: Modifier = Modifier) {
    val noteState by baseNote.live().metadata.observeAsState()
    val author by remember(noteState) {
        derivedStateOf {
            noteState?.note?.author
        }
    }

    author?.let {
        ObserveDisplayNip05Status(it, columnModifier)
    }
}

@Composable
fun ObserveDisplayNip05Status(baseUser: User, columnModifier: Modifier = Modifier) {
    val userState by baseUser.live().metadata.observeAsState()
    val isValidNIP05 by remember(userState) {
        derivedStateOf {
            userState?.user?.nip05()?.split("@")?.size == 2
        }
    }
    val nip05 by remember(userState) {
        derivedStateOf {
            userState?.user?.nip05()
        }
    }

    if (isValidNIP05) {
        nip05?.let {
            DisplayNIP05Line(it, baseUser, columnModifier)
        }
    }
}

@Composable
private fun DisplayNIP05Line(nip05: String, baseUser: User, columnModifier: Modifier = Modifier) {
    Column(modifier = columnModifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val nip05Verified by nip05VerificationAsAState(baseUser.info!!, baseUser.pubkeyHex)
            DisplayNIP05(nip05, nip05Verified)
        }
    }
}

@Composable
private fun DisplayNIP05(
    nip05: String,
    nip05Verified: Boolean?
) {
    val uri = LocalUriHandler.current
    val (user, domain) = remember(nip05) {
        nip05.split("@")
    }

    if (user != "_") {
        Text(
            text = AnnotatedString(user),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (nip05Verified == null) {
        Icon(
            tint = Color.Yellow,
            imageVector = Icons.Default.Downloading,
            contentDescription = "Downloading",
            modifier = Modifier
                .size(14.dp)
                .padding(top = 1.dp)
        )
    } else if (nip05Verified == true) {
        Icon(
            painter = painterResource(R.drawable.ic_verified),
            "NIP-05 Verified",
            tint = Nip05.copy(0.52f),
            modifier = Modifier
                .size(14.dp)
                .padding(top = 1.dp)
        )
    } else {
        Icon(
            tint = Color.Red,
            imageVector = Icons.Default.Report,
            contentDescription = "Invalid Nip05",
            modifier = Modifier
                .size(14.dp)
                .padding(top = 1.dp)
        )
    }

    ClickableText(
        text = AnnotatedString(domain),
        onClick = { runCatching { uri.openUri("https://$domain") } },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary.copy(0.52f)),
        maxLines = 1,
        overflow = TextOverflow.Visible
    )
}

@Composable
fun DisplayNip05ProfileStatus(user: User) {
    val uri = LocalUriHandler.current

    user.nip05()?.let { nip05 ->
        if (nip05.split("@").size == 2) {
            val nip05Verified by nip05VerificationAsAState(user.info!!, user.pubkeyHex)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (nip05Verified == null) {
                    Icon(
                        tint = Color.Yellow,
                        imageVector = Icons.Default.Downloading,
                        contentDescription = "Downloading",
                        modifier = Modifier.size(16.dp)
                    )
                } else if (nip05Verified == true) {
                    Icon(
                        painter = painterResource(R.drawable.ic_verified),
                        "NIP-05 Verified",
                        tint = Nip05,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        tint = Color.Red,
                        imageVector = Icons.Default.Report,
                        contentDescription = "Invalid Nip05",
                        modifier = Modifier.size(16.dp)
                    )
                }

                var domainPadStart = 5.dp

                if (nip05.split("@")[0] != "_") {
                    Text(
                        text = AnnotatedString(nip05.split("@")[0] + "@"),
                        modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    domainPadStart = 0.dp
                }

                ClickableText(
                    text = AnnotatedString(nip05.split("@")[1]),
                    onClick = { nip05.let { runCatching { uri.openUri("https://${it.split("@")[1]}") } } },
                    style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary),
                    modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = domainPadStart),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
