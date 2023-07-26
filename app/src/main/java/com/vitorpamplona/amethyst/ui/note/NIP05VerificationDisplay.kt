package com.vitorpamplona.amethyst.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserMetadata
import com.vitorpamplona.amethyst.service.Nip05Verifier
import com.vitorpamplona.amethyst.ui.note.NIP05CheckingIcon
import com.vitorpamplona.amethyst.ui.note.NIP05FailedVerification
import com.vitorpamplona.amethyst.ui.note.NIP05VerifiedIcon
import com.vitorpamplona.amethyst.ui.theme.NIP05IconSize
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.ui.theme.nip05
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun nip05VerificationAsAState(userMetadata: UserMetadata, pubkeyHex: String): MutableState<Boolean?> {
    val nip05Verified = remember(userMetadata.nip05) {
        // starts with null if must verify or already filled in if verified in the last hour
        val default = if ((userMetadata.nip05LastVerificationTime ?: 0) > TimeUtils.oneHourAgo()) {
            userMetadata.nip05Verified
        } else {
            null
        }

        mutableStateOf(default)
    }

    if (nip05Verified.value == null) {
        LaunchedEffect(key1 = userMetadata.nip05) {
            launch(Dispatchers.IO) {
                userMetadata.nip05?.ifBlank { null }?.let { nip05 ->
                    Nip05Verifier().verifyNip05(
                        nip05,
                        onSuccess = {
                            // Marks user as verified
                            if (it == pubkeyHex) {
                                userMetadata.nip05Verified = true
                                userMetadata.nip05LastVerificationTime = TimeUtils.now()

                                if (nip05Verified.value != true) {
                                    nip05Verified.value = true
                                }
                            } else {
                                userMetadata.nip05Verified = false
                                userMetadata.nip05LastVerificationTime = 0

                                if (nip05Verified.value != false) {
                                    nip05Verified.value = false
                                }
                            }
                        },
                        onError = {
                            userMetadata.nip05LastVerificationTime = 0
                            userMetadata.nip05Verified = false

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
    val nip05 by baseUser.live().metadata.map {
        it.user.nip05()
    }.observeAsState(baseUser.nip05())

    Crossfade(targetState = nip05, modifier = columnModifier) {
        if (it != null) {
            DisplayNIP05Line(it, baseUser, columnModifier)
        } else {
            Text(
                text = baseUser.pubkeyDisplayHex(),
                color = MaterialTheme.colors.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = columnModifier
            )
        }
    }
}

@Composable
private fun DisplayNIP05Line(nip05: String, baseUser: User, columnModifier: Modifier = Modifier) {
    Column(modifier = columnModifier) {
        val nip05Verified = nip05VerificationAsAState(baseUser.info!!, baseUser.pubkeyHex)
        Crossfade(targetState = nip05Verified) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DisplayNIP05(nip05, it)
            }
        }
    }
}

@Composable
private fun DisplayNIP05(
    nip05: String,
    nip05Verified: MutableState<Boolean?>
) {
    val uri = LocalUriHandler.current
    val (user, domain) = remember(nip05) {
        val parts = nip05.split("@")
        if (parts.size == 1) {
            listOf("_", parts[0])
        } else {
            listOf(parts[0], parts[1])
        }
    }

    if (user != "_") {
        Text(
            text = remember(nip05) { AnnotatedString(user) },
            color = MaterialTheme.colors.nip05,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    NIP05VerifiedSymbol(nip05Verified, NIP05IconSize)

    ClickableText(
        text = remember(nip05) { AnnotatedString(domain) },
        onClick = { runCatching { uri.openUri("https://$domain") } },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.nip05),
        maxLines = 1,
        overflow = TextOverflow.Visible
    )
}

@Composable
private fun NIP05VerifiedSymbol(nip05Verified: MutableState<Boolean?>, modifier: Modifier) {
    Crossfade(targetState = nip05Verified.value) {
        when (it) {
            null -> NIP05CheckingIcon(modifier = modifier)
            true -> NIP05VerifiedIcon(modifier = modifier)
            false -> NIP05FailedVerification(modifier = modifier)
        }
    }
}

@Composable
fun DisplayNip05ProfileStatus(user: User) {
    val uri = LocalUriHandler.current

    user.nip05()?.let { nip05 ->
        if (nip05.split("@").size <= 2) {
            val nip05Verified = nip05VerificationAsAState(user.info!!, user.pubkeyHex)
            Row(verticalAlignment = Alignment.CenterVertically) {
                NIP05VerifiedSymbol(nip05Verified, Size16Modifier)
                var domainPadStart = 5.dp

                val (user, domain) = remember(nip05) {
                    val parts = nip05.split("@")
                    if (parts.size == 1) {
                        listOf("_", parts[0])
                    } else {
                        listOf(parts[0], parts[1])
                    }
                }

                if (user != "_") {
                    Text(
                        text = remember { AnnotatedString(user + "@") },
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    domainPadStart = 0.dp
                }

                ClickableText(
                    text = AnnotatedString(domain),
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
