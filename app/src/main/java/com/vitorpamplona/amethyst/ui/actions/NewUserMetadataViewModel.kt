package com.vitorpamplona.amethyst.ui.actions

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.model.GitHubIdentity
import com.vitorpamplona.amethyst.service.model.MastodonIdentity
import com.vitorpamplona.amethyst.service.model.TwitterIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.StringWriter

class NewUserMetadataViewModel : ViewModel() {
    private lateinit var account: Account

    val userName = mutableStateOf("")
    val displayName = mutableStateOf("")
    val about = mutableStateOf("")

    val picture = mutableStateOf("")
    val banner = mutableStateOf("")

    val website = mutableStateOf("")
    val nip05 = mutableStateOf("")
    val lnAddress = mutableStateOf("")
    val lnURL = mutableStateOf("")

    val twitter = mutableStateOf("")
    val github = mutableStateOf("")
    val mastodon = mutableStateOf("")

    var isUploadingImageForPicture by mutableStateOf(false)
    var isUploadingImageForBanner by mutableStateOf(false)
    val imageUploadingError = MutableSharedFlow<String?>()

    fun load(account: Account) {
        this.account = account

        account.userProfile().let {
            userName.value = it.bestUsername() ?: ""
            displayName.value = it.bestDisplayName() ?: ""
            about.value = it.info?.about ?: ""
            picture.value = it.info?.picture ?: ""
            banner.value = it.info?.banner ?: ""
            website.value = it.info?.website ?: ""
            nip05.value = it.info?.nip05 ?: ""
            lnAddress.value = it.info?.lud16 ?: ""
            lnURL.value = it.info?.lud06 ?: ""

            twitter.value = ""
            github.value = ""
            mastodon.value = ""

            // TODO: Validate Telegram input, somehow.
            it.info?.latestMetadata?.identityClaims()?.forEach {
                when (it) {
                    is TwitterIdentity -> twitter.value = it.toProofUrl()
                    is GitHubIdentity -> github.value = it.toProofUrl()
                    is MastodonIdentity -> mastodon.value = it.toProofUrl()
                }
            }
        }
    }

    fun create() {
        // Tries to not delete any existing attribute that we do not work with.
        val latest = account.userProfile().info?.latestMetadata
        val currentJson = if (latest != null) {
            ObjectMapper().readTree(
                ByteArrayInputStream(latest.content.toByteArray(Charsets.UTF_8))
            ) as ObjectNode
        } else {
            ObjectMapper().createObjectNode()
        }
        currentJson.put("name", userName.value.trim())
        currentJson.put("username", userName.value.trim())
        currentJson.put("display_name", displayName.value.trim())
        currentJson.put("displayName", displayName.value.trim())
        currentJson.put("picture", picture.value.trim())
        currentJson.put("banner", banner.value.trim())
        currentJson.put("website", website.value.trim())
        currentJson.put("about", about.value.trim())
        currentJson.put("nip05", nip05.value.trim())
        currentJson.put("lud16", lnAddress.value.trim())
        currentJson.put("lud06", lnURL.value.trim())

        var claims = latest?.identityClaims() ?: emptyList()

        if (twitter.value.isBlank()) {
            // delete twitter
            claims = claims.filter { it !is TwitterIdentity }
        }

        if (github.value.isBlank()) {
            // delete github
            claims = claims.filter { it !is GitHubIdentity }
        }

        if (mastodon.value.isBlank()) {
            // delete mastodon
            claims = claims.filter { it !is MastodonIdentity }
        }

        // Updates while keeping other identities intact
        val newClaims = listOfNotNull(
            TwitterIdentity.parseProofUrl(twitter.value),
            GitHubIdentity.parseProofUrl(github.value),
            MastodonIdentity.parseProofUrl(mastodon.value)
        ) + claims.filter { it !is TwitterIdentity && it !is GitHubIdentity && it !is MastodonIdentity }

        val writer = StringWriter()
        ObjectMapper().writeValue(writer, currentJson)

        viewModelScope.launch(Dispatchers.IO) {
            account.sendNewUserMetadata(writer.buffer.toString(), newClaims)
        }

        clear()
    }

    fun clear() {
        userName.value = ""
        displayName.value = ""
        about.value = ""
        picture.value = ""
        banner.value = ""
        website.value = ""
        nip05.value = ""
        lnAddress.value = ""
        lnURL.value = ""
        twitter.value = ""
        github.value = ""
        mastodon.value = ""
    }

    fun uploadForPicture(uri: Uri, context: Context) {
        upload(
            uri,
            context,
            onUploading = {
                isUploadingImageForPicture = it
            },
            onUploaded = {
                picture.value = it
            }
        )
    }

    fun uploadForBanner(uri: Uri, context: Context) {
        upload(
            uri,
            context,
            onUploading = {
                isUploadingImageForBanner = it
            },
            onUploaded = {
                banner.value = it
            }
        )
    }

    fun upload(it: Uri, context: Context, onUploading: (Boolean) -> Unit, onUploaded: (String) -> Unit) {
        onUploading(true)

        ImageUploader.uploadImage(
            uri = it,
            server = account.defaultFileServer,
            contentResolver = context.contentResolver,
            onSuccess = { imageUrl, mimeType ->
                onUploading(false)
                onUploaded(imageUrl)
            },
            onError = {
                onUploading(false)
                viewModelScope.launch {
                    imageUploadingError.emit("Failed to upload the image / video")
                }
            }
        )
    }
}
