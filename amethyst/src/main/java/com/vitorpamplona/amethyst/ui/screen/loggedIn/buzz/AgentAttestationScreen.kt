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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzHeldAttestations
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.AttestationConditions
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Owner-side NIP-OA attestation issuance. The owner signs a standalone commitment
 * ([OwnerAttestation]) authorizing an agent pubkey to publish under optional
 * [AttestationConditions]; the agent then attaches the resulting `auth` tag to its
 * events and the relay grants it virtual membership while the owner stays a member.
 *
 * This is entirely offline: the signature covers a hashed commitment string, not a
 * Nostr event, so it needs the owner's **raw private key** — a NIP-46 bunker or NIP-55
 * external signer cannot produce it. Read-only accounts see an explanation instead of
 * the form. Nothing is published; the signed tag is a credential the owner hands to the
 * agent operator out-of-band.
 */
@Composable
fun AgentAttestationScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val keyPair = accountViewModel.account.settings.keyPair
    val myPubkey = accountViewModel.account.userProfile().pubkeyHex

    Scaffold(
        topBar = { TopBarWithBackButton("Attestations", nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Agent side: hold an attestation an owner gave you, so this account
            // authenticates to the owner's Buzz relays as a virtual member. Available to
            // any signer — holding a credential doesn't require the raw key.
            HoldAttestationSection(myPubkey = myPubkey)

            // Owner side: issue an attestation for an agent key. Needs the raw private key.
            val privKey = keyPair.privKey
            if (privKey == null) {
                ReadOnlyKeyNotice()
            } else {
                AttestationForm(ownerKey = keyPair)
            }
        }
    }
}

/**
 * Agent-side: paste an `auth` tag an owner issued to this account's key. It is verified
 * against [myPubkey] and, on success, stored in [BuzzHeldAttestations] so the auth
 * coordinator attaches it when this account AUTHs to a Buzz relay. In-memory only for
 * now — re-paste after an app restart.
 */
@Composable
private fun HoldAttestationSection(myPubkey: String) {
    val held by BuzzHeldAttestations.flow.collectAsState()
    val mine = held[myPubkey]

    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Hold an attestation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (mine != null) {
                Text(
                    text = "Holding an attestation for this account. It is attached automatically when you authenticate to a Buzz relay.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Grants: " + mine.conditions.ifEmpty { "any kind, any time (unrestricted)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { BuzzHeldAttestations.remove(myPubkey) }) {
                    Text("Remove")
                }
            } else {
                Text(
                    text = "Paste an owner-signed auth tag issued to this account to authenticate to their Buzz workspace as a virtual member.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    label = { Text("auth tag JSON") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        when (val outcome = parseHeldAttestation(input, myPubkey)) {
                            is HoldOutcome.Failure -> error = outcome.message
                            is HoldOutcome.Success -> {
                                BuzzHeldAttestations.put(myPubkey, outcome.attestation)
                                input = ""
                                error = null
                            }
                        }
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Hold attestation")
                }
            }
        }
    }
}

private sealed interface HoldOutcome {
    data class Success(
        val attestation: OwnerAttestation,
    ) : HoldOutcome

    data class Failure(
        val message: String,
    ) : HoldOutcome
}

/**
 * Parses a pasted `["auth", owner, conditions, sig]` JSON array and verifies it
 * authorizes [myPubkey]. Returns a human-readable failure on malformed JSON, a
 * non-`auth` tag, or a signature that doesn't verify for this key.
 */
private fun parseHeldAttestation(
    input: String,
    myPubkey: String,
): HoldOutcome {
    val tag =
        try {
            Json
                .parseToJsonElement(input.trim())
                .jsonArray
                .map { it.jsonPrimitive.content }
                .toTypedArray()
        } catch (e: Exception) {
            return HoldOutcome.Failure("Not a valid JSON tag array. Paste the [\"auth\", …] tag you were given.")
        }
    val attestation =
        OwnerAttestation.parse(tag)
            ?: return HoldOutcome.Failure("Not a NIP-OA auth tag.")
    if (!attestation.verify(myPubkey)) {
        return HoldOutcome.Failure("This attestation does not authorize the current account, or its signature is invalid.")
    }
    return HoldOutcome.Success(attestation)
}

