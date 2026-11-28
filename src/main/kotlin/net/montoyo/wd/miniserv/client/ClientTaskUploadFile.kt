/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv.client

import net.montoyo.wd.miniserv.Constants
import net.montoyo.wd.miniserv.OutgoingPacket
import net.montoyo.wd.miniserv.PacketID
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.Util
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.util.function.BiConsumer
import java.util.function.Consumer

class ClientTaskUploadFile @Throws(IOException::class) constructor(private val file: File) :
    ClientTask<ClientTaskUploadFile>(),
    Consumer<OutgoingPacket?> {

    private val size: Long = Files.size(file.toPath())
    private val fis: FileInputStream = FileInputStream(file)
    private var abortFupa: Boolean = false
    private var uploadStatus: Int = 0
    private var uploadPos: Long = 0
    private var onProgress: BiConsumer<Long, Long>? = null

    init {
        runCallbackOnMcThread = true
    }

    override fun start() {
        val pkt = OutgoingPacket()
        pkt.writeByte(PacketID.BEGIN_FILE_UPLOAD.ordinal)
        pkt.writeString(file.name)
        pkt.writeLong(size)

        client.sendPacket(pkt)
    }

    override fun abort() {
        abortFupa = true
        uploadStatus = Constants.FUPA_STATUS_CONNECTION_LOST
        Util.silentClose(fis)
    }

    fun onReceivedUploadStatus(status: Int) {
        if (status == 0) {
            Log.info("Now uploading %s", file.name)
            accept(null)
        } else {
            Util.silentClose(fis)
            uploadStatus = status
            client.nextTask()
        }
    }

    fun onUploadFinishedStatus(status: Int) {
        abortFupa = true
        uploadStatus = status
        client.nextTask()
    }

    override fun accept(nocare: OutgoingPacket?) {
        if (abortFupa) return

        var rd: Int
        do {
            rd = try {
                fis.read(UPLOAD_BUFFER)
            } catch (ex: IOException) {
                Log.warningEx("Caught IOException while sending some file", ex)
                0 // This will cause a FUPA_STATUS_USER_ABORT
            }
        } while (rd == 0)

        if (rd >= 0) {
            val pkt = OutgoingPacket()
            pkt.writeByte(PacketID.FILE_PART.ordinal)
            pkt.writeShort(rd)
            pkt.writeBytes(UPLOAD_BUFFER, 0, rd)
            client.sendPacket(pkt)

            onProgress?.let { cb ->
                uploadPos += rd.toLong()
                cb.accept(uploadPos, size)
            }

            if (rd > 0) {
                @Suppress("UNCHECKED_CAST")
                pkt.setOnFinishAction(this as Consumer<OutgoingPacket>)
                return
            }
        }

        Util.silentClose(file)
    }

    fun getUploadStatus(): Int = uploadStatus

    fun setProgressCallback(onProgress: BiConsumer<Long, Long>?) {
        this.onProgress = onProgress
    }

    companion object {
        private val UPLOAD_BUFFER = ByteArray(65535)
    }
}
