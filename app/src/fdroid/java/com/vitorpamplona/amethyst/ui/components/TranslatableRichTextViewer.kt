package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun TranslatableRichTextViewer(
    content: String,
    canPreview: Boolean,
    modifier: Modifier = Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) = ExpandableRichTextViewer(
    content,
    canPreview,
    modifier,
    tags,
    backgroundColor,
    accountViewModel,
    nav
)
