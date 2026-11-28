/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv.server

import net.montoyo.wd.WebDisplays
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.Util
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

class Server : Runnable {
    private var server: ServerSocketChannel? = null
    private var selector: Selector? = null
    private var port = 25566
    private val clientList = ArrayList<ServerClient>()
    private val clientMap = HashMap<SocketChannel, ServerClient>()
    private val readBuffer: ByteBuffer = ByteBuffer.allocateDirect(8192)
    private val clientMgr = ClientManager()
    private var directory: File? = null

    @Volatile
    private var running: Boolean = false

    @Volatile
    private var thread: Thread? = null

    fun getPort(): Int = port

    fun setPort(p: Int) {
        port = p
    }

    fun start() {
        val t = Thread(this)
        t.name = "MiniServServer"
        t.isDaemon = true
        thread = t

        try {
            val s = ServerSocketChannel.open()
            s.bind(InetSocketAddress(WebDisplays.INSTANCE.miniservBind ?: "0.0.0.0", port))
            s.configureBlocking(false)
            server = s
            Log.info("Miniserv listening on %s:%d", WebDisplays.INSTANCE.miniservBind, port)
        } catch (t2: Throwable) {
            Log.errorEx(
                "Could not bind miniserv to ${WebDisplays.INSTANCE.miniservBind}:$port (check the miniserv_bind option)",
                t2
            )
            server?.let { Util.silentClose(it) }
            server = null
            return
        }

        try {
            val sel = Selector.open()
            server!!.register(sel, SelectionKey.OP_ACCEPT)
            selector = sel
        } catch (t2: Throwable) {
            t2.printStackTrace()
            selector?.let { Util.silentClose(it) }
            server?.let { Util.silentClose(it) }
            selector = null
            server = null
            return
        }

        synchronized(this) {
            running = true
        }

        t.start()
    }

    @Throws(IOException::class)
    fun stopServer() {
        if (getRunning()) {
            val t = thread

            synchronized(this) {
                running = false
                selector?.wakeup()
                server?.close()
            }

            if (t != null) {
                while (t.isAlive) {
                    try {
                        t.join()
                    } catch (_: InterruptedException) {
                    }
                }
            }

            Log.info("Miniserv server stopped")
        }
    }

    private fun getRunning(): Boolean {
        synchronized(this) {
            return running
        }
    }

    override fun run() {
        while (getRunning()) {
            try {
                loopUnsafe()
            } catch (t: Throwable) {
                Log.errorEx("Miniserv Server crashed", t)
                break
            }
        }

        synchronized(this) {
            running = false
        }

        for (cli in clientList) {
            Util.silentClose(cli.getChannel())
        }

        clientList.clear()
        clientMap.clear()
        selector?.let { Util.silentClose(it) }
        server?.let { Util.silentClose(it) }
        selector = null
        server = null
        thread = null
    }

    @Throws(Throwable::class)
    private fun loopUnsafe() {
        val sel = selector ?: return
        sel.select(1000) // Allow the server to kick timed-out clients

        for (key in sel.selectedKeys()) {
            if (key == null) continue

            try {
                if (key.isAcceptable) {
                    val chan = try {
                        server!!.accept()
                    } catch (t: Throwable) {
                        Log.warningEx("Could not accept client", t)
                        null
                    }

                    if (chan != null) {
                        chan.configureBlocking(false)
                        val toAdd = ServerClient(chan, sel)
                        clientMap[chan] = toAdd
                        clientList.add(toAdd)
                    }
                }

                if (key.isReadable) {
                    val cli = clientMap[key.channel()]
                    if (cli == null) {
                        Log.warning("Received read info from unknown client")
                    } else {
                        try {
                            readBuffer.clear()
                            val read = cli.getChannel().read(readBuffer)
                            if (read < 0) {
                                cli.setShouldRemove()
                            } else if (read > 0) {
                                readBuffer.position(0)
                                readBuffer.limit(read)
                                cli.readyRead(readBuffer)
                            }
                        } catch (t: Throwable) {
                            Log.warningEx("Could not read data from client", t)
                            cli.setShouldRemove()
                        }
                    }
                }

                if (key.isWritable) {
                    val cli = clientMap[key.channel()]
                    if (cli == null) {
                        Log.warning("Received write info from unknown client")
                    } else {
                        try {
                            cli.readyWrite()
                        } catch (t: Throwable) {
                            Log.warningEx("Could not write data to client", t)
                            cli.setShouldRemove()
                        }
                    }
                }
            } catch (_: Throwable) {
                continue
            }
        }

        val ctime = System.currentTimeMillis()
        for (i in clientList.size - 1 downTo 0) {
            val cli = clientList[i]
            if (cli.shouldRemove()) {
                removeClient(cli)
            } else if (cli.hasTimedOut(ctime)) {
                Log.info("Client %s has timed out!", cli.getUUIDString())
                removeClient(cli)
            }
        }
    }

    private fun removeClient(cli: ServerClient) {
        clientMap.remove(cli.getChannel())
        clientList.remove(cli)
        Util.silentClose(cli.getChannel())
    }

    fun getClientManager(): ClientManager = clientMgr

    fun setDirectory(dir: File) {
        if (!dir.exists() && !dir.mkdir()) {
            Log.warning("Could not create miniserv storage directory %s", dir.absolutePath)
        }
        directory = dir
    }

    fun getDirectory(): File? = directory

    fun getMaxQuota(): Long = WebDisplays.INSTANCE.miniservQuota

    companion object {
        private var instance: Server? = null

        @JvmStatic
        fun getInstance(): Server {
            if (instance == null) {
                instance = Server()
            }
            return instance!!
        }
    }
}
