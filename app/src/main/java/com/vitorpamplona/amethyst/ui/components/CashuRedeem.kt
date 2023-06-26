package com.vitorpamplona.amethyst.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import com.google.gson.JsonParser
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import java.util.Base64

@Composable
fun CashuPreview(cashutoken: String, accountViewModel: AccountViewModel) {
    val context = LocalContext.current
    val lnaddress = accountViewModel.account.userProfile().info?.lud16
    val base64token = cashutoken.replace("cashuA", "")
    val cashu = JsonParser.parseString(String(Base64.getDecoder().decode(base64token)))

    val token = cashu.asJsonObject.get("token").asJsonArray[0].asJsonObject
    val proofs = token["proofs"].asJsonArray
    val mint = token["mint"]

    var totalamount = 0
    for (proof in proofs) {
        totalamount += proof.asJsonObject["amount"].asInt
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 30.dp)
            .clip(shape = QuoteBorder)
            .border(1.dp, MaterialTheme.colors.subtleBorder, QuoteBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(30.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.cashu),
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )

                Text(
                    text = stringResource(R.string.cashu),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            Divider()

            totalamount?.let {
                Text(
                    text = "$it ${stringResource(id = R.string.sats)}",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                onClick = {
                    runCatching {
                        // Alternative directly from client: call POST https://{mint}/melt with body
                        // {
                        //  "proofs: {proofs},
                        //  "pr": {create ln invoice with totalamount * 0.98, at least -2 sats}
                        // }
                        val url = "https://redeem.cashu.me?token=$cashutoken&lightning=$lnaddress&autopay=true"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(context, intent, null)
                    }
                },
                shape = QuoteBorder,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
            ) {
                Text(stringResource(R.string.cashu_redeem), color = Color.White, fontSize = 20.sp)
            }
        }
    }
}
