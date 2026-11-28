package net.montoyo.wd

import com.google.gson.Gson
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.montoyo.wd.config.CommonConfig
import net.montoyo.wd.controls.ScreenControlRegistry
import net.montoyo.wd.core.Criterion
import net.montoyo.wd.init.BlockInit
import net.montoyo.wd.init.ItemInit
import net.montoyo.wd.init.TabInit
import net.montoyo.wd.init.TileInit
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.registry.DeferredRegister
import net.montoyo.wd.utilities.Util
import java.net.MalformedURLException
import java.net.URL

class WebDisplays {
    // Sounds
    @JvmField var soundTyping: SoundEvent? = null
    @JvmField var soundUpgradeAdd: SoundEvent? = null
    @JvmField var soundUpgradeDel: SoundEvent? = null
    @JvmField var soundScreenCfg: SoundEvent? = null
    @JvmField var soundServer: SoundEvent? = null
    @JvmField var soundIronic: SoundEvent? = null

    // Criteria
    @JvmField var criterionPadBreak: Criterion? = null
    @JvmField var criterionUpgradeScreen: Criterion? = null
    @JvmField var criterionLinkPeripheral: Criterion? = null
    @JvmField var criterionKeyboardCat: Criterion? = null

    // Config
    @JvmField var padResX: Double = 0.0
    @JvmField var padResY: Double = 0.0
    @JvmField var unloadDistance: Double = 32.0
    @JvmField var unloadDistance2: Double = 0.0
    @JvmField var loadDistance2: Double = 0.0
    @JvmField var miniservPort: Int = 0
    @JvmField var miniservBind: String? = null
    @JvmField var miniservQuota: Long = 0
    @JvmField var ytVolume: Float = 0f
    @JvmField var avDist100: Float = 0f
    @JvmField var avDist0: Float = 0f
    @JvmField var enableBackfaceCulling: Boolean = true
    @JvmField var pauseCulledScreens: Boolean = true
    @JvmField var maxBrowsersPerFrame: Int = 1
    @JvmField var enableAdaptiveLod: Boolean = true
    @JvmField var lodCooldownMs: Int = 750

    private fun onRegisterSounds() {
        soundTyping = registerSound("keyboard_type")
        soundUpgradeAdd = registerSound("upgrade_add")
        soundUpgradeDel = registerSound("upgrade_del")
        soundScreenCfg = registerSound("screencfg_open")
        soundServer = registerSound("server")
        soundIronic = registerSound("ironic")
    }

    companion object {
        @JvmField var INSTANCE: WebDisplays = WebDisplays()
        @JvmField var PROXY: SharedProxy = SharedProxy()

        @JvmField
        val ADV_PAD_BREAK: Identifier = Identifier.fromNamespaceAndPath("webdisplays", "pad_break")
        const val BLACKLIST_URL = "webdisplays://webdisplays/blacklisted.html"
        @JvmField val GSON = Gson()
        const val PAD_RATIO = 59.0 / 30.0

        @JvmField
        val SOUNDS: DeferredRegister<SoundEvent> = DeferredRegister.create(Registries.SOUND_EVENT, "webdisplays")

        @JvmStatic
        fun initialize() {
            INSTANCE = WebDisplays()
            PROXY = SharedProxy()

            CommonConfig.init()

            val padBreak = Criterion("pad_break")
            val upgradeScreen = Criterion("upgrade_screen")
            val linkPeripheral = Criterion("link_peripheral")
            val keyboardCat = Criterion("keyboard_cat")

            INSTANCE.criterionPadBreak = padBreak
            INSTANCE.criterionUpgradeScreen = upgradeScreen
            INSTANCE.criterionLinkPeripheral = linkPeripheral
            INSTANCE.criterionKeyboardCat = keyboardCat
            registerTrigger(padBreak, upgradeScreen, linkPeripheral, keyboardCat)

            WDNetworkRegistry.init()
            INSTANCE.onRegisterSounds()
            BlockInit.init()
            ItemInit.init()
            TileInit.init()
            TabInit.init()
            ScreenControlRegistry.init()
        }

        private fun registerSound(resName: String): SoundEvent {
            val resLoc = Identifier.fromNamespaceAndPath("webdisplays", resName)
            val ret = SoundEvent.createVariableRangeEvent(resLoc)
            SOUNDS.register(resName) { ret }
            return ret
        }

        private fun registerTrigger(vararg criteria: Criterion) {
            for (c in criteria) {
                Registry.register(BuiltInRegistries.TRIGGER_TYPES, c.id, c)
            }
        }

        @JvmStatic
        fun isSiteBlacklisted(url: String): Boolean {
            return try {
                val parsed = URL(Util.addProtocol(url))
                CommonConfig.Browser.blacklist.any { it.equals(parsed.host, ignoreCase = true) }
            } catch (_: MalformedURLException) {
                false
            }
        }

        @JvmStatic
        fun applyBlacklist(url: String?): String? {
            return if (url != null && isSiteBlacklisted(url)) BLACKLIST_URL else rewriteLegacyModScheme(url)
        }

        private fun rewriteLegacyModScheme(url: String?): String? {
            return if (url != null && url.startsWith("mod://")) {
                "webdisplays://" + url.substring("mod://".length)
            } else {
                url
            }
        }
    }
}
