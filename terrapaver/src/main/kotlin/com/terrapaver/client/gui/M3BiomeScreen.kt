package com.terrapaver.client.gui

import com.terrapaver.client.ClientBiomeState
import com.terrapaver.config.BiomeConfig
import com.terrapaver.config.BiomeShape
import com.terrapaver.config.BiomeSplitMode
import com.terrapaver.config.BiomeVerticalMode
import com.terrapaver.config.PavingConfig
import com.terrapaver.network.TerraPaverNet
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class M3BiomeScreen : Screen(Component.translatable("terrapaver.biome.gui.title")) {
    private enum class ButtonStyle { FILLED, TONAL, OUTLINED, DANGER }

    private data class BiomeEntry(
        val id: String,
        val namespace: String,
        val path: String,
        val displayName: String,
        val category: Int,
        val grassColor: Int,
        val waterColor: Int
    )

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

    private var cfg: BiomeConfig = BiomeConfig.loadClient().copySanitized()
    private var activeTab = 0
    private var categoryFilter = 0
    private var modNamespaces: List<String> = listOf("")
    private var selectedModIdx = 0
    private val tabScroll = IntArray(4)
    private var lastContentHeight = 0

    private val allBiomes = ArrayList<BiomeEntry>()
    private var filteredBiomes: List<BiomeEntry> = emptyList()
    private var searchBox: EditBox? = null
    private var lastQuery = ""

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

    private var clipTop = 0
    private var clipBottom = Int.MAX_VALUE

    override fun init() {
        super.init()
        winW = min(668, width - 16).coerceAtLeast(min(340, width - 8))
        winH = min(432, height - 14).coerceAtLeast(min(220, height - 8))
        winX = (width - winW) / 2
        winY = (height - winH) / 2

        val barH = if (winH < 270) 36 else 42
        val railW = if (winW < 420) 56 else 72
        val sx = winX + railW + 24
        val sy = winY + barH + 18
        val sw = (winW - railW - 44 - 160).coerceAtLeast(90)

        val box = EditBox(font, sx, sy, sw, 18, Component.empty())
        box.setMaxLength(64)
        box.isBordered = false
        box.setTextColor(theme().onSurface)
        box.setHint(Component.translatable("terrapaver.biome.search_hint"))
        box.value = lastQuery
        box.setResponder { q ->
            lastQuery = q
            tabScroll[0] = 0
            rebuildFilter()
        }
        addWidget(box)
        searchBox = box

        loadBiomesFromRegistry()
        rebuildFilter()
    }

    override fun isPauseScreen(): Boolean = false

    private fun save() {
        BiomeConfig.saveClient(cfg)
    }

    private fun theme(): M3Palette = M3Theme.get(PavingConfig.clientConfig.m3SeedTheme)

    private fun loadBiomesFromRegistry() {
        allBiomes.clear()
        val nsSet = LinkedHashSet<String>()
        nsSet.add("")
        val level = Minecraft.getInstance().level ?: return
        val reg = level.registryAccess().registryOrThrow(Registries.BIOME)
        for (entry in reg.entrySet()) {
            val id = entry.key.location()
            val ns = id.namespace
            nsSet.add(ns)
            val path = id.path
            val fullId = id.toString()
            val name = I18n.get("biome.$ns.$path").let {
                if (it == "biome.$ns.$path") path.replace('_', ' ') else it
            }
            val b = entry.value
            val cat = classifyBiome(ns, path, b.baseTemperature)
            val gCol = (b.getGrassColor(0.0, 0.0) and 0xFFFFFF) or (0xFF shl 24)
            val wCol = (b.waterColor and 0xFFFFFF) or (0xFF shl 24)
            allBiomes.add(BiomeEntry(fullId, ns, path, name, cat, gCol, wCol))
        }
        modNamespaces = nsSet.toList()
        if (selectedModIdx >= modNamespaces.size) selectedModIdx = 0
        allBiomes.sortWith(compareBy<BiomeEntry> { it.id != cfg.targetBiome }.thenBy { !cfg.favoriteBiomes.contains(it.id) }.thenBy { it.displayName })
    }

    // Categories: 0=All, 1=Forest/Plains, 2=Cold/Snow, 3=Dry/Desert, 4=Lush/Swamp/Cave, 5=Water, 6=Nether, 7=End, 8=Modded
    private fun classifyBiome(ns: String, path: String, temp: Float): Int {
        if (path.contains("nether") || path.contains("crimson") || path.contains("warped") ||
            path.contains("basalt") || path.contains("soul_sand")
        ) return 6
        if (path.contains("end") && !path.contains("meadow")) return 7
        if (path.contains("ocean") || path.contains("river") || path.contains("beach")) return 5
        if (path.contains("snow") || path.contains("frozen") || path.contains("ice") || path.contains("grove") ||
            path.contains("peaks") || path.contains("taiga") || temp <= 0.25f
        ) return 2
        if (path.contains("desert") || path.contains("badlands") || path.contains("savanna") || temp >= 1.2f) return 3
        if (path.contains("jungle") || path.contains("swamp") || path.contains("mangrove") ||
            path.contains("lush") || path.contains("mushroom") || path.contains("dripstone") || path.contains("deep_dark")
        ) return 4
        if (ns != "minecraft") return 8
        return 1
    }

    private fun rebuildFilter() {
        val q = lastQuery.trim().lowercase()
        val modFilter = modNamespaces.getOrElse(selectedModIdx) { "" }
        filteredBiomes = allBiomes.filter { b ->
            val modOk = modFilter.isEmpty() || b.namespace == modFilter
            val catOk = when (categoryFilter) {
                0 -> true
                8 -> b.namespace != "minecraft"
                else -> b.category == categoryFilter
            }
            val qOk = q.isEmpty() ||
                b.displayName.lowercase().contains(q) ||
                b.id.lowercase().contains(q) ||
                b.path.lowercase().contains(q)
            modOk && catOk && qOk
        }
    }

    override fun renderBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        g.fill(0, 0, width, height, 0x550B1018)
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(g, mouseX, mouseY, partialTick)
        clickRegions.clear()
        sliderRegions.clear()

        val t = theme()
        clipTop = 0
        clipBottom = height

        M3Draw.drawElevationShadow(g, winX, winY, winW, winH, 18, 4)
        M3Draw.fillRoundedRect(g, winX, winY, winW, winH, 18, t.surface)
        M3Draw.strokeRoundedRect(g, winX, winY, winW, winH, 18, t.outlineVariant)

        val barH = if (winH < 270) 36 else 42
        val railW = if (winW < 420) 56 else 72

        renderTopAppBar(g, mouseX, mouseY, barH, t)
        renderNavigationRail(g, mouseX, mouseY, barH, railW, t)

        contentX = winX + railW + 14
        contentY = winY + barH + 8
        contentW = winW - railW - 24
        contentH = winH - barH - 16

        val actionBarH = 44
        val bodyH = (contentH - actionBarH - 8).coerceAtLeast(60)
        clipTop = contentY
        clipBottom = contentY + bodyH

        val maxScroll = max(0, lastContentHeight - bodyH)
        tabScroll[activeTab] = tabScroll[activeTab].coerceIn(0, maxScroll)

        searchBox?.visible = (activeTab == 0)

        g.enableScissor(contentX, contentY, contentX + contentW, contentY + bodyH)
        val yStart = contentY - tabScroll[activeTab]
        val endY = when (activeTab) {
            0 -> renderBiomePickerTab(g, mouseX, mouseY, yStart, t, partialTick)
            1 -> renderShapeSplitTab(g, mouseX, mouseY, yStart, t)
            2 -> renderDimensionHeightTab(g, mouseX, mouseY, yStart, t)
            else -> renderSurfaceActionsTab(g, mouseX, mouseY, yStart, t)
        }
        g.disableScissor()
        lastContentHeight = (endY - yStart).coerceAtLeast(0)

        if (maxScroll > 0) {
            val trackX = contentX + contentW - 4
            val thumbH = max(20, (bodyH * bodyH) / max(bodyH + 1, lastContentHeight))
            val thumbY = contentY + ((bodyH - thumbH) * (tabScroll[activeTab].toFloat() / maxScroll.toFloat())).toInt()
            M3Draw.fillRoundedRect(g, trackX, contentY + 2, 3, bodyH - 4, 2, t.surfaceHigh)
            M3Draw.fillRoundedRect(g, trackX, thumbY, 3, thumbH, 2, t.outline)
        }

        clipTop = 0
        clipBottom = height
        renderActionBar(g, mouseX, mouseY, contentY + bodyH + 6, actionBarH - 6, t)
    }

    private fun renderTopAppBar(g: GuiGraphics, mouseX: Int, mouseY: Int, barH: Int, t: M3Palette) {
        M3Draw.fillRoundedRect(g, winX + 1, winY + 1, winW - 2, barH - 1, 17, t.surfaceLow)
        g.fill(winX + 1, winY + barH - 1, winX + winW - 1, winY + barH, t.outlineVariant)

        M3Draw.fillCircle(g, winX + 20, winY + barH / 2, 7, 0xFF1B8A5A.toInt())
        val titleText = I18n.get("terrapaver.biome.gui.title")
        val titleX = winX + 34
        val closeX = winX + winW - 34
        g.drawString(font, M3Draw.fitText(font, titleText, closeX - titleX - 150), titleX, winY + (barH - 8) / 2, t.onSurface, false)

        val currentBiomeName = formatBiomeName(cfg.targetBiome)
        val badgeText = M3Draw.fitText(font, currentBiomeName, 132)
        val badgeW = font.width(badgeText) + 16
        val badgeX = closeX - badgeW - 8
        M3Draw.fillRoundedRect(g, badgeX, winY + (barH - 20) / 2, badgeW, 20, 10, t.secondaryContainer)
        g.drawString(font, badgeText, badgeX + 8, winY + (barH - 8) / 2, t.onSecondaryContainer, false)

        val closeY = winY + (barH - 22) / 2
        val hoverClose = mouseX in closeX..(closeX + 22) && mouseY in closeY..(closeY + 22)
        M3Draw.fillRoundedRect(g, closeX, closeY, 22, 22, 11, if (hoverClose) t.surfaceHigh else t.surfaceContainer)
        g.drawString(font, "X", closeX + 8, closeY + 7, t.onSurfaceVariant, false)
        registerClick(closeX, closeY, 22, 22) { onClose() }
    }

    private fun renderNavigationRail(g: GuiGraphics, mouseX: Int, mouseY: Int, barH: Int, railW: Int, t: M3Palette) {
        g.fill(winX + 1, winY + barH, winX + railW, winY + winH - 14, t.surfaceLow)
        g.fill(winX + railW, winY + barH, winX + railW + 1, winY + winH - 1, t.outlineVariant)

        val tabs = arrayOf(
            I18n.get("terrapaver.biome.tab.pick"),
            I18n.get("terrapaver.biome.tab.area"),
            I18n.get("terrapaver.biome.tab.height"),
            I18n.get("terrapaver.biome.tab.surface")
        )
        var y = winY + barH + 12
        for (i in tabs.indices) {
            val selected = (activeTab == i)
            val pillW = railW - 12
            val pillX = winX + 6
            val hover = mouseX in pillX..(pillX + pillW) && mouseY in y..(y + 34)
            val bg = when {
                selected -> t.primaryContainer
                hover -> t.surfaceHigh
                else -> 0x00000000
            }
            if ((bg ushr 24) > 0) {
                M3Draw.fillRoundedRect(g, pillX, y, pillW, 32, 14, bg)
            }
            val label = M3Draw.fitText(font, tabs[i], pillW - 6)
            val lx = pillX + (pillW - font.width(label)) / 2
            g.drawString(font, label, lx, y + 12, if (selected) t.onPrimaryContainer else t.onSurfaceVariant, false)
            registerClick(pillX, y, pillW, 32) {
                activeTab = i
                searchBox?.isFocused = (i == 0)
            }
            y += 38
        }
    }

    private fun renderBiomePickerTab(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        yStart: Int,
        t: M3Palette,
        partialTick: Float
    ): Int {
        var y = yStart
        val cardW = contentW - 8

        // Row 1: Search bar + Pick Feet + Pick Aim + Mod Filter cycle
        val modBtnW = 78
        val pickFeetW = 64
        val pickAimW = 64
        val searchW = (cardW - modBtnW - pickFeetW - pickAimW - 12).coerceAtLeast(80)
        M3Draw.fillRoundedRect(g, contentX, y, searchW, 24, 12, t.surfaceContainer)
        M3Draw.strokeRoundedRect(g, contentX, y, searchW, 24, 12, if (searchBox?.isFocused == true) t.primary else t.outlineVariant)
        searchBox?.let { sb ->
            sb.x = contentX + 10
            sb.y = y + 8
            sb.width = searchW - 20
            sb.setTextColor(t.onSurface)
            sb.render(g, mouseX, mouseY, partialTick)
        }
        var bx = contentX + searchW + 4
        drawActionButton(g, mouseX, mouseY, bx, y, pickFeetW, 24, I18n.get("terrapaver.biome.btn.pick_feet"), ButtonStyle.TONAL, t) {
            pickStandingBiome(asFilter = false)
        }
        bx += pickFeetW + 4
        drawActionButton(g, mouseX, mouseY, bx, y, pickAimW, 24, I18n.get("terrapaver.biome.btn.pick_aim"), ButtonStyle.TONAL, t) {
            pickAimedBiome()
        }
        bx += pickAimW + 4
        val modLabel = modNamespaces.getOrElse(selectedModIdx) { "" }.ifEmpty { I18n.get("terrapaver.biome.mod_all") }
        drawActionButton(g, mouseX, mouseY, bx, y, modBtnW, 24, modLabel, ButtonStyle.OUTLINED, t) {
            selectedModIdx = (selectedModIdx + 1) % max(1, modNamespaces.size)
            tabScroll[0] = 0
            rebuildFilter()
        }
        y += 28

        // Row 2: 9 Ecology & Dimension Categories
        val catKeys = arrayOf(
            "terrapaver.biome.cat.all",
            "terrapaver.biome.cat.forest",
            "terrapaver.biome.cat.cold",
            "terrapaver.biome.cat.dry",
            "terrapaver.biome.cat.lush",
            "terrapaver.biome.cat.water",
            "terrapaver.biome.cat.nether",
            "terrapaver.biome.cat.end",
            "terrapaver.biome.cat.modded"
        )
        val chipGap = 3
        val chipW = (cardW - chipGap * (catKeys.size - 1)) / catKeys.size
        for (i in catKeys.indices) {
            val cx = contentX + i * (chipW + chipGap)
            val sel = categoryFilter == i
            val hov = mouseX in cx..(cx + chipW) && mouseY in y..(y + 18)
            val bg = if (sel) t.primaryContainer else if (hov) t.surfaceHigh else t.surfaceContainer
            M3Draw.fillRoundedRect(g, cx, y, chipW, 18, 9, bg)
            if (!sel) M3Draw.strokeRoundedRect(g, cx, y, chipW, 18, 9, t.outlineVariant)
            val lbl = M3Draw.fitText(font, I18n.get(catKeys[i]), chipW - 4)
            g.drawString(font, lbl, cx + (chipW - font.width(lbl)) / 2, y + 5, if (sel) t.onPrimaryContainer else t.onSurfaceVariant, false)
            registerClick(cx, y, chipW, 18) {
                categoryFilter = i
                tabScroll[0] = 0
                rebuildFilter()
            }
        }
        y += 23

        // Row 3: Quick Favorites & Recent strip
        val quickIds = (cfg.favoriteBiomes + cfg.recentBiomes).distinct().take(6)
        if (quickIds.isNotEmpty()) {
            val qGap = 4
            val qW = (cardW - qGap * (quickIds.size - 1)) / quickIds.size
            for (i in quickIds.indices) {
                val qid = quickIds[i]
                val qx = contentX + i * (qW + qGap)
                val sel = qid == cfg.targetBiome
                val hov = mouseX in qx..(qx + qW) && mouseY in y..(y + 18)
                M3Draw.fillRoundedRect(g, qx, y, qW, 18, 9, if (sel) t.secondaryContainer else if (hov) t.surfaceHigh else t.surfaceLow)
                M3Draw.strokeRoundedRect(g, qx, y, qW, 18, 9, if (sel) t.primary else t.outlineVariant)
                val lbl = M3Draw.fitText(font, "* ${formatBiomeName(qid)}", qW - 6)
                g.drawString(font, lbl, qx + (qW - font.width(lbl)) / 2, y + 5, if (sel) t.onSecondaryContainer else t.onSurface, false)
                registerClick(qx, y, qW, 18) {
                    selectPrimaryBiome(qid)
                }
            }
            y += 23
        }

        // Row 4: 2-column scrollable biome cards with swatch, favorite star, and +Split quick button!
        val cols = if (cardW >= 320) 2 else 1
        val colGap = 6
        val itemW = (cardW - colGap * (cols - 1)) / cols
        val itemH = 34

        for (i in filteredBiomes.indices) {
            val b = filteredBiomes[i]
            val col = i % cols
            val row = i / cols
            val ix = contentX + col * (itemW + colGap)
            val iy = y + row * (itemH + 5)

            if (iy + itemH >= clipTop - 10 && iy <= clipBottom + 10) {
                val selected = b.id == cfg.targetBiome
                val inSplit = cfg.splitPalette.contains(b.id)
                val isFav = cfg.favoriteBiomes.contains(b.id)
                val hover = mouseX in ix..(ix + itemW) && mouseY in iy..(iy + itemH)
                val bg = when {
                    selected -> t.primaryContainer
                    hover -> t.surfaceHigh
                    else -> t.surfaceContainer
                }
                M3Draw.fillRoundedRect(g, ix, iy, itemW, itemH, 10, bg)
                M3Draw.strokeRoundedRect(g, ix, iy, itemW, itemH, 10, if (selected) t.primary else t.outlineVariant)

                // Grass + water swatch
                M3Draw.fillRoundedRect(g, ix + 6, iy + 6, 14, 22, 5, b.grassColor)
                M3Draw.fillRoundedRect(g, ix + 14, iy + 18, 9, 10, 4, b.waterColor)

                // Right-side mini buttons: [★] and [+Split]
                val starX = ix + itemW - 22
                val splitX = ix + itemW - 50
                val textX = ix + 27
                val maxTextW = (splitX - textX - 4).coerceAtLeast(36)

                val nameStr = M3Draw.fitText(font, b.displayName, maxTextW)
                val idStr = M3Draw.fitText(font, b.id, maxTextW)
                g.drawString(font, nameStr, textX, iy + 6, if (selected) t.onPrimaryContainer else t.onSurface, false)
                g.drawString(font, idStr, textX, iy + 19, if (selected) t.primary else t.onSurfaceVariant, false)

                // +Split button
                val splitBg = if (inSplit) t.secondaryContainer else t.surface
                M3Draw.fillRoundedRect(g, splitX, iy + 8, 25, 18, 6, splitBg)
                M3Draw.strokeRoundedRect(g, splitX, iy + 8, 25, 18, 6, t.outlineVariant)
                val spLbl = if (inSplit) "✓" else "+分"
                g.drawString(font, spLbl, splitX + (25 - font.width(spLbl)) / 2, iy + 13, if (inSplit) t.onSecondaryContainer else t.onSurfaceVariant, false)
                registerClick(splitX, iy + 8, 25, 18) {
                    toggleSplitBiome(b.id)
                }

                // Favorite ★ button
                M3Draw.fillRoundedRect(g, starX, iy + 8, 18, 18, 6, if (isFav) 0xFFFFF3C4.toInt() else t.surface)
                g.drawString(font, if (isFav) "★" else "☆", starX + 5, iy + 13, if (isFav) 0xFFB87800.toInt() else t.onSurfaceVariant, false)
                registerClick(starX, iy + 8, 18, 18) {
                    if (isFav) cfg.favoriteBiomes.remove(b.id) else cfg.favoriteBiomes.add(0, b.id)
                    save()
                }

                // Main card click selects primary target biome
                registerClick(ix, iy, splitX - ix - 2, itemH) {
                    selectPrimaryBiome(b.id)
                }
            }
        }
        val rows = (filteredBiomes.size + cols - 1) / cols
        return y + rows * (itemH + 5) + 6
    }

    private fun selectPrimaryBiome(id: String) {
        cfg.targetBiome = id
        cfg.recordRecent(id)
        if (!cfg.splitPalette.contains(id)) {
            if (cfg.splitPalette.isEmpty()) cfg.splitPalette.add(id) else cfg.splitPalette[0] = id
        }
        save()
    }

    private fun toggleSplitBiome(id: String) {
        if (cfg.splitPalette.contains(id) && cfg.splitPalette.size > 1) {
            cfg.splitPalette.remove(id)
        } else if (!cfg.splitPalette.contains(id)) {
            if (cfg.splitPalette.size >= 6) cfg.splitPalette.removeAt(cfg.splitPalette.lastIndex)
            cfg.splitPalette.add(id)
            cfg.secondaryBiome = id
        }
        if (cfg.splitMode == BiomeSplitMode.SINGLE && cfg.splitPalette.size >= 2) {
            cfg.splitMode = BiomeSplitMode.VORONOI_MIX
        }
        save()
    }

    private fun renderShapeSplitTab(g: GuiGraphics, mouseX: Int, mouseY: Int, yStart: Int, t: M3Palette): Int {
        var y = yStart
        val cardW = contentW - 8

        y = drawSectionCard(g, contentX, y, cardW, 134, I18n.get("terrapaver.biome.sec.shape"), t) { cx, cy, cw ->
            drawSegmentedEnum(
                g, mouseX, mouseY, cx, cy, cw, 26,
                BiomeShape.entries.toTypedArray(), cfg.shape,
                { I18n.get(it.key) }, t
            ) {
                cfg.shape = it
                save()
            }

            val presets = intArrayOf(8, 16, 32, 64, 128, 256)
            val gap = 4
            val pw = (cw - gap * (presets.size - 1)) / presets.size
            for (i in presets.indices) {
                val pVal = presets[i]
                val px = cx + i * (pw + gap)
                val py = cy + 32
                val sel = cfg.radius == pVal
                val hov = mouseX in px..(px + pw) && mouseY in py..(py + 20)
                M3Draw.fillRoundedRect(g, px, py, pw, 20, 10, if (sel) t.secondaryContainer else if (hov) t.surfaceHigh else t.surface)
                if (!sel) M3Draw.strokeRoundedRect(g, px, py, pw, 20, 10, t.outlineVariant)
                val txt = "${pVal}m"
                g.drawString(font, txt, px + (pw - font.width(txt)) / 2, py + 6, if (sel) t.onSecondaryContainer else t.onSurface, false)
                registerClick(px, py, pw, 20) {
                    cfg.radius = pVal
                    save()
                }
            }

            drawSliderRow(
                g, mouseX, mouseY, cx, cy + 58, cw,
                I18n.get("terrapaver.biome.cfg.radius"), "${cfg.radius} m",
                2, 256, { cfg.radius }, t
            ) {
                cfg.radius = it
                save()
            }
        }

        // Biome Split & Voronoi Mix Card!
        y = drawSectionCard(g, contentX, y, cardW, 134, I18n.get("terrapaver.biome.sec.split"), t) { cx, cy, cw ->
            drawSegmentedEnum(
                g, mouseX, mouseY, cx, cy, cw, 26,
                BiomeSplitMode.entries.toTypedArray(), cfg.splitMode,
                { I18n.get(it.key) }, t
            ) {
                cfg.splitMode = it
                save()
            }

            drawSliderRow(
                g, mouseX, mouseY, cx, cy + 32, cw,
                I18n.get("terrapaver.biome.cfg.patch_scale"), "${cfg.splitPatchScale} m",
                4, 64, { cfg.splitPatchScale }, t
            ) {
                cfg.splitPatchScale = it
                save()
            }

            // Palette chips + Swap Primary/Secondary button
            val swapW = 72
            val chipsW = cw - swapW - 6
            val nChips = cfg.splitPalette.size.coerceAtLeast(1)
            val gap = 4
            val cwEach = (chipsW - gap * (nChips - 1)) / nChips
            for (i in cfg.splitPalette.indices) {
                val bid = cfg.splitPalette[i]
                val bx = cx + i * (cwEach + gap)
                val by = cy + 68
                M3Draw.fillRoundedRect(g, bx, by, cwEach, 22, 9, t.surfaceLow)
                M3Draw.strokeRoundedRect(g, bx, by, cwEach, 22, 9, t.outlineVariant)
                val txt = M3Draw.fitText(font, "${formatBiomeName(bid)} ×", cwEach - 6)
                g.drawString(font, txt, bx + 4, by + 7, t.onSurface, false)
                registerClick(bx, by, cwEach, 22) {
                    if (cfg.splitPalette.size > 1) {
                        cfg.splitPalette.removeAt(i)
                        save()
                    }
                }
            }
            drawActionButton(g, mouseX, mouseY, cx + chipsW + 6, cy + 68, swapW, 22, I18n.get("terrapaver.biome.btn.swap"), ButtonStyle.TONAL, t) {
                val tmp = cfg.targetBiome
                cfg.targetBiome = cfg.secondaryBiome
                cfg.secondaryBiome = tmp
                save()
            }
        }

        y = drawSectionCard(g, contentX, y, cardW, 108, I18n.get("terrapaver.biome.sec.chunks_feather"), t) { cx, cy, cw ->
            val chunkSide = cfg.chunkRadius * 2 + 1
            drawSliderRow(
                g, mouseX, mouseY, cx, cy, cw,
                I18n.get("terrapaver.biome.cfg.chunk_grid"), "${chunkSide}x${chunkSide} chunks",
                0, 7, { cfg.chunkRadius }, t
            ) {
                cfg.chunkRadius = it
                save()
            }
            drawSliderRow(
                g, mouseX, mouseY, cx, cy + 36, cw,
                I18n.get("terrapaver.biome.cfg.feather"), "${cfg.edgeFeather} m",
                0, 32, { cfg.edgeFeather }, t
            ) {
                cfg.edgeFeather = it
                save()
            }
        }

        return y
    }

    private fun renderDimensionHeightTab(g: GuiGraphics, mouseX: Int, mouseY: Int, yStart: Int, t: M3Palette): Int {
        var y = yStart
        val cardW = contentW - 8

        // Loaded Dimensions Card (All Vanilla + Modded Dimensions!)
        val dims = ClientBiomeState.serverDimensions
        val dimRows = (dims.size + 1)
        val dimCols = if (cardW >= 320) 2 else 1
        val numGridRows = (dimRows + dimCols - 1) / dimCols
        val dimCardH = 38 + numGridRows * 26

        y = drawSectionCard(g, contentX, y, cardW, dimCardH, I18n.get("terrapaver.biome.sec.dimensions"), t) { cx, cy, cw ->
            val gap = 5
            val itemW = (cw - gap * (dimCols - 1)) / dimCols
            for (idx in 0 until dimRows) {
                val col = idx % dimCols
                val row = idx / dimCols
                val dx = cx + col * (itemW + gap)
                val dy = cy + row * 26

                val dimId = if (idx == 0) "" else dims[idx - 1].id
                val label = if (idx == 0) {
                    "${I18n.get("terrapaver.biome.dim_current")} (${shortDimName(ClientBiomeState.currentDimension)})"
                } else {
                    val info = dims[idx - 1]
                    "${shortDimName(info.id)} [${info.minY}..${info.maxY}]"
                }
                val sel = cfg.targetDimension == dimId
                val hov = mouseX in dx..(dx + itemW) && mouseY in dy..(dy + 22)
                M3Draw.fillRoundedRect(g, dx, dy, itemW, 22, 10, if (sel) t.primaryContainer else if (hov) t.surfaceHigh else t.surface)
                M3Draw.strokeRoundedRect(g, dx, dy, itemW, 22, 10, if (sel) t.primary else t.outlineVariant)
                val txt = M3Draw.fitText(font, label, itemW - 10)
                g.drawString(font, txt, dx + 6, dy + 7, if (sel) t.onPrimaryContainer else t.onSurface, false)
                registerClick(dx, dy, itemW, 22) {
                    cfg.targetDimension = dimId
                    val targetBounds = activeDimBounds()
                    cfg.customMinY = cfg.customMinY.coerceIn(targetBounds.first, targetBounds.second)
                    cfg.customMaxY = cfg.customMaxY.coerceIn(targetBounds.first, targetBounds.second)
                    save()
                }
            }
        }

        val (dimMinY, dimMaxY) = activeDimBounds()
        y = drawSectionCard(g, contentX, y, cardW, 180, I18n.get("terrapaver.biome.sec.vertical"), t) { cx, cy, cw ->
            drawSegmentedEnum(
                g, mouseX, mouseY, cx, cy, cw, 26,
                BiomeVerticalMode.entries.toTypedArray(), cfg.verticalMode,
                { I18n.get(it.key) }, t
            ) {
                cfg.verticalMode = it
                save()
            }

            drawSliderRow(
                g, mouseX, mouseY, cx, cy + 34, cw,
                I18n.get("terrapaver.biome.cfg.surf_down"), "${cfg.surfaceDepthDown} m",
                0, 128, { cfg.surfaceDepthDown }, t
            ) {
                cfg.surfaceDepthDown = it
                save()
            }
            drawSliderRow(
                g, mouseX, mouseY, cx, cy + 68, cw,
                I18n.get("terrapaver.biome.cfg.surf_up"), "${cfg.surfaceHeightUp} m",
                4, 256, { cfg.surfaceHeightUp }, t
            ) {
                cfg.surfaceHeightUp = it
                save()
            }
            drawSliderRow(
                g, mouseX, mouseY, cx, cy + 102, cw,
                I18n.get("terrapaver.biome.cfg.min_y"), "Y = ${cfg.customMinY}",
                dimMinY, dimMaxY, { cfg.customMinY.coerceIn(dimMinY, dimMaxY) }, t
            ) {
                cfg.customMinY = it
                if (cfg.customMinY > cfg.customMaxY) cfg.customMaxY = cfg.customMinY
                save()
            }
        }

        y = drawSectionCard(g, contentX, y, cardW, 146, I18n.get("terrapaver.biome.sec.filter"), t) { cx, cy, cw ->
            drawSliderRow(
                g, mouseX, mouseY, cx, cy, cw,
                I18n.get("terrapaver.biome.cfg.max_y"), "Y = ${cfg.customMaxY}",
                dimMinY, dimMaxY, { cfg.customMaxY.coerceIn(dimMinY, dimMaxY) }, t
            ) {
                cfg.customMaxY = it
                if (cfg.customMaxY < cfg.customMinY) cfg.customMinY = cfg.customMaxY
                save()
            }
            drawSwitchRow(g, mouseX, mouseY, cx, cy + 36, cw, I18n.get("terrapaver.biome.cfg.preserve_water"), cfg.preserveRiversAndOceans, t) {
                cfg.preserveRiversAndOceans = it
                save()
            }
            drawSwitchRow(g, mouseX, mouseY, cx, cy + 64, cw, I18n.get("terrapaver.biome.cfg.filter_source"), cfg.filterSourceBiome, t) {
                cfg.filterSourceBiome = it
                save()
            }
            drawActionButton(
                g, mouseX, mouseY, cx, cy + 92, cw, 22,
                I18n.get("terrapaver.biome.btn.pick_filter", formatBiomeName(cfg.sourceBiomeFilter)),
                ButtonStyle.OUTLINED, t
            ) {
                pickStandingBiome(asFilter = true)
            }
        }

        return y
    }

    private fun renderSurfaceActionsTab(g: GuiGraphics, mouseX: Int, mouseY: Int, yStart: Int, t: M3Palette): Int {
        var y = yStart
        val cardW = contentW - 8

        y = drawSectionCard(g, contentX, y, cardW, 96, I18n.get("terrapaver.biome.sec.surface"), t) { cx, cy, cw ->
            drawSwitchRow(g, mouseX, mouseY, cx, cy, cw, I18n.get("terrapaver.biome.cfg.adapt_surface"), cfg.adaptSurfaceBlocks, t) {
                cfg.adaptSurfaceBlocks = it
                save()
            }
            drawSwitchRow(g, mouseX, mouseY, cx, cy + 30, cw, I18n.get("terrapaver.biome.cfg.adapt_snow"), cfg.adaptWeatherSnow, t) {
                cfg.adaptWeatherSnow = it
                save()
            }
        }

        y = drawSectionCard(g, contentX, y, cardW, 138, I18n.get("terrapaver.biome.sec.overview"), t) { cx, cy, cw ->
            val dimTxt = if (cfg.targetDimension.isBlank()) {
                "${I18n.get("terrapaver.biome.dim_current")} (${ClientBiomeState.currentDimension})"
            } else {
                cfg.targetDimension
            }
            val lines = listOf(
                "${I18n.get("terrapaver.biome.target")}: ${formatBiomeName(cfg.targetBiome)} (${cfg.targetBiome})",
                "${I18n.get("terrapaver.biome.sec.split")}: ${I18n.get(cfg.splitMode.key)} (${cfg.splitPalette.size} biomes)",
                "${I18n.get("terrapaver.biome.sec.dimensions")}: $dimTxt",
                "${I18n.get("terrapaver.biome.sec.shape")}: ${I18n.get(cfg.shape.key)} (${cfg.radius}m, ${I18n.get("terrapaver.biome.cfg.feather")} ${cfg.edgeFeather}m)",
                "${I18n.get("terrapaver.biome.sec.vertical")}: ${I18n.get(cfg.verticalMode.key)}",
                I18n.get("terrapaver.biome.tip_fast")
            )
            var ly = cy
            for (line in lines) {
                g.drawString(font, M3Draw.fitText(font, line, cw), cx, ly, t.onSurfaceVariant, false)
                ly += 16
            }
        }

        return y
    }

    private fun renderActionBar(g: GuiGraphics, mouseX: Int, mouseY: Int, y: Int, h: Int, t: M3Palette) {
        val gap = 6
        val btnW = (contentW - gap * 3) / 4

        drawActionButton(
            g, mouseX, mouseY, contentX, y, btnW, h,
            I18n.get("terrapaver.biome.btn.apply_here"),
            ButtonStyle.FILLED, t
        ) {
            save()
            val player = Minecraft.getInstance().player ?: return@drawActionButton
            TerraPaverNet.sendToServer(TerraPaverNet.C2SApplyBiomeBrush(player.blockPosition(), cfg.toJson()))
            onClose()
        }

        drawActionButton(
            g, mouseX, mouseY, contentX + (btnW + gap), y, btnW, h,
            I18n.get("terrapaver.biome.btn.undo"),
            if (ClientBiomeState.canUndo) ButtonStyle.TONAL else ButtonStyle.OUTLINED, t
        ) {
            TerraPaverNet.sendToServer(TerraPaverNet.C2SBiomeControl(TerraPaverNet.BIOME_ACTION_UNDO))
        }

        drawActionButton(
            g, mouseX, mouseY, contentX + (btnW + gap) * 2, y, btnW, h,
            I18n.get("terrapaver.biome.btn.clear_box"),
            ButtonStyle.OUTLINED, t
        ) {
            TerraPaverNet.sendToServer(TerraPaverNet.C2SBiomeControl(TerraPaverNet.BIOME_ACTION_CLEAR))
        }

        drawActionButton(
            g, mouseX, mouseY, contentX + (btnW + gap) * 3, y, btnW, h,
            I18n.get("terrapaver.btn.save_close"),
            ButtonStyle.TONAL, t
        ) {
            save()
            onClose()
        }
    }

    private fun activeDimBounds(): Pair<Int, Int> {
        val target = if (cfg.targetDimension.isBlank()) ClientBiomeState.currentDimension else cfg.targetDimension
        val found = ClientBiomeState.serverDimensions.firstOrNull { it.id == target }
        return if (found != null) found.minY to found.maxY else -64 to 320
    }

    private fun shortDimName(idStr: String): String {
        val rl = ResourceLocation.tryParse(idStr) ?: return idStr
        return if (rl.namespace == "minecraft") rl.path.replace('_', ' ') else "${rl.namespace}:${rl.path}"
    }

    private fun pickStandingBiome(asFilter: Boolean) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        val key = level.getBiome(player.blockPosition()).unwrapKey().orElse(null)?.location()?.toString() ?: return
        if (asFilter) {
            cfg.sourceBiomeFilter = key
            cfg.filterSourceBiome = true
            save()
        } else {
            selectPrimaryBiome(key)
            allBiomes.sortWith(compareBy<BiomeEntry> { it.id != cfg.targetBiome }.thenBy { !cfg.favoriteBiomes.contains(it.id) }.thenBy { it.displayName })
            rebuildFilter()
        }
    }

    private fun pickAimedBiome() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        val start = player.getEyePosition(1.0f)
        val look = player.lookAngle
        val end = start.add(look.x * 96.0, look.y * 96.0, look.z * 96.0)
        val hit = level.clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        val pos = if (hit.type == HitResult.Type.BLOCK) hit.blockPos else player.blockPosition()
        val key = level.getBiome(pos).unwrapKey().orElse(null)?.location()?.toString() ?: return
        selectPrimaryBiome(key)
        allBiomes.sortWith(compareBy<BiomeEntry> { it.id != cfg.targetBiome }.thenBy { !cfg.favoriteBiomes.contains(it.id) }.thenBy { it.displayName })
        rebuildFilter()
    }

    private fun formatBiomeName(idStr: String): String {
        val rl = ResourceLocation.tryParse(idStr) ?: return idStr
        val k = "biome.${rl.namespace}.${rl.path}"
        val tr = I18n.get(k)
        return if (tr == k) rl.path.replace('_', ' ') else tr
    }

    private fun drawSectionCard(
        g: GuiGraphics,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        title: String,
        t: M3Palette,
        body: (cx: Int, cy: Int, cw: Int) -> Unit
    ): Int {
        M3Draw.fillRoundedRect(g, x, y, w, h, 14, t.surfaceContainer)
        M3Draw.strokeRoundedRect(g, x, y, w, h, 14, t.outlineVariant)
        g.drawString(font, M3Draw.fitText(font, title, w - 24), x + 12, y + 10, t.primary, false)
        body(x + 12, y + 26, w - 24)
        return y + h + 8
    }

    private fun <E : Enum<E>> drawSegmentedEnum(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        values: Array<E>,
        selected: E,
        labelFn: (E) -> String,
        t: M3Palette,
        onSelect: (E) -> Unit
    ) {
        val segW = w / values.size
        M3Draw.fillRoundedRect(g, x, y, w, h, h / 2, t.surfaceHigh)
        M3Draw.strokeRoundedRect(g, x, y, w, h, h / 2, t.outlineVariant)
        for (i in values.indices) {
            val sx = x + i * segW
            val sw = if (i == values.lastIndex) w - i * segW else segW
            val isSel = values[i] == selected
            val hover = mouseX in sx..(sx + sw) && mouseY in y..(y + h)
            if (isSel) {
                M3Draw.fillRoundedRect(g, sx + 2, y + 2, sw - 4, h - 4, (h - 4) / 2, t.primaryContainer)
            } else if (hover) {
                M3Draw.fillRoundedRect(g, sx + 2, y + 2, sw - 4, h - 4, (h - 4) / 2, M3Theme.withAlpha(t.primary, 18))
            }
            val label = M3Draw.fitText(font, labelFn(values[i]), sw - 8)
            val tx = sx + (sw - font.width(label)) / 2
            g.drawString(font, label, tx, y + (h - 8) / 2, if (isSel) t.onPrimaryContainer else t.onSurfaceVariant, false)
            registerClick(sx, y, sw, h) { onSelect(values[i]) }
        }
    }

    private fun drawSwitchRow(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        x: Int,
        y: Int,
        w: Int,
        label: String,
        checked: Boolean,
        t: M3Palette,
        onToggle: (Boolean) -> Unit
    ) {
        val rowH = 24
        val hover = mouseX in x..(x + w) && mouseY in y..(y + rowH)
        if (hover) {
            M3Draw.fillRoundedRect(g, x - 4, y, w + 8, rowH, 8, M3Theme.withAlpha(t.primary, 14))
        }
        g.drawString(font, M3Draw.fitText(font, label, w - 48), x, y + 8, t.onSurface, false)

        val sw = 38
        val sh = 20
        val sx = x + w - sw
        val sy = y + 2
        M3Draw.fillRoundedRect(g, sx, sy, sw, sh, 10, if (checked) t.primary else t.surfaceHigh)
        M3Draw.strokeRoundedRect(g, sx, sy, sw, sh, 10, if (checked) t.primary else t.outline)
        val thumbR = if (checked) 7 else 5
        val thumbX = if (checked) sx + sw - 10 else sx + 10
        M3Draw.fillCircle(g, thumbX, sy + sh / 2, thumbR, if (checked) t.onPrimary else t.outline)

        registerClick(x, y, w, rowH) { onToggle(!checked) }
    }

    private fun drawSliderRow(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        x: Int,
        y: Int,
        w: Int,
        label: String,
        valueText: String,
        minVal: Int,
        maxVal: Int,
        current: () -> Int,
        t: M3Palette,
        onChange: (Int) -> Unit
    ) {
        val labelMax = (w - font.width(valueText) - 10).coerceAtLeast(40)
        g.drawString(font, M3Draw.fitText(font, label, labelMax), x, y + 1, t.onSurface, false)
        val vx = x + w - font.width(valueText)
        g.drawString(font, valueText, vx, y + 1, t.primary, false)

        val trackY = y + 15
        val trackH = 6
        val ratio = ((current() - minVal).toFloat() / max(1, maxVal - minVal).toFloat()).coerceIn(0f, 1f)
        val activeW = (w * ratio).roundToInt()

        M3Draw.fillRoundedRect(g, x, trackY, w, trackH, 3, t.surfaceHigh)
        if (activeW > 0) {
            M3Draw.fillRoundedRect(g, x, trackY, activeW, trackH, 3, t.primary)
        }
        val knobX = (x + activeW).coerceIn(x + 6, x + w - 6)
        val hover = mouseX in x..(x + w) && mouseY in (y + 10)..(y + 28)
        if (hover) {
            M3Draw.fillCircle(g, knobX, trackY + 3, 10, M3Theme.withAlpha(t.primary, 38))
        }
        M3Draw.fillCircle(g, knobX, trackY + 3, 6, t.primary)

        if (y + 28 >= clipTop && y + 10 <= clipBottom) {
            sliderRegions.add(SliderRegion(x, max(clipTop, y + 10), w, 18, minVal, maxVal, current, onChange))
        }
    }

    private fun drawActionButton(
        g: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        label: String,
        style: ButtonStyle,
        t: M3Palette,
        onClick: () -> Unit
    ) {
        val hover = mouseX in x..(x + w) && mouseY in y..(y + h)
        val bg = when (style) {
            ButtonStyle.FILLED -> if (hover) M3Theme.blend(t.primary, 0xFF000000.toInt(), 0.10f) else t.primary
            ButtonStyle.TONAL -> if (hover) M3Theme.blend(t.secondaryContainer, t.primary, 0.12f) else t.secondaryContainer
            ButtonStyle.OUTLINED -> if (hover) t.surfaceHigh else t.surface
            ButtonStyle.DANGER -> if (hover) M3Theme.blend(t.errorContainer, t.error, 0.14f) else t.errorContainer
        }
        val fg = when (style) {
            ButtonStyle.FILLED -> t.onPrimary
            ButtonStyle.TONAL -> t.onSecondaryContainer
            ButtonStyle.OUTLINED -> t.primary
            ButtonStyle.DANGER -> t.onErrorContainer
        }
        M3Draw.fillRoundedRect(g, x, y, w, h, h / 2, bg)
        if (style == ButtonStyle.OUTLINED) {
            M3Draw.strokeRoundedRect(g, x, y, w, h, h / 2, t.outline)
        }
        val txt = M3Draw.fitText(font, label, w - 10)
        g.drawString(font, txt, x + (w - font.width(txt)) / 2, y + (h - 8) / 2, fg, false)
        registerClick(x, y, w, h, onClick)
    }

    private fun registerClick(x: Int, y: Int, w: Int, h: Int, onClick: () -> Unit) {
        val top = max(y, clipTop)
        val bot = min(y + h, clipBottom)
        if (bot > top) {
            clickRegions.add(ClickRegion(x, top, w, bot - top, onClick))
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            if (searchBox?.isMouseOver(mouseX, mouseY) == true) return true
        }
        if (button != 0) return false
        val mx = mouseX.roundToInt()
        val my = mouseY.roundToInt()

        searchBox?.let { sb ->
            if (activeTab == 0 && mx in sb.x..(sb.x + sb.width) && my in (sb.y - 6)..(sb.y + sb.height + 6)) {
                sb.isFocused = true
                focused = sb
                return true
            } else {
                sb.isFocused = false
            }
        }

        for (sr in sliderRegions.asReversed()) {
            if (mx in sr.x..(sr.x + sr.w) && my in sr.y..(sr.y + sr.h)) {
                draggingSlider = sr
                applySliderAt(sr, mx)
                return true
            }
        }
        for (cr in clickRegions.asReversed()) {
            if (mx in cr.x..(cr.x + cr.w) && my in cr.y..(cr.y + cr.h)) {
                cr.onClick()
                return true
            }
        }
        return false
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        val sr = draggingSlider
        if (button == 0 && sr != null) {
            applySliderAt(sr, mouseX.roundToInt())
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= clipTop && mouseY <= clipBottom) {
            val delta = (-scrollY * 22.0).roundToInt()
            tabScroll[activeTab] = (tabScroll[activeTab] + delta).coerceAtLeast(0)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    private fun applySliderAt(sr: SliderRegion, mx: Int) {
        val ratio = ((mx - sr.x).toFloat() / max(1, sr.w).toFloat()).coerceIn(0f, 1f)
        val v = (sr.minVal + ratio * (sr.maxVal - sr.minVal)).roundToInt().coerceIn(sr.minVal, sr.maxVal)
        if (v != sr.current()) {
            sr.onChange(v)
        }
    }
}
