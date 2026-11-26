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
package com.vitorpamplona.androiddevice.grpc

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Path
import javax.net.SocketFactory

/**
 * Sockets to one Unix domain socket file, for an HTTP client that only knows TCP: OkHttp connects
 * the [Socket] this makes to whatever address it resolved, and it opens [path] instead. That is
 * how a local server that listens on a socket file (idb's companion, `--grpc-domain-sock`) is
 * reached without listening on a TCP port anyone on the network could try.
 */
class UnixSocketFactory(
    val path: Path,
) : SocketFactory() {
    override fun createSocket(): Socket = UnixSocket(path)

    override fun createSocket(
        host: String?,
        port: Int,
    ): Socket = createSocket().apply { connect(null) }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int,
    ): Socket = createSocket(host, port)

    override fun createSocket(
        host: InetAddress?,
        port: Int,
    ): Socket = createSocket().apply { connect(null) }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket = createSocket(address, port)
}

/**
 * A [Socket] over a blocking Unix-domain [SocketChannel]. Its streams call the channel directly:
 * `Channels.newInputStream` would hold the channel's blocking lock through a read, and an HTTP/2
 * connection reads on one thread while it writes on another.
 */
private class UnixSocket(
    private val path: Path,
) : Socket() {
    @Volatile
    private var channel: SocketChannel? = null

    @Volatile
    private var closed = false

    @Volatile
    private var inputShut = false

    @Volatile
    private var outputShut = false

    private fun open(): SocketChannel = channel ?: throw IOException("Not connected to $path")

    override fun connect(endpoint: SocketAddress?) = connect(endpoint, 0)

    override fun connect(
        endpoint: SocketAddress?,
        timeout: Int,
    ) {
        if (closed) throw IOException("Socket closed")
        val ch = SocketChannel.open(StandardProtocolFamily.UNIX)
        try {
            ch.connect(UnixDomainSocketAddress.of(path))
        } catch (e: IOException) {
            ch.close()
            throw IOException("Can't connect to $path: ${e.message}", e)
        }
        channel = ch
    }

    override fun isConnected(): Boolean = channel?.isConnected == true

    override fun isBound(): Boolean = isConnected

    override fun isClosed(): Boolean = closed

    override fun isInputShutdown(): Boolean = inputShut

    override fun isOutputShutdown(): Boolean = outputShut

    override fun close() {
        closed = true
        channel?.close()
    }

    override fun shutdownInput() {
        inputShut = true
        open().shutdownInput()
    }

    override fun shutdownOutput() {
        outputShut = true
        open().shutdownOutput()
    }

    override fun getInetAddress(): InetAddress = InetAddress.getLoopbackAddress()

    override fun getLocalAddress(): InetAddress = InetAddress.getLoopbackAddress()

    override fun getPort(): Int = 0

    override fun getLocalPort(): Int = -1

    override fun getRemoteSocketAddress(): SocketAddress? = channel?.let { UnixDomainSocketAddress.of(path) }

    override fun getLocalSocketAddress(): SocketAddress? = null

    // TCP options mean nothing on a socket file: taken and ignored, as a local socket would.
    override fun setSoTimeout(timeout: Int) = Unit

    override fun getSoTimeout(): Int = 0

    override fun setTcpNoDelay(on: Boolean) = Unit

    override fun getTcpNoDelay(): Boolean = true

    override fun setKeepAlive(on: Boolean) = Unit

    override fun setSendBufferSize(size: Int) = Unit

    override fun setReceiveBufferSize(size: Int) = Unit

    override fun getInputStream(): InputStream =
        object : InputStream() {
            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xff
            }

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int {
                if (len == 0) return 0
                return open().read(ByteBuffer.wrap(b, off, len))
            }

            override fun close() = this@UnixSocket.close()
        }

    override fun getOutputStream(): OutputStream =
        object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(
                b: ByteArray,
                off: Int,
                len: Int,
            ) {
                val buffer = ByteBuffer.wrap(b, off, len)
                val ch = open()
                while (buffer.hasRemaining()) ch.write(buffer)
            }

            override fun close() = this@UnixSocket.close()
        }
}
