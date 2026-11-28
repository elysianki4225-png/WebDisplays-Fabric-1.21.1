/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv.client

import net.minecraft.client.Minecraft
import net.montoyo.wd.miniserv.AbstractClient
import net.montoyo.wd.miniserv.Constants
import net.montoyo.wd.miniserv.KeyParameters
import net.montoyo.wd.miniserv.OutgoingPacket
import net.montoyo.wd.miniserv.PacketHandler
import net.montoyo.wd.miniserv.PacketID
import net.montoyo.wd.net.server_bound.C2SMessageMiniservConnect
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.Util
import java.io.DataInputStream
import java.io.IOException
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.security.InvalidKeyException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.util.ArrayDeque
import java.util.UUID
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.Mac
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max

class Client : AbstractClient(), Runnable {
    private val random = SecureRandom()
    private var keyPair: KeyPair? = null
    private var key: ByteArray? = null
    private var address: SocketAddress? = null

    @Volatile
    private var running: Boolean = false

    @Volatile
    private var connected: Boolean = false

    private val readBuffer: ByteBuffer = ByteBuffer.allocateDirect(8192)

    @Volatile
    private var thread: Thread? = null

    private var clientUUID: UUID? = null
    private val tasks = ArrayDeque<ClientTask<*>>()
    private var currentTask: ClientTask<*>? = null

    @Volatile
    private var authenticated: Boolean = false

    private var lastPingTime: Long = 0

    fun beginConnection(): C2SMessageMiniservConnect? {
        if (keyPair == null) {
            try {
                val keygen = KeyPairGenerator.getInstance("RSA")
                keygen.initialize(KeyParameters.RSA_KEY_SIZE)
                keyPair = keygen.generateKeyPair()
            } catch (ex: NoSuchAlgorithmException) {
                Log.warningEx("RSA is unsupported?!?!", ex)
                return null
            }
        }

        val pubKey = keyPair!!.public as RSAPublicKey
        return C2SMessageMiniservConnect(pubKey.modulus.toByteArray(), pubKey.publicExponent.toByteArray())
    }

    fun decryptKey(encKey: ByteArray): Boolean {
        try {
            val cipher = Cipher.getInstance(KeyParameters.RSA_CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, keyPair!!.private, random)
            key = cipher.doFinal(encKey)
            return true
        } catch (ex: NoSuchAlgorithmException) {
            Log.warningEx("%s unsupported...", ex, KeyParameters.RSA_CIPHER)
        } catch (ex: NoSuchPaddingException) {
            Log.warningEx("%s unsupported...", ex, KeyParameters.RSA_CIPHER)
        } catch (ex: InvalidKeyException) {
            Log.warningEx("The generated key is invalid...", ex)
        } catch (ex: IllegalBlockSizeException) {
            Log.warningEx("Could not decrypt key", ex)
        } catch (ex: BadPaddingException) {
            Log.warningEx("Could not decrypt key", ex)
        }
        return false
    }

    private fun authenticate(challenge: ByteArray): ByteArray? {
        try {
            val mac = Mac.getInstance(KeyParameters.MAC_ALGORITHM)
            mac.init(SecretKeySpec(key!!, KeyParameters.MAC_ALGORITHM))
            return mac.doFinal(challenge)
        } catch (ex: NoSuchAlgorithmException) {
            Log.warningEx("%s unsupported...", ex, KeyParameters.MAC_ALGORITHM)
        } catch (ex: InvalidKeyException) {
            Log.warningEx("The key given by the server is invalid", ex)
        }
        return null
    }

    fun start(addr: SocketAddress) {
        if (getRunning()) {
            Log.warning("Called Client.start() twice")
            return
        }

        address = addr
        val t = Thread(this)
        t.name = "MiniServClient"
        t.isDaemon = true
        thread = t

        synchronized(this) {
            running = true
            connected = false
        }

        t.start()
    }

    fun stop() {
        if (getRunning()) {
            val t = thread
            synchronized(this) {
                running = false
                if (connected) selector.wakeup()
            }

            if (t != null) {
                while (t.isAlive) {
                    try {
                        t.join()
                    } catch (_: InterruptedException) {
                    }
                }
            }

            Log.info("Miniserv client stopped")
        }
    }

    private fun getRunning(): Boolean {
        synchronized(this) {
            return running
        }
    }

    override fun run() {
        if (clientUUID == null) {
            val player = Minecraft.getInstance().player
            if (player != null) {
                clientUUID = player.gameProfile.id
            } else {
                return // can't tick yet; player does not exist
            }
        }

        try {
            selector = Selector.open()
            socket = SocketChannel.open()
            socket.connect(address)
            socket.configureBlocking(false)

            selKey = socket.register(selector, SelectionKey.OP_READ)
        } catch (ex: IOException) {
            Log.errorEx("Couldn't start client", ex)
            synchronized(this) {
                running = false
            }
            return
        }

        synchronized(this) {
            connected = true
        }

        Log.info("Miniserv client connected!")

        val connPacket = OutgoingPacket()
        connPacket.writeByte(PacketID.INIT_CONN.ordinal)
        connPacket.writeLong(clientUUID!!.mostSignificantBits)
        connPacket.writeLong(clientUUID!!.leastSignificantBits)
        sendPacket(connPacket)
        lastPingTime = System.currentTimeMillis()

        while (getRunning()) {
            try {
                unsafeLoop()
            } catch (t: Throwable) {
                Log.errorEx("MiniServ Client crashed", t)
                break
            }
        }

        synchronized(this) {
            connected = false
            running = false
            authenticated = false
        }

        Util.silentClose(selector)
        Util.silentClose(socket)

        currentTask?.let {
            it.abort()
            it.onFinished()
            currentTask = null
        }

        synchronized(tasks) {
            var task = tasks.poll()
            while (task != null) {
                task.abort()
                task.onFinished()
                task = tasks.poll()
            }
        }

        clearSendQueue()
        thread = null
    }

