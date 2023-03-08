package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.toShortenHex

@Composable
fun ClickableNoteTag(
    baesNote: Note,
    navController: NavController
) {
    ClickableText(
        text = AnnotatedString("@${baesNote.idNote().toShortenHex()} "),
        onClick = { navController.navigate("Note/${baesNote.idHex}") },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
    )
}
