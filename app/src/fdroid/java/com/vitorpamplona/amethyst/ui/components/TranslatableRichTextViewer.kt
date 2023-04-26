package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import java.net.Proxy

@Composable
fun TranslatableRichTextViewer(
    content: String,
    canPreview: Boolean,
    modifier: Modifier = Modifier,
    tags: List<List<String>>?,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController,
    proxy: Proxy?
) = ExpandableRichTextViewer(
    content,
    canPreview,
    modifier,
    tags,
    backgroundColor,
    accountViewModel,
    navController,
    proxy
)