    @Throws(Throwable::class)
    private fun unsafeLoop() {
        val timeBeforePing = Constants.CLIENT_PING_PERIOD - (System.currentTimeMillis() - lastPingTime)
        selector.select(max(0L, timeBeforePing))

        if (currentTask == null || currentTask!!.isCanceled()) {
            nextTask()
        }

        for (key in selector.selectedKeys()) {
            if (key.isReadable) {
                readBuffer.clear()
                val rd = socket.read(readBuffer)

                if (rd < 0) {
                    Log.warning("Connection was closed, stopping...")
                    running = false
                } else if (rd > 0) {
                    readBuffer.position(0)
                    readBuffer.limit(rd)
                    readyRead(readBuffer)
                }
            }

            if (key.isWritable) {
                try {
                    readyWrite()
                } catch (t: Throwable) {
                    Log.errorEx("Caught error while sending data to miniserv, stopping...", t)
                    running = false
                }
            }
        }

        val t = System.currentTimeMillis()
        if (t - lastPingTime >= Constants.CLIENT_PING_PERIOD) {
            val pkt = OutgoingPacket()
            pkt.writeByte(PacketID.PING.ordinal)
            sendPacket(pkt)
            lastPingTime = t
        }
    }

    override fun onWriteError() {
        running = false
        Log.error("Write error, stopping...")
    }

    @PacketHandler(PacketID.AUTHENTICATE)
    @Throws(IOException::class)
    fun handleAuth(dis: DataInputStream) {
        val len = dis.readByte().toInt()
        val challenge = ByteArray(len)
        dis.readFully(challenge)
        val mac = authenticate(challenge)!!

        val pkt = OutgoingPacket()
        pkt.writeByte(PacketID.AUTHENTICATE.ordinal)
        pkt.writeByte(mac.size)
        pkt.writeBytes(mac)
        sendPacket(pkt)

        Log.info("Miniserv client authenticated")

        synchronized(this) {
            authenticated = true
        }
    }

    @PacketHandler(PacketID.BEGIN_FILE_UPLOAD)
    @Throws(IOException::class)
    fun handleBeginUpload(dis: DataInputStream) {
        (currentTask as? ClientTaskUploadFile)?.onReceivedUploadStatus(dis.readByte().toInt())
    }

    @PacketHandler(PacketID.FILE_STATUS)
    @Throws(IOException::class)
    fun handleFileStatus(dis: DataInputStream) {
        (currentTask as? ClientTaskUploadFile)?.onUploadFinishedStatus(dis.readByte().toInt())
    }

    @PacketHandler(PacketID.GET_FILE)
    @Throws(IOException::class)
    fun handleGetFile(dis: DataInputStream) {
        when (val task = currentTask) {
            is ClientTaskGetFile -> task.onGetFileResponse(dis.readByte().toInt())
            is ClientTaskCheckFile -> task.onStatus(dis.readByte().toInt())
        }
    }

    @PacketHandler(PacketID.FILE_PART)
    @Throws(IOException::class)
    fun handleFilePart(dis: DataInputStream) {
        val task = currentTask as? ClientTaskGetFile ?: return
        val len = dis.readShort().toInt() and 0xFFFF
        task.onData(getCurrentPacketRawData()!!, len)
    }

    @PacketHandler(PacketID.QUOTA)
    @Throws(IOException::class)
    fun handleQuota(dis: DataInputStream) {
        val q = dis.readLong()
        val m = dis.readLong()
        (currentTask as? ClientTaskGetQuota)?.onQuotaData(q, m)
    }

    @PacketHandler(PacketID.LIST)
    @Throws(IOException::class)
    fun handleList(dis: DataInputStream) {
        val cnt = dis.readByte().toInt() and 0xFF
        val files = Array(cnt) { readString(dis) }
        (currentTask as? ClientTaskGetFileList)?.onFileList(files)
    }

    @PacketHandler(PacketID.DELETE)
    @Throws(IOException::class)
    fun handleDelete(dis: DataInputStream) {
        (currentTask as? ClientTaskDeleteFile)?.onStatusPacket(dis.readByte().toInt())
    }

    fun nextTask() {
        currentTask?.onFinished()

        synchronized(tasks) {
            currentTask = tasks.poll()
        }

        currentTask?.start()
    }

    fun addTask(task: ClientTask<*>): Boolean {
        val cancel: Boolean
        synchronized(this) {
            cancel = !running || !authenticated
        }
        if (cancel) return false

        synchronized(tasks) {
            tasks.offer(task)
        }

        selector.wakeup()
        return true
    }

    fun wakeup() {
        val conn: Boolean
        synchronized(this) {
            conn = connected
        }
        if (conn) selector.wakeup()
    }

    override fun onDataSent() {
        lastPingTime = System.currentTimeMillis()
    }

    companion object {
        private var instance: Client? = null

        @JvmStatic
        fun getInstance(): Client {
            if (instance == null) {
                instance = Client()
            }
            return instance!!
        }
    }
}
