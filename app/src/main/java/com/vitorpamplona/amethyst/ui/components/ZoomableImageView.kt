package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.ui.actions.CloseButton

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun ExtendedImageView(word: String) {
  // store the dialog open or close state
  var dialogOpen by remember {
    mutableStateOf(false)
  }

  AsyncImage(
    model = word,
    contentDescription = word,
    contentScale = ContentScale.FillWidth,
    modifier = Modifier
      .padding(top = 4.dp)
      .fillMaxWidth()
      .clip(shape = RoundedCornerShape(15.dp))
      .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
      .clickable(
        onClick = { dialogOpen = true }
      )
  )

  if (dialogOpen) {
    Dialog(
      onDismissRequest = { dialogOpen = false },
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
        Column(
          modifier = Modifier.padding(10.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            CloseButton(onCancel = {
              dialogOpen = false
            })
          }

          ZoomableAsyncImage(word)
        }
      }
    }
  }
}