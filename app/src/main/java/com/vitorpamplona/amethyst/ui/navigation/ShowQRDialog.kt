package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import nostr.postr.toNpub

@Composable
fun ShowQRDialog(user: User, onScan: (String) -> Unit, onClose: () -> Unit) {
  var presenting by remember { mutableStateOf(true) }

  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
          .background(MaterialTheme.colors.background)
          .fillMaxSize(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          CloseButton(onCancel = onClose)
        }

        Column(
          modifier = Modifier.fillMaxSize().padding(10.dp),
          verticalArrangement = Arrangement.Center
        ) {
          if (presenting) {

            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
              AsyncImage(
                model = user.profilePicture() ?: "https://robohash.org/ohno.png",
                contentDescription = "Profile Image",
                placeholder = rememberAsyncImagePainter("https://robohash.org/${user.pubkeyHex}.png"),
                modifier = Modifier
                  .width(120.dp)
                  .height(120.dp)
                  .clip(shape = CircleShape)
                  .border(3.dp, MaterialTheme.colors.background, CircleShape)
                  .background(MaterialTheme.colors.background)
              )
            }
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
              Text(
                user.bestDisplayName() ?: "",
                modifier = Modifier.padding(top = 7.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
              )
            }
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
              Text(" @${user.bestUsername()}", color = Color.LightGray)
            }

            Row(
              horizontalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth().padding(30.dp)
            ) {
              QrCodeDrawer("nostr:${user.pubkey.toNpub()}")
            }

            Row(
              horizontalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth().padding(30.dp)
            ) {

              Button(
                onClick = { presenting = false },
                shape = RoundedCornerShape(35.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp),
                colors = ButtonDefaults
                  .buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                  )
              ) {
                Text(text = "Scan QR")
              }
            }

          } else {

            Row(
              horizontalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth().padding(30.dp)
            ) {
              QrCodeScanner(onScan)
            }

            Button(
              onClick = { presenting = true },
              shape = RoundedCornerShape(35.dp),
              modifier = Modifier
                .fillMaxWidth().padding(30.dp)
                .height(50.dp),
              colors = ButtonDefaults
                .buttonColors(
                  backgroundColor = MaterialTheme.colors.primary
                )
            ) {
              Text(text = "Show QR")
            }

          }


        }
      }
    }
  }
}