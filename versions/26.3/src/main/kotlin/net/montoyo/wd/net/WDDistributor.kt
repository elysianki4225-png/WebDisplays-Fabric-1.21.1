package net.montoyo.wd.net

import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Replaces Forge's PacketDistributor: picks which players a server-bound send reaches.
 */
object WDDistributor {
    @JvmField
    val PLAYER = PlayerSelector()

    @JvmField
    val NEAR = NearSelector()

    class TargetPoint(
        @JvmField val excluded: ServerPlayer?,
        @JvmField val x: Double,
        @JvmField val y: Double,
        @JvmField val z: Double,
        @JvmField val radius: Double,
        @JvmField val dimension: ResourceKey<Level>
    ) {
        constructor(
            x: Double,
            y: Double,
            z: Double,
            radius: Double,
            dimension: ResourceKey<Level>
        ) : this(null, x, y, z, radius, dimension)
    }

    fun interface Target {
        fun forEachPlayer(server: MinecraftServer, action: Consumer<ServerPlayer>)
    }

    class PlayerSelector {
        fun with(player: Supplier<ServerPlayer>): Target = Target { _, action ->
            val receiver = player.get()
            if (receiver != null) action.accept(receiver)
        }
    }

    class NearSelector {
        fun with(point: Supplier<TargetPoint>): Target = Target { server, action ->
            val tp = point.get()
            val radiusSq = tp.radius * tp.radius

            for (player in server.playerList.players) {
                if (player.level().dimension() != tp.dimension) continue
                if (player === tp.excluded) continue

                val dx = player.x - tp.x
                val dy = player.y - tp.y
                val dz = player.z - tp.z
                if (dx * dx + dy * dy + dz * dz > radiusSq) continue

                action.accept(player)
            }
        }
    }
}
