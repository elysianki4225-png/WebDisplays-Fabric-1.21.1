package com.terrapaver.network

import com.terrapaver.config.PavingConfig
import com.terrapaver.engine.ServerPavingManager
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

object TerraPaverNet {
    const val ACTION_TOGGLE_PAUSE = 0
    const val ACTION_CANCEL = 1
    const val ACTION_UNDO = 2
    const val ACTION_POP_WAYPOINT = 3
    const val ACTION_CLEAR_SELECTION = 4
    const val ACTION_START_SELECTED = 5

    data class C2SStartPave(
        val waypoints: List<BlockPos>,
        val configJson: String
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<C2SStartPave>(
                ResourceLocation.fromNamespaceAndPath("terrapaver", "start_pave")
            )
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, C2SStartPave> = StreamCodec.of(
                { buf, p ->
                    buf.writeVarInt(p.waypoints.size)
                    for (wp in p.waypoints) buf.writeBlockPos(wp)
                    buf.writeUtf(p.configJson, 32767)
                },
                { buf ->
                    val n = buf.readVarInt().coerceIn(0, 512)
                    val list = ArrayList<BlockPos>(n)
                    for (i in 0 until n) list.add(buf.readBlockPos())
                    C2SStartPave(list, buf.readUtf(32767))
                }
            )
        }
    }

    data class C2SPreviewRoute(
        val waypoints: List<BlockPos>,
        val configJson: String
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<C2SPreviewRoute>(
                ResourceLocation.fromNamespaceAndPath("terrapaver", "preview_route")
            )
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, C2SPreviewRoute> = StreamCodec.of(
                { buf, p ->
                    buf.writeVarInt(p.waypoints.size)
                    for (wp in p.waypoints) buf.writeBlockPos(wp)
                    buf.writeUtf(p.configJson, 32767)
                },
                { buf ->
                    val n = buf.readVarInt().coerceIn(0, 512)
                    val list = ArrayList<BlockPos>(n)
                    for (i in 0 until n) list.add(buf.readBlockPos())
                    C2SPreviewRoute(list, buf.readUtf(32767))
                }
            )
        }
    }

    data class C2SControlPave(val action: Int) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<C2SControlPave>(
                ResourceLocation.fromNamespaceAndPath("terrapaver", "control_pave")
            )
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, C2SControlPave> = StreamCodec.of(
                { buf, p -> buf.writeVarInt(p.action) },
                { buf -> C2SControlPave(buf.readVarInt()) }
            )
        }
    }

    data class C2SAddWaypoint(val pos: BlockPos) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<C2SAddWaypoint>(
                ResourceLocation.fromNamespaceAndPath("terrapaver", "add_waypoint")
            )
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, C2SAddWaypoint> = StreamCodec.of(
                { buf, p -> buf.writeBlockPos(p.pos) },
                { buf -> C2SAddWaypoint(buf.readBlockPos()) }
            )
        }
    }

    data class S2CPavingStatus(
        val active: Boolean,
        val paused: Boolean,
        val phase: Int,
        val progress: Float,
        val currentStep: Int,
        val totalSteps: Int,
        val placedBlocks: Int,
        val tunnelSegments: Int,
        val viaductSegments: Int,
        val statusKey: String,
        val headX: Int,
        val headY: Int,
        val headZ: Int
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            const val PHASE_IDLE = 0
            const val PHASE_SURVEYING = 1
            const val PHASE_PAVING = 2
            const val PHASE_DONE = 3

            val TYPE = CustomPacketPayload.Type<S2CPavingStatus>(
                ResourceLocation.fromNamespaceAndPath("terrapaver", "paving_status")
            )
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, S2CPavingStatus> = StreamCodec.of(
                { buf, p ->
                    buf.writeBoolean(p.active)
                    buf.writeBoolean(p.paused)
                    buf.writeVarInt(p.phase)
                    buf.writeFloat(p.progress)
                    buf.writeVarInt(p.currentStep)
                    buf.writeVarInt(p.totalSteps)
                    buf.writeVarInt(p.placedBlocks)
                    buf.writeVarInt(p.tunnelSegments)
                    buf.writeVarInt(p.viaductSegments)
                    buf.writeUtf(p.statusKey, 256)
                    buf.writeVarInt(p.headX)
                    buf.writeVarInt(p.headY)
                    buf.writeVarInt(p.headZ)
                },
                { buf ->
                    S2CPavingStatus(
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readVarInt(),
                        buf.readFloat(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readUtf(256),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt()
                    )
                }
            )
        }
    }

    data class S2CSyncSelection(
        val waypoints: List<BlockPos>,
        val canUndo: Boolean
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<S2CSyncSelection>(
                ResourceLocation.fromNamespaceAndPath("terrapaver", "sync_selection")
            )
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, S2CSyncSelection> = StreamCodec.of(
                { buf, p ->
                    buf.writeVarInt(p.waypoints.size)
                    for (wp in p.waypoints) buf.writeBlockPos(wp)
                    buf.writeBoolean(p.canUndo)
                },
                { buf ->
                    val n = buf.readVarInt().coerceIn(0, 512)
                    val list = ArrayList<BlockPos>(n)
                    for (i in 0 until n) list.add(buf.readBlockPos())
                    S2CSyncSelection(list, buf.readBoolean())
                }
            )
        }
    }

    data class S2CRoutePreview(
        val xs: IntArray,
        val ys: IntArray,
        val zs: IntArray,
        val kinds: ByteArray
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is S2CRoutePreview) return false
            return xs.contentEquals(other.xs) && ys.contentEquals(other.ys) && zs.contentEquals(other.zs) && kinds.contentEquals(other.kinds)
        }

        override fun hashCode(): Int {
            var r = xs.contentHashCode()
            r = 31 * r + ys.contentHashCode()
            r = 31 * r + zs.contentHashCode()
            r = 31 * r + kinds.contentHashCode()
            return r
        }

        companion object {
            val TYPE = CustomPacketPayload.Type<S2CRoutePreview>(
                ResourceLocation.fromNamespaceAndPath("terrapaver", "route_preview")
            )
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, S2CRoutePreview> = StreamCodec.of(
                { buf, p ->
                    val n = p.xs.size
                    buf.writeVarInt(n)
                    for (i in 0 until n) {
                        buf.writeVarInt(p.xs[i])
                        buf.writeVarInt(p.ys[i])
                        buf.writeVarInt(p.zs[i])
                        buf.writeByte(p.kinds[i].toInt())
                    }
                },
                { buf ->
                    val n = buf.readVarInt().coerceIn(0, 4096)
                    val xs = IntArray(n)
                    val ys = IntArray(n)
                    val zs = IntArray(n)
                    val kinds = ByteArray(n)
                    for (i in 0 until n) {
                        xs[i] = buf.readVarInt()
                        ys[i] = buf.readVarInt()
                        zs[i] = buf.readVarInt()
                        kinds[i] = buf.readByte()
                    }
                    S2CRoutePreview(xs, ys, zs, kinds)
                }
            )
        }
    }

    private var payloadsRegistered = false

    fun registerPayloadsAndServerReceivers() {
        if (!payloadsRegistered) {
            payloadsRegistered = true
            PayloadTypeRegistry.playC2S().register(C2SStartPave.TYPE, C2SStartPave.CODEC)
            PayloadTypeRegistry.playC2S().register(C2SPreviewRoute.TYPE, C2SPreviewRoute.CODEC)
            PayloadTypeRegistry.playC2S().register(C2SControlPave.TYPE, C2SControlPave.CODEC)
            PayloadTypeRegistry.playC2S().register(C2SAddWaypoint.TYPE, C2SAddWaypoint.CODEC)

            PayloadTypeRegistry.playS2C().register(S2CPavingStatus.TYPE, S2CPavingStatus.CODEC)
            PayloadTypeRegistry.playS2C().register(S2CSyncSelection.TYPE, S2CSyncSelection.CODEC)
            PayloadTypeRegistry.playS2C().register(S2CRoutePreview.TYPE, S2CRoutePreview.CODEC)
        }

        ServerPlayNetworking.registerGlobalReceiver(C2SStartPave.TYPE) { payload, ctx ->
            val player = ctx.player()
            ctx.server().execute {
                val cfg = PavingConfig.fromJson(payload.configJson)
                ServerPavingManager.startJob(player, payload.waypoints, cfg)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(C2SPreviewRoute.TYPE) { payload, ctx ->
            val player = ctx.player()
            ctx.server().execute {
                val cfg = PavingConfig.fromJson(payload.configJson)
                ServerPavingManager.sendQuickPreview(player, payload.waypoints, cfg)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(C2SControlPave.TYPE) { payload, ctx ->
            val player = ctx.player()
            ctx.server().execute {
                when (payload.action) {
                    ACTION_TOGGLE_PAUSE -> ServerPavingManager.togglePause(player)
                    ACTION_CANCEL -> ServerPavingManager.cancelJob(player)
                    ACTION_UNDO -> ServerPavingManager.undoLast(player)
                    ACTION_POP_WAYPOINT -> ServerPavingManager.popWaypoint(player)
                    ACTION_CLEAR_SELECTION -> ServerPavingManager.clearSelection(player)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(C2SAddWaypoint.TYPE) { payload, ctx ->
            val player = ctx.player()
            ctx.server().execute {
                ServerPavingManager.addWaypoint(player, payload.pos)
            }
        }
    }

    fun registerClientReceivers(
        onStatus: (S2CPavingStatus) -> Unit,
        onSelection: (S2CSyncSelection) -> Unit,
        onPreview: (S2CRoutePreview) -> Unit
    ) {
        ClientPlayNetworking.registerGlobalReceiver(S2CPavingStatus.TYPE) { payload, ctx ->
            ctx.client().execute { onStatus(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(S2CSyncSelection.TYPE) { payload, ctx ->
            ctx.client().execute { onSelection(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(S2CRoutePreview.TYPE) { payload, ctx ->
            ctx.client().execute { onPreview(payload) }
        }
    }

    fun sendToPlayer(player: ServerPlayer, payload: CustomPacketPayload) {
        ServerPlayNetworking.send(player, payload)
    }

    fun sendToServer(payload: CustomPacketPayload) {
        ClientPlayNetworking.send(payload)
    }
}
