package com.terrapaver

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.terrapaver.client.TerraPaverClientHooks
import com.terrapaver.engine.ServerBiomeManager
import com.terrapaver.engine.ServerPavingManager
import com.terrapaver.item.ItemBiomeEditor
import com.terrapaver.item.ItemPavingSurveyor
import com.terrapaver.network.TerraPaverNet
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object TerraPaverMod : ModInitializer {
    const val MOD_ID = "terrapaver"

    lateinit var PAVING_SURVEYOR: Item
        private set

    lateinit var BIOME_EDITOR: Item
        private set

    lateinit var CREATIVE_TAB: CreativeModeTab
        private set

    override fun onInitialize() {
        PAVING_SURVEYOR = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "paving_surveyor"),
            ItemPavingSurveyor(Item.Properties())
        )

        BIOME_EDITOR = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "biome_editor"),
            ItemBiomeEditor(Item.Properties())
        )

        CREATIVE_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "main"),
            FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.terrapaver"))
                .icon { ItemStack(PAVING_SURVEYOR) }
                .displayItems { _, output ->
                    output.accept(PAVING_SURVEYOR)
                    output.accept(BIOME_EDITOR)
                }
                .build()
        )

        TerraPaverNet.registerPayloadsAndServerReceivers()

        ServerTickEvents.END_SERVER_TICK.register { server ->
            ServerPavingManager.tick(server)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            ServerPavingManager.shutdown(server)
            ServerBiomeManager.shutdown(server)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            ServerPavingManager.onDisconnect(handler.player)
            ServerBiomeManager.onDisconnect(handler.player)
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("terrapaver")
                    .then(Commands.literal("pos1").executes { ctx ->
                        val player = ctx.source.playerOrException
                        ServerPavingManager.setStart(player, player.blockPosition().below())
                        1
                    })
                    .then(
                        Commands.literal("pos2")
                            .executes { ctx ->
                                val player = ctx.source.playerOrException
                                ServerPavingManager.setEndAndSync(player, player.blockPosition().below())
                                1
                            }
                            .then(
                                Commands.argument("x", IntegerArgumentType.integer())
                                    .then(
                                        Commands.argument("y", IntegerArgumentType.integer(-64, 320))
                                            .then(
                                                Commands.argument("z", IntegerArgumentType.integer())
                                                    .executes { ctx ->
                                                        val player = ctx.source.playerOrException
                                                        val x = IntegerArgumentType.getInteger(ctx, "x")
                                                        val y = IntegerArgumentType.getInteger(ctx, "y")
                                                        val z = IntegerArgumentType.getInteger(ctx, "z")
                                                        ServerPavingManager.setEndAndSync(player, BlockPos(x, y, z))
                                                        1
                                                    }
                                            )
                                    )
                            )
                    )
                    .then(Commands.literal("add").executes { ctx ->
                        val player = ctx.source.playerOrException
                        ServerPavingManager.addWaypoint(player, player.blockPosition().below())
                        1
                    })
                    .then(Commands.literal("pop").executes { ctx ->
                        ServerPavingManager.popWaypoint(ctx.source.playerOrException)
                        1
                    })
                    .then(Commands.literal("start").executes { ctx ->
                        ServerPavingManager.startSelectedJob(ctx.source.playerOrException, com.terrapaver.config.PavingConfig.clientConfig)
                        1
                    })
                    .then(Commands.literal("ahead")
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(16, 2500)).executes { ctx ->
                            val player = ctx.source.playerOrException
                            val dist = IntegerArgumentType.getInteger(ctx, "blocks")
                            ServerPavingManager.extendAhead(player, dist)
                            1
                        })
                    )
                    .then(Commands.literal("stop").executes { ctx ->
                        ServerPavingManager.cancelJob(ctx.source.playerOrException)
                        1
                    })
                    .then(Commands.literal("pause").executes { ctx ->
                        ServerPavingManager.togglePause(ctx.source.playerOrException)
                        1
                    })
                    .then(Commands.literal("undo").executes { ctx ->
                        ServerPavingManager.undoLast(ctx.source.playerOrException)
                        1
                    })
                    .then(Commands.literal("clear").executes { ctx ->
                        ServerPavingManager.clearSelection(ctx.source.playerOrException)
                        1
                    })
                    .then(Commands.literal("gui").executes { _ ->
                        TerraPaverClientHooks.openSettingsScreen()
                        1
                    })
            )
        }
    }
}
