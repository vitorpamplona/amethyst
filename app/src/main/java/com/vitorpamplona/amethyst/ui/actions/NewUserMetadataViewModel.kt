package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vitorpamplona.amethyst.model.Account
import java.io.ByteArrayInputStream
import java.io.StringWriter

class NewUserMetadataViewModel: ViewModel() {
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

    fun load(account: Account) {
        this.account = account

        account.userProfile().let {
            userName.value = it.bestUsername() ?: ""
            displayName.value = it.bestDisplayName() ?: ""
            about.value = it.info.about ?: ""
            picture.value = it.info.picture ?: ""
            banner.value = it.info.banner ?: ""
            website.value = it.info.website ?: ""
            nip05.value = it.info.nip05 ?: ""
            lnAddress.value = it.info.lud16 ?: ""
            lnURL.value = it.info.lud06 ?: ""
        }
    }

    fun create() {
        // Tries to not delete any existing attribute that we do not work with.
        val latest = account.userProfile().latestMetadata
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

        val writer = StringWriter()
        ObjectMapper().writeValue(writer, currentJson)

        account.sendNewUserMetadata(writer.buffer.toString())

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
    }
}