package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BlankNote(modifier: Modifier = Modifier, isQuote: Boolean = false) {
  Column(modifier = modifier) {
    Row(modifier = Modifier.padding(horizontal = if (!isQuote) 12.dp else 6.dp)) {
      Column(modifier = Modifier.padding(start = if (!isQuote) 10.dp else 5.dp)) {
        Row(
          modifier = Modifier.padding(
            start = 20.dp,
            end = 20.dp,
            bottom = 25.dp,
            top = 15.dp
          ),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Referenced post not found",
            modifier = Modifier.padding(30.dp),
            color = Color.Gray,
          )
        }

        Divider(
          modifier = Modifier.padding(vertical = 10.dp),
          thickness = 0.25.dp
        )
      }
    }
  }
}


@Composable
fun HiddenNote(modifier: Modifier = Modifier, isQuote: Boolean = false) {
  Column(modifier = modifier) {
    Row(modifier = Modifier.padding(horizontal = if (!isQuote) 12.dp else 6.dp)) {
      Column(modifier = Modifier.padding(start = if (!isQuote) 10.dp else 5.dp)) {
        Row(
          modifier = Modifier.padding(
            start = 20.dp,
            end = 20.dp,
            bottom = 25.dp,
            top = 15.dp
          ),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Post was flagged as inappropriate",
            modifier = Modifier.padding(30.dp),
            color = Color.Gray,
          )
        }

        Divider(
          modifier = Modifier.padding(vertical = 10.dp),
          thickness = 0.25.dp
        )
      }
    }
  }
}