/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv

import net.montoyo.wd.utilities.Log
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.ArrayDeque

abstract class AbstractClient {
    private val sendBuffer: ByteBuffer = ByteBuffer.allocateDirect(8192)
    private val sendQueue = ArrayDeque<OutgoingPacket>()
    private val packetReader = PacketReader()
    private val packetWriter = PacketWriter()
    private val packetHandlers = arrayOfNulls<Method>(PacketID.entries.size)

    protected lateinit var socket: SocketChannel
    protected lateinit var selector: Selector
    protected lateinit var selKey: SelectionKey

    init {
        sendBuffer.limit(0)

        for (m in javaClass.methods) {
            val ph = m.getAnnotation(PacketHandler::class.java)
            if (ph != null) {
                when {
                    packetHandlers[ph.value.ordinal] != null ->
                        Log.warning("AbstractClient: several packet handlers for %s, ignoring %s", ph.value.toString(), m.name)
                    m.parameterCount != 1 || m.parameterTypes[0] != DataInputStream::class.java ->
                        Log.warning("AbstractClient: found invalid packet handler %s", m.name)
                    else ->
                        packetHandlers[ph.value.ordinal] = m
                }
            }
        }
    }

    protected abstract fun onWriteError()

    fun readyRead(bb: ByteBuffer) {
        while (bb.remaining() > 0) {
            if (packetReader.readFrom(bb)) { // End of packet
                val pkt = packetReader.getPacketData()

                if (pkt != null) {
                    try {
                        val dis = DataInputStream(ByteArrayInputStream(pkt))
                        val pid = dis.readByte().toInt() and 0xFF

                        if (pid >= packetHandlers.size) {
                            Log.error("Caught invalid packet ID %d", pid)
                        } else {
                            val handler = packetHandlers[pid]
                            if (handler != null) {
                                try {
                                    handler.invoke(this, dis)
                                } catch (ex: IllegalAccessException) {
                                    Log.errorEx("This shouldn't have happened", ex)
                                } catch (ex: InvocationTargetException) {
                                    Log.warningEx("Caught exception while handling packet %d", ex.targetException, pid)
                                }
                            }
                        }
                    } catch (ex: IOException) {
                        Log.warningEx("IOException while trying to handle packet", ex)
                    }
                }

                packetReader.reset()
            }
        }

        onDataReceived()
    }

    @Throws(Throwable::class)
    fun readyWrite() {
        if (sendBuffer.remaining() > 0 || fillSendBuffer()) {
            val sent = socket.write(sendBuffer)
            if (sent < 0) onWriteError()
            else if (sent > 0) onDataSent()
        }
    }

    private fun fillSendBuffer(): Boolean {
        sendBuffer.clear()

        do {
            if (packetWriter.writeTo(sendBuffer)) {
                var pkt: OutgoingPacket?
                synchronized(sendQueue) {
                    pkt = sendQueue.poll()
                    if (pkt == null) {
                        selKey.interestOps(SelectionKey.OP_READ) // Remove write op
                        break
                    }
                }

                packetWriter.reset(pkt!!.finish())
            }
        } while (sendBuffer.remaining() > 0)

        val pos = sendBuffer.position()
        sendBuffer.position(0)
        sendBuffer.limit(pos)
        return pos > 0
    }

    fun sendPacket(pkt: OutgoingPacket) {
        synchronized(sendQueue) {
            sendQueue.offer(pkt)
            if (selKey.isValid && (selKey.interestOps() and SelectionKey.OP_WRITE) == 0) {
                selKey.interestOps(SelectionKey.OP_READ or SelectionKey.OP_WRITE)
                selector.wakeup()
            }
        }
    }

    protected fun getCurrentPacketRawData(): ByteArray? = packetReader.getPacketData()

    protected fun clearSendQueue() {
        synchronized(sendQueue) {
            packetWriter.clear()
            sendQueue.clear()
        }
    }

    protected open fun onDataReceived() {
    }

    protected open fun onDataSent() {
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        protected fun readString(dis: DataInputStream): String {
            val len = dis.readShort().toInt() and 0xFFFF
            val str = ByteArray(len)
            dis.readFully(str)
            return String(str, StandardCharsets.UTF_8)
        }
    }
}
