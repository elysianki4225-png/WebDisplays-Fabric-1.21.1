package net.montoyo.wd.client.renderers;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;

public class ScreenModelLoader {
    public static final Identifier SCREEN_MODEL_ID = Identifier.fromNamespaceAndPath("webdisplays", "screen");
    public static final Identifier SCREEN_SIDE = Identifier.fromNamespaceAndPath("webdisplays", "block/screen");

    public static final Material[] MATERIALS_SIDES = new Material[16];

    static {
        for (int i = 0; i < MATERIALS_SIDES.length; i++) {
            Identifier tex = Identifier.fromNamespaceAndPath(SCREEN_SIDE.getNamespace(), SCREEN_SIDE.getPath() + i);
            MATERIALS_SIDES[i] = new Material(tex);
        }
    }

    public static void register() {
        CustomUnbakedBlockStateModel.register(SCREEN_MODEL_ID, Unbaked.CODEC);
    }

    public record Unbaked() implements CustomUnbakedBlockStateModel, ModelDebugName {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return CODEC;
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
        }

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            Material.Baked[] materials = new Material.Baked[MATERIALS_SIDES.length];
            for (int i = 0; i < MATERIALS_SIDES.length; i++) {
                materials[i] = baker.materials().get(MATERIALS_SIDES[i], this);
            }
            return new ScreenBaker(materials);
        }

        @Override
        public String debugName() {
            return ScreenModelLoader.class.getName();
        }
    }
}
