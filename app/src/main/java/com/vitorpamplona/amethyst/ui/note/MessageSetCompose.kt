package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.screen.MessageSetCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageSetCompose(messageSetCard: MessageSetCard, routeForLastRead: String, showHidden: Boolean = false, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val baseNote = remember { messageSetCard.note }

    val popupExpanded = remember { mutableStateOf(false) }
    val enablePopup = remember {
        { popupExpanded.value = true }
    }

    val scope = rememberCoroutineScope()

    val defaultBackgroundColor = MaterialTheme.colorScheme.background
    val backgroundColor = remember { mutableStateOf<Color>(defaultBackgroundColor) }
    val newItemColor = MaterialTheme.colorScheme.newItemBackgroundColor

    LaunchedEffect(key1 = messageSetCard) {
        accountViewModel.loadAndMarkAsRead(routeForLastRead, messageSetCard.createdAt()) { isNew ->
            val newBackgroundColor = if (isNew) {
                newItemColor.compositeOver(defaultBackgroundColor)
            } else {
                defaultBackgroundColor
            }

            if (backgroundColor.value != newBackgroundColor) {
                backgroundColor.value = newBackgroundColor
            }
        }
    }

    val columnModifier = remember(backgroundColor.value) {
        Modifier
            .background(backgroundColor.value)
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp
            )
            .combinedClickable(
                onClick = {
                    scope.launch {
                        routeFor(
                            baseNote,
                            accountViewModel.userProfile()
                        )?.let { nav(it) }
                    }
                },
                onLongClick = enablePopup
            )
            .fillMaxWidth()
    }

    Column(columnModifier) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                modifier = remember {
                    Modifier
                        .width(55.dp)
                        .padding(top = 5.dp, end = 5.dp)
                }
            ) {
                MessageIcon(
                    remember {
                        Modifier
                            .size(16.dp)
                            .align(Alignment.TopEnd)
                    }
                )
            }

            Column(modifier = remember { Modifier.padding(start = 10.dp) }) {
                NoteCompose(
                    baseNote = baseNote,
                    routeForLastRead = null,
                    isBoostedNote = true,
                    addMarginTop = false,
                    showHidden = showHidden,
                    parentBackgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav
                )

                NoteDropDownMenu(baseNote, popupExpanded, accountViewModel)
            }
        }

        Divider(
            thickness = DividerThickness
        )
    }
}
