package net.montoyo.wd.config

import net.montoyo.wd.WebDisplays
import net.montoyo.wd.config.annoconfg.AnnoCFG
import net.montoyo.wd.config.annoconfg.annotation.format.CFGSegment
import net.montoyo.wd.config.annoconfg.annotation.format.Comment
import net.montoyo.wd.config.annoconfg.annotation.format.Config
import net.montoyo.wd.config.annoconfg.annotation.format.ConfigSide
import net.montoyo.wd.config.annoconfg.annotation.format.Name
import net.montoyo.wd.config.annoconfg.annotation.format.Translation
import net.montoyo.wd.config.annoconfg.annotation.value.Default
import net.montoyo.wd.config.annoconfg.annotation.value.DoubleRange
import net.montoyo.wd.config.annoconfg.annotation.value.IntRange

@Config(type = ConfigSide.CLIENT)
object ClientConfig {
    @Name("load_distance")
    @Comment("How far (in blocks) you can be before a screen starts rendering")
    @Translation("config.webdisplays.load_distance")
    @DoubleRange(minV = 0.0, maxV = Double.MAX_VALUE)
    @Default(valueD = 30.0)
    @JvmField
    var loadDistance: Double = 30.0

    @Name("unload_distance")
    @Comment("How far you can be before a screen stops rendering")
    @Translation("config.webdisplays.unload_distance")
    @DoubleRange(minV = 0.0, maxV = Double.MAX_VALUE)
    @Default(valueD = 32.0)
    @JvmField
    var unloadDistance: Double = 32.0

    @Name("pad_resolution")
    @Comment(
        "The resolution that minePads should use",
        "Smaller values produce lower qualities, higher values produce higher qualities",
        "A good goto value for this would be the height of your monitor, in pixels"
    )
    @Translation("config.webdisplays.pad_res")
    @IntRange(minV = 120, maxV = 2160)
    @Default(valueI = 720)
    @JvmField
    var padResolution: Int = 720

    @Name("side_pad")
    @Comment(
        "When this is true, the minePad is placed off to the side of the screen when held",
        "When this is false, the minePad is placed closer to the center of the screen"
    )
    @Translation("config.webdisplays.side_pad")
    @Default(valueBoolean = true)
    @JvmField
    var sidePad: Boolean = true

    @Name("switch_buttons")
    @Comment("If the left and right buttons should be swapped when using a laser")
    @Translation("config.webdisplays.switch_buttons")
    @Default(valueBoolean = true)
    @JvmField
    var switchButtons: Boolean = true

    @Comment("Rendering & Chromium performance optimizations")
    @CFGSegment("performance")
    object Performance {
        @Name("backface_culling")
        @Comment("Skip rendering & texture updates for screen faces pointing away from the camera")
        @Translation("config.webdisplays.backface_culling")
        @Default(valueBoolean = true)
        @JvmField
        var backfaceCulling: Boolean = true

        @Name("pause_culled_screens")
        @Comment("Pause Chromium offscreen painting when a screen stays behind the camera or culled")
        @Translation("config.webdisplays.pause_culled_screens")
        @Default(valueBoolean = true)
        @JvmField
        var pauseCulledScreens: Boolean = true

        @Name("max_browsers_per_frame")
        @Comment("Maximum number of new screen browsers created per frame to prevent teleport/join lag spikes")
        @Translation("config.webdisplays.max_browsers_per_frame")
        @IntRange(minV = 1, maxV = 16)
        @Default(valueI = 1)
        @JvmField
        var maxBrowsersPerFrame: Int = 1

        @Name("adaptive_lod")
        @Comment("Dynamically adjust browser render resolution based on viewing distance to save CPU/GPU")
        @Translation("config.webdisplays.adaptive_lod")
        @Default(valueBoolean = true)
        @JvmField
        var adaptiveLod: Boolean = true

        @Name("lod_cooldown_ms")
        @Comment("Minimum time in milliseconds between adaptive LOD browser resizes")
        @Translation("config.webdisplays.lod_cooldown_ms")
        @IntRange(minV = 200, maxV = 5000)
        @Default(valueI = 750)
        @JvmField
        var lodCooldownMs: Int = 750
    }

    @Comment("AutoVolume makes audio fade off based on distance")
    @CFGSegment("auto_volume")
    object AutoVolumeControl {
        @Name("enabled")
        @Comment("Whether or not auto volume should be enabled")
        @Translation("config.webdisplays.auto_vol")
        @Default(valueBoolean = true)
        @JvmField
        var enableAutoVolume: Boolean = true

        @Name("youtube_volume")
        @Comment("How loud youtube should be by default")
        @Translation("config.webdisplays.yt_vol")
        @DoubleRange(minV = 0.0, maxV = 100.0)
        @Default(valueD = 100.0)
        @JvmField
        var ytVolume: Double = 100.0

        @Name("dist0")
        @Comment("Distance after which you can't hear anything (in blocks)")
        @Translation("config.webdisplays.d0")
        @DoubleRange(minV = 0.0, maxV = Double.MAX_VALUE)
        @Default(valueD = 30.0)
        @JvmField
        var dist0: Double = 30.0

        @Name("dist100")
        @Comment("Distance after which the sound starts dropping (in blocks)")
        @Translation("config.webdisplays.d100")
        @DoubleRange(minV = 0.0, maxV = Double.MAX_VALUE)
        @Default(valueD = 10.0)
        @JvmField
        var dist100: Double = 10.0
    }

    @Suppress("unused")
    private val CFG = AnnoCFG(ConfigSide.CLIENT, ClientConfig::class.java)

    @JvmStatic
    fun init() {
        // loads the object
    }

    @Suppress("unused")
    @JvmStatic
    fun postLoad() {
        if (unloadDistance < loadDistance + 2.0) {
            unloadDistance = loadDistance + 2.0
        }

        if (AutoVolumeControl.dist0 < AutoVolumeControl.dist100 + 0.1) {
            AutoVolumeControl.dist0 = AutoVolumeControl.dist100 + 0.1
        }

        WebDisplays.INSTANCE.padResY = padResolution.toDouble()
        WebDisplays.INSTANCE.padResX = WebDisplays.INSTANCE.padResY * WebDisplays.PAD_RATIO

        WebDisplays.INSTANCE.unloadDistance = unloadDistance
        WebDisplays.INSTANCE.unloadDistance2 = unloadDistance * unloadDistance
        WebDisplays.INSTANCE.loadDistance2 = loadDistance * loadDistance

        WebDisplays.INSTANCE.enableBackfaceCulling = Performance.backfaceCulling
        WebDisplays.INSTANCE.pauseCulledScreens = Performance.pauseCulledScreens
        WebDisplays.INSTANCE.maxBrowsersPerFrame = Performance.maxBrowsersPerFrame
        WebDisplays.INSTANCE.enableAdaptiveLod = Performance.adaptiveLod
        WebDisplays.INSTANCE.lodCooldownMs = Performance.lodCooldownMs

        WebDisplays.INSTANCE.ytVolume = AutoVolumeControl.ytVolume.toFloat()
        WebDisplays.INSTANCE.avDist100 = AutoVolumeControl.dist100.toFloat()
        WebDisplays.INSTANCE.avDist0 = AutoVolumeControl.dist0.toFloat()
    }
}
