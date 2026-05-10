/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.cast.dlna

import android.content.Context
import android.net.wifi.WifiManager
import com.vitorpamplona.amethyst.service.cast.CastDevice
import com.vitorpamplona.amethyst.service.cast.CastDeviceKind
import com.vitorpamplona.amethyst.service.cast.CastRequest
import com.vitorpamplona.amethyst.service.cast.CastSessionState
import com.vitorpamplona.amethyst.service.cast.VideoCaster
import com.vitorpamplona.amethyst.service.cast.effectiveMimeType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jupnp.UpnpService
import org.jupnp.UpnpServiceImpl
import org.jupnp.android.AndroidUpnpServiceConfiguration
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.message.header.STAllHeader
import org.jupnp.model.meta.LocalDevice
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.types.UDADeviceType
import org.jupnp.model.types.UDAServiceType
import org.jupnp.model.types.UDN
import org.jupnp.registry.Registry
import org.jupnp.registry.RegistryListener
import org.jupnp.support.avtransport.callback.Play
import org.jupnp.support.avtransport.callback.SetAVTransportURI
import org.jupnp.support.avtransport.callback.Stop
import org.jupnp.support.contentdirectory.DIDLParser
import org.jupnp.support.model.DIDLContent
import org.jupnp.support.model.ProtocolInfo
import org.jupnp.support.model.Res
import org.jupnp.support.model.item.VideoItem
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "DlnaCaster"

private val MEDIA_RENDERER = UDADeviceType("MediaRenderer")
private val AV_TRANSPORT = UDAServiceType("AVTransport")

