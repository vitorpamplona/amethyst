package com.vitorpamplona.amethyst.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

@Composable
fun CashuPreview(cashutoken: String, accountViewModel: AccountViewModel) {
    val useWebService = false
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lud16 = accountViewModel.account.userProfile().info?.lud16
    val base64token = cashutoken.replace("cashuA", "")
    val cashu = JsonParser.parseString(String(Base64.getDecoder().decode(base64token)))
    val token = cashu.asJsonObject.get("token").asJsonArray[0].asJsonObject
    val proofs = token["proofs"].asJsonArray
    val mint = token["mint"]

    var totalamount = 0
    for (proof in proofs) {
        totalamount += proof.asJsonObject["amount"].asInt
    }
    var fees = Math.max(((totalamount * 0.02).toInt()), 2)
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
                    // Just in case we want to use a webservice instead of directly contacting the mint
                    if (useWebService) {
                        val url = "https://redeem.cashu.me?token=$cashutoken&lightning=$lud16&autopay=true"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(context, intent, null)
                    } else {
                        if (lud16 != null) {
                            runCatching {
                                LightningAddressResolver().lnAddressInvoice(
                                    lud16,
                                    ((totalamount - fees) * 1000).toLong(), // Make invoice and leave room for fees
                                    "Reedem Cashu",
                                    onSuccess = {
                                        val invoice = it
                                        val client = OkHttpClient()
                                        var url = mint.asString + "/melt" // Melt cashu tokens at Mint

                                        val jsonObject = JsonObject()
                                        jsonObject.add("proofs", proofs)
                                        jsonObject.addProperty("pr", invoice)

                                        val mediaType = "application/json; charset=utf-8".toMediaType()
                                        val requestBody = jsonObject.toString().toRequestBody(mediaType)
                                        val request = Request.Builder()
                                            .url(url)
                                            .post(requestBody)
                                            .build()

                                        val response = client.newCall(request).execute()
                                        val body = response.body?.string()
                                        val tree = jacksonObjectMapper().readTree(body)
                                        if (tree?.get("paid")?.asText() == "true") {
                                            scope.launch {
                                                Toast.makeText(context, "Redeemed " + (totalamount - fees) + " Sats" + " (Fees: " + fees + " Sats)", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            scope.launch {
                                                Toast.makeText(context, tree?.get("detail")?.asText()?.split('.')?.get(0) + "." ?: "Error", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onProgress = {
                                    },
                                    onError = {
                                        scope.launch {
                                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        } else {
                            scope.launch {
                                Toast.makeText(context, "No Lightning Address set", Toast.LENGTH_SHORT).show()
                            }
                        }
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
