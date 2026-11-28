package net.montoyo.wd.client

import net.fabricmc.loader.api.FabricLoader
import net.montoyo.wd.utilities.Log
import org.cef.callback.CefCallback
import org.cef.handler.CefResourceHandler
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Replaces MCEF's own mod:// handler: it asks the ClassLoader for a resource name that starts with
 * a slash, which never resolves under Fabric's Knot, so every mod:// page came back missing.
 */
class WDModScheme : CefResourceHandler {
    private var data: ByteArray? = null
    private var offset = 0
    private var mime: String? = null

    override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
        data = null
        offset = 0
        mime = null

        val url = request.url
        val prefix = when {
            url.startsWith("webdisplays://") -> "webdisplays://"
            url.startsWith("mod://") -> "mod://"
            else -> return false
        }

        val rest = url.substring(prefix.length)
        val slash = rest.indexOf('/')
        if (slash < 0) return false

        val domain = rest.substring(0, slash).lowercase(Locale.ROOT)
        var file = rest.substring(slash + 1).lowercase(Locale.ROOT)

        val query = file.indexOf('?')
        if (query >= 0) file = file.substring(0, query)

        if (domain.isEmpty() || file.isEmpty() || file.contains("..")) return false

        val container = FabricLoader.getInstance().getModContainer(domain)
        if (container.isEmpty) return false

        val candidates = arrayOf("assets/$domain/html/$file", "assets/$domain/$file")
        for (candidate in candidates) {
            val found = container.get().findPath(candidate)
            if (found.isEmpty) continue

            try {
                Files.newInputStream(found.get()).use { input ->
                    data = readAll(input)
                }
            } catch (ex: IOException) {
                Log.warningEx("Could not read mod resource %s", ex, candidate)
                return false
            }

            val dot = file.lastIndexOf('.')
            val type = if (dot >= 0) WDScheme.mapMime(file.substring(dot + 1)) else null
            mime = type ?: "application/octet-stream"
            Log.info("WD mod:// serving %s (%d bytes)", candidate, data!!.size)
            callback.Continue()
            return true
        }

        Log.warning("WD resource handler: nothing matched %s in %s/%s", prefix, domain, file)
        return false
    }

    override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, stringRef: StringRef) {
        val d = data
        if (d == null) {
            response.status = 404
            response.statusText = "Not Found"
            responseLength.set(0)
            return
        }

        response.mimeType = mime
        response.status = 200
        response.statusText = "OK"
        responseLength.set(d.size)
    }

    override fun readResponse(buf: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback): Boolean {
        val d = data
        if (d == null || offset >= d.size) {
            data = null
            return false
        }

        val count = min(bytesToRead, d.size - offset)
        System.arraycopy(d, offset, buf, 0, count)
        offset += count
        bytesRead.set(count)
        return true
    }

    override fun cancel() {
        data = null
    }

    companion object {
        @Throws(IOException::class)
        private fun readAll(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream(max(input.available(), 4096))
            val chunk = ByteArray(8192)
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                out.write(chunk, 0, read)
            }
            return out.toByteArray()
        }
    }
}
