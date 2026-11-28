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
import net.montoyo.wd.config.annoconfg.annotation.value.IntRange
import net.montoyo.wd.config.annoconfg.annotation.value.LongRange

@Config(type = ConfigSide.COMMON)
object CommonConfig {
    @Name("hard_recipes")
    @Comment("If true, breaking the minePad is required to craft upgrades.")
    @Translation("config.webdisplays.hard_recipes")
    @IntRange(minV = 0, maxV = Int.MAX_VALUE)
    @Default(valueBoolean = true)
    @JvmField
    var hardRecipes: Boolean = true

    @Name("disable_ownership_thief")
    @Comment("If true, the ownership thief item will be disabled")
    @Translation("config.webdisplays.disable_thief")
    @Default(valueBoolean = false)
    @JvmField
    var disableOwnershipThief: Boolean = false

    @Comment("Options for the browsers (both the minePad and the screens)")
    @CFGSegment("browser_options")
    object Browser {
        @Name("blacklist")
        @Comment("The page which screens should open up to when turning on")
        @Translation("config.webdisplays.blacklist")
        @Default(valueStr = "")
        @JvmField
        var blacklist: Array<String> = emptyArray()

        @Name("home_page")
        @Comment("The page which screens should open up to when turning on")
        @Translation("config.webdisplays.home_page")
        @Default(valueStr = "webdisplays://webdisplays/main.html")
        @JvmField
        var homepage: String = "webdisplays://webdisplays/main.html"
    }

    @Comment("Options for the in world screen blocks")
    @CFGSegment("screen_options")
    object Screen {
        @Name("max_resolution_x")
        @Comment("The maximum value screen's horizontal resolution, in pixels")
        @Translation("config.webdisplays.max_res_x")
        @IntRange(minV = 0, maxV = Int.MAX_VALUE)
        @Default(valueI = 1920)
        @JvmField
        var maxResolutionX: Int = 1920

        @Name("max_resolution_y")
        @Comment("The maximum value screen's vertical resolution, in pixels")
        @Translation("config.webdisplays.max_res_y")
        @IntRange(minV = 0, maxV = Int.MAX_VALUE)
        @Default(valueI = 1080)
        @JvmField
        var maxResolutionY: Int = 1080

        @Name("max_width")
        @Comment("The maximum width for the screen multiblock, in blocks")
        @Translation("config.webdisplays.max_width")
        @IntRange(minV = 0, maxV = Int.MAX_VALUE)
        @Default(valueI = 16)
        @JvmField
        var maxScreenSizeX: Int = 16

        @Name("max_height")
        @Comment("The maximum height for the screen multiblock, in blocks")
        @Translation("config.webdisplays.max_height")
        @IntRange(minV = 0, maxV = Int.MAX_VALUE)
        @Default(valueI = 16)
        @JvmField
        var maxScreenSizeY: Int = 16
    }

    @Comment("Options for the miniserver")
    @CFGSegment("mini_server")
    object MiniServ {
        @Name("miniserv_bind")
        @Comment(
            "The address miniserv listens on.",
            "0.0.0.0 (default) exposes the file host on every network interface.",
            "Use 127.0.0.1 to keep it local; see the warning file in the project root."
        )
        @Translation("config.webdisplays.miniserv_bind")
        @Default(valueStr = "0.0.0.0")
        @JvmField
        var miniservBind: String = "0.0.0.0"

        @Name("miniserv_port")
        @Comment("The port used by miniserv. 0 to disable")
        @Translation("config.webdisplays.miniserv_port")
        @IntRange(minV = 0, maxV = Short.MAX_VALUE.toInt())
        @Default(valueI = 25566)
        @JvmField
        var miniservPort: Int = 25566

        @Name("miniserv_quota")
        @Comment("The amount of data that can be uploaded to miniserv, in KiB (so 1024 = 1 MiO)")
        @Translation("config.webdisplays.miniserv_quota")
        @LongRange(minV = 0L, maxV = Long.MAX_VALUE)
        @Default(valueL = 1920L)
        @JvmField
        var miniservQuota: Long = 1024L
    }

    @Suppress("unused")
    private val CFG = AnnoCFG(ConfigSide.COMMON, CommonConfig::class.java)

    @JvmStatic
    fun init() {
        // loads the object
    }

    @Suppress("unused")
    @JvmStatic
    fun postLoad() {
        WebDisplays.INSTANCE.miniservPort = MiniServ.miniservPort
        WebDisplays.INSTANCE.miniservBind = MiniServ.miniservBind
        WebDisplays.INSTANCE.miniservQuota = MiniServ.miniservQuota * 1024L
    }
}