class DlnaCaster(
    private val appContext: Context,
) : VideoCaster {
    override val id: String = "dlna"

    private val devicesFlow = MutableStateFlow<List<CastDevice>>(emptyList())
    override val devices: StateFlow<List<CastDevice>> = devicesFlow.asStateFlow()

    private val sessionFlow = MutableStateFlow<CastSessionState>(CastSessionState.Idle)
    override val sessionState: StateFlow<CastSessionState> = sessionFlow.asStateFlow()

    private val knownDevices = ConcurrentHashMap<String, RemoteDevice>()
    private var multicastLock: WifiManager.MulticastLock? = null

    @Volatile
    private var upnpService: UpnpService? = null

    private val listener =
        object : RegistryListener {
            override fun remoteDeviceDiscoveryStarted(
                registry: Registry,
                device: RemoteDevice,
            ) {
                Log.d(TAG) { "discoveryStarted udn=${device.identity.udn} type=${device.type}" }
            }

            override fun remoteDeviceDiscoveryFailed(
                registry: Registry,
                device: RemoteDevice,
                ex: Exception?,
            ) {
                Log.w(TAG, "discoveryFailed udn=${device.identity.udn} type=${device.type} ex=${ex?.message}", ex)
            }

            override fun remoteDeviceAdded(
                registry: Registry,
                device: RemoteDevice,
            ) {
                val isRenderer = device.type.implementsVersion(MEDIA_RENDERER)
                Log.d(TAG) {
                    "remoteDeviceAdded udn=${device.identity.udn} type=${device.type} renderer=$isRenderer name=${device.details?.friendlyName}"
                }
                if (isRenderer) addDevice(device)
            }

            override fun remoteDeviceUpdated(
                registry: Registry,
                device: RemoteDevice,
            ) {
                if (device.type.implementsVersion(MEDIA_RENDERER)) addDevice(device)
            }

            override fun remoteDeviceRemoved(
                registry: Registry,
                device: RemoteDevice,
            ) {
                Log.d(TAG) { "remoteDeviceRemoved udn=${device.identity.udn}" }
                removeDevice(device.identity.udn.identifierString)
            }

            override fun localDeviceAdded(
                registry: Registry,
                device: LocalDevice,
            ) = Unit

            override fun localDeviceRemoved(
                registry: Registry,
                device: LocalDevice,
            ) = Unit

            override fun beforeShutdown(registry: Registry) = Unit

            override fun afterShutdown() = Unit
        }

    private fun addDevice(device: RemoteDevice) {
        val udn = device.identity.udn.identifierString
        knownDevices[udn] = device
        publish()
    }

    private fun removeDevice(udn: String) {
        knownDevices.remove(udn)
        publish()
    }

    private fun publish() {
        devicesFlow.value =
            knownDevices.values.map { device ->
                CastDevice(
                    id = device.identity.udn.identifierString,
                    name = device.details?.friendlyName ?: device.displayString,
                    kind = CastDeviceKind.Dlna,
                    casterId = id,
                )
            }
    }

    @Synchronized
    override fun startDiscovery() {
        Log.d(TAG) { "startDiscovery (already running? ${upnpService != null})" }
        if (upnpService != null) return
        try {
            acquireMulticastLock()
            val service = UpnpServiceImpl(AndroidUpnpServiceConfiguration())
            service.startup()
            service.registry.addListener(listener)
            // Republish anything already in the registry (covers reuse).
            val preexisting =
                service.registry.remoteDevices
                    .filter { it.type.implementsVersion(MEDIA_RENDERER) }
            Log.d(TAG) { "startDiscovery: preexisting renderers=${preexisting.size}" }
            preexisting.forEach { addDevice(it) }
            service.controlPoint.search(STAllHeader())
            upnpService = service
            Log.d(TAG) { "startDiscovery: jUPnP started, SSDP M-SEARCH sent (multicastLock held=${multicastLock?.isHeld})" }
        } catch (t: Throwable) {
            Log.w(TAG, "startDiscovery failed: ${t.message}", t)
            releaseMulticastLock()
        }
    }

    @Synchronized
    override fun stopDiscovery() {
        Log.d(TAG) { "stopDiscovery (running? ${upnpService != null})" }
        val service = upnpService ?: return
        try {
            service.registry.removeListener(listener)
            service.shutdown()
        } catch (t: Throwable) {
            Log.w(TAG, "stopDiscovery failed: ${t.message}", t)
        }
        upnpService = null
        knownDevices.clear()
        publish()
        releaseMulticastLock()
        Log.d(TAG) { "stopDiscovery: jUPnP stopped, multicast lock released" }
    }

    private fun acquireMulticastLock() {
        if (multicastLock != null) return
        try {
            val wifi = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifi == null) {
                Log.w(TAG, "acquireMulticastLock: WifiManager unavailable")
                return
            }
            val lock = wifi.createMulticastLock("amethyst-dlna-cast")
            lock.setReferenceCounted(false)
            lock.acquire()
            multicastLock = lock
            Log.d(TAG) { "acquireMulticastLock: held=${lock.isHeld}" }
        } catch (t: Throwable) {
            Log.w(TAG, "acquireMulticastLock failed: ${t.message}", t)
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.takeIf { it.isHeld }?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "releaseMulticastLock failed: ${t.message}", t)
        }
        multicastLock = null
    }

    override suspend fun cast(
        device: CastDevice,
        request: CastRequest,
    ) {
        Log.d(TAG) { "cast device=${device.name} udn=${device.id} url=${request.url}" }
        val service = upnpService
        if (service == null) {
            Log.w(TAG, "cast: jUPnP service not running")
            sessionFlow.value = CastSessionState.Error(device, "DLNA service not running")
            return
        }
        val remoteDevice = knownDevices[device.id] ?: service.registry.getRemoteDevice(UDN.valueOf(device.id), true)
        if (remoteDevice == null) {
            Log.w(TAG, "cast: device ${device.id} not in registry")
            sessionFlow.value = CastSessionState.Error(device, "Device went offline")
            return
        }
        val avTransport = remoteDevice.findService(AV_TRANSPORT)
        if (avTransport == null) {
            Log.w(TAG, "cast: device ${device.id} has no AVTransport service")
            sessionFlow.value = CastSessionState.Error(device, "Device does not advertise AVTransport")
            return
        }

        sessionFlow.value = CastSessionState.Connecting(device)

        val metadata = buildDidlMetadata(request)
        Log.d(TAG) { "cast: SetAVTransportURI -> ${request.url} (didl=${metadata.length}b)" }

        val setUriDone = CompletableDeferred<Boolean>()
        service.controlPoint.execute(
            object : SetAVTransportURI(avTransport, request.url, metadata) {
                override fun success(invocation: ActionInvocation<*>?) {
                    setUriDone.complete(true)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?,
                ) {
                    Log.w(TAG, "SetAVTransportURI failed: $defaultMsg")
                    setUriDone.complete(false)
                }
            },
        )

        val uriOk = setUriDone.await()
        if (!uriOk) {
            sessionFlow.value = CastSessionState.Error(device, "Receiver rejected the media URL")
            return
        }

        val playDone = CompletableDeferred<Boolean>()
        service.controlPoint.execute(
            object : Play(avTransport) {
                override fun success(invocation: ActionInvocation<*>?) {
                    playDone.complete(true)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?,
                ) {
                    Log.w(TAG, "Play failed: $defaultMsg")
                    playDone.complete(false)
                }
            },
        )

        if (playDone.await()) {
            Log.d(TAG) { "cast: Play succeeded" }
            sessionFlow.value = CastSessionState.Casting(device, request)
        } else {
            Log.w(TAG, "cast: Play action rejected by receiver")
            sessionFlow.value = CastSessionState.Error(device, "Receiver rejected Play action")
        }
    }

    override suspend fun stopCasting() {
        Log.d(TAG) { "stopCasting (running? ${upnpService != null}, state=${sessionFlow.value::class.simpleName})" }
        val service =
            upnpService ?: run {
                sessionFlow.value = CastSessionState.Idle
                return
            }
        val current = sessionFlow.value
        val device =
            when (current) {
                is CastSessionState.Casting -> {
                    current.device
                }

                is CastSessionState.Connecting -> {
                    current.device
                }

                else -> {
                    sessionFlow.value = CastSessionState.Idle
                    return
                }
            }
        val remoteDevice = knownDevices[device.id]
        val avTransport = remoteDevice?.findService(AV_TRANSPORT)
        if (avTransport != null) {
            val done = CompletableDeferred<Unit>()
            service.controlPoint.execute(
                object : Stop(avTransport) {
                    override fun success(invocation: ActionInvocation<*>?) {
                        done.complete(Unit)
                    }

                    override fun failure(
                        invocation: ActionInvocation<*>?,
                        operation: UpnpResponse?,
                        defaultMsg: String?,
                    ) {
                        done.complete(Unit)
                    }
                },
            )
            done.await()
        }
        sessionFlow.value = CastSessionState.Idle
    }

    private fun buildDidlMetadata(request: CastRequest): String =
        try {
            val mime = request.effectiveMimeType()
            val res = Res(ProtocolInfo("http-get:*:$mime:*"), null as Long?, request.url)
            val item =
                VideoItem(
                    // id =
                    "amethyst-cast-1",
                    // parentID =
                    "0",
                    // title =
                    request.title ?: "Amethyst",
                    // creator =
                    "Amethyst",
                    res,
                )
            val didl = DIDLContent()
            didl.addItem(item)
            DIDLParser().generate(didl)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not build DIDL metadata, sending empty: ${t.message}", t)
            ""
        }
}
