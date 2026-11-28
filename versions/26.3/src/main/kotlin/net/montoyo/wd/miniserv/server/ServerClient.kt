/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv.server

import net.montoyo.wd.miniserv.AbstractClient
import net.montoyo.wd.miniserv.Constants
import net.montoyo.wd.miniserv.OutgoingPacket
import net.montoyo.wd.miniserv.PacketHandler
import net.montoyo.wd.miniserv.PacketID
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.Util
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.util.UUID
import java.util.function.Consumer

class ServerClient internal constructor(s: SocketChannel, ss: Selector) : AbstractClient() {
    private var remove: Boolean = false
    private var isAuthenticated: Boolean = false
    private var uuid: UUID? = null
    private var challenge: ByteArray? = null
    private var userDir: File? = null
    private var quota: Long = 0
    private var currentFile: FileOutputStream? = null
    private var currentFileSize: Long = 0
    private var currentFileExpectedSize: Long = 0
    private var sendingFile: Boolean = false // != receiving, which is handled by currentFile
    private var lastDataTime: Long = System.currentTimeMillis()

    init {
        socket = s
        selector = ss

        try {
            selKey = socket.register(selector, SelectionKey.OP_READ)
        } catch (_: ClosedChannelException) {
        }
    }

    override fun onWriteError() {
        remove = true
    }

    internal fun setShouldRemove() {
        remove = true
    }

    fun shouldRemove(): Boolean = remove

    fun hasTimedOut(now: Long): Boolean = now - lastDataTime >= Constants.CLIENT_TIMEOUT

    internal fun getChannel(): SocketChannel = socket

    @PacketHandler(PacketID.INIT_CONN)
    @Throws(IOException::class)
    fun handleInitConnPacket(dis: DataInputStream) {
        if (uuid != null) {
            Log.warning("A client tried to change his UUID?")
            return
        }

        val msb = dis.readLong()
        val lsb = dis.readLong()
        val id = UUID(msb, lsb)
        val key = Server.getInstance().getClientManager().getClientKey(id)

        if (key == null) {
            Log.warning("Unkown client with UUID %s wanted to connect", id.toString())
            remove = true
        } else {
            uuid = id
            val ch = Server.getInstance().getClientManager().generateChallenge()
            challenge = ch

            val pkt = OutgoingPacket()
            pkt.writeByte(PacketID.AUTHENTICATE.ordinal)
            pkt.writeByte(ch.size)
            pkt.writeBytes(ch)

            sendPacket(pkt)
        }
    }

    @PacketHandler(PacketID.AUTHENTICATE)
    @Throws(IOException::class)
    fun handleAuthPacket(dis: DataInputStream) {
        val id = uuid
        if (id == null) {
            Log.warning("A client tried to authenticate, but he didn't send the connection packet")
            remove = true
            return
        }

        val len = dis.readByte().toInt() and 0xFF
        val mac = ByteArray(len)
        dis.readFully(mac)

        if (Server.getInstance().getClientManager().verifyClient(id, challenge, mac)) {
            Log.info("Client with UUID %s authenticated successfully", id.toString())

            val dir = File(Server.getInstance().getDirectory(), id.toString())
            userDir = dir
            if (!dir.exists() && !dir.mkdir()) {
                Log.warning("Could not create storage directory for user %s, things may go wrong...", id.toString())
            }

            try {
                val quotaDis = DataInputStream(FileInputStream(File(dir, ".quota")))
                quota = quotaDis.readLong()
                Util.silentClose(quotaDis)
            } catch (_: FileNotFoundException) {
                quota = 0
            } catch (ex: IOException) {
                Log.warningEx("Couldn't read quota for user %s, things may go wrong...", ex, id.toString())
                quota = Server.getInstance().getMaxQuota()
            }

            isAuthenticated = true
        } else {
            Log.warning("Client with UUID %s failed to authenticate", id.toString())
            remove = true
        }
    }

    @PacketHandler(PacketID.PING)
    fun handlePing(dis: DataInputStream) {
        if (isAuthenticated) {
            val pkt = OutgoingPacket()
            pkt.writeByte(PacketID.PING.ordinal)
            sendPacket(pkt)
        }
    }

