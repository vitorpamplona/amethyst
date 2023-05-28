package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.toShortenHex

@Composable
fun ClickableNoteTag(
    baseNote: Note,
    nav: (String) -> Unit
) {
    ClickableText(
        text = AnnotatedString("@${baseNote.idNote().toShortenHex()}"),
        onClick = { nav("Note/${baseNote.idHex}") },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
    )
}
