package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.RoboHashCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.components.AsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.qrcode.QrCodeScanner

@Composable
fun ShowQRDialog(user: User, onScan: (String) -> Unit, onClose: () -> Unit) {
  var presenting by remember { mutableStateOf(true) }

  val ctx = LocalContext.current.applicationContext

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
          modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          if (presenting) {
            Row(
              horizontalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 10.dp)
            ) {

            }

            Column(modifier = Modifier.fillMaxWidth()) {
              Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                AsyncImageProxy(
                  model = ResizeImage(user.profilePicture(), 100.dp),
                  placeholder = BitmapPainter(RoboHashCache.get(ctx, user.pubkeyHex)),
                  fallback = BitmapPainter(RoboHashCache.get(ctx, user.pubkeyHex)),
                  error = BitmapPainter(RoboHashCache.get(ctx, user.pubkeyHex)),
                  contentDescription = stringResource(R.string.profile_image),
                  modifier = Modifier
                    .width(100.dp)
                    .height(100.dp)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 35.dp, vertical = 10.dp)
              ) {
                QrCodeDrawer("nostr:${user.pubkeyNpub()}")
              }

            }

            Row(
              horizontalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 10.dp)
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
                Text(text = stringResource(R.string.scan_qr))
              }
            }

          } else {

            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
              Text(
                stringResource(R.string.point_to_the_qr_code),
                modifier = Modifier.padding(top = 7.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp
              )
            }

            Row(
              horizontalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth().padding(30.dp)
            ) {
              QrCodeScanner(onScan)
            }

            Row(
              horizontalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 10.dp)
            ) {

              Button(
                onClick = { presenting = true },
                shape = RoundedCornerShape(35.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp),
                colors = ButtonDefaults
                  .buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                  )
              ) {
                Text(text = stringResource(R.string.show_qr))
              }
            }

          }


        }
      }
    }
  }
}