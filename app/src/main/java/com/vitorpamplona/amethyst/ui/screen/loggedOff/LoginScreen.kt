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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R

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
                color = MaterialTheme.colors.primary
            )
        )
    }
    Column(
        modifier = Modifier.padding(20.dp).fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        val key = remember { mutableStateOf(TextFieldValue("")) }
        var errorMessage by remember { mutableStateOf("") }

        Image(
            painterResource(id = R.drawable.amethyst_logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(300.dp),
            contentScale = ContentScale.Inside
        )

        Spacer(modifier = Modifier.height(20.dp))

        //Text(text = "Insert your private or public key (view-only)")

        var showPassword by remember {
            mutableStateOf(false)
        }

        OutlinedTextField(
            value = key.value,
            onValueChange = { key.value = it },
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Go
            ),
            placeholder = {
                Text(
                    text = "nsec / npub / hex private key",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "Show Password" else "Hide Password"
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardActions = KeyboardActions(
                onGo = {
                    try {
                        accountViewModel.login(key.value.text)
                    } catch (e: Exception) {
                        errorMessage = "Invalid key"
                    }
                }
            )
        )
        if (errorMessage.isNotBlank()) {
            Text(
                text = "${errorMessage}",
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.caption
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(modifier = Modifier.padding(40.dp, 0.dp, 40.dp, 0.dp)) {
            Button(
                onClick = {
                    try {
                        accountViewModel.login(key.value.text)
                    } catch (e: Exception) {
                        errorMessage = "Invalid key"
                    }
                },
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