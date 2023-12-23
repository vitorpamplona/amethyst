package com.vitorpamplona.amethyst.ui.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.note.RepostedIcon
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
@Preview
private fun GenericRepostSectionPreview() {
    GenericRepostLayout(
        baseAuthorPicture = {
            Text("ab")
        },
        repostAuthorPicture = {
            Text("cd")
        }
    )
}

@Composable
fun GenericRepostLayout(
    baseAuthorPicture: @Composable () -> Unit,
    repostAuthorPicture: @Composable () -> Unit
) {
    Box(modifier = Size55Modifier) {
        Box(remember { Size35Modifier.align(Alignment.TopStart) }) {
            baseAuthorPicture()
        }

        Box(
            remember {
                Size18Modifier
                    .align(Alignment.BottomStart)
                    .padding(1.dp)
            }
        ) {
            RepostedIcon(modifier = Size18Modifier, MaterialTheme.colorScheme.placeholderText)
        }

        Box(
            remember { Size35Modifier.align(Alignment.BottomEnd) },
            contentAlignment = Alignment.BottomEnd
        ) {
            repostAuthorPicture()
        }
    }
}
