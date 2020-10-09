package com.absinthe.kage.connect.tcp

import com.absinthe.kage.connect.tcp.KageSocket.ISocketCallback
import com.absinthe.kage.device.cmd.HeartbeatCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.DataInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class PacketReader(private val mIn: DataInputStream?, private val mSocketCallback: ISocketCallback?) : IPacketReader {

    private val mRequests: MutableMap<String?, Request?> = TreeMap()
    private val mPackets = LinkedBlockingQueue<Packet>()
    private val mExecutorService = Executors.newSingleThreadExecutor()
    private val timeout = DEFAULT_TIMEOUT
    private var shutdown = false

    init {
        ReceiveDataThread().start()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                while (!shutdown) {
                    val packet = mPackets.poll(timeout, TimeUnit.MILLISECONDS)

                    if (shutdown) {
                        return@launch
                    }
                    if (packet == null) {
                        withContext(Dispatchers.Main) {
                            mSocketCallback?.onReaderIdle()
                        }
                    }
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    override fun addRequest(request: Request) {
        synchronized(this) { mRequests.put(request.id, request) }
    }

    override fun shutdown() {
        synchronized(this) {
            shutdown = true
            mExecutorService.shutdownNow()
            mRequests.clear()
        }
        mPackets.clear()
    }

    private inner class ReceiveDataThread : Thread() {

        override fun run() {
            while (!shutdown) {
                if (mIn != null) {
                    val data = try {
                        readFromStream(mIn)
                    } catch (e: IllegalArgumentException) {
                        Timber.e("PacketReader error")
                        mSocketCallback?.onReadAndWriteError(ISocketCallback.READ_ERROR_CODE_RECEIVE_LENGTH_TOO_BIG)
                        return
                    }

                    synchronized(this@PacketReader) {
                        if (shutdown) {
                            return
                        }
                        if (data == null) {
                            Timber.e("ReceiveDataThread receive data == null")
                            GlobalScope.launch(Dispatchers.Main) {
                                mSocketCallback?.onReadAndWriteError(ISocketCallback.READ_ERROR_CODE_CONNECT_UNKNOWN)
                            }
                            return
                        }
                        if (data.isEmpty()) {
                            return@synchronized
                        }
                        Timber.d("Receive Data: $data")

                        mExecutorService.submit {
                            try {
                                mPackets.put(Packet().apply {
                                    this.data = data
                                })
                            } catch (e: InterruptedException) {
                                e.printStackTrace()
                            }
                        }

                        responseAllHeartBeat() //收到任何数据都消费掉所有的心跳超时

                        if (!isHeartBeat(data)) {
                            GlobalScope.launch(Dispatchers.Main) {
                                mSocketCallback?.onReceiveMsg(data)
                            }
                        }
                    }
                }
            }
        }

        @Throws(IllegalArgumentException::class)
        private fun readFromStream(dis: DataInputStream): String? {
            val bArray = readNextPacket(dis) ?: return null
            return String(bArray, StandardCharsets.UTF_8)
        }
    }

    private fun responseAllHeartBeat() {
        val tempMap: MutableMap<String?, Request?> = TreeMap()
        synchronized(this) {
            if (mRequests.isNotEmpty()) {
                tempMap.putAll(mRequests)
                mRequests.clear()
            }
        }
        if (tempMap.isNotEmpty()) {
            val entries: MutableSet<MutableMap.MutableEntry<String?, Request?>> = tempMap.entries
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                iterator.remove()
                mExecutorService.submit {
                    val response = Response()
                    next.value?.setResponse(response)
                }
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun readNextPacket(dis: DataInputStream): ByteArray? {
        val receiveLength = try {
            dis.readInt()
        } catch (e: IOException) {
            e.printStackTrace()
            Timber.d("readNextPacket IO: ${e.message}")
            return null
        }

        require(receiveLength < MAX_READ_LENGTH) { "receiveLength is too large, receiveLength = $receiveLength" }
        val bArray = ByteArray(receiveLength)
        var bytesRead = 0

        while (bytesRead < receiveLength) {
            bytesRead += try {
                val result = dis.read(bArray, bytesRead, receiveLength - bytesRead)
                if (result == -1) break
                result
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }
        return bArray
    }

    private fun isHeartBeat(data: String): Boolean {
        return HeartbeatCommand().parseReceived(data)
    }

    companion object {
        private const val DEFAULT_TIMEOUT = 5 * 1000.toLong()
        private const val MAX_READ_LENGTH = 223 * 10000.toLong() //一次性最大读取指令的长度，超出将可能OOM
    }
}