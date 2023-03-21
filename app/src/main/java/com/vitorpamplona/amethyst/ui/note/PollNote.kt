package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarDefaults.backgroundColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.ui.components.TranslateableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun PollNote(
    pollEvent: PollNoteEvent,
    canPreview: Boolean,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    pollEvent.pollOptions().values.forEachIndexed { index, string ->
        TranslateableRichTextViewer(
            string,
            canPreview = canPreview && !makeItShort,
            Modifier.fillMaxWidth().border(BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.32f))),
            pollEvent.tags(),
            backgroundColor,
            accountViewModel,
            navController
        )
    }
}
