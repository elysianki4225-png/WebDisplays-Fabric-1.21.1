package com.terrapaver.client.gui

import com.terrapaver.client.ClientPavingState
import com.terrapaver.config.AlignmentMode
import com.terrapaver.config.PavingConfig
import com.terrapaver.config.PavingMode
import com.terrapaver.config.RiverStrategy
import com.terrapaver.config.RoadStyle
import com.terrapaver.config.TunnelShape
import com.terrapaver.config.TunnelWallStyle
import com.terrapaver.network.TerraPaverNet
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class M3PavingScreen : Screen(Component.translatable("terrapaver.gui.title")) {
    private enum class ButtonStyle { FILLED, TONAL, OUTLINED, DANGER }

    private data class ClickRegion(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val onClick: () -> Unit
    )

    private data class SliderRegion(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val minVal: Int,
        val maxVal: Int,
        val current: () -> Int,
        val onChange: (Int) -> Unit
    )

    private var cfg: PavingConfig = PavingConfig.loadClient().copySanitized()
    private var activeTab = 0

    private val clickRegions = ArrayList<ClickRegion>()
    private val sliderRegions = ArrayList<SliderRegion>()
    private var draggingSlider: SliderRegion? = null

    private var winX = 0
    private var winY = 0
    private var winW = 0
    private var winH = 0

    override fun init() {
        super.init()
        winW = min(560, width - 24).coerceAtLeast(380)
        winH = min(376, height - 18).coerceAtLeast(256)
        winX = (width - winW) / 2
        winY = (height - winH) / 2
    }

    override fun isPauseScreen(): Boolean = false

    private fun save() {
        PavingConfig.saveClient(cfg)
    }

    private fun theme(): M3Palette = M3Theme.get(cfg.m3SeedTheme)

    override fun renderBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Subtle scrim behind M3 floating dialog
        g.fill(0, 0, width, height, 0x660B1018)
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(g, mouseX, mouseY, partialTick)
        clickRegions.clear()
        sliderRegions.clear()

        val t = theme()

        // Main M3 Light Sheet
        M3Draw.drawElevationShadow(g, winX, winY, winW, winH, 20, 4)
        M3Draw.fillRoundedRect(g, winX, winY, winW, winH, 20, t.surface)
        M3Draw.strokeRoundedRect(g, winX, winY, winW, winH, 20, t.outlineVariant)

        renderTopAppBar(g, mouseX, mouseY, t)
        renderNavigationRail(g, mouseX, mouseY, t)

        val contentX = winX + 86
        val contentY = winY + 52
        val contentW = winW - 98
        val contentH = winH - 64

        when (activeTab) {
            0 -> renderOverviewTab(g, mouseX, mouseY, contentX, contentY, contentW, contentH, t)
            1 -> renderRoadRailTab(g, mouseX, mouseY, contentX, contentY, contentW, contentH, t)
            2 -> renderTunnelBridgeTab(g, mouseX, mouseY, contentX, contentY, contentW, contentH, t)
            3 -> renderEngineTab(g, mouseX, mouseY, contentX, contentY, contentW, contentH, t)
        }

        super.render(g, mouseX, mouseY, partialTick)
    }

    private fun renderTopAppBar(g: GuiGraphics, mouseX: Int, mouseY: Int, t: M3Palette) {
        val barH = 44
        M3Draw.fillRoundedRect(g, winX + 4, winY + 4, winW - 8, barH, 16, t.surfaceLow)

        // Accent brand badge
        M3Draw.fillRoundedRect(g, winX + 16, winY + 13, 24, 24, 8, t.primaryContainer)
        M3Draw.fillRoundedRect(g, winX + 22, winY + 19, 12, 12, 4, t.primary)

        g.drawString(font, I18n.get("terrapaver.gui.title"), winX + 48, winY + 14, t.onSurface, false)
        g.drawString(font, I18n.get("terrapaver.gui.subtitle"), winX + 48, winY + 26, t.onSurfaceVariant, false)

        // 4 M3 Dynamic Color Seed Chips
        val seedColors = intArrayOf(0xFF0B57D0.toInt(), 0xFF6750A4.toInt(), 0xFF006A60.toInt(), 0xFF984716.toInt())
        val seedStartX = winX + winW - 190
        for (i in seedColors.indices) {
            val sx = seedStartX + i * 20
            val sy = winY + 18
            if (cfg.m3SeedTheme == i) {
                M3Draw.fillCircle(g, sx + 7, sy + 7, 9, t.primaryContainer)
            }
            M3Draw.fillCircle(g, sx + 7, sy + 7, 6, seedColors[i])
            clickRegions.add(ClickRegion(sx, sy, 15, 15) {
                cfg.m3SeedTheme = i
                save()
            })
        }

        // Primary Save & Close pill
        drawPillButton(
            g, mouseX, mouseY,
            winX + winW - 98, winY + 13, 84, 24,
            I18n.get("terrapaver.gui.done"),
            ButtonStyle.FILLED, t, true
        ) {
            save()
            onClose()
        }
    }

    private fun renderNavigationRail(g: GuiGraphics, mouseX: Int, mouseY: Int, t: M3Palette) {
        val railX = winX + 10
        val railY = winY + 52
        val railW = 68
        val railH = winH - 64
        M3Draw.fillRoundedRect(g, railX, railY, railW, railH, 16, t.surfaceLow)

        val tabs = arrayOf(
            "terrapaver.tab.overview" to "AI",
            "terrapaver.tab.road" to "R/T",
            "terrapaver.tab.tunnel" to "T/B",
            "terrapaver.tab.engine" to "SYS"
        )

        for (i in tabs.indices) {
            val iy = railY + 12 + i * 60
            val selected = activeTab == i
            val hovered = mouseX in (railX + 6)..(railX + railW - 6) && mouseY in iy..(iy + 48)

            // Active pill indicator
            val pillColor = when {
                selected -> t.secondaryContainer
                hovered -> M3Theme.withAlpha(t.onSurface, 18)
                else -> 0x00000000
            }
            if (pillColor != 0) {
                M3Draw.fillRoundedRect(g, railX + 10, iy + 4, railW - 20, 24, 12, pillColor)
            }

            val iconColor = if (selected) t.onSecondaryContainer else t.onSurfaceVariant
            val badgeW = font.width(tabs[i].second)
            g.drawString(font, tabs[i].second, railX + (railW - badgeW) / 2, iy + 12, iconColor, false)

            val label = I18n.get(tabs[i].first)
            val lw = font.width(label)
            g.drawString(
                font,
                label,
                railX + (railW - lw) / 2,
                iy + 32,
                if (selected) t.primary else t.onSurfaceVariant,
                false
            )

            clickRegions.add(ClickRegion(railX + 6, iy, railW - 12, 48) {
                activeTab = i
            })
        }
    }

    private fun renderOverviewTab(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int, h: Int, t: M3Palette
    ) {
        val leftW = (w * 0.56).toInt()
        val rightX = x + leftW + 10
        val rightW = w - leftW - 10

        // Card 1: Route Anchors & Quick Actions
        M3Draw.drawCard(g, x, y, leftW, 134, t, elevated = true)
        g.drawString(font, I18n.get("terrapaver.overview.route_title"), x + 12, y + 10, t.onSurface, false)

        val startStr = if (ClientPavingState.hasStart) {
            val p = ClientPavingState.startPos
            "${p.x}, ${p.y}, ${p.z}"
        } else I18n.get("terrapaver.overview.unset")

        val endStr = if (ClientPavingState.hasEnd) {
            val p = ClientPavingState.endPos
            "${p.x}, ${p.y}, ${p.z}"
        } else I18n.get("terrapaver.overview.unset")

        g.drawString(font, "${I18n.get("terrapaver.overview.start")}: $startStr", x + 12, y + 27, t.onSurfaceVariant, false)
        g.drawString(font, "${I18n.get("terrapaver.overview.end")}: $endStr", x + 12, y + 40, t.onSurfaceVariant, false)

        if (ClientPavingState.hasStart && ClientPavingState.hasEnd) {
            val d = hypot(
                (ClientPavingState.endPos.x - ClientPavingState.startPos.x).toDouble(),
                (ClientPavingState.endPos.z - ClientPavingState.startPos.z).toDouble()
            ).roundToInt()
            g.drawString(font, I18n.get("terrapaver.overview.distance", d), x + 12, y + 54, t.primary, false)
        } else {
            g.drawString(font, I18n.get("terrapaver.overview.hint_extend"), x + 12, y + 54, t.onSurfaceVariant, false)
        }

        // Quick Extend Ahead pills (+100m, +250m, +500m)
        val btnW = (leftW - 36) / 3
        val dists = intArrayOf(100, 250, 500)
        for (i in dists.indices) {
            val d = dists[i]
            drawPillButton(
                g, mouseX, mouseY,
                x + 12 + i * (btnW + 6), y + 70, btnW, 22,
                "+${d}m",
                ButtonStyle.TONAL, t, true
            ) {
                extendAheadClient(d)
            }
        }

        // Main Action Row: Start / Pause / Cancel / Undo
        val st = ClientPavingState.status
        val isActive = st?.active == true
        val canStart = ClientPavingState.hasStart && ClientPavingState.hasEnd
        val halfActW = (leftW - 30) / 2

        if (!isActive) {
            drawPillButton(
                g, mouseX, mouseY,
                x + 12, y + 100, halfActW, 24,
                I18n.get("terrapaver.action.start"),
                ButtonStyle.FILLED, t, canStart
            ) {
                save()
                TerraPaverNet.sendToServer(
                    TerraPaverNet.C2SStartPave(
                        ClientPavingState.startPos,
                        ClientPavingState.endPos,
                        cfg.toJson()
                    )
                )
            }
        } else {
            val pauseText = I18n.get(if (st?.paused == true) "terrapaver.action.resume" else "terrapaver.action.pause")
            drawPillButton(
                g, mouseX, mouseY,
                x + 12, y + 100, halfActW, 24,
                pauseText,
                ButtonStyle.TONAL, t, true
            ) {
                TerraPaverNet.sendToServer(TerraPaverNet.C2SControlPave(TerraPaverNet.ACTION_TOGGLE_PAUSE))
            }
        }

        if (isActive) {
            drawPillButton(
                g, mouseX, mouseY,
                x + 18 + halfActW, y + 100, halfActW, 24,
                I18n.get("terrapaver.action.cancel"),
                ButtonStyle.DANGER, t, true
            ) {
                TerraPaverNet.sendToServer(TerraPaverNet.C2SControlPave(TerraPaverNet.ACTION_CANCEL))
            }
        } else {
            drawPillButton(
                g, mouseX, mouseY,
                x + 18 + halfActW, y + 100, halfActW, 24,
                I18n.get("terrapaver.action.undo"),
                ButtonStyle.OUTLINED, t, ClientPavingState.canUndo
            ) {
                TerraPaverNet.sendToServer(TerraPaverNet.C2SControlPave(TerraPaverNet.ACTION_UNDO))
            }
        }

        // Card 2: Live Progress & Telemetry Card
        val progY = y + 144
        val progH = h - 144
        M3Draw.drawCard(g, x, progY, leftW, progH, t, elevated = true)
        g.drawString(font, I18n.get("terrapaver.overview.progress_title"), x + 12, progY + 10, t.onSurface, false)

        val statusLabel = if (st != null) I18n.get(st.statusKey) else I18n.get("terrapaver.status.ready")
        val pct = if (st != null) (st.progress * 100f).roundToInt() else 0
        g.drawString(font, "$statusLabel ($pct%)", x + 12, progY + 28, t.primary, false)

        M3Draw.drawLinearProgress(
            g, x + 12, progY + 44, leftW - 24, 8,
            st?.progress ?: 0f,
            t,
            indeterminate = (st?.active == true && st.phase == TerraPaverNet.S2CPavingStatus.PHASE_SURVEYING && st.progress < 0.05f)
        )

        val placed = st?.placedBlocks ?: 0
        val tunnels = st?.tunnelSegments ?: 0
        val viaducts = st?.viaductSegments ?: 0
        g.drawString(
            font,
            I18n.get("terrapaver.overview.stats", placed, tunnels, viaducts),
            x + 12, progY + 60, t.onSurfaceVariant, false
        )

        val modeHint = if (cfg.pavingMode == PavingMode.CREATE_RAIL) {
            I18n.get("terrapaver.overview.create_note")
        } else {
            I18n.get("terrapaver.overview.road_note")
        }
        g.drawString(font, modeHint, x + 12, progY + 76, t.tertiary, false)

        drawPillButton(
            g, mouseX, mouseY,
            x + 12, progY + progH - 30, leftW - 24, 22,
            I18n.get("terrapaver.action.clear"),
            ButtonStyle.OUTLINED, t, ClientPavingState.hasStart || ClientPavingState.hasEnd
        ) {
            TerraPaverNet.sendToServer(TerraPaverNet.C2SControlPave(TerraPaverNet.ACTION_CLEAR_SELECTION))
        }

        // Right Column: Live Cross-Section Preview + Engineering Summary
        M3Draw.drawCard(g, rightX, y, rightW, h, t, elevated = true)
        M3Draw.drawCrossSectionPreview(
            g, font,
            rightX + 8, y + 8, rightW - 16, h - 76,
            cfg, t, I18n.get("terrapaver.preview.cross_section")
        )

        val sy = y + h - 60
        g.drawString(font, "${I18n.get("terrapaver.cfg.mode")}: ${I18n.get(cfg.pavingMode.key)}", rightX + 12, sy, t.onSurface, false)
        g.drawString(font, "${I18n.get("terrapaver.cfg.river")}: ${I18n.get(cfg.riverStrategy.key)}", rightX + 12, sy + 14, t.onSurfaceVariant, false)
        g.drawString(font, "${I18n.get("terrapaver.cfg.align")}: ${I18n.get(cfg.alignmentMode.key)}", rightX + 12, sy + 28, t.onSurfaceVariant, false)
        g.drawString(font, "Max Grade: 1:${cfg.maxSlopeRun}", rightX + 12, sy + 42, t.primary, false)
    }

    private fun renderRoadRailTab(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int, h: Int, t: M3Palette
    ) {
        M3Draw.drawCard(g, x, y, w, h, t, elevated = true)
        var cy = y + 12

        g.drawString(font, I18n.get("terrapaver.cfg.mode"), x + 14, cy, t.onSurface, false)
        cy += 14
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 24,
            listOf(I18n.get(PavingMode.ROAD.key), I18n.get(PavingMode.CREATE_RAIL.key)),
            if (cfg.pavingMode == PavingMode.ROAD) 0 else 1,
            t
        ) { idx ->
            cfg.pavingMode = if (idx == 0) PavingMode.ROAD else PavingMode.CREATE_RAIL
            if (cfg.pavingMode == PavingMode.CREATE_RAIL) {
                cfg.roadStyle = RoadStyle.CREATE_BALLAST
            } else if (cfg.roadStyle == RoadStyle.CREATE_BALLAST) {
                cfg.roadStyle = RoadStyle.ASPHALT
            }
            save()
        }
        cy += 34

        g.drawString(font, I18n.get("terrapaver.cfg.style"), x + 14, cy, t.onSurface, false)
        cy += 14
        val styles = RoadStyle.entries
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 22,
            styles.map { I18n.get(it.key) },
            styles.indexOf(cfg.roadStyle),
            t
        ) { idx ->
            cfg.roadStyle = styles[idx]
            save()
        }
        cy += 32

        cy = drawSliderRow(
            g, mouseX, mouseY, x + 14, cy, w - 28,
            I18n.get("terrapaver.cfg.road_width"),
            3, 15, { cfg.roadWidth }, "${cfg.roadWidth}m", t
        ) { v -> cfg.roadWidth = v; save() }

        cy = drawSliderRow(
            g, mouseX, mouseY, x + 14, cy, w - 28,
            I18n.get("terrapaver.cfg.max_slope"),
            2, 8, { cfg.maxSlopeRun }, "1:${cfg.maxSlopeRun}", t
        ) { v -> cfg.maxSlopeRun = v; save() }

        g.drawString(font, I18n.get("terrapaver.cfg.align"), x + 14, cy, t.onSurface, false)
        cy += 14
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 22,
            listOf(I18n.get(AlignmentMode.TERRAIN_SMART.key), I18n.get(AlignmentMode.DIRECT.key)),
            if (cfg.alignmentMode == AlignmentMode.TERRAIN_SMART) 0 else 1,
            t
        ) { idx ->
            cfg.alignmentMode = if (idx == 0) AlignmentMode.TERRAIN_SMART else AlignmentMode.DIRECT
            save()
        }
        cy += 32

        val colW = (w - 34) / 2
        drawSwitchTile(g, mouseX, mouseY, x + 14, cy, colW, I18n.get("terrapaver.cfg.road_lights"), cfg.addRoadLights, t) {
            cfg.addRoadLights = !cfg.addRoadLights; save()
        }
        drawSwitchTile(g, mouseX, mouseY, x + 20 + colW, cy, colW, I18n.get("terrapaver.cfg.rail_lights"), cfg.addRailLights, t) {
            cfg.addRailLights = !cfg.addRailLights; save()
        }
        cy += 32
        drawSwitchTile(g, mouseX, mouseY, x + 14, cy, colW, I18n.get("terrapaver.cfg.guardrails"), cfg.addGuardrails, t) {
            cfg.addGuardrails = !cfg.addGuardrails; save()
        }
        drawSwitchTile(g, mouseX, mouseY, x + 20 + colW, cy, colW, I18n.get("terrapaver.cfg.markings"), cfg.addLaneMarkings, t) {
            cfg.addLaneMarkings = !cfg.addLaneMarkings; save()
        }
    }

    private fun renderTunnelBridgeTab(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int, h: Int, t: M3Palette
    ) {
        M3Draw.drawCard(g, x, y, w, h, t, elevated = true)
        var cy = y + 10
        val halfW = (w - 36) / 2

        // Auto-adapt tunnel diameter switch
        drawSwitchTile(
            g, mouseX, mouseY, x + 14, cy, w - 28,
            I18n.get("terrapaver.cfg.auto_tunnel", cfg.effectiveTunnelWidth(), cfg.effectiveTunnelHeight()),
            cfg.autoTunnelSize, t
        ) {
            cfg.autoTunnelSize = !cfg.autoTunnelSize
            save()
        }
        cy += 32

        if (!cfg.autoTunnelSize) {
            drawSliderRow(
                g, mouseX, mouseY, x + 14, cy, halfW,
                I18n.get("terrapaver.cfg.tunnel_width"),
                5, 21, { cfg.tunnelWidth }, "${cfg.tunnelWidth}m", t
            ) { v -> cfg.tunnelWidth = v; save() }
            drawSliderRow(
                g, mouseX, mouseY, x + 22 + halfW, cy, halfW,
                I18n.get("terrapaver.cfg.tunnel_height"),
                4, 15, { cfg.tunnelHeight }, "${cfg.tunnelHeight}m", t
            ) { v -> cfg.tunnelHeight = v; save() }
            cy += 30
        } else {
            M3Draw.fillRoundedRect(g, x + 14, cy, w - 28, 24, 10, t.primaryContainer)
            g.drawString(
                font,
                I18n.get("terrapaver.cfg.auto_tunnel_info", cfg.effectiveTunnelWidth(), cfg.effectiveTunnelHeight()),
                x + 24, cy + 8, t.onPrimaryContainer, false
            )
            cy += 30
        }

        // Tunnel Walls + Tunnel Lights switches
        drawSwitchTile(
            g, mouseX, mouseY, x + 14, cy, halfW,
            I18n.get("terrapaver.cfg.tunnel_walls"),
            cfg.addTunnelWalls, t
        ) {
            cfg.addTunnelWalls = !cfg.addTunnelWalls
            save()
        }
        drawSwitchTile(
            g, mouseX, mouseY, x + 22 + halfW, cy, halfW,
            I18n.get("terrapaver.cfg.tunnel_lights"),
            cfg.addTunnelLights, t
        ) {
            cfg.addTunnelLights = !cfg.addTunnelLights
            save()
        }
        cy += 32

        g.drawString(font, I18n.get("terrapaver.cfg.tunnel_wall_style"), x + 14, cy, t.onSurface, false)
        cy += 12
        val wallStyles = TunnelWallStyle.entries
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 21,
            wallStyles.map { I18n.get(it.key) },
            wallStyles.indexOf(cfg.tunnelWallStyle),
            t
        ) { idx ->
            cfg.tunnelWallStyle = wallStyles[idx]
            save()
        }
        cy += 27

        g.drawString(font, I18n.get("terrapaver.cfg.tunnel_shape"), x + 14, cy, t.onSurface, false)
        cy += 12
        val shapes = TunnelShape.entries
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 21,
            shapes.map { I18n.get(it.key) },
            shapes.indexOf(cfg.tunnelShape),
            t
        ) { idx ->
            cfg.tunnelShape = shapes[idx]
            save()
        }
        cy += 27

        g.drawString(font, I18n.get("terrapaver.cfg.river"), x + 14, cy, t.onSurface, false)
        cy += 12
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 21,
            listOf(I18n.get(RiverStrategy.AUTO_VIADUCT.key), I18n.get(RiverStrategy.SHIELD_TUNNEL.key)),
            if (cfg.riverStrategy == RiverStrategy.AUTO_VIADUCT) 0 else 1,
            t
        ) { idx ->
            cfg.riverStrategy = if (idx == 0) RiverStrategy.AUTO_VIADUCT else RiverStrategy.SHIELD_TUNNEL
            save()
        }
        cy += 28

        drawSliderRow(
            g, mouseX, mouseY, x + 14, cy, halfW,
            I18n.get("terrapaver.cfg.tunnel_light_interval"),
            4, 16, { cfg.tunnelLightInterval }, "${cfg.tunnelLightInterval}m", t
        ) { v -> cfg.tunnelLightInterval = v; save() }

        drawSliderRow(
            g, mouseX, mouseY, x + 22 + halfW, cy, halfW,
            I18n.get("terrapaver.cfg.pillar_spacing"),
            4, 16, { cfg.viaductPillarSpacing }, "${cfg.viaductPillarSpacing}m", t
        ) { v -> cfg.viaductPillarSpacing = v; save() }
    }

    private fun renderEngineTab(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int, h: Int, t: M3Palette
    ) {
        M3Draw.drawCard(g, x, y, w, h, t, elevated = true)
        var cy = y + 12

        cy = drawSliderRow(
            g, mouseX, mouseY, x + 14, cy, w - 28,
            I18n.get("terrapaver.cfg.speed"),
            24, 512, { cfg.blocksPerTick }, "${cfg.blocksPerTick}/tick", t
        ) { v -> cfg.blocksPerTick = v; save() }

        cy = drawSliderRow(
            g, mouseX, mouseY, x + 14, cy, w - 28,
            I18n.get("terrapaver.cfg.light_interval"),
            6, 24, { cfg.lightInterval }, "${cfg.lightInterval}m", t
        ) { v -> cfg.lightInterval = v; save() }

        cy = drawSliderRow(
            g, mouseX, mouseY, x + 14, cy, w - 28,
            I18n.get("terrapaver.cfg.cut_threshold"),
            2, 12, { cfg.tunnelCutThreshold }, "${cfg.tunnelCutThreshold}m", t
        ) { v -> cfg.tunnelCutThreshold = v; save() }

        drawSwitchTile(
            g, mouseX, mouseY, x + 14, cy, w - 28,
            I18n.get("terrapaver.cfg.unloaded_chunks"),
            cfg.enableUnloadedChunks, t
        ) {
            cfg.enableUnloadedChunks = !cfg.enableUnloadedChunks
            save()
        }
        cy += 36

        drawSwitchTile(
            g, mouseX, mouseY, x + 14, cy, w - 28,
            I18n.get("terrapaver.cfg.route_preview"),
            cfg.showRoutePreview, t
        ) {
            cfg.showRoutePreview = !cfg.showRoutePreview
            save()
        }
        cy += 36

        drawSwitchTile(
            g, mouseX, mouseY, x + 14, cy, w - 28,
            I18n.get("terrapaver.cfg.drop_blocks"),
            cfg.dropExcavatedBlocks, t
        ) {
            cfg.dropExcavatedBlocks = !cfg.dropExcavatedBlocks
            save()
        }
        cy += 42

        drawPillButton(
            g, mouseX, mouseY,
            x + 14, cy, 140, 24,
            I18n.get("terrapaver.action.reset"),
            ButtonStyle.OUTLINED, t, true
        ) {
            val themeKeep = cfg.m3SeedTheme
            cfg = PavingConfig(m3SeedTheme = themeKeep)
            save()
        }
    }

    private fun drawPillButton(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int, h: Int,
        label: String, style: ButtonStyle, t: M3Palette, enabled: Boolean,
        onClick: () -> Unit
    ) {
        val hovered = enabled && mouseX in x..(x + w) && mouseY in y..(y + h)
        val r = h / 2

        val bg = when {
            !enabled -> M3Theme.withAlpha(t.onSurface, 26)
            style == ButtonStyle.FILLED -> if (hovered) M3Theme.blend(t.primary, 0xFF000000.toInt(), 0.12f) else t.primary
            style == ButtonStyle.TONAL -> if (hovered) M3Theme.blend(t.secondaryContainer, t.primary, 0.12f) else t.secondaryContainer
            style == ButtonStyle.DANGER -> if (hovered) M3Theme.blend(t.errorContainer, t.error, 0.12f) else t.errorContainer
            else -> if (hovered) M3Theme.withAlpha(t.primary, 20) else 0x00000000
        }
        val fg = when {
            !enabled -> M3Theme.withAlpha(t.onSurface, 100)
            style == ButtonStyle.FILLED -> t.onPrimary
            style == ButtonStyle.TONAL -> t.onSecondaryContainer
            style == ButtonStyle.DANGER -> t.onErrorContainer
            else -> t.primary
        }

        if (bg != 0) {
            M3Draw.fillRoundedRect(g, x, y, w, h, r, bg)
        }
        if (style == ButtonStyle.OUTLINED) {
            M3Draw.strokeRoundedRect(g, x, y, w, h, r, if (enabled) t.outline else t.outlineVariant)
        }

        val tw = font.width(label)
        g.drawString(font, label, x + (w - tw) / 2, y + (h - 8) / 2, fg, false)
        if (enabled) {
            clickRegions.add(ClickRegion(x, y, w, h, onClick))
        }
    }

    private fun drawSegmentedRow(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int, h: Int,
        labels: List<String>, selectedIndex: Int, t: M3Palette,
        onSelect: (Int) -> Unit
    ) {
        val r = h / 2
        M3Draw.fillRoundedRect(g, x, y, w, h, r, t.surfaceLow)
        M3Draw.strokeRoundedRect(g, x, y, w, h, r, t.outline)
        val count = labels.size.coerceAtLeast(1)
        val segW = w / count

        for (i in 0 until count) {
            val sx = x + i * segW
            val sw = if (i == count - 1) (x + w - sx) else segW
            val active = i == selectedIndex
            val hovered = mouseX in sx..(sx + sw) && mouseY in y..(y + h)
            if (active) {
                M3Draw.fillRoundedRect(g, sx + 1, y + 1, sw - 2, h - 2, max(2, r - 1), t.secondaryContainer)
            } else if (hovered) {
                M3Draw.fillRoundedRect(g, sx + 1, y + 1, sw - 2, h - 2, max(2, r - 1), M3Theme.withAlpha(t.primary, 18))
            }
            if (i > 0) {
                g.fill(sx, y + 2, sx + 1, y + h - 2, t.outlineVariant)
            }
            val text = if (active) "✓ ${labels[i]}" else labels[i]
            val tw = font.width(text)
            g.drawString(
                font, text,
                sx + (sw - tw) / 2, y + (h - 8) / 2,
                if (active) t.onSecondaryContainer else t.onSurface,
                false
            )
            clickRegions.add(ClickRegion(sx, y, sw, h) { onSelect(i) })
        }
    }

    private fun drawSwitchTile(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int,
        label: String, checked: Boolean, t: M3Palette,
        onToggle: () -> Unit
    ) {
        val h = 28
        val hovered = mouseX in x..(x + w) && mouseY in y..(y + h)
        val bg = if (hovered) t.surfaceContainer else t.surfaceLow
        M3Draw.fillRoundedRect(g, x, y, w, h, 10, bg)
        g.drawString(font, label, x + 8, y + 10, t.onSurface, false)

        val swW = 34
        val swH = 18
        val swX = x + w - swW - 8
        val swY = y + 5
        M3Draw.fillRoundedRect(g, swX, swY, swW, swH, 9, if (checked) t.primary else t.surfaceHigh)
        if (!checked) {
            M3Draw.strokeRoundedRect(g, swX, swY, swW, swH, 9, t.outline)
        }
        val thumbR = if (checked) 6 else 5
        val thumbCx = if (checked) swX + swW - 9 else swX + 9
        M3Draw.fillCircle(g, thumbCx, swY + 9, thumbR, if (checked) t.onPrimary else t.outline)

        clickRegions.add(ClickRegion(x, y, w, h, onToggle))
    }

    private fun drawSliderRow(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int,
        label: String, minVal: Int, maxVal: Int,
        getter: () -> Int, valueText: String, t: M3Palette,
        setter: (Int) -> Unit
    ): Int {
        g.drawString(font, label, x, y, t.onSurface, false)
        val vw = font.width(valueText)
        M3Draw.fillRoundedRect(g, x + w - vw - 12, y - 2, vw + 12, 13, 6, t.primaryContainer)
        g.drawString(font, valueText, x + w - vw - 6, y + 1, t.onPrimaryContainer, false)

        val trackY = y + 15
        val trackH = 6
        val cur = getter().coerceIn(minVal, maxVal)
        val frac = (cur - minVal).toFloat() / max(1, maxVal - minVal).toFloat()

        M3Draw.fillRoundedRect(g, x, trackY, w, trackH, 3, t.surfaceHigh)
        val activeW = (w * frac).roundToInt().coerceAtLeast(trackH)
        M3Draw.fillRoundedRect(g, x, trackY, activeW, trackH, 3, t.primary)

        // M3 pill thumb handle
        val thumbX = (x + w * frac).roundToInt().coerceIn(x + 3, x + w - 3)
        M3Draw.fillRoundedRect(g, thumbX - 3, trackY - 4, 6, 14, 3, t.primary)

        sliderRegions.add(SliderRegion(x, trackY - 6, w, 18, minVal, maxVal, getter, setter))
        return y + 30
    }

    private fun extendAheadClient(distance: Int) {
        val player = Minecraft.getInstance().player ?: return
        val start = if (ClientPavingState.hasStart) ClientPavingState.startPos else player.blockPosition().below()
        val look = player.lookAngle
        val hLen = hypot(look.x, look.z).coerceAtLeast(1e-4)
        val ex = (start.x + look.x / hLen * distance).roundToInt()
        val ez = (start.z + look.z / hLen * distance).roundToInt()
        val end = BlockPos(ex, start.y, ez)
        TerraPaverNet.sendToServer(TerraPaverNet.C2SSetEndPos(end))
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            for (s in sliderRegions) {
                if (mouseX >= s.x - 4 && mouseX <= s.x + s.w + 4 && mouseY >= s.y && mouseY <= s.y + s.h) {
                    draggingSlider = s
                    updateSlider(s, mouseX)
                    return true
                }
            }
            for (r in clickRegions.asReversed()) {
                if (mouseX >= r.x && mouseX <= r.x + r.w && mouseY >= r.y && mouseY <= r.y + r.h) {
                    r.onClick()
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        val s = draggingSlider
        if (s != null && button == 0) {
            updateSlider(s, mouseX)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && draggingSlider != null) {
            draggingSlider = null
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    private fun updateSlider(s: SliderRegion, mouseX: Double) {
        val frac = ((mouseX - s.x) / max(1, s.w).toDouble()).coerceIn(0.0, 1.0)
        val v = (s.minVal + frac * (s.maxVal - s.minVal)).roundToInt().coerceIn(s.minVal, s.maxVal)
        if (v != s.current()) {
            s.onChange(v)
        }
    }
}
