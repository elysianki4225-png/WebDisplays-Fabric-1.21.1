/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.config.CommonConfig;
import net.montoyo.wd.core.CraftComponent;
import net.montoyo.wd.net.WDNetworkRegistry;
import net.montoyo.wd.net.server_bound.C2SMessageMinepadUrl;
import net.montoyo.wd.utilities.NbtCompat;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public class ItemMinePad2 extends Item implements WDItem, IItemEntityTickHandler {

	public ItemMinePad2(Properties properties) {
		super(properties
				.stacksTo(1)
				.durability(0)
		);
	}

	private static String getURL(ItemStack is) {
		CustomData data = is.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.copyTag().contains("PadURL"))
			return CommonConfig.Browser.homepage;
		else
			return data.copyTag().getStringOr("PadURL", CommonConfig.Browser.homepage);
	}

	@Override
	@NotNull
	public InteractionResult use(Level world, Player ply, @NotNull InteractionHand hand) {
		ItemStack is = ply.getItemInHand(hand);
		CompoundTag tag = is.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

		if (ply.isShiftKeyDown()) {
			if (world.isClientSide())
				WebDisplays.PROXY.displaySetPadURLGui(is, getURL(is));
		} else if (NbtCompat.hasUUID(tag, "PadID")) {
			if (world.isClientSide())
				WebDisplays.PROXY.openMinePadGui(NbtCompat.getUUID(tag, "PadID"));
		} else {
			UUID uuid = UUID.randomUUID();
			String url = getURL(is);
			WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageMinepadUrl(uuid, url));
			NbtCompat.putUUID(tag, "PadID", uuid);
			is.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean onEntityItemUpdate(ItemStack stack, ItemEntity ent) {
		if (ent.onGround() && !ent.level().isClientSide()) {
			CustomData data = ent.getItem().get(DataComponents.CUSTOM_DATA);
			CompoundTag tag = data == null ? null : data.copyTag();

			if (tag != null && tag.contains("ThrowHeight")) {
				double height = tag.getDoubleOr("ThrowHeight", 0.0);
				UUID thrower = null;

				if (tag.contains("ThrowerMSB") && tag.contains("ThrowerLSB"))
					thrower = new UUID(tag.getLongOr("ThrowerMSB", 0L), tag.getLongOr("ThrowerLSB", 0L));

				if (NbtCompat.hasUUID(tag, "PadID") || tag.contains("PadURL")) {
					tag.remove("ThrowerMSB");
					tag.remove("ThrowerLSB");
					tag.remove("ThrowHeight");
					ent.getItem().set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				} else
					ent.getItem().remove(DataComponents.CUSTOM_DATA);

				if (thrower != null && height - ent.getBlockY() >= 20.0) {
					ent.level().playSound(null, ent.getBlockX(), ent.getBlockY(), ent.getBlockZ(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 4.0f, 1.0f);
					ent.level().addFreshEntity(new ItemEntity(ent.level(), ent.getBlockX(), ent.getBlockY(), ent.getBlockZ(), CraftComponent.EXTCARD.makeItemStack()));
					ent.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);

					Player ply = ent.level().getPlayerByUUID(thrower);
					if (ply instanceof ServerPlayer sp)
						WebDisplays.INSTANCE.criterionPadBreak.trigger(sp);
				}
			}
		}

		return false;
	}

	@Nullable
	@Override
	public String getWikiName(@NotNull ItemStack is) {
		return is.getHoverName().getString();
	}

}