    @PacketHandler(PacketID.BEGIN_FILE_UPLOAD)
    @Throws(IOException::class)
    fun handleBeginUpload(dis: DataInputStream) {
        if (!isAuthenticated) return
        val id = uuid!!

        val fname = readString(dis)
        val size = dis.readLong()

        val rep = OutgoingPacket()
        rep.writeByte(PacketID.BEGIN_FILE_UPLOAD.ordinal)

        if (Util.isFileNameInvalid(fname)) {
            Log.warning("Client %s tried to upload a file with a bad name", id.toString())
            rep.writeByte(Constants.FUPA_STATUS_BAD_NAME)
        } else if (size <= 0) {
            Log.warning("Client %s tried to upload a file an invalid size", id.toString())
            rep.writeByte(Constants.FUPA_STATUS_INVALID_SIZE)
        } else if (quota + size > Server.getInstance().getMaxQuota()) {
            rep.writeByte(Constants.FUPA_STATUS_EXCEEDS_QUOTA)
        } else if (currentFile != null || sendingFile) {
            rep.writeByte(Constants.FUPA_STATUS_OCCUPIED)
        } else {
            val fle = File(userDir, fname)
            if (fle.exists()) {
                rep.writeByte(Constants.FUPA_STATUS_FILE_EXISTS)
            } else {
                try {
                    currentFile = FileOutputStream(fle)
                    currentFileSize = 0
                    currentFileExpectedSize = size
                    rep.writeByte(0) // OK
                } catch (ex: IOException) {
                    Log.warningEx("IOException while uploading file %s from user %s", ex, fname, id.toString())
                    rep.writeByte(Constants.FUPA_STATUS_INTERNAL_ERROR)
                }
            }
        }

        sendPacket(rep)
    }

    @PacketHandler(PacketID.FILE_PART)
    @Throws(IOException::class)
    fun handleFilePart(dis: DataInputStream) {
        if (!isAuthenticated || currentFile == null) return

        val len = dis.readShort().toInt() and 0xFFFF
        if (len <= 0) {
            finishUpload(Constants.FUPA_STATUS_USER_ABORT)
            return
        }

        currentFileSize += len.toLong()
        if (currentFileSize > currentFileExpectedSize) {
            finishUpload(Constants.FUPA_STATUS_LIER)
            return
        }

        try {
            currentFile!!.write(getCurrentPacketRawData()!!, 3, len)
        } catch (ex: IOException) {
            Log.warningEx("Client %s encountered an IOException while uploading some file", ex, uuid.toString())
            finishUpload(Constants.FUPA_STATUS_INTERNAL_ERROR)
            currentFileSize -= len.toLong()
            return
        }

        if (currentFileSize >= currentFileExpectedSize) {
            finishUpload(0) // No error
        }
    }

    @PacketHandler(PacketID.GET_FILE)
    @Throws(IOException::class)
    fun handleGetFile(dis: DataInputStream) {
        if (!isAuthenticated || currentFile != null) return

        val msb = dis.readLong()
        val lsb = dis.readLong()
        val fname = readString(dis)
        val doQuery = dis.readBoolean()

        val rep = OutgoingPacket()
        rep.writeByte(PacketID.GET_FILE.ordinal)

        if (Util.isFileNameInvalid(fname)) {
            rep.writeByte(Constants.GETF_STATUS_BAD_NAME)
        } else {
            val user = UUID(msb, lsb)
            val fle = File(Server.getInstance().getDirectory(), "$user${File.separatorChar}$fname")

            if (doQuery) {
                try {
                    rep.setOnFinishAction(SendFileCallback(fle))
                    rep.writeByte(0)
                    sendingFile = true
                } catch (_: FileNotFoundException) {
                    rep.writeByte(Constants.GETF_STATUS_NOT_FOUND)
                }
            } else {
                rep.writeByte(if (fle.exists() && fle.isFile) 0 else Constants.GETF_STATUS_NOT_FOUND)
            }
        }

        sendPacket(rep)
    }

