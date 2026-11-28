package net.montoyo.wd.client.renderers

import com.mojang.serialization.MapCodec
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.resources.model.ModelBaker
import net.minecraft.client.resources.model.ModelDebugName
import net.minecraft.client.resources.model.sprite.Material
import net.minecraft.resources.Identifier

object ScreenModelLoader {
    @JvmField
    val SCREEN_MODEL_ID: Identifier = Identifier.fromNamespaceAndPath("webdisplays", "screen")

    @JvmField
    val SCREEN_SIDE: Identifier = Identifier.fromNamespaceAndPath("webdisplays", "block/screen")

    @JvmField
    val MATERIALS_SIDES: Array<Material> = Array(16) { i ->
        Material(Identifier.fromNamespaceAndPath(SCREEN_SIDE.namespace, SCREEN_SIDE.path + i))
    }

    @JvmStatic
    fun register() {
        CustomUnbakedBlockStateModel.register(SCREEN_MODEL_ID, Unbaked.CODEC)
    }

    class Unbaked : CustomUnbakedBlockStateModel, ModelDebugName {
        override fun codec(): MapCodec<out CustomUnbakedBlockStateModel> = CODEC

        override fun resolveDependencies(resolver: CustomUnbakedBlockStateModel.Resolver) {
        }

        override fun bake(baker: ModelBaker): BlockStateModel {
            val materials = Array(MATERIALS_SIDES.size) { i ->
                baker.materials().get(MATERIALS_SIDES[i], this)
            }
            return ScreenBaker(materials)
        }

        override fun debugName(): String = ScreenModelLoader::class.java.name

        override fun equals(other: Any?): Boolean = other is Unbaked

        override fun hashCode(): Int = javaClass.hashCode()

        companion object {
            @JvmField
            val INSTANCE = Unbaked()

            @JvmField
            val CODEC: MapCodec<Unbaked> = MapCodec.unit(INSTANCE)
        }
    }
}