@Composable
private fun ReadOnlyKeyNotice() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Local key required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    "A NIP-OA attestation is a signature over a hashed commitment, not a Nostr event, " +
                        "so it can only be produced by a signer that holds your raw private key. This " +
                        "account uses a remote (NIP-46 bunker) or external (NIP-55) signer, which cannot " +
                        "sign an attestation.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AttestationForm(ownerKey: KeyPair) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var agentInput by remember { mutableStateOf("") }
    var kindInput by remember { mutableStateOf("") }
    var afterInput by remember { mutableStateOf("") }
    var beforeInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<OwnerAttestation?>(null) }

    Text(
        text =
            "Authorize an agent pubkey to publish in your workspace without enrolling its key. " +
                "The agent attaches the signed tag below to its events.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = agentInput,
        onValueChange = {
            agentInput = it
            error = null
            result = null
        },
        label = { Text("Agent public key (npub or hex)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text = "Conditions (optional) — leave blank for an unrestricted attestation.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = kindInput,
        onValueChange = {
            kindInput = it.filter(Char::isDigit)
            error = null
            result = null
        },
        label = { Text("Restrict to kind (0–65535)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = afterInput,
        onValueChange = {
            afterInput = it.filter(Char::isDigit)
            error = null
            result = null
        },
        label = { Text("Only events after (unix seconds)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = beforeInput,
        onValueChange = {
            beforeInput = it.filter(Char::isDigit)
            error = null
            result = null
        },
        label = { Text("Only events before (unix seconds)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    error?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Button(
        onClick = {
            when (val outcome = buildAttestation(agentInput, kindInput, afterInput, beforeInput, ownerKey)) {
                is AttestationOutcome.Failure -> {
                    error = outcome.message
                    result = null
                }
                is AttestationOutcome.Success -> {
                    error = null
                    result = outcome.attestation
                }
            }
        },
        enabled = agentInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Generate attestation")
    }

    result?.let { attestation ->
        AttestationResultCard(
            attestation = attestation,
            onCopy = { scope.launch { clipboard.setText(attestation.toTagJson()) } },
        )
    }
}

@Composable
private fun AttestationResultCard(
    attestation: OwnerAttestation,
    onCopy: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Signed attestation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    "Grants: " +
                        (attestation.conditions.ifEmpty { "any kind, any time (unrestricted)" }),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = attestation.toTagJson(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onCopy) {
                    Text("Copy tag")
                }
            }
            Text(
                text =
                    "⚠ Hand this to the agent operator only. While it is valid and you remain a " +
                        "workspace member, the relay lets this agent post as a member under the " +
                        "conditions above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private sealed interface AttestationOutcome {
    data class Success(
        val attestation: OwnerAttestation,
    ) : AttestationOutcome

    data class Failure(
        val message: String,
    ) : AttestationOutcome
}

/**
 * Validates the form inputs and signs an [OwnerAttestation], or returns a
 * human-readable failure. Kept out of composition so the signing (raw-key) path is a
 * plain function.
 */
private fun buildAttestation(
    agentInput: String,
    kindInput: String,
    afterInput: String,
    beforeInput: String,
    ownerKey: KeyPair,
): AttestationOutcome {
    val agentHex =
        decodePublicKeyAsHexOrNull(agentInput.trim())
            ?: return AttestationOutcome.Failure("Invalid agent public key. Enter an npub or 64-char hex key.")
    if (!agentHex.isValid()) {
        return AttestationOutcome.Failure("Invalid agent public key. Enter an npub or 64-char hex key.")
    }

    val kind =
        if (kindInput.isBlank()) {
            null
        } else {
            val parsed = kindInput.toIntOrNull()
            if (parsed == null || parsed !in 0..65535) {
                return AttestationOutcome.Failure("Kind must be between 0 and 65535.")
            }
            parsed
        }

    val after = parseOptionalUnix(afterInput) ?: return AttestationOutcome.Failure("\"After\" must be a unix time in 0–4294967295.")
    val before = parseOptionalUnix(beforeInput) ?: return AttestationOutcome.Failure("\"Before\" must be a unix time in 0–4294967295.")

    val conditions = AttestationConditions(kind = kind, createdAtBefore = before.value, createdAtAfter = after.value)

    return try {
        AttestationOutcome.Success(OwnerAttestation.sign(agentHex, conditions, ownerKey))
    } catch (e: IllegalArgumentException) {
        AttestationOutcome.Failure(e.message ?: "Could not sign the attestation.")
    }
}

/** Wraps a nullable parse so "absent" and "invalid" are distinguishable from the caller. */
private class OptionalUnix(
    val value: Long?,
)

private fun parseOptionalUnix(input: String): OptionalUnix? {
    if (input.isBlank()) return OptionalUnix(null)
    val parsed = input.toLongOrNull() ?: return null
    if (parsed !in 0..4294967295L) return null
    return OptionalUnix(parsed)
}

/** Serializes the `auth` tag to a JSON array string (values are hex / canonical ASCII). */
private fun OwnerAttestation.toTagJson(): String = toTag().joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
