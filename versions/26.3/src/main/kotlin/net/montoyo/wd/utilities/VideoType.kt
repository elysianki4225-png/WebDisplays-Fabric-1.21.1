/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

import java.net.MalformedURLException
import java.net.URL

enum class VideoType(
    private val base: String,
    private val volume: Function,
    private val getTime: Function,
    private val setTime: Function
) {
    YOUTUBE(
        "document.getElementById(\"movie_player\").",
        Function("setVolume(", ")"),
        Function("getCurrentTime(", ")"),
        Function("seekTo(", ")")
    ),
    YOUTUBE_EMBED(
        "document.getElementsByClassName(\"html5-video-player\")[0].",
        Function("setVolume(", ")"),
        Function("getCurrentTime(", ")"),
        Function("seekTo(", ")")
    );

    private val volumeCap: Int = volume.prefix.length + 5 + volume.suffix.length

    protected class Function(val prefix: String, val suffix: String) {
        fun apply(): String = prefix + suffix

        fun apply(arg: String): String = prefix + arg + suffix
    }

    fun getVideoIDFromURL(url: URL): String {
        if (this == YOUTUBE) {
            if (url.host.equals("youtu.be", ignoreCase = true)) {
                return url.path.substring(1)
            }

            val args = url.query?.split("&") ?: return ""
            for (arg in args) {
                if (arg.startsWith("v=")) {
                    return arg.substring(2)
                }
            }
        } else if (this == YOUTUBE_EMBED) {
            return url.path.substring(7)
        }

        return ""
    }

    fun getURLFromID(vid: String, autoplay: Boolean): String {
        val format = when (this) {
            YOUTUBE -> if (autoplay) "https://www.youtube.com/watch?v=%s&autoplay=1" else "https://www.youtube.com/watch?v=%s"
            YOUTUBE_EMBED -> if (autoplay) "https://www.youtube.com/embed/%s?autoplay=1" else "https://www.youtube.com/embed/%s"
        }
        return String.format(format, vid)
    }

    fun getVolumeJSQuery(volInt: Int, volFrac: Int): String = volume.apply("$volInt.$volFrac")

    fun getTimeStampQuery(): String = getTime.apply()

    fun setTimeStampQuery(ts: Float): String = setTime.apply(ts.toString())

    companion object {
        @JvmStatic
        fun getTypeFromURL(url: URL): VideoType? {
            val loHost = url.host.lowercase()
            if (loHost == "youtu.be") {
                return if (url.path.length > 1) YOUTUBE else null
            } else if (loHost != "www.youtube.com" && loHost != "youtube.com") {
                return null
            }

            val loPath = url.path.lowercase()
            if (loPath == "/watch") {
                val query = url.query
                if (query != null && (query.startsWith("v=") || query.contains("&v="))) {
                    return YOUTUBE
                }
            } else if (loPath.startsWith("/embed/")) {
                return if (loPath.length > 7) YOUTUBE_EMBED else null
            }

            return null
        }

        @JvmStatic
        fun getTypeFromURL(url: String): VideoType? {
            return try {
                getTypeFromURL(URL(url))
            } catch (_: MalformedURLException) {
                null
            }
        }
    }
}
