package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.getGradient
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.secondaryButtonBackground
import com.vitorpamplona.quartz.events.ImmutableListOfLists

const val SHORT_TEXT_LENGTH = 350
const val SHORTEN_AFTER_LINES = 10

@Composable
fun ExpandableRichTextViewer(
    content: String,
    canPreview: Boolean,
    modifier: Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    var showFullText by remember { mutableStateOf(false) }

    val whereToCut = remember(content) {
        // Cuts the text in the first space or new line after SHORT_TEXT_LENGTH characters
        val firstSpaceAfterCut = content.indexOf(' ', SHORT_TEXT_LENGTH).let { if (it < 0) content.length else it }
        val firstNewLineAfterCut = content.indexOf('\n', SHORT_TEXT_LENGTH).let { if (it < 0) content.length else it }

        // or after SHORTEN_AFTER_LINES lines
        val numberOfLines = content.count { it == '\n' }

        var charactersInLines = minOf(firstSpaceAfterCut, firstNewLineAfterCut)

        if (numberOfLines > SHORTEN_AFTER_LINES) {
            val shortContent = content.lines().take(SHORTEN_AFTER_LINES)
            charactersInLines = 0
            for (line in shortContent) {
                // +1 because new line character is omitted from .lines
                charactersInLines += (line.length + 1)
            }
        }

        minOf(firstSpaceAfterCut, firstNewLineAfterCut, charactersInLines)
    }

    val text by remember(content) {
        derivedStateOf {
            if (showFullText) {
                content
            } else {
                content.take(whereToCut)
            }
        }
    }

    Box {
        RichTextViewer(
            text,
            canPreview,
            modifier.align(Alignment.TopStart),
            tags,
            backgroundColor,
            accountViewModel,
            nav
        )

        if (content.length > whereToCut && !showFullText) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(getGradient(backgroundColor))
            ) {
                ShowMoreButton {
                    showFullText = !showFullText
                }
            }
        }
    }
}

@Composable
fun ShowMoreButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(top = 10.dp),
        onClick = onClick,
        shape = ButtonBorder,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryButtonBackground
        ),
        contentPadding = ButtonPadding
    ) {
        Text(text = stringResource(R.string.show_more), color = Color.White)
    }
}
