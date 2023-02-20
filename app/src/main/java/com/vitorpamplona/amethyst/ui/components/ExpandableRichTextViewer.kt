package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ExpandableRichTextViewer(
  content: String,
  canPreview: Boolean,
  modifier: Modifier = Modifier,
  tags: List<List<String>>?,
  accountViewModel: AccountViewModel,
  navController: NavController
) {
  var showFullText by remember { mutableStateOf(false) }

  val text = if (showFullText) content else content.take(350)

  Box(contentAlignment = Alignment.BottomCenter) {
    RichTextViewer(text, canPreview, modifier, tags, accountViewModel, navController)

    if (content.length > 350 && !showFullText) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
          .fillMaxWidth()
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(
                MaterialTheme.colors.background.copy(alpha = 0f),
                MaterialTheme.colors.background
              )
            )
          )
      ) {
        Button(
          modifier = Modifier.padding(top = 10.dp),
          onClick = { showFullText = !showFullText },
          shape = RoundedCornerShape(20.dp),
          colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.primary
          ),
          contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
        ) {
          Text(text = "Show More", color = Color.White)
        }
      }
    }
  }
}