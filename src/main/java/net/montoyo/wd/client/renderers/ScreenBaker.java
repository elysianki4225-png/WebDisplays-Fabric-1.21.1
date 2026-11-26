/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.montoyo.wd.utilities.BlockSide;
import net.montoyo.wd.utilities.Vector3f;
import net.montoyo.wd.utilities.Vector3i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public class ScreenBaker implements BakedModel, FabricBakedModel {

	private static final List<BakedQuad> NO_QUADS = ImmutableList.of();
	private static final BlockSide[] BLOCK_SIDES = BlockSide.values();
	private static final Direction[] BLOCK_FACINGS = Direction.values();

	private static final int BAR_BOTTOM = 1;
	private static final int BAR_RIGHT = 2;
	private static final int BAR_TOP = 4;
	private static final int BAR_LEFT = 8;

	private final TextureAtlasSprite[] texs = new TextureAtlasSprite[16];
	private final BakedQuad[][] prebakedQuads = new BakedQuad[6][16];
	@SuppressWarnings("unchecked")
	private final List<BakedQuad>[][] prebakedLists = new List[6][16];
	@Nullable
	private final RenderMaterial standardMaterial;

	public ScreenBaker(Function<Material, TextureAtlasSprite> spriteGetter) {
		for (int i = 0; i < texs.length; i++)
			texs[i] = spriteGetter.apply(ScreenModelLoader.MATERIALS_SIDES[i]);

		for (int dirIdx = 0; dirIdx < 6; dirIdx++) {
			BlockSide quadSide = BLOCK_SIDES[BlockSide.reverse(dirIdx)];
			for (int texIdx = 0; texIdx < 16; texIdx++) {
				BakedQuad quad = bakeSide(quadSide, texs[texIdx]);
				prebakedQuads[dirIdx][texIdx] = quad;
				prebakedLists[dirIdx][texIdx] = ImmutableList.of(quad);
			}
		}

		Renderer renderer = RendererAccess.INSTANCE.getRenderer();
		this.standardMaterial = renderer != null ? renderer.materialById(RenderMaterial.MATERIAL_STANDARD) : null;
	}

	private void putVertex(int[] buf, int pos, Vector3f vpos, TextureAtlasSprite tex, Vector3f uv, Vector3i normal) {
		pos *= 8;

		buf[pos] = Float.floatToRawIntBits(vpos.x);
		buf[pos + 1] = Float.floatToRawIntBits(vpos.y);
		buf[pos + 2] = Float.floatToRawIntBits(vpos.z);
		buf[pos + 3] = 0xFFFFFFFF;
		buf[pos + 4] = Float.floatToRawIntBits(tex.getU(uv.x / 16.0f));
		buf[pos + 5] = Float.floatToRawIntBits(tex.getV(uv.y / 16.0f));

		int nx = (normal.x * 127) & 0xFF;
		int ny = (normal.y * 127) & 0xFF;
		int nz = (normal.z * 127) & 0xFF;
		buf[pos + 7] = nx | (ny << 8) | (nz << 16);
	}

	private Vector3f rotateVec(Vector3f vec, BlockSide side) {
		return switch (side) {
			case BOTTOM -> new Vector3f(vec.x, 1.0f, 1.0f - vec.z);
			case TOP -> new Vector3f(vec.x, 0.0f, vec.z);
			case NORTH -> new Vector3f(vec.x, vec.z, 1.0f);
			case SOUTH -> new Vector3f(vec.x, 1.0f - vec.z, 0.0f);
			case WEST -> new Vector3f(1.f, vec.x, vec.z);
			case EAST -> new Vector3f(0.0f, 1.0f - vec.x, vec.z);
		};
	}

	private Vector3f rotateTex(BlockSide side, float u, float v) {
		return switch (side) {
			case BOTTOM, NORTH -> new Vector3f(16.f - u, 16.f - v, 0.0f);
			case TOP -> new Vector3f(16.f - u, v, 0.0f);
			case SOUTH -> new Vector3f(u, v, 0.0f);
			case WEST -> new Vector3f(16.f - v, u, 0.0f);
			case EAST -> new Vector3f(v, 16.f - u, 0.0f);
		};
	}

	private BakedQuad bakeSide(BlockSide side, TextureAtlasSprite tex) {
		int[] data = new int[8 * 4];

		int rotation = switch (side) {
			case NORTH, TOP, BOTTOM -> 2;
			case SOUTH -> 0;
			case EAST -> 1;
			case WEST -> 3;
		};

		putVertex(data, (rotation + 3) % 4, rotateVec(new Vector3f(0.0f, 0.0f, 0.0f), side), tex, rotateTex(side, 16.0f, 0.0f), side.backward);
		putVertex(data, (rotation + 2) % 4, rotateVec(new Vector3f(0.0f, 0.0f, 1.0f), side), tex, rotateTex(side, 16.0f, 16.0f), side.backward);
		putVertex(data, (rotation + 1) % 4, rotateVec(new Vector3f(1.0f, 0.0f, 1.0f), side), tex, rotateTex(side, 0.0f, 16.0f), side.backward);
		putVertex(data, (rotation) % 4, rotateVec(new Vector3f(1.0f, 0.0f, 0.0f), side), tex, rotateTex(side, 0.0f, 0.0f), side.backward);

		return new BakedQuad(data, 0xFFFFFFFF, BLOCK_FACINGS[side.ordinal()].getOpposite(), tex, true);
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
		QuadEmitter emitter = context.getEmitter();
		for (int dirIdx = 0; dirIdx < 6; dirIdx++) {
			Direction dir = BLOCK_FACINGS[dirIdx];
			int texIdx = textureIndexFor(state, blockView, pos, BLOCK_SIDES[dirIdx]);
			emitter.fromVanilla(prebakedQuads[dirIdx][texIdx], standardMaterial, dir);
			emitter.emit();
		}
	}

	@Override
	public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
		QuadEmitter emitter = context.getEmitter();
		for (int dirIdx = 0; dirIdx < 6; dirIdx++) {
			Direction dir = BLOCK_FACINGS[dirIdx];
			emitter.fromVanilla(prebakedQuads[dirIdx][15], standardMaterial, dir);
			emitter.emit();
		}
	}

	@Override
	public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random) {
		if (side == null)
			return NO_QUADS;

		int dirIdx = side.ordinal();
		int texIdx = textureIndexFor(state, ScreenModelContext.level(), ScreenModelContext.pos(), BLOCK_SIDES[dirIdx]);
		return prebakedLists[dirIdx][texIdx];
	}

	private int textureIndexFor(@Nullable BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, BlockSide side) {
		if (state == null || level == null || pos == null)
			return 15;

		int res = switch (check(state, level, pos, side.up)) {
			case 1 -> BAR_BOTTOM;
			case 2 -> BAR_TOP;
			case 3 -> BAR_TOP | BAR_BOTTOM;
			default -> 0;
		};

		res |= switch (check(state, level, pos, side.right)) {
			case 1 -> BAR_LEFT;
			case 2 -> BAR_RIGHT;
			case 3 -> BAR_LEFT | BAR_RIGHT;
			default -> 0;
		};

		return res;
	}

	protected byte check(BlockState state, BlockAndTintGetter level, BlockPos pos, Vector3i dir) {
		BlockState u = level.getBlockState(pos.offset(dir.x, dir.y, dir.z));
		BlockState d = level.getBlockState(pos.offset(-dir.x, -dir.y, -dir.z));
		boolean uSame = u.getBlock() == state.getBlock();
		boolean dSame = d.getBlock() == state.getBlock();
		if (uSame && !dSame) return (byte) 1;
		if (dSame && !uSame) return (byte) 2;
		if (!dSame && !uSame) return (byte) 3;
		return (byte) 0;
	}

	@Override
	public boolean useAmbientOcclusion() {
		return true;
	}

	@Override
	public boolean isGui3d() {
		return true;
	}

	@Override
	public boolean usesBlockLight() {
		return false;
	}

	@Override
	public boolean isCustomRenderer() {
		return false;
	}

	@Override
	@NotNull
	public TextureAtlasSprite getParticleIcon() {
		return texs[15];
	}

	@Override
	@NotNull
	public ItemTransforms getTransforms() {
		return ItemTransforms.NO_TRANSFORMS;
	}

	@Override
	@NotNull
	public ItemOverrides getOverrides() {
		return ItemOverrides.EMPTY;
	}
}
