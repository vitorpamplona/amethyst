package com.vitorpamplona.amethyst.ios

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair

@Composable
fun AmethystApp() {
    MaterialTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.Login) }

        when (val current = screen) {
            is Screen.Login -> LoginScreen(
                onLogin = { pubkey ->
                    screen = Screen.Feed(pubkey)
                },
            )
            is Screen.Feed -> FeedScreen(
                pubkey = current.pubkey,
                onLogout = { screen = Screen.Login },
            )
        }
    }
}

sealed class Screen {
    data object Login : Screen()
    data class Feed(val pubkey: String) : Screen()
}

@Composable
fun LoginScreen(onLogin: (String) -> Unit) {
    var nsecInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Amethyst",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Nostr client for iOS",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = nsecInput,
            onValueChange = { nsecInput = it; errorMessage = null },
            label = { Text("Enter nsec or npub") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (nsecInput.isBlank()) {
                    // Generate a random keypair for demo
                    val kp = KeyPair()
                    onLogin(kp.pubKey.toHexKey())
                } else {
                    // TODO: parse nsec/npub
                    errorMessage = "nsec/npub parsing not yet implemented"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (nsecInput.isBlank()) "Continue as Guest" else "Login")
        }
    }
}

@Composable
fun FeedScreen(pubkey: String, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Amethyst",
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = onLogout) {
                Text("Logout")
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Logged in as: ${pubkey.take(8)}...${pubkey.takeLast(4)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Welcome to Amethyst for iOS! 🎉",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "This is the initial iOS build using Kotlin Multiplatform. " +
                        "The app shares business logic, Nostr protocol (quartz), and " +
                        "UI components (commons) with the Android and Desktop apps.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Coming soon:",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                listOf(
                    "• Relay connections and feed loading",
                    "• Note composition and publishing",
                    "• Profile viewing and editing",
                    "• NIP-46 remote signer support",
                    "• Image loading with blurhash previews",
                ).forEach { item ->
                    Text(item, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
