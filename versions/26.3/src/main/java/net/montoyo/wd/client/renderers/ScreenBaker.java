/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.montoyo.wd.utilities.BlockSide;
import net.montoyo.wd.utilities.Vector3i;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public class ScreenBaker implements BlockStateModel {

	private static final BlockSide[] BLOCK_SIDES = BlockSide.values();
	private static final Direction[] BLOCK_FACINGS = Direction.values();

	private static final int BAR_BOTTOM = 1;
	private static final int BAR_RIGHT = 2;
	private static final int BAR_TOP = 4;
	private static final int BAR_LEFT = 8;

	private final Material.Baked[] materials;
	private final @BakedQuad.MaterialFlags int staticMaterialFlags;

	public ScreenBaker(Material.Baked[] materials) {
		this.materials = materials;
		int flags = 0;
		for (Material.Baked mat : materials) {
			if (mat.forceTranslucent() || mat.sprite().contents().computeTransparency(0.0f, 0.0f, 1.0f, 1.0f).hasTranslucent()) {
				flags |= BakedQuad.FLAG_TRANSLUCENT;
			}
			if (mat.sprite().contents().isAnimated()) {
				flags |= BakedQuad.FLAG_ANIMATED;
			}
		}
		this.staticMaterialFlags = flags;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
		for (int dirIdx = 0; dirIdx < 6; dirIdx++) {
			Direction dir = BLOCK_FACINGS[dirIdx];
			if (cullTest.test(dir))
				continue;

			int texIdx = textureIndexFor(state, level, pos, BLOCK_SIDES[dirIdx]);
			emitter.square(dir, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f)
					.materialBake(materials[texIdx], MutableQuadView.BAKE_LOCK_UV)
					.emit();
		}
	}

	@Override
	public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		record Key(int d, int u, int n, int s, int w, int e) {
		}

		return new Key(
				textureIndexFor(state, level, pos, BLOCK_SIDES[0]),
				textureIndexFor(state, level, pos, BLOCK_SIDES[1]),
				textureIndexFor(state, level, pos, BLOCK_SIDES[2]),
				textureIndexFor(state, level, pos, BLOCK_SIDES[3]),
				textureIndexFor(state, level, pos, BLOCK_SIDES[4]),
				textureIndexFor(state, level, pos, BLOCK_SIDES[5])
		);
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
	public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
	}

	@Override
	public Material.Baked particleMaterial() {
		return materials[15];
	}

	@Override
	public @BakedQuad.MaterialFlags int materialFlags() {
		return staticMaterialFlags;
	}
}
