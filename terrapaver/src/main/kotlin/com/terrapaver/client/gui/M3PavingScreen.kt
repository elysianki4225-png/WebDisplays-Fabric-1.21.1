package com.terrapaver.client.gui

import com.terrapaver.client.ClientPavingState
import com.terrapaver.config.AlignmentMode
import com.terrapaver.config.LampLayout
import com.terrapaver.config.LampStyle
import com.terrapaver.config.ObstacleMode
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
    private val tabScroll = IntArray(4)
    private var lastContentHeight = 0

    private val clickRegions = ArrayList<ClickRegion>()
    private val sliderRegions = ArrayList<SliderRegion>()
    private var draggingSlider: SliderRegion? = null

    private var winX = 0
    private var winY = 0
    private var winW = 0
    private var winH = 0

    private var contentX = 0
    private var contentY = 0
    private var contentW = 0
    private var contentH = 0

    // Current active clip box for interactive hit registration
    private var clipTop = 0
    private var clipBottom = Int.MAX_VALUE

    override fun init() {
        super.init()
        winW = min(640, width - 16).coerceAtLeast(min(340, width - 8))
        winH = min(410, height - 14).coerceAtLeast(min(220, height - 8))
        winX = (width - winW) / 2
        winY = (height - winH) / 2
    }

    override fun isPauseScreen(): Boolean = false

    private fun save() {
        PavingConfig.saveClient(cfg)
        if (ClientPavingState.hasEnoughWaypoints() && ClientPavingState.status?.active != true) {
            TerraPaverNet.sendToServer(
                TerraPaverNet.C2SPreviewRoute(ClientPavingState.waypoints.toList(), cfg.toJson())
            )
        }
    }

    private fun theme(): M3Palette = M3Theme.get(cfg.m3SeedTheme)

    override fun renderBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Draw only a clean semi-transparent backdrop behind the M3 sheet; never on top of it.
        g.fill(0, 0, width, height, 0x550B1018)
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(g, mouseX, mouseY, partialTick)
        clickRegions.clear()
        sliderRegions.clear()

        val t = theme()
        clipTop = 0
        clipBottom = height

        // Main crisp M3 Light Sheet (NO super.render after this, avoiding 1.21.1 gray overlay!)
        M3Draw.drawElevationShadow(g, winX, winY, winW, winH, 18, 4)
        M3Draw.fillRoundedRect(g, winX, winY, winW, winH, 18, t.surface)
        M3Draw.strokeRoundedRect(g, winX, winY, winW, winH, 18, t.outlineVariant)

        val barH = if (winH < 270) 36 else 42
        val railW = if (winW < 420) 56 else 68

        renderTopAppBar(g, mouseX, mouseY, barH, t)
        renderNavigationRail(g, mouseX, mouseY, barH, railW, t)

        contentX = winX + railW + 14
        contentY = winY + barH + 8
        contentW = winW - railW - 24
        contentH = winH - barH - 16

        val scroll = tabScroll[activeTab]
        val contentMouseY = if (mouseX in contentX..(contentX + contentW) && mouseY in contentY..(contentY + contentH)) {
            mouseY
        } else -10000

        // Scissor-clipped scrollable tab viewport
        g.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH)
        clipTop = contentY
        clipBottom = contentY + contentH

        val totalH = when (activeTab) {
            0 -> renderOverviewTab(g, mouseX, contentMouseY, contentX, contentY - scroll, contentW - 8, contentH, t)
            1 -> renderRoadRailTab(g, mouseX, contentMouseY, contentX, contentY - scroll, contentW - 8, t)
            2 -> renderTunnelBridgeTab(g, mouseX, contentMouseY, contentX, contentY - scroll, contentW - 8, t)
            else -> renderEngineTab(g, mouseX, contentMouseY, contentX, contentY - scroll, contentW - 8, t)
        }

        g.disableScissor()
        clipTop = 0
        clipBottom = height

        lastContentHeight = totalH
        val maxScroll = max(0, totalH - contentH)
        if (tabScroll[activeTab] > maxScroll) {
            tabScroll[activeTab] = maxScroll
        }

        // M3 slim scrollbar indicator when content overflows
        if (maxScroll > 0) {
            val trackX = contentX + contentW - 4
            M3Draw.fillRoundedRect(g, trackX, contentY + 2, 4, contentH - 4, 2, t.surfaceHigh)
            val thumbH = max(20, ((contentH.toFloat() / totalH.toFloat()) * (contentH - 4)).roundToInt())
            val thumbY = contentY + 2 + ((tabScroll[activeTab].toFloat() / maxScroll.toFloat()) * (contentH - 4 - thumbH)).roundToInt()
            M3Draw.fillRoundedRect(g, trackX, thumbY, 4, thumbH, 2, t.primary)
        }
    }

    private fun renderTopAppBar(g: GuiGraphics, mouseX: Int, mouseY: Int, barH: Int, t: M3Palette) {
        M3Draw.fillRoundedRect(g, winX + 4, winY + 4, winW - 8, barH, 14, t.surfaceLow)

        val badgeSize = if (barH < 40) 20 else 24
        val badgeY = winY + 4 + (barH - badgeSize) / 2
        M3Draw.fillRoundedRect(g, winX + 14, badgeY, badgeSize, badgeSize, 7, t.primaryContainer)
        M3Draw.fillRoundedRect(g, winX + 19, badgeY + 5, badgeSize - 10, badgeSize - 10, 4, t.primary)

        val titleMaxW = winW - 235
        val titleStr = M3Draw.fitText(font, I18n.get("terrapaver.gui.title"), titleMaxW)
        if (barH >= 40) {
            g.drawString(font, titleStr, winX + 44, winY + 11, t.onSurface, false)
            val subStr = M3Draw.fitText(font, I18n.get("terrapaver.gui.subtitle"), titleMaxW)
            g.drawString(font, subStr, winX + 44, winY + 23, t.onSurfaceVariant, false)
        } else {
            g.drawString(font, titleStr, winX + 40, winY + 14, t.onSurface, false)
        }

        // 4 M3 Dynamic Color Seed Chips
        val seedColors = intArrayOf(0xFF0B57D0.toInt(), 0xFF6750A4.toInt(), 0xFF006A60.toInt(), 0xFF984716.toInt())
        val doneW = 76
        val seedStartX = winX + winW - doneW - 96
        val seedY = winY + 4 + (barH - 14) / 2
        for (i in seedColors.indices) {
            val sx = seedStartX + i * 19
            if (cfg.m3SeedTheme == i) {
                M3Draw.fillCircle(g, sx + 7, seedY + 7, 9, t.primaryContainer)
            }
            M3Draw.fillCircle(g, sx + 7, seedY + 7, 6, seedColors[i])
            registerClick(sx, seedY, 15, 15) {
                cfg.m3SeedTheme = i
                save()
            }
        }

        drawPillButton(
            g, mouseX, mouseY,
            winX + winW - doneW - 12, winY + 4 + (barH - 22) / 2, doneW, 22,
            I18n.get("terrapaver.gui.done"),
            ButtonStyle.FILLED, t, true
        ) {
            save()
            onClose()
        }
    }

    private fun renderNavigationRail(g: GuiGraphics, mouseX: Int, mouseY: Int, barH: Int, railW: Int, t: M3Palette) {
        val railX = winX + 8
        val railY = winY + barH + 8
        val railH = winH - barH - 16
        M3Draw.fillRoundedRect(g, railX, railY, railW, railH, 14, t.surfaceLow)

        val tabs = arrayOf(
            "terrapaver.tab.overview" to "AI",
            "terrapaver.tab.road" to "R/T",
            "terrapaver.tab.tunnel" to "T/B",
            "terrapaver.tab.engine" to "SYS"
        )

        val step = min(56, (railH - 16) / tabs.size).coerceAtLeast(36)
        for (i in tabs.indices) {
            val iy = railY + 8 + i * step
            val selected = activeTab == i
            val hovered = mouseX in (railX + 4)..(railX + railW - 4) && mouseY in iy..(iy + step - 4)

            val pillColor = when {
                selected -> t.secondaryContainer
                hovered -> M3Theme.withAlpha(t.onSurface, 18)
                else -> 0x00000000
            }
            if (pillColor != 0) {
                M3Draw.fillRoundedRect(g, railX + 6, iy + 2, railW - 12, 20, 10, pillColor)
            }

            val iconColor = if (selected) t.onSecondaryContainer else t.onSurfaceVariant
            val badgeW = font.width(tabs[i].second)
            g.drawString(font, tabs[i].second, railX + (railW - badgeW) / 2, iy + 8, iconColor, false)

            val label = M3Draw.fitText(font, I18n.get(tabs[i].first), railW - 8)
            val lw = font.width(label)
            g.drawString(
                font,
                label,
                railX + (railW - lw) / 2,
                iy + 24,
                if (selected) t.primary else t.onSurfaceVariant,
                false
            )

            registerClick(railX + 4, iy, railW - 8, step - 4) {
                activeTab = i
            }
        }
    }

    private fun renderOverviewTab(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int, viewportH: Int, t: M3Palette
    ): Int {
        val stacked = w < 380
        val leftW = if (stacked) w else (w * 0.57).toInt()
        val rightX = if (stacked) x else x + leftW + 10
        val rightW = if (stacked) w else w - leftW - 10

        val wps = ClientPavingState.waypoints
        val card1H = 162
        M3Draw.drawCard(g, x, y, leftW, card1H, t, elevated = true)
        g.drawString(font, M3Draw.fitText(font, I18n.get("terrapaver.overview.route_title"), leftW - 24), x + 12, y + 10, t.onSurface, false)

        val wpSummary = if (wps.isEmpty()) {
            I18n.get("terrapaver.overview.wp_empty")
        } else {
            val first = wps.first()
            val last = wps.last()
            if (wps.size == 1) {
                I18n.get("terrapaver.overview.wp_one", first.x, first.y, first.z)
            } else {
                I18n.get("terrapaver.overview.wp_multi", wps.size, first.x, first.z, last.x, last.z)
            }
        }
        g.drawString(font, M3Draw.fitText(font, wpSummary, leftW - 24), x + 12, y + 26, t.onSurfaceVariant, false)

        val spanInfo = if (ClientPavingState.hasEnoughWaypoints()) {
            I18n.get("terrapaver.overview.distance_multi", ClientPavingState.totalSpanBlocks(), wps.size - 1)
        } else {
            I18n.get("terrapaver.overview.hint_extend")
        }
        g.drawString(font, M3Draw.fitText(font, spanInfo, leftW - 24), x + 12, y + 40, t.primary, false)

        // Row 1: Add Here | Undo Point | Clear
        val b3W = (leftW - 32) / 3
        drawPillButton(
            g, mouseX, mouseY, x + 12, y + 56, b3W, 20,
            I18n.get("terrapaver.action.add_wp"),
            ButtonStyle.TONAL, t, true
        ) {
            val player = Minecraft.getInstance().player ?: return@drawPillButton
            TerraPaverNet.sendToServer(TerraPaverNet.C2SAddWaypoint(player.blockPosition().below()))
        }
        drawPillButton(
            g, mouseX, mouseY, x + 16 + b3W, y + 56, b3W, 20,
            I18n.get("terrapaver.action.pop_wp"),
            ButtonStyle.OUTLINED, t, wps.isNotEmpty()
        ) {
            TerraPaverNet.sendToServer(TerraPaverNet.C2SControlPave(TerraPaverNet.ACTION_POP_WAYPOINT))
        }
        drawPillButton(
            g, mouseX, mouseY, x + 20 + b3W * 2, y + 56, leftW - 32 - b3W * 2, 20,
            I18n.get("terrapaver.action.clear"),
            ButtonStyle.OUTLINED, t, wps.isNotEmpty()
        ) {
            TerraPaverNet.sendToServer(TerraPaverNet.C2SControlPave(TerraPaverNet.ACTION_CLEAR_SELECTION))
        }

        // Row 2: Quick Extend Ahead (+100m, +250m, +500m)
        val dists = intArrayOf(100, 250, 500)
        for (i in dists.indices) {
            val d = dists[i]
            drawPillButton(
                g, mouseX, mouseY,
                x + 12 + i * (b3W + 4), y + 82, b3W, 20,
                "+${d}m",
                ButtonStyle.TONAL, t, true
            ) {
                extendAheadClient(d)
            }
        }

        // Row 3: Start / Pause / Cancel / Undo Build
        val st = ClientPavingState.status
        val isActive = st?.active == true
        val canStart = ClientPavingState.hasEnoughWaypoints()
        val halfActW = (leftW - 30) / 2

        if (!isActive) {
            drawPillButton(
                g, mouseX, mouseY,
                x + 12, y + 110, halfActW, 24,
                I18n.get("terrapaver.action.start"),
                ButtonStyle.FILLED, t, canStart
            ) {
                save()
                TerraPaverNet.sendToServer(
                    TerraPaverNet.C2SStartPave(ClientPavingState.waypoints.toList(), cfg.toJson())
                )
            }
        } else {
            val pauseText = I18n.get(if (st?.paused == true) "terrapaver.action.resume" else "terrapaver.action.pause")
            drawPillButton(
                g, mouseX, mouseY,
                x + 12, y + 110, halfActW, 24,
                pauseText,
                ButtonStyle.TONAL, t, true
            ) {
                TerraPaverNet.sendToServer(TerraPaverNet.C2SControlPave(TerraPaverNet.ACTION_TOGGLE_PAUSE))
            }
        }

        if (isActive) {
            drawPillButton(
                g, mouseX, mouseY,
                x + 18 + halfActW, y + 110, halfActW, 24,
                I18n.get("terrapaver.action.cancel"),
                ButtonStyle.DANGER, t, true
            ) {
                TerraPaverNet.sendToServer(TerraPaverNet.C2SControlPave(TerraPaverNet.ACTION_CANCEL))
            }
        } else {
            drawPillButton(
                g, mouseX, mouseY,
                x + 18 + halfActW, y + 110, halfActW, 24,
                I18n.get("terrapaver.action.undo"),
                ButtonStyle.OUTLINED, t, ClientPavingState.canUndo
            ) {
                TerraPaverNet.sendToServer(TerraPaverNet.C2SControlPave(TerraPaverNet.ACTION_UNDO))
            }
        }

        val hintLine = M3Draw.fitText(font, I18n.get("terrapaver.overview.wp_tip"), leftW - 24)
        g.drawString(font, hintLine, x + 12, y + 142, t.onSurfaceVariant, false)

        // Card 2: Live Progress & Telemetry Card
        val progY = y + card1H + 10
        val progH = max(108, viewportH - card1H - 10)
        M3Draw.drawCard(g, x, progY, leftW, progH, t, elevated = true)
        g.drawString(font, M3Draw.fitText(font, I18n.get("terrapaver.overview.progress_title"), leftW - 24), x + 12, progY + 10, t.onSurface, false)

        val statusLabel = if (st != null) I18n.get(st.statusKey) else I18n.get("terrapaver.status.ready")
        val pct = if (st != null) (st.progress * 100f).roundToInt() else 0
        g.drawString(font, M3Draw.fitText(font, "$statusLabel ($pct%)", leftW - 24), x + 12, progY + 26, t.primary, false)

        M3Draw.drawLinearProgress(
            g, x + 12, progY + 42, leftW - 24, 8,
            st?.progress ?: 0f,
            t,
            indeterminate = (st?.active == true && st.phase == TerraPaverNet.S2CPavingStatus.PHASE_SURVEYING && st.progress < 0.05f)
        )

        val placed = st?.placedBlocks ?: 0
        val tunnels = st?.tunnelSegments ?: 0
        val viaducts = st?.viaductSegments ?: 0
        g.drawString(
            font,
            M3Draw.fitText(font, I18n.get("terrapaver.overview.stats", placed, tunnels, viaducts), leftW - 24),
            x + 12, progY + 58, t.onSurfaceVariant, false
        )

        val modeHint = if (cfg.pavingMode == PavingMode.CREATE_RAIL) {
            I18n.get("terrapaver.overview.create_note")
        } else {
            I18n.get("terrapaver.overview.road_note")
        }
        g.drawString(font, M3Draw.fitText(font, modeHint, leftW - 24), x + 12, progY + 76, t.tertiary, false)

        val leftBottom = progY + progH

        // Right Column: Live Cross-Section Preview + Engineering Summary
        val rightY = if (stacked) leftBottom + 10 else y
        val rightH = if (stacked) 220 else max(280, viewportH)
        M3Draw.drawCard(g, rightX, rightY, rightW, rightH, t, elevated = true)
        M3Draw.drawCrossSectionPreview(
            g, font,
            rightX + 8, rightY + 8, rightW - 16, rightH - 74,
            cfg, t, I18n.get("terrapaver.preview.cross_section")
        )

        val sy = rightY + rightH - 58
        g.drawString(font, M3Draw.fitText(font, "${I18n.get("terrapaver.cfg.mode")}: ${I18n.get(cfg.pavingMode.key)}", rightW - 24), rightX + 12, sy, t.onSurface, false)
        g.drawString(font, M3Draw.fitText(font, "${I18n.get("terrapaver.cfg.align")}: ${I18n.get(cfg.alignmentMode.key)}", rightW - 24), rightX + 12, sy + 14, t.onSurfaceVariant, false)
        g.drawString(font, M3Draw.fitText(font, "${I18n.get("terrapaver.cfg.obstacle")}: ${I18n.get(cfg.obstacleMode.key)}", rightW - 24), rightX + 12, sy + 28, t.onSurfaceVariant, false)
        val smoothVal = if (cfg.terrainBlending) "${cfg.blendRadius}m" else I18n.get("terrapaver.common.off")
        g.drawString(font, M3Draw.fitText(font, I18n.get("terrapaver.overview.grade_smooth", cfg.maxSlopeRun, smoothVal), rightW - 24), rightX + 12, sy + 42, t.primary, false)

        return max(leftBottom, rightY + rightH) - y
    }

    private fun renderRoadRailTab(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int, t: M3Palette
    ): Int {
        val cardH = 446
        M3Draw.drawCard(g, x, y, w, cardH, t, elevated = true)
        var cy = y + 12

        g.drawString(font, I18n.get("terrapaver.cfg.mode"), x + 14, cy, t.onSurface, false)
        cy += 13
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 22,
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
        cy += 30

        g.drawString(font, I18n.get("terrapaver.cfg.style"), x + 14, cy, t.onSurface, false)
        cy += 13
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
        cy += 30

        g.drawString(font, I18n.get("terrapaver.cfg.align"), x + 14, cy, t.onSurface, false)
        cy += 13
        val aligns = AlignmentMode.entries
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 22,
            aligns.map { I18n.get(it.key) },
            aligns.indexOf(cfg.alignmentMode),
            t
        ) { idx ->
            cfg.alignmentMode = aligns[idx]
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

        // Architectural Street Lighting Controls
        g.drawString(font, I18n.get("terrapaver.cfg.road_lamp_layout"), x + 14, cy, t.onSurface, false)
        cy += 13
        val layouts = LampLayout.entries
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 21,
            layouts.map { I18n.get(it.key) },
            layouts.indexOf(cfg.roadLampLayout),
            t
        ) { idx ->
            cfg.roadLampLayout = layouts[idx]
            save()
        }
        cy += 28

        g.drawString(font, I18n.get("terrapaver.cfg.rail_lamp_layout"), x + 14, cy, t.onSurface, false)
        cy += 13
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 21,
            layouts.map { I18n.get(it.key) },
            layouts.indexOf(cfg.railLampLayout),
            t
        ) { idx ->
            cfg.railLampLayout = layouts[idx]
            save()
        }
        cy += 28

        g.drawString(font, I18n.get("terrapaver.cfg.lamp_style"), x + 14, cy, t.onSurface, false)
        cy += 13
        val lampStyles = LampStyle.entries
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 21,
            lampStyles.map { I18n.get(it.key) },
            lampStyles.indexOf(cfg.lampStyle),
            t
        ) { idx ->
            cfg.lampStyle = lampStyles[idx]
            save()
        }
        cy += 30

        val colW = (w - 34) / 2
        drawSwitchTile(g, mouseX, mouseY, x + 14, cy, colW, I18n.get("terrapaver.cfg.guardrails"), cfg.addGuardrails, t) {
            cfg.addGuardrails = !cfg.addGuardrails; save()
        }
        drawSwitchTile(g, mouseX, mouseY, x + 20 + colW, cy, colW, I18n.get("terrapaver.cfg.markings"), cfg.addLaneMarkings, t) {
            cfg.addLaneMarkings = !cfg.addLaneMarkings; save()
        }
        cy += 32
        drawSwitchTile(g, mouseX, mouseY, x + 14, cy, w - 28, I18n.get("terrapaver.cfg.smooth_slabs"), cfg.smoothSlabs, t) {
            cfg.smoothSlabs = !cfg.smoothSlabs; save()
        }
        return cardH
    }

    private fun renderTunnelBridgeTab(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int, t: M3Palette
    ): Int {
        val cardH = 352
        M3Draw.drawCard(g, x, y, w, cardH, t, elevated = true)
        var cy = y + 10
        val halfW = (w - 36) / 2

        g.drawString(font, I18n.get("terrapaver.cfg.obstacle"), x + 14, cy, t.onSurface, false)
        cy += 12
        val obsModes = ObstacleMode.entries
        drawSegmentedRow(
            g, mouseX, mouseY, x + 14, cy, w - 28, 22,
            obsModes.map { I18n.get(it.key) },
            obsModes.indexOf(cfg.obstacleMode),
            t
        ) { idx ->
            cfg.obstacleMode = obsModes[idx]
            save()
        }
        cy += 30

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
            M3Draw.fillRoundedRect(g, x + 14, cy, w - 28, 22, 10, t.primaryContainer)
            g.drawString(
                font,
                M3Draw.fitText(font, I18n.get("terrapaver.cfg.auto_tunnel_info", cfg.effectiveTunnelWidth(), cfg.effectiveTunnelHeight()), w - 48),
                x + 22, cy + 7, t.onPrimaryContainer, false
            )
            cy += 28
        }

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
        cy += 32

        // Natural Terrain Blending Section
        drawSwitchTile(
            g, mouseX, mouseY, x + 14, cy, halfW,
            I18n.get("terrapaver.cfg.terrain_blending"),
            cfg.terrainBlending, t
        ) {
            cfg.terrainBlending = !cfg.terrainBlending
            save()
        }
        drawSliderRow(
            g, mouseX, mouseY, x + 22 + halfW, cy + 1, halfW,
            I18n.get("terrapaver.cfg.blend_radius"),
            2, 10, { cfg.blendRadius }, "${cfg.blendRadius}m", t
        ) { v -> cfg.blendRadius = v; save() }

        return cardH
    }

    private fun renderEngineTab(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int, t: M3Palette
    ): Int {
        val cardH = 266
        M3Draw.drawCard(g, x, y, w, cardH, t, elevated = true)
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
        cy += 32

        drawSwitchTile(
            g, mouseX, mouseY, x + 14, cy, w - 28,
            I18n.get("terrapaver.cfg.route_preview"),
            cfg.showRoutePreview, t
        ) {
            cfg.showRoutePreview = !cfg.showRoutePreview
            save()
        }
        cy += 32

        drawSwitchTile(
            g, mouseX, mouseY, x + 14, cy, w - 28,
            I18n.get("terrapaver.cfg.drop_blocks"),
            cfg.dropExcavatedBlocks, t
        ) {
            cfg.dropExcavatedBlocks = !cfg.dropExcavatedBlocks
            save()
        }
        cy += 38

        drawPillButton(
            g, mouseX, mouseY,
            x + 14, cy, 140, 22,
            I18n.get("terrapaver.action.reset"),
            ButtonStyle.OUTLINED, t, true
        ) {
            val themeKeep = cfg.m3SeedTheme
            cfg = PavingConfig(m3SeedTheme = themeKeep)
            save()
        }
        return cardH
    }

    private fun registerClick(x: Int, y: Int, w: Int, h: Int, onClick: () -> Unit) {
        val top = max(y, clipTop)
        val bottom = min(y + h, clipBottom)
        if (bottom > top) {
            clickRegions.add(ClickRegion(x, top, w, bottom - top, onClick))
        }
    }

    private fun registerSlider(
        x: Int, y: Int, w: Int, h: Int,
        minVal: Int, maxVal: Int,
        getter: () -> Int, setter: (Int) -> Unit
    ) {
        val top = max(y, clipTop)
        val bottom = min(y + h, clipBottom)
        if (bottom > top) {
            sliderRegions.add(SliderRegion(x, top, w, bottom - top, minVal, maxVal, getter, setter))
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

        val fitted = M3Draw.fitText(font, label, w - 10)
        val tw = font.width(fitted)
        g.drawString(font, fitted, x + (w - tw) / 2, y + (h - 8) / 2, fg, false)
        if (enabled) {
            registerClick(x, y, w, h, onClick)
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
            val raw = if (active) "✓ ${labels[i]}" else labels[i]
            val fitted = M3Draw.fitText(font, raw, sw - 6)
            val tw = font.width(fitted)
            g.drawString(
                font, fitted,
                sx + (sw - tw) / 2, y + (h - 8) / 2,
                if (active) t.onSecondaryContainer else t.onSurface,
                false
            )
            registerClick(sx, y, sw, h) { onSelect(i) }
        }
    }

    private fun drawSwitchTile(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int,
        label: String, checked: Boolean, t: M3Palette,
        onToggle: () -> Unit
    ) {
        val h = 26
        val hovered = mouseX in x..(x + w) && mouseY in y..(y + h)
        val bg = if (hovered) t.surfaceContainer else t.surfaceLow
        M3Draw.fillRoundedRect(g, x, y, w, h, 10, bg)

        val swW = 32
        val swH = 16
        val fitted = M3Draw.fitText(font, label, w - swW - 20)
        g.drawString(font, fitted, x + 8, y + 9, t.onSurface, false)

        val swX = x + w - swW - 8
        val swY = y + 5
        M3Draw.fillRoundedRect(g, swX, swY, swW, swH, 8, if (checked) t.primary else t.surfaceHigh)
        if (!checked) {
            M3Draw.strokeRoundedRect(g, swX, swY, swW, swH, 8, t.outline)
        }
        val thumbR = if (checked) 5 else 4
        val thumbCx = if (checked) swX + swW - 8 else swX + 8
        M3Draw.fillCircle(g, thumbCx, swY + 8, thumbR, if (checked) t.onPrimary else t.outline)

        registerClick(x, y, w, h, onToggle)
    }

    private fun drawSliderRow(
        g: GuiGraphics, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int,
        label: String, minVal: Int, maxVal: Int,
        getter: () -> Int, valueText: String, t: M3Palette,
        setter: (Int) -> Unit
    ): Int {
        val vw = font.width(valueText)
        val fittedLabel = M3Draw.fitText(font, label, w - vw - 20)
        g.drawString(font, fittedLabel, x, y, t.onSurface, false)
        M3Draw.fillRoundedRect(g, x + w - vw - 10, y - 2, vw + 10, 13, 6, t.primaryContainer)
        g.drawString(font, valueText, x + w - vw - 5, y + 1, t.onPrimaryContainer, false)

        val trackY = y + 14
        val trackH = 6
        val cur = getter().coerceIn(minVal, maxVal)
        val frac = (cur - minVal).toFloat() / max(1, maxVal - minVal).toFloat()

        M3Draw.fillRoundedRect(g, x, trackY, w, trackH, 3, t.surfaceHigh)
        val activeW = (w * frac).roundToInt().coerceAtLeast(trackH)
        M3Draw.fillRoundedRect(g, x, trackY, activeW, trackH, 3, t.primary)

        val thumbX = (x + w * frac).roundToInt().coerceIn(x + 3, x + w - 3)
        M3Draw.fillRoundedRect(g, thumbX - 3, trackY - 4, 6, 14, 3, t.primary)

        registerSlider(x, trackY - 6, w, 18, minVal, maxVal, getter, setter)
        return y + 29
    }

    private fun extendAheadClient(distance: Int) {
        val player = Minecraft.getInstance().player ?: return
        val base = ClientPavingState.waypoints.lastOrNull() ?: run {
            val start = player.blockPosition().below()
            TerraPaverNet.sendToServer(TerraPaverNet.C2SAddWaypoint(start))
            start
        }
        val look = player.lookAngle
        val hLen = hypot(look.x, look.z).coerceAtLeast(1e-4)
        val ex = (base.x + look.x / hLen * distance).roundToInt()
        val ez = (base.z + look.z / hLen * distance).roundToInt()
        val next = BlockPos(ex, base.y, ez)
        TerraPaverNet.sendToServer(TerraPaverNet.C2SAddWaypoint(next))
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val maxScroll = max(0, lastContentHeight - contentH)
        if (maxScroll > 0) {
            tabScroll[activeTab] = (tabScroll[activeTab] - (scrollY * 24).roundToInt()).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
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
