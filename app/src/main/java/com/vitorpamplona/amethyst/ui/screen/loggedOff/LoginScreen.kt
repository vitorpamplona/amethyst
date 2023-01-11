package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.theme.Purple700

@Composable
fun LoginPage(accountViewModel: AccountStateViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        ClickableText(
            text = AnnotatedString("Generate a new key"),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp),
            onClick = { accountViewModel.newKey() },
            style = TextStyle(
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                color = Purple700
            )
        )
    }
    Column(
        modifier = Modifier.padding(20.dp).fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        val key = remember { mutableStateOf(TextFieldValue("")) }

        Image(
            painterResource(id = R.drawable.amethyst_logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(300.dp),
            contentScale = ContentScale.Inside
        )

        Spacer(modifier = Modifier.height(20.dp))

        //Text(text = "Insert your private or public key (view-only)")

        OutlinedTextField(
            value = key.value,
            onValueChange = { key.value = it },
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next
            ),
            placeholder = {
                Text(
                    text = "nsec / npub / hex private key",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(modifier = Modifier.padding(40.dp, 0.dp, 40.dp, 0.dp)) {
            Button(
                onClick = { accountViewModel.login(key.value.text) },
                shape = RoundedCornerShape(35.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(text = "Login")
            }
        }
    }
}