/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client

import net.montoyo.wd.miniserv.Constants
import net.montoyo.wd.miniserv.client.Client
import net.montoyo.wd.miniserv.client.ClientTaskGetFile
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.Util
import org.cef.callback.CefCallback
import org.cef.handler.CefResourceHandler
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.UUID

class WDScheme(@JvmField var url: String) : CefResourceHandler {
    private var task: ClientTaskGetFile? = null
    private var isErrorPage = false
    private var dataToWrite: ByteArray? = null
    private var dataOffset = 0
    private var amountToWrite = 0

    override fun processRequest(cefRequest: CefRequest, cefCallback: CefCallback): Boolean {
        url = cefRequest.url

        val pos = url.indexOf('/')
        if (pos < 0) return false

        val uuidStr = url.substring(0, pos)
        var fileStr = url.substring(pos + 1)

        try {
            fileStr = URLDecoder.decode(fileStr, "UTF-8")
        } catch (ex: UnsupportedEncodingException) {
            Log.warningEx("UTF-8 isn't supported... yeah... and I'm a billionaire...", ex)
        }

        if (uuidStr.isEmpty() || Util.isFileNameInvalid(fileStr)) return false

        val uuid = try {
            UUID.fromString(uuidStr)
        } catch (_: IllegalArgumentException) {
            return false
        }

        val t = ClientTaskGetFile(uuid, fileStr)
        task = t
        return Client.getInstance().addTask(t)
    }

    override fun getResponseHeaders(cefResponse: CefResponse, intRef: IntRef, stringRef: StringRef) {
        val t = task ?: return
        Log.info("Waiting for response...")
        val status = t.waitForResponse()
        Log.info("Got response %d", status)

        if (status == 0) {
            val extPos = t.getFileName().lastIndexOf('.')
            if (extPos >= 0) {
                val mime = mapMime(t.getFileName().substring(extPos + 1))
                if (mime != null) cefResponse.mimeType = mime
            }

            cefResponse.status = 200
            cefResponse.statusText = "OK"
            cefResponse.setHeaderByName("content-length", "-1", true)
            return
        }

        val errCode: Int
        val errStr: String
        if (status == Constants.GETF_STATUS_NOT_FOUND) {
            errCode = 404
            errStr = "Not Found"
        } else {
            errCode = 500
            errStr = "Internal Server Error"
        }

        cefResponse.status = errCode
        cefResponse.statusText = errStr

        try {
            dataToWrite = String.format(ERROR_PAGE, errCode, errStr).toByteArray(Charsets.UTF_8)
            dataOffset = 0
            amountToWrite = dataToWrite!!.size
            isErrorPage = true
            cefResponse.setHeaderByName("content-length", amountToWrite.toString(), true)
        } catch (_: Exception) {
            cefResponse.setHeaderByName("content-length", "0", true)
        }
    }

    override fun readResponse(bytes: ByteArray, i: Int, intRef: IntRef, cefCallback: CefCallback): Boolean {
        val t = task
        if (dataToWrite == null) {
            if (isErrorPage || t == null) return false

            dataToWrite = t.waitForData()
            dataOffset = 3
            amountToWrite = t.getDataLength()

            if (amountToWrite <= 0) {
                dataToWrite = null
                return false
            }
        }

        if (amountToWrite <= 0) {
            if (!isErrorPage) t?.nextData()
            dataToWrite = null
        }

        return true
    }

    override fun cancel() {}

    companion object {
        private const val ERROR_PAGE =
            "<!DOCTYPE html><html><head></head><body><h1>%d %s</h1><hr /><i>Miniserv powered by WebDisplays</i></body></html>"

        @JvmStatic
        fun mapMime(ext: String): String? {
            return when (ext) {
                "htm", "html" -> "text/html"
                "css" -> "text/css"
                "js" -> "text/javascript"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "svg" -> "image/svg+xml"
                "xml" -> "text/xml"
                "txt" -> "text/plain"
                else -> null
            }
        }
    }
}
