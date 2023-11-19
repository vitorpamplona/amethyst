package com.vitorpamplona.amethyst.service

import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import kotlin.properties.Delegates

object HttpClient {
    var proxyChangeListeners = ArrayList<() -> Unit>()

    // fires off every time value of the property changes
    private var internalProxy: Proxy? by Delegates.observable(null) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            proxyChangeListeners.forEach {
                it()
            }
        }
    }

    fun start(proxy: Proxy?) {
        this.internalProxy = proxy
    }

    fun getHttpClient(): OkHttpClient {
        val seconds = if (internalProxy != null) 20L else 10L
        val duration = Duration.ofSeconds(seconds)
        return OkHttpClient.Builder()
            .proxy(internalProxy)
            .readTimeout(duration)
            .connectTimeout(duration)
            .writeTimeout(duration)
            .build()
    }

    fun getProxy(): Proxy? {
        return internalProxy
    }

    fun initProxy(useProxy: Boolean, hostname: String, port: Int): Proxy? {
        return if (useProxy) Proxy(Proxy.Type.SOCKS, InetSocketAddress(hostname, port)) else null
    }
}
