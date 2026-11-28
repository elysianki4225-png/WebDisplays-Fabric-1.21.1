package com.terrapaver.client

import com.terrapaver.TerraPaverMod
import com.terrapaver.client.gui.M3Draw
import com.terrapaver.client.gui.M3Theme
import com.terrapaver.config.BiomeConfig
import com.terrapaver.config.BiomeShape
import com.terrapaver.config.BiomeSplitMode
import com.terrapaver.config.PavingConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

object BiomeHudOverlay {
    fun render(g: GuiGraphics, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val mc = Minecraft.getInstance()
        if (mc.screen != null) return
        val player = mc.player ?: return
        val level = mc.level ?: return

        val holdingBiome = player.mainHandItem.`is`(TerraPaverMod.BIOME_EDITOR) ||
            player.offhandItem.`is`(TerraPaverMod.BIOME_EDITOR)
        if (!holdingBiome) return

        val cfg = BiomeConfig.clientConfig
        val theme = M3Theme.get(PavingConfig.clientConfig.m3SeedTheme)
        val font = mc.font
        val screenW = mc.window.guiScaledWidth

        val targetName = formatBiomeName(cfg.targetBiome)
        val standingLoc = level.getBiome(player.blockPosition()).unwrapKey().orElse(null)?.location()
        val standingName = if (standingLoc != null) formatBiomeName(standingLoc.toString()) else "?"
        val dimLoc = if (cfg.targetDimension.isBlank()) level.dimension().location().toString() else cfg.targetDimension
        val dimShort = formatDimName(dimLoc)

        val shapeInfo = when (cfg.shape) {
            BiomeShape.RADIUS_BRUSH -> "${I18n.get(cfg.shape.key)} ${cfg.radius}m"
            BiomeShape.SQUARE_BRUSH -> "${I18n.get(cfg.shape.key)} ${cfg.radius}m"
            BiomeShape.CHUNK_GRID -> {
                val s = cfg.chunkRadius * 2 + 1
                I18n.get("terrapaver.biome.fmt.chunks", s, s)
            }
            BiomeShape.BOX_2POINT -> if (ClientBiomeState.hasCorner1) {
                val c = ClientBiomeState.corner1
                "${I18n.get(cfg.shape.key)} (${c.x},${c.z})"
            } else {
                I18n.get(cfg.shape.key)
            }
        }
        val splitSuffix = when (cfg.splitMode) {
            BiomeSplitMode.SINGLE -> ""
            BiomeSplitMode.SPLIT_X -> " · ${I18n.get(cfg.splitMode.key)} -> ${formatBiomeName(cfg.secondaryBiome)}"
            BiomeSplitMode.SPLIT_Z -> " · ${I18n.get(cfg.splitMode.key)} -> ${formatBiomeName(cfg.secondaryBiome)}"
            BiomeSplitMode.VORONOI_MIX -> " · ${I18n.get(cfg.splitMode.key)} (${cfg.splitPalette.size})"
        }

        val line1 = "$targetName$splitSuffix"
        val line2 = "$shapeInfo · $dimShort · ${I18n.get("terrapaver.biome.hud.standing", standingName)} · [B]"

        val cardW = (maxOf(font.width(line1), font.width(line2)) + 38).coerceIn(190, screenW - 24)
        val cardH = 34
        val x = (screenW - cardW) / 2
        val y = if (ClientPavingState.shouldShowHud()) 60 else 10

        M3Draw.drawElevationShadow(g, x, y, cardW, cardH, 14, 3)
        M3Draw.fillRoundedRect(g, x, y, cardW, cardH, 14, theme.surface)
        M3Draw.strokeRoundedRect(g, x, y, cardW, cardH, 14, theme.outlineVariant)

        val swatchColor = resolveBiomeSwatchColor(level, cfg.targetBiome)
        M3Draw.fillCircle(g, x + 14, y + 12, 5, swatchColor)
        g.drawString(font, M3Draw.fitText(font, line1, cardW - 32), x + 24, y + 8, theme.onSurface, false)
        g.drawString(font, M3Draw.fitText(font, line2, cardW - 22), x + 12, y + 20, theme.onSurfaceVariant, false)
    }

    private fun resolveBiomeSwatchColor(level: net.minecraft.world.level.Level, idStr: String): Int {
        val rl = ResourceLocation.tryParse(idStr) ?: return 0xFF1B8A5A.toInt()
        val reg = level.registryAccess().registryOrThrow(Registries.BIOME)
        val holder = reg.getHolder(ResourceKey.create(Registries.BIOME, rl)).orElse(null) ?: return 0xFF1B8A5A.toInt()
        return (holder.value().getGrassColor(0.0, 0.0) and 0xFFFFFF) or (0xFF shl 24)
    }

    private fun formatDimName(idStr: String): String {
        val rl = ResourceLocation.tryParse(idStr) ?: return idStr
        if (rl.namespace == "minecraft") {
            return when (rl.path) {
                "overworld" -> I18n.get("terrapaver.biome.dim.overworld")
                "the_nether" -> I18n.get("terrapaver.biome.dim.the_nether")
                "the_end" -> I18n.get("terrapaver.biome.dim.the_end")
                else -> rl.path.replace('_', ' ')
            }
        }
        return "${rl.namespace}:${rl.path}"
    }

    private fun formatBiomeName(idStr: String): String {
        val rl = ResourceLocation.tryParse(idStr) ?: return idStr
        val k = "biome.${rl.namespace}.${rl.path}"
        val tr = I18n.get(k)
        return if (tr == k) rl.path.replace('_', ' ') else tr
    }
}
