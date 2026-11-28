package net.montoyo.wd.client.renderers

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockAndTintGetter

/**
 * Chunk meshing happens on worker threads, so the position being tessellated is tracked per thread
 * as a fallback for vanilla rendering when FabricBakedModel isn't invoked.
 */
object ScreenModelContext {
    private val LEVEL = ThreadLocal<BlockAndTintGetter?>()
    private val POS = ThreadLocal<BlockPos?>()

    @JvmStatic
    fun set(level: BlockAndTintGetter?, pos: BlockPos?) {
        LEVEL.set(level)
        POS.set(pos)
    }

    @JvmStatic
    fun clear() {
        LEVEL.remove()
        POS.remove()
    }

    @JvmStatic
    fun level(): BlockAndTintGetter? = LEVEL.get()

    @JvmStatic
    fun pos(): BlockPos? = POS.get()
}