    @PacketHandler(PacketID.QUOTA)
    fun handleQuota(dis: DataInputStream) {
        val pkt = OutgoingPacket()
        pkt.writeByte(PacketID.QUOTA.ordinal)
        pkt.writeLong(quota)
        pkt.writeLong(Server.getInstance().getMaxQuota())
        sendPacket(pkt)
    }

    @PacketHandler(PacketID.LIST)
    @Throws(IOException::class)
    fun handleList(dis: DataInputStream) {
        val msb = dis.readLong()
        val lsb = dis.readLong()
        val dir = File(Server.getInstance().getDirectory(), UUID(msb, lsb).toString())
        var list: Array<String>? = null

        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                list = files.filter { it.isFile && !Util.isFileNameInvalid(it.name) }
                    .map { it.name }
                    .toTypedArray()
            }
        }

        val pkt = OutgoingPacket()
        pkt.writeByte(PacketID.LIST.ordinal)

        if (list == null) {
            pkt.writeByte(0)
        } else {
            pkt.writeByte(list.size)
            list.forEach(pkt::writeString)
        }

        sendPacket(pkt)
    }

    @PacketHandler(PacketID.DELETE)
    @Throws(IOException::class)
    fun handleDelete(dis: DataInputStream) {
        val fname = readString(dis)
        var status = 2

        if (!Util.isFileNameInvalid(fname)) {
            val file = File(userDir, fname)
            if (file.exists() && file.isFile) {
                try {
                    val sz = Files.size(file.toPath())
                    if (file.delete()) {
                        quota -= sz
                        if (quota < 0) quota = 0
                        saveQuota()
                        status = 0
                    }
                } catch (ex: IOException) {
                    Log.errorEx("Couldn't get size of file %s of user %s for removal", ex, file.absolutePath, uuid.toString())
                }
            } else {
                status = 1
            }
        }

        val ret = OutgoingPacket()
        ret.writeByte(PacketID.DELETE.ordinal)
        ret.writeByte(status)
        sendPacket(ret)
    }

    private fun finishUpload(status: Int) {
        val cf = currentFile ?: return
        val pkt = OutgoingPacket()
        pkt.writeByte(PacketID.FILE_STATUS.ordinal)
        pkt.writeByte(status)
        sendPacket(pkt)

        Util.silentClose(cf)
        currentFile = null

        quota += currentFileSize
        saveQuota()
    }

    private fun saveQuota() {
        try {
            val dos = DataOutputStream(FileOutputStream(File(userDir, ".quota")))
            dos.writeLong(quota)
            Util.silentClose(dos)
        } catch (ex: IOException) {
            Log.errorEx("Could not save quota data for user %s", ex, uuid.toString())
        }
    }

    private inner class SendFileCallback(fle: File) : Consumer<OutgoingPacket> {
        private val fis = FileInputStream(fle)

        override fun accept(nocare: OutgoingPacket) {
            var rd: Int
            do {
                rd = try {
                    fis.read(FILE_UPLOAD_BUFFER)
                } catch (ex: IOException) {
                    Log.warningEx("Caught IOException while sending some file", ex)
                    val pkt = OutgoingPacket()
                    pkt.writeByte(PacketID.GET_FILE.ordinal)
                    pkt.writeByte(Constants.GETF_STATUS_INTERNAL_ERROR)
                    sendPacket(pkt)

                    Util.silentClose(fis)
                    sendingFile = false
                    return
                }
            } while (rd == 0)

            val pkt = OutgoingPacket()
            if (rd < 0) {
                rd = 0 // EOF
                sendingFile = false
            } else {
                pkt.setOnFinishAction(this)
            }

            pkt.writeByte(PacketID.FILE_PART.ordinal)
            pkt.writeShort(rd)
            pkt.writeBytes(FILE_UPLOAD_BUFFER, 0, rd)
            sendPacket(pkt)
        }
    }

    override fun onDataReceived() {
        lastDataTime = System.currentTimeMillis()
    }

    fun getUUIDString(): String = uuid?.toString() ?: "[NOT IDENTIFIED YET]"

    companion object {
        private val FILE_UPLOAD_BUFFER = ByteArray(65535)
    }
}
