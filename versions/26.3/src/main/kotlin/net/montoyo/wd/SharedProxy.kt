package net.montoyo.wd

import com.cinemamod.mcef.MCEF
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.montoyo.wd.core.HasAdvancement
import net.montoyo.wd.core.JSServerRequest
import net.montoyo.wd.data.GuiData
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.*
import java.util.UUID

open class SharedProxy {
    open fun preInit() {}

    open fun init() {
        MCEF.scheduleForInit { onCefInit() }
    }

    open fun postInit() {}

    open fun onCefInit() {}

    @Deprecated("For removal", level = DeprecationLevel.WARNING)
    open fun getWorld(dim: ResourceKey<Level>): Level? = getServer()?.getLevel(dim)

    open fun getWorld(context: WDContext): BlockGetter? = context.sender?.level()

    open fun enqueue(r: Runnable) {
        getServer()?.execute(r)
    }

    open fun displayGui(data: GuiData) {
        Log.error("Called SharedProxy.displayGui() on server side...")
    }

    open fun trackScreen(tes: TileEntityScreen, track: Boolean) {}

    open fun onAutocompleteResult(pairs: Array<NameUUIDPair>) {}

    open fun getOnlineGameProfiles(): Array<GameProfile> {
        val server = getServer() ?: return emptyArray()
        return server.playerList.players.map { it.gameProfile }.toTypedArray()
    }

    open fun screenUpdateResolutionInGui(pos: Vector3i, side: BlockSide, res: Vector2i) {}

    open fun screenUpdateRotationInGui(pos: Vector3i, side: BlockSide, rot: Rotation) {}

    open fun screenUpdateAutoVolumeInGui(pos: Vector3i, side: BlockSide, av: Boolean) {}

    open fun displaySetPadURLGui(stack: ItemStack, padURL: String) {
        Log.error("Called SharedProxy.displaySetPadURLGui() on server side...")
    }

    open fun openMinePadGui(padId: UUID) {
        Log.error("Called SharedProxy.openMinePadGui() on server side...")
    }

    open fun handleJSResponseSuccess(reqId: Int, type: JSServerRequest, data: ByteArray) {
        Log.error("Called SharedProxy.handleJSResponseSuccess() on server side...")
    }

    open fun handleJSResponseError(reqId: Int, type: JSServerRequest, errCode: Int, err: String) {
        Log.error("Called SharedProxy.handleJSResponseError() on server side...")
    }

    open fun hasClientPlayerAdvancement(rl: Identifier): HasAdvancement = HasAdvancement.DONT_KNOW

    open fun getServer(): MinecraftServer? = WebDisplaysFabric.currentServer

    open fun setMiniservClientPort(port: Int) {}

    open fun startMiniservClient() {}

    open fun isMiniservDisabled(): Boolean = false

    open fun closeGui(bp: BlockPos, bs: BlockSide) {}

    open fun renderRecipes() {}

    open fun isShiftDown(): Boolean = false
}
