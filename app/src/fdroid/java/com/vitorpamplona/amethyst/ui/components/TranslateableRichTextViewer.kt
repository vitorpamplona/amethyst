package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun TranslateableRichTextViewer(
    content: String,
    canPreview: Boolean,
    modifier: Modifier = Modifier,
    tags: List<List<String>>?,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) = ExpandableRichTextViewer(
    content,
    canPreview,
    modifier,
    tags,
    backgroundColor,
    accountViewModel,
    navController
)
