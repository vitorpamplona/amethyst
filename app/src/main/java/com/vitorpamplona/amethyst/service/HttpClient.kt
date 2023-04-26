package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import okhttp3.OkHttpClient
import java.net.Proxy

object HttpClient {
    private var proxy: Proxy? = null

    fun start(account: Account?) {
        this.proxy = account?.proxy
    }

    fun getHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().proxy(proxy).build()
    }
}
