package com.vitorpamplona.amethyst.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.Contact
import com.vitorpamplona.amethyst.model.FollowSortOption

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val contacts by viewModel.contacts.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Followers") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                        Spacer(modifier = Modifier.width(8.dp))
                        DropdownMenu(
                            expanded = false, // In a real app, this would be managed by state
                            onDismissRequest = { }
                        ) {
                            FollowSortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    onClick = { viewModel.setSortOption(option) }
                                ) {
                                    Text(option.displayName)
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues)
        ) {
            items(contacts) { contact ->
                ContactItem(contact = contact)
            }
        }
    }
}

@Composable
fun ContactItem(contact: Contact) {
    ListItem(
        headlineText = { Text(contact.displayName) },
        supportingText = { Text("Followers: ${contact.followerCount} • Sats Sent: ${contact.satsSent}") }
    )
}
