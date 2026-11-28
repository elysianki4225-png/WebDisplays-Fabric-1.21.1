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
        winW = min(680, width - 16).coerceAtLeast(min(360, width - 8))
        winH = min(444, height - 14).coerceAtLeast(min(230, height - 8))
        winX = (width - winW) / 2
        winY = (height - winH) / 2

        val barH = if (winH < 270) 38 else 46
        val railW = if (winW < 440) 64 else 82
        val sx = winX + railW + 26
        val sy = winY + barH + 18
        val sw = (winW - railW - 44 - 160).coerceAtLeast(90)

        val box = EditBox(font, sx, sy, sw, 18, Component.empty())
        box.setMaxLength(64)
        box.isBordered = false
        box.setTextColor(theme().onSurface)
        // We draw our own cleanly fitted hint so Minecraft's EditBox never overflows horizontally
        box.setHint(Component.empty())
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

        M3Draw.drawElevationShadow(g, winX, winY, winW, winH, 20, 4)
        M3Draw.fillRoundedRect(g, winX, winY, winW, winH, 20, t.surface)
        M3Draw.strokeRoundedRect(g, winX, winY, winW, winH, 20, M3Theme.withAlpha(t.outlineVariant, 170), 0.9f)

        val barH = if (winH < 270) 38 else 46
        val railW = if (winW < 440) 64 else 82

        renderTopAppBar(g, mouseX, mouseY, barH, t)
        renderNavigationRail(g, mouseX, mouseY, barH, railW, t)

        contentX = winX + railW + 16
        contentY = winY + barH + 10
        contentW = winW - railW - 28
        contentH = winH - barH - 18

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
            val thumbH = max(24, (bodyH * bodyH) / max(bodyH + 1, lastContentHeight))
            val thumbY = contentY + ((bodyH - thumbH) * (tabScroll[activeTab].toFloat() / maxScroll.toFloat())).toInt()
            M3Draw.fillRoundedRect(g, trackX, contentY + 2, 3, bodyH - 4, 2, t.surfaceHigh)
            M3Draw.fillRoundedRect(g, trackX, thumbY, 3, thumbH, 2, M3Theme.withAlpha(t.outline, 160))
        }

        clipTop = 0
        clipBottom = height
        renderActionBar(g, mouseX, mouseY, contentY + bodyH + 6, actionBarH - 6, t)
    }

    private fun renderTopAppBar(g: GuiGraphics, mouseX: Int, mouseY: Int, barH: Int, t: M3Palette) {
        M3Draw.fillRoundedRect(g, winX + 1, winY + 1, winW - 2, barH - 1, 19, t.surfaceLow)
        g.fill(winX + 1, winY + barH - 1, winX + winW - 1, winY + barH, M3Theme.withAlpha(t.outlineVariant, 130))

        M3Draw.fillCircleF(g, winX + 22f, winY + barH * 0.5f, 7f, 0xFF1B8A5A.toInt())
        val titleText = I18n.get("terrapaver.biome.gui.title")
        val titleX = winX + 36
        val closeX = winX + winW - 36

        val currentBiomeName = formatBiomeName(cfg.targetBiome)
        val badgeText = M3Draw.fitText(font, currentBiomeName, 140)
        val badgeW = font.width(badgeText) + 20
        val badgeX = closeX - badgeW - 10
        M3Draw.fillRoundedRect(g, badgeX, winY + (barH - 22) / 2, badgeW, 22, 11, t.secondaryContainer)
        g.drawString(font, badgeText, badgeX + 10, winY + (barH - 8) / 2, t.onSecondaryContainer, false)

        g.drawString(font, M3Draw.fitText(font, titleText, badgeX - titleX - 12), titleX, winY + (barH - 8) / 2, t.onSurface, false)

        val closeY = winY + (barH - 24) / 2
        val hoverClose = mouseX in closeX..(closeX + 24) && mouseY in closeY..(closeY + 24)
        M3Draw.fillRoundedRect(g, closeX, closeY, 24, 24, 12, if (hoverClose) t.surfaceHigh else t.surfaceContainer)
        g.drawString(font, "×", closeX + 9, closeY + 8, t.onSurfaceVariant, false)
        registerClick(closeX, closeY, 24, 24) { onClose() }
    }

    private fun renderNavigationRail(g: GuiGraphics, mouseX: Int, mouseY: Int, barH: Int, railW: Int, t: M3Palette) {
        g.fill(winX + 1, winY + barH, winX + railW, winY + winH - 16, t.surfaceLow)
        g.fill(winX + railW, winY + barH, winX + railW + 1, winY + winH - 1, M3Theme.withAlpha(t.outlineVariant, 130))

        val tabs = arrayOf(
            I18n.get("terrapaver.biome.tab.pick"),
            I18n.get("terrapaver.biome.tab.area"),
            I18n.get("terrapaver.biome.tab.height"),
            I18n.get("terrapaver.biome.tab.surface")
        )
        var y = winY + barH + 14
        for (i in tabs.indices) {
            val selected = (activeTab == i)
            val pillW = railW - 14
            val pillX = winX + 7
            val hover = mouseX in pillX..(pillX + pillW) && mouseY in y..(y + 34)
            val bg = when {
                selected -> t.primaryContainer
                hover -> t.surfaceHigh
                else -> 0x00000000
            }
            if ((bg ushr 24) > 0) {
                M3Draw.fillRoundedRect(g, pillX, y, pillW, 34, 15, bg)
            }
            val label = M3Draw.fitText(font, tabs[i], pillW - 8)
            val lx = pillX + (pillW - font.width(label)) / 2
            g.drawString(font, label, lx, y + 13, if (selected) t.onPrimaryContainer else t.onSurfaceVariant, false)
            registerClick(pillX, y, pillW, 34) {
                activeTab = i
                searchBox?.isFocused = (i == 0)
            }
            y += 42
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

        // Row 1: Spacious Search Pill + 2 Clean Eyedropper Buttons (Pick Feet, Pick Aim)
        val pickFeetLabel = I18n.get("terrapaver.biome.btn.pick_feet")
        val pickAimLabel = I18n.get("terrapaver.biome.btn.pick_aim")
        val pickFeetW = max(68, font.width(pickFeetLabel) + 20)
        val pickAimW = max(68, font.width(pickAimLabel) + 20)
        val searchW = (cardW - pickFeetW - pickAimW - 12).coerceAtLeast(100)

        val focused = searchBox?.isFocused == true
        M3Draw.fillRoundedRect(g, contentX, y, searchW, 28, 14, t.card)
        M3Draw.strokeRoundedRect(
            g, contentX, y, searchW, 28, 14,
            if (focused) t.primary else M3Theme.withAlpha(t.outlineVariant, 180),
            if (focused) 1.3f else 0.9f
        )

        searchBox?.let { sb ->
            val sbX = contentX + 12
            val sbY = y + 10
            val sbW = (searchW - 24).coerceAtLeast(24)
            sb.x = sbX
            sb.y = sbY
            sb.width = sbW
            sb.setTextColor(t.onSurface)
            if (sb.value.isEmpty()) {
                val hint = M3Draw.fitText(font, I18n.get("terrapaver.biome.search_hint"), sbW)
                g.drawString(font, hint, sbX, sbY, t.outline, false)
            }
            g.enableScissor(sbX - 1, y + 2, sbX + sbW + 1, y + 26)
            sb.render(g, mouseX, mouseY, partialTick)
            g.disableScissor()
        }

        var bx = contentX + searchW + 6
        drawActionButton(g, mouseX, mouseY, bx, y + 1, pickFeetW, 26, pickFeetLabel, ButtonStyle.TONAL, t) {
            pickStandingBiome(asFilter = false)
        }
        bx += pickFeetW + 6
        drawActionButton(g, mouseX, mouseY, bx, y + 1, pickAimW, 26, pickAimLabel, ButtonStyle.TONAL, t) {
            pickAimedBiome()
        }
        y += 35

        // Row 2: CSS Flexbox-style auto-wrapping Category Chips (+ Mod filter chip when modded biomes exist)
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
        var chipX = contentX
        val chipH = 21
        val chipGapX = 5
        val chipGapY = 5

        for (i in catKeys.indices) {
            // Hide "Modded" tab if there are no non-vanilla biomes loaded
            if (i == 8 && modNamespaces.size <= 2 && allBiomes.none { it.namespace != "minecraft" }) continue
            val lbl = I18n.get(catKeys[i])
            val cw = font.width(lbl) + 18
            if (chipX > contentX && chipX + cw > contentX + cardW) {
                chipX = contentX
                y += chipH + chipGapY
            }
            val sel = categoryFilter == i
            val hov = mouseX in chipX..(chipX + cw) && mouseY in y..(y + chipH)
            val bg = if (sel) t.primaryContainer else if (hov) t.surfaceHigh else t.card
            M3Draw.fillRoundedRect(g, chipX, y, cw, chipH, 10, bg)
            if (!sel) M3Draw.strokeRoundedRect(g, chipX, y, cw, chipH, 10, M3Theme.withAlpha(t.outlineVariant, 150), 0.85f)
            g.drawString(font, lbl, chipX + 9, y + 7, if (sel) t.onPrimaryContainer else t.onSurfaceVariant, false)
            registerClick(chipX, y, cw, chipH) {
                categoryFilter = i
                tabScroll[0] = 0
                rebuildFilter()
            }
            chipX += cw + chipGapX
        }

        if (modNamespaces.size > 2) {
            val modName = modNamespaces.getOrElse(selectedModIdx) { "" }.ifEmpty { I18n.get("terrapaver.biome.mod_all") }
            val modLbl = M3Draw.fitText(font, modName, 110)
            val mw = font.width(modLbl) + 18
            if (chipX > contentX && chipX + mw > contentX + cardW) {
                chipX = contentX
                y += chipH + chipGapY
            }
            val modActive = selectedModIdx != 0
            val hov = mouseX in chipX..(chipX + mw) && mouseY in y..(y + chipH)
            M3Draw.fillRoundedRect(g, chipX, y, mw, chipH, 10, if (modActive) t.secondaryContainer else if (hov) t.surfaceHigh else t.card)
            M3Draw.strokeRoundedRect(g, chipX, y, mw, chipH, 10, if (modActive) t.primary else M3Theme.withAlpha(t.outlineVariant, 160), 0.9f)
            g.drawString(font, modLbl, chipX + 9, y + 7, if (modActive) t.onSecondaryContainer else t.primary, false)
            registerClick(chipX, y, mw, chipH) {
                selectedModIdx = (selectedModIdx + 1) % max(1, modNamespaces.size)
                tabScroll[0] = 0
                rebuildFilter()
            }
        }
        y += chipH + 8

        // Row 3: Quick Favorites Strip (shown cleanly only when user has pinned favorites)
        val favIds = cfg.favoriteBiomes.distinct().take(5)
        if (favIds.isNotEmpty()) {
            var fx = contentX
            for (fid in favIds) {
                val lbl = M3Draw.fitText(font, "★ ${formatBiomeName(fid)}", 112)
                val fw = font.width(lbl) + 16
                if (fx > contentX && fx + fw > contentX + cardW) break
                val sel = fid == cfg.targetBiome
                val hov = mouseX in fx..(fx + fw) && mouseY in y..(y + 20)
                M3Draw.fillRoundedRect(g, fx, y, fw, 20, 10, if (sel) t.secondaryContainer else if (hov) t.surfaceHigh else t.surfaceLow)
                M3Draw.strokeRoundedRect(g, fx, y, fw, 20, 10, if (sel) t.primary else M3Theme.withAlpha(t.outlineVariant, 150), 0.85f)
                g.drawString(font, lbl, fx + 8, y + 6, if (sel) t.onSecondaryContainer else t.onSurface, false)
                registerClick(fx, y, fw, 20) {
                    selectPrimaryBiome(fid)
                }
                fx += fw + 5
            }
            y += 26
        }

        // Row 4: Clean Elevated 2-Column Biome Cards
        val cols = if (cardW >= 340) 2 else 1
        val colGap = 8
        val itemW = (cardW - colGap * (cols - 1)) / cols
        val itemH = 38
        val splitAddLbl = I18n.get("terrapaver.biome.btn.split_add")

        for (i in filteredBiomes.indices) {
            val b = filteredBiomes[i]
            val col = i % cols
            val row = i / cols
            val ix = contentX + col * (itemW + colGap)
            val iy = y + row * (itemH + 6)

            if (iy + itemH >= clipTop - 10 && iy <= clipBottom + 10) {
                val selected = b.id == cfg.targetBiome
                val inSplit = cfg.splitPalette.contains(b.id)
                val isFav = cfg.favoriteBiomes.contains(b.id)
                val hover = mouseX in ix..(ix + itemW) && mouseY in iy..(iy + itemH)
                val bg = when {
                    selected -> t.primaryContainer
                    hover -> t.surfaceLow
                    else -> t.card
                }
                M3Draw.fillRoundedRect(g, ix, iy, itemW, itemH, 12, bg)
                M3Draw.strokeRoundedRect(
                    g, ix, iy, itemW, itemH, 12,
                    if (selected) t.primary else M3Theme.withAlpha(t.outlineVariant, 155),
                    if (selected) 1.35f else 0.85f
                )

                // Grass + water smooth swatch
                M3Draw.fillRoundedRect(g, ix + 8, iy + 7, 14, 24, 6, b.grassColor)
                M3Draw.fillRoundedRect(g, ix + 15, iy + 20, 9, 11, 4, b.waterColor)

                val starW = 20
                val spLbl = if (inSplit) "✓" else splitAddLbl
                val splitW = max(28, font.width(spLbl) + 12)
                val starX = ix + itemW - starW - 7
                val splitX = starX - splitW - 4
                val textX = ix + 30
                val maxTextW = (splitX - textX - 6).coerceAtLeast(36)

                val nameStr = M3Draw.fitText(font, b.displayName, maxTextW)
                val idStr = M3Draw.fitText(font, b.id, maxTextW)
                g.drawString(font, nameStr, textX, iy + 7, if (selected) t.onPrimaryContainer else t.onSurface, false)
                g.drawString(font, idStr, textX, iy + 21, if (selected) t.primary else t.onSurfaceVariant, false)

                // +Mix / ✓ button
                val splitBg = if (inSplit) t.secondaryContainer else t.surfaceContainer
                M3Draw.fillRoundedRect(g, splitX, iy + 9, splitW, 20, 10, splitBg)
                if (!inSplit) M3Draw.strokeRoundedRect(g, splitX, iy + 9, splitW, 20, 10, M3Theme.withAlpha(t.outlineVariant, 140), 0.8f)
                g.drawString(font, spLbl, splitX + (splitW - font.width(spLbl)) / 2, iy + 15, if (inSplit) t.onSecondaryContainer else t.onSurfaceVariant, false)
                registerClick(splitX, iy + 9, splitW, 20) {
                    toggleSplitBiome(b.id)
                }

                // Favorite ★ button
                M3Draw.fillRoundedRect(g, starX, iy + 9, starW, 20, 10, if (isFav) 0xFFFFF3C4.toInt() else t.surfaceContainer)
                g.drawString(font, if (isFav) "★" else "☆", starX + 6, iy + 15, if (isFav) 0xFFB87800.toInt() else t.onSurfaceVariant, false)
                registerClick(starX, iy + 9, starW, 20) {
                    if (isFav) cfg.favoriteBiomes.remove(b.id) else cfg.favoriteBiomes.add(0, b.id)
                    save()
                }

                registerClick(ix, iy, splitX - ix - 2, itemH) {
                    selectPrimaryBiome(b.id)
                }
            }
        }
        val rows = (filteredBiomes.size + cols - 1) / cols
        return y + rows * (itemH + 6) + 8
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

        y = drawSectionCard(g, contentX, y, cardW, 138, I18n.get("terrapaver.biome.sec.shape"), t) { cx, cy, cw ->
            drawSegmentedEnum(
                g, mouseX, mouseY, cx, cy, cw, 26,
                BiomeShape.entries.toTypedArray(), cfg.shape,
                { I18n.get(it.key) }, t
            ) {
                cfg.shape = it
                save()
            }

            val presets = intArrayOf(8, 16, 32, 64, 128, 256)
            val gap = 6
            val pw = (cw - gap * (presets.size - 1)) / presets.size
            for (i in presets.indices) {
                val pVal = presets[i]
                val px = cx + i * (pw + gap)
                val py = cy + 33
                val sel = cfg.radius == pVal
                val hov = mouseX in px..(px + pw) && mouseY in py..(py + 21)
                M3Draw.fillRoundedRect(g, px, py, pw, 21, 10, if (sel) t.secondaryContainer else if (hov) t.surfaceHigh else t.surfaceLow)
                if (!sel) M3Draw.strokeRoundedRect(g, px, py, pw, 21, 10, M3Theme.withAlpha(t.outlineVariant, 150), 0.85f)
                val txt = "${pVal}m"
                g.drawString(font, txt, px + (pw - font.width(txt)) / 2, py + 7, if (sel) t.onSecondaryContainer else t.onSurface, false)
                registerClick(px, py, pw, 21) {
                    cfg.radius = pVal
                    save()
                }
            }

            drawSliderRow(
                g, mouseX, mouseY, cx, cy + 61, cw,
                I18n.get("terrapaver.biome.cfg.radius"), "${cfg.radius} m",
                2, 256, { cfg.radius }, t
            ) {
                cfg.radius = it
                save()
            }
        }

        y = drawSectionCard(g, contentX, y, cardW, 138, I18n.get("terrapaver.biome.sec.split"), t) { cx, cy, cw ->
            drawSegmentedEnum(
                g, mouseX, mouseY, cx, cy, cw, 26,
                BiomeSplitMode.entries.toTypedArray(), cfg.splitMode,
                { I18n.get(it.key) }, t
            ) {
                cfg.splitMode = it
                save()
            }

            drawSliderRow(
                g, mouseX, mouseY, cx, cy + 34, cw,
                I18n.get("terrapaver.biome.cfg.patch_scale"), "${cfg.splitPatchScale} m",
                4, 64, { cfg.splitPatchScale }, t
            ) {
                cfg.splitPatchScale = it
                save()
            }

            val swapLbl = I18n.get("terrapaver.biome.btn.swap")
            val swapW = max(72, font.width(swapLbl) + 18)
            val chipsW = cw - swapW - 8
            val nChips = cfg.splitPalette.size.coerceAtLeast(1)
            val gap = 5
            val cwEach = (chipsW - gap * (nChips - 1)) / nChips
            for (i in cfg.splitPalette.indices) {
                val bid = cfg.splitPalette[i]
                val bx = cx + i * (cwEach + gap)
                val by = cy + 70
                M3Draw.fillRoundedRect(g, bx, by, cwEach, 22, 11, t.surfaceLow)
                M3Draw.strokeRoundedRect(g, bx, by, cwEach, 22, 11, M3Theme.withAlpha(t.outlineVariant, 160), 0.85f)
                val txt = M3Draw.fitText(font, "${formatBiomeName(bid)} ×", cwEach - 8)
                g.drawString(font, txt, bx + 5, by + 7, t.onSurface, false)
                registerClick(bx, by, cwEach, 22) {
                    if (cfg.splitPalette.size > 1) {
                        cfg.splitPalette.removeAt(i)
                        save()
                    }
                }
            }
            drawActionButton(g, mouseX, mouseY, cx + chipsW + 8, cy + 70, swapW, 22, swapLbl, ButtonStyle.TONAL, t) {
                val tmp = cfg.targetBiome
                cfg.targetBiome = cfg.secondaryBiome
                cfg.secondaryBiome = tmp
                save()
            }
        }

        y = drawSectionCard(g, contentX, y, cardW, 110, I18n.get("terrapaver.biome.sec.chunks_feather"), t) { cx, cy, cw ->
            val chunkSide = cfg.chunkRadius * 2 + 1
            drawSliderRow(
                g, mouseX, mouseY, cx, cy, cw,
                I18n.get("terrapaver.biome.cfg.chunk_grid"),
                I18n.get("terrapaver.biome.fmt.chunks", chunkSide, chunkSide),
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

        val dims = ClientBiomeState.serverDimensions
        val dimRows = (dims.size + 1)
        val dimCols = if (cardW >= 320) 2 else 1
        val numGridRows = (dimRows + dimCols - 1) / dimCols
        val dimCardH = 40 + numGridRows * 28

        y = drawSectionCard(g, contentX, y, cardW, dimCardH, I18n.get("terrapaver.biome.sec.dimensions"), t) { cx, cy, cw ->
            val gap = 6
            val itemW = (cw - gap * (dimCols - 1)) / dimCols
            for (idx in 0 until dimRows) {
                val col = idx % dimCols
                val row = idx / dimCols
                val dx = cx + col * (itemW + gap)
                val dy = cy + row * 28

                val dimId = if (idx == 0) "" else dims[idx - 1].id
                val label = if (idx == 0) {
                    "${I18n.get("terrapaver.biome.dim_current")} (${shortDimName(ClientBiomeState.currentDimension)})"
                } else {
                    val info = dims[idx - 1]
                    "${shortDimName(info.id)} [${info.minY}..${info.maxY}]"
                }
                val sel = cfg.targetDimension == dimId
                val hov = mouseX in dx..(dx + itemW) && mouseY in dy..(dy + 23)
                M3Draw.fillRoundedRect(g, dx, dy, itemW, 23, 11, if (sel) t.primaryContainer else if (hov) t.surfaceHigh else t.surfaceLow)
                M3Draw.strokeRoundedRect(
                    g, dx, dy, itemW, 23, 11,
                    if (sel) t.primary else M3Theme.withAlpha(t.outlineVariant, 155),
                    if (sel) 1.25f else 0.85f
                )
                val txt = M3Draw.fitText(font, label, itemW - 12)
                g.drawString(font, txt, dx + 7, dy + 8, if (sel) t.onPrimaryContainer else t.onSurface, false)
                registerClick(dx, dy, itemW, 23) {
                    cfg.targetDimension = dimId
                    val targetBounds = activeDimBounds()
                    cfg.customMinY = cfg.customMinY.coerceIn(targetBounds.first, targetBounds.second)
                    cfg.customMaxY = cfg.customMaxY.coerceIn(targetBounds.first, targetBounds.second)
                    save()
                }
            }
        }

        val (dimMinY, dimMaxY) = activeDimBounds()
        y = drawSectionCard(g, contentX, y, cardW, 182, I18n.get("terrapaver.biome.sec.vertical"), t) { cx, cy, cw ->
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

        y = drawSectionCard(g, contentX, y, cardW, 148, I18n.get("terrapaver.biome.sec.filter"), t) { cx, cy, cw ->
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
                g, mouseX, mouseY, cx, cy + 94, cw, 23,
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

        y = drawSectionCard(g, contentX, y, cardW, 98, I18n.get("terrapaver.biome.sec.surface"), t) { cx, cy, cw ->
            drawSwitchRow(g, mouseX, mouseY, cx, cy, cw, I18n.get("terrapaver.biome.cfg.adapt_surface"), cfg.adaptSurfaceBlocks, t) {
                cfg.adaptSurfaceBlocks = it
                save()
            }
            drawSwitchRow(g, mouseX, mouseY, cx, cy + 30, cw, I18n.get("terrapaver.biome.cfg.adapt_snow"), cfg.adaptWeatherSnow, t) {
                cfg.adaptWeatherSnow = it
                save()
            }
        }

        y = drawSectionCard(g, contentX, y, cardW, 144, I18n.get("terrapaver.biome.sec.overview"), t) { cx, cy, cw ->
            val dimTxt = if (cfg.targetDimension.isBlank()) {
                "${I18n.get("terrapaver.biome.dim_current")} (${shortDimName(ClientBiomeState.currentDimension)})"
            } else {
                shortDimName(cfg.targetDimension)
            }
            val lines = listOf(
                "${I18n.get("terrapaver.biome.target")}: ${formatBiomeName(cfg.targetBiome)} (${cfg.targetBiome})",
                "${I18n.get("terrapaver.biome.sec.split")}: ${I18n.get(cfg.splitMode.key)} (${I18n.get("terrapaver.biome.fmt.palette_count", cfg.splitPalette.size)})",
                "${I18n.get("terrapaver.biome.sec.dimensions")}: $dimTxt",
                "${I18n.get("terrapaver.biome.sec.shape")}: ${I18n.get(cfg.shape.key)} (${cfg.radius}m, ${I18n.get("terrapaver.biome.cfg.feather")} ${cfg.edgeFeather}m)",
                "${I18n.get("terrapaver.biome.sec.vertical")}: ${I18n.get(cfg.verticalMode.key)}",
                I18n.get("terrapaver.biome.tip_fast")
            )
            var ly = cy
            for (line in lines) {
                g.drawString(font, M3Draw.fitText(font, line, cw), cx, ly, t.onSurfaceVariant, false)
                ly += 17
            }
        }

        return y
    }

    private fun renderActionBar(g: GuiGraphics, mouseX: Int, mouseY: Int, y: Int, h: Int, t: M3Palette) {
        g.fill(contentX, y - 4, contentX + contentW, y - 3, M3Theme.withAlpha(t.outlineVariant, 110))

        val undoLbl = I18n.get("terrapaver.biome.btn.undo")
        val clearLbl = I18n.get("terrapaver.biome.btn.clear_box")
        val doneLbl = I18n.get("terrapaver.gui.done")
        val applyLbl = I18n.get("terrapaver.biome.btn.apply_here")

        val gap = 6
        val minPerBtn = 62
        var undoW = (font.width(undoLbl) + 22).coerceAtLeast(minPerBtn)
        var clearW = (font.width(clearLbl) + 22).coerceAtLeast(minPerBtn)
        var doneW = (font.width(doneLbl) + 24).coerceAtLeast(minPerBtn)
        var applyW = (font.width(applyLbl) + 28).coerceAtLeast(72)

        val totalNeeded = undoW + clearW + doneW + applyW + gap * 3
        if (totalNeeded > contentW) {
            val scale = (contentW - gap * 3).toFloat() / (undoW + clearW + doneW + applyW).toFloat()
            undoW = (undoW * scale).toInt()
            clearW = (clearW * scale).toInt()
            doneW = (doneW * scale).toInt()
            applyW = contentW - gap * 3 - undoW - clearW - doneW
        }

        // Left: secondary controls (Undo, Clear Box)
        drawActionButton(
            g, mouseX, mouseY, contentX, y, undoW, h, undoLbl,
            if (ClientBiomeState.canUndo) ButtonStyle.TONAL else ButtonStyle.OUTLINED, t
        ) {
            TerraPaverNet.sendToServer(TerraPaverNet.C2SBiomeControl(TerraPaverNet.BIOME_ACTION_UNDO))
        }

        drawActionButton(
            g, mouseX, mouseY, contentX + undoW + gap, y, clearW, h, clearLbl,
            ButtonStyle.OUTLINED, t
        ) {
            TerraPaverNet.sendToServer(TerraPaverNet.C2SBiomeControl(TerraPaverNet.BIOME_ACTION_CLEAR))
        }

        // Right: primary dialog actions (Save & Close, Apply Here)
        val applyX = contentX + contentW - applyW
        val doneX = applyX - doneW - gap

        drawActionButton(
            g, mouseX, mouseY, doneX, y, doneW, h, doneLbl,
            ButtonStyle.TONAL, t
        ) {
            save()
            onClose()
        }

        drawActionButton(
            g, mouseX, mouseY, applyX, y, applyW, h, applyLbl,
            ButtonStyle.FILLED, t
        ) {
            save()
            val player = Minecraft.getInstance().player ?: return@drawActionButton
            TerraPaverNet.sendToServer(TerraPaverNet.C2SApplyBiomeBrush(player.blockPosition(), cfg.toJson()))
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
        M3Draw.drawCard(g, x, y, w, h, t, elevated = true, radius = 14)
        g.drawString(font, M3Draw.fitText(font, title, w - 28), x + 14, y + 11, t.primary, false)
        body(x + 14, y + 28, w - 28)
        return y + h + 10
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
        M3Draw.fillRoundedRect(g, x, y, w, h, h / 2, t.surfaceLow)
        M3Draw.strokeRoundedRect(g, x, y, w, h, h / 2, M3Theme.withAlpha(t.outlineVariant, 160), 0.9f)
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
            val label = M3Draw.fitText(font, labelFn(values[i]), sw - 10)
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
            M3Draw.fillRoundedRect(g, x - 4, y, w + 8, rowH, 10, M3Theme.withAlpha(t.primary, 14))
        }
        g.drawString(font, M3Draw.fitText(font, label, w - 48), x, y + 8, t.onSurface, false)

        val sw = 38
        val sh = 20
        val sx = x + w - sw
        val sy = y + 2
        M3Draw.fillRoundedRect(g, sx, sy, sw, sh, 10, if (checked) t.primary else t.surfaceHigh)
        M3Draw.strokeRoundedRect(g, sx, sy, sw, sh, 10, if (checked) t.primary else t.outline, 0.9f)
        val thumbR = if (checked) 7f else 5.2f
        val thumbX = if (checked) (sx + sw - 10).toFloat() else (sx + 10).toFloat()
        M3Draw.fillCircleF(g, thumbX, sy + sh * 0.5f, thumbR, if (checked) t.onPrimary else t.outline)

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
        val trackH = 5
        val ratio = ((current() - minVal).toFloat() / max(1, maxVal - minVal).toFloat()).coerceIn(0f, 1f)
        val activeW = w * ratio

        M3Draw.fillRoundedRectF(g, x.toFloat(), trackY.toFloat(), w.toFloat(), trackH.toFloat(), 2.5f, t.surfaceHigh)
        if (activeW > 0.5f) {
            M3Draw.fillRoundedRectF(g, x.toFloat(), trackY.toFloat(), activeW, trackH.toFloat(), 2.5f, t.primary)
        }
        val knobX = (x + activeW).coerceIn(x + 6f, x + w - 6f)
        val hover = mouseX in x..(x + w) && mouseY in (y + 10)..(y + 28)
        if (hover) {
            M3Draw.fillCircleF(g, knobX, trackY + 2.5f, 10f, M3Theme.withAlpha(t.primary, 32))
        }
        M3Draw.fillCircleF(g, knobX, trackY + 2.5f, 6f, t.primary)
        M3Draw.fillCircleF(g, knobX, trackY + 2.5f, 2.5f, t.onPrimary)

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
            ButtonStyle.OUTLINED -> if (hover) t.surfaceLow else t.card
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
            M3Draw.strokeRoundedRect(g, x, y, w, h, h / 2, M3Theme.withAlpha(t.outline, 190), 0.9f)
        }
        val txt = M3Draw.fitText(font, label, w - 12)
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
            if (activeTab == 0 && mx in sb.x..(sb.x + sb.width) && my in (sb.y - 8)..(sb.y + sb.height + 8)) {
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
