/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.BufferUploader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.language.I18n
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.client.gui.GuiMinePad.Companion.getChar
import net.montoyo.wd.miniserv.Constants
import net.montoyo.wd.miniserv.client.Client
import net.montoyo.wd.miniserv.client.ClientTask
import net.montoyo.wd.miniserv.client.ClientTaskCheckFile
import net.montoyo.wd.miniserv.client.ClientTaskDeleteFile
import net.montoyo.wd.miniserv.client.ClientTaskGetFileList
import net.montoyo.wd.miniserv.client.ClientTaskGetQuota
import net.montoyo.wd.miniserv.client.ClientTaskUploadFile
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.NameUUIDPair
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector3i
import org.lwjgl.glfw.GLFW
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.text.Collator
import java.util.Arrays
import java.util.function.Supplier
import javax.swing.filechooser.FileSystemView

class GuiServer(
    private val serverPos: Vector3i,
    private val owner: NameUUIDPair
) : WDScreen(Component.nullToEmpty(null)) {
    private val lines = ArrayList<String>()
    private var prompt = ""
    private val userPrompt = "> "
    private var blinkTime = 0
    private var lastCmd: String? = null
    private var promptLocked = false

    @Volatile
    private var queryTime = 0L
    private var currentTask: ClientTask<*>? = null
    private var selectedLine = -1

    private var accessTrials = 0
    private var accessTime = 0
    private var accessState = -1
    private var accessSound: SimpleSoundInstance? = null

    private var uploadWizard = false
    private lateinit var uploadDir: File
    private val uploadFiles = ArrayList<File>()
    private var uploadOffset = 0
    private var uploadFirstIsParent = false
    private var uploadFilter = ""
    private var uploadFilterTime = 0L

    private var queueRead = 0
    private val queue = ArrayList<String>()

    init {
        if (COMMAND_MAP.isEmpty()) buildCommandMap()

        lines.add("MiniServ 1.0")
        lines.add(tr("info"))
        uploadCD(FileSystemView.getFileSystemView().defaultDirectory)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {
        super.render(graphics, mouseX, mouseY, ptt)

        var x = (width - 256) / 2
        var y = (height - 176) / 2

        RenderSystem.setShaderTexture(0, BG_IMAGE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        graphics.blit(BG_IMAGE, x, y, 0, 0, 256, 256)

        x += 18
        y += 18

        for (i in lines.indices) {
            if (selectedLine == i) {
                drawWhiteQuad(x - 1, y - 2, font.width(lines[i]) + 1, 12)
                graphics.drawString(Minecraft.getInstance().font, lines[i], x, y, 0xFF129700.toInt(), false)
            } else {
                graphics.drawString(Minecraft.getInstance().font, lines[i], x, y, 0xFFFFFFFF.toInt(), false)
            }
            y += 12
        }

        if (!promptLocked) {
            if (queue.isEmpty()) {
                x = graphics.drawString(Minecraft.getInstance().font, userPrompt, x, y, 0xFFFFFFFF.toInt(), false)
                x = graphics.drawString(Minecraft.getInstance().font, prompt, x, y, 0xFFFFFFFF.toInt(), false)
            } else {
                x = graphics.drawString(Minecraft.getInstance().font, tr("press_for_more"), x, y, 0xFFFFFFFF.toInt(), false)
            }
        }

        if (!uploadWizard && blinkTime < 5) {
            drawWhiteQuad(x + 1, y, 6, 8)
        }

        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)
        RenderSystem.setShaderTexture(0, FG_IMAGE)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    private fun drawWhiteQuad(x: Int, y: Int, w: Int, h: Int) {
        val xd = x.toFloat()
        val xd2 = (x + w).toFloat()
        val yd = y.toFloat()
        val yd2 = (y + h).toFloat()
        val zd = getBlitOffset()

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        val bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
        bb.addVertex(xd, yd2, zd)
        bb.addVertex(xd2, yd2, zd)
        bb.addVertex(xd2, yd, zd)
        bb.addVertex(xd, yd, zd)
        BufferUploader.drawWithShader(bb.build())
    }

    private fun getBlitOffset(): Float = 0f

    override fun tick() {
        super.tick()

        if (accessState >= 0) {
            if (--accessTime <= 0) {
                accessState++

                if (accessState == 1) {
                    if (lines.isNotEmpty()) lines.removeAt(lines.size - 1)
                    lines.add("access: PERMISSION DENIED....and...")
                    accessTime = 20
                } else {
                    if (accessSound == null) {
                        accessSound = SimpleSoundInstance(
                            WebDisplays.INSTANCE.soundServer!!.location,
                            SoundSource.MASTER,
                            1.0f,
                            1.0f,
                            RandomSource.create(),
                            true,
                            0,
                            SoundInstance.Attenuation.NONE,
                            0.0,
                            0.0,
                            0.0,
                            false
                        )
                        minecraft!!.soundManager.play(accessSound!!)
                    }

                    writeLine("YOU DIDN'T SAY THE MAGIC WORD!")
                    accessTime = 2
                }
            }
        } else {
            blinkTime = (blinkTime + 1) % 10

            val task = currentTask
            if (task != null) {
                val qt: Long
                synchronized(this) {
                    qt = this.queryTime
                }

                if (System.currentTimeMillis() - qt >= 10000) {
                    writeLine(tr("timeout"))
                    task.cancel()
                    clearTask()
                }
            }

            if (uploadFilter.isNotEmpty() && System.currentTimeMillis() - uploadFilterTime >= 1000) {
                Log.info("Upload filter cleared")
                uploadFilter = ""
            }
        }

        val maxl = if (uploadWizard) MAX_LINES else MAX_LINES - 1
        if (queue.isNotEmpty()) {
            while (queue.isNotEmpty()) {
                if (lines.size >= maxl) break
                writeLine(queue.removeAt(0))
            }
        }

        while (lines.size > maxl) lines.removeAt(0)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val predicate = Supplier { super.keyReleased(keyCode, scanCode, modifiers) }
        return try {
            handleKeyboardInput(keyCode, false, predicate)
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !uploadWizard) {
            Minecraft.getInstance().setScreen(null)
            return true
        }

        getChar(keyCode, scanCode).ifPresent { c ->
            try {
                keyTyped(c, keyCode, modifiers)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        return try {
            handleKeyboardInput(keyCode, true) { true }
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    @Throws(IOException::class)
    fun handleKeyboardInput(keyCode: Int, keyState: Boolean, booleanSupplier: Supplier<Boolean>): Boolean {
        if (queue.isNotEmpty()) return false

        if (uploadWizard) {
            if (keyState) {
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    if (selectedLine > 3) {
                        selectedLine--
                    } else if (uploadOffset > 0) {
                        uploadOffset--
                        updateUploadScreen()
                    }
                } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
                    if (selectedLine < MAX_LINES - 1) {
                        selectedLine++
                    } else if (uploadOffset + selectedLine - 2 < uploadFiles.size) {
                        uploadOffset++
                        updateUploadScreen()
                    }
                } else if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                    selectedLine = 3
                    var dst = uploadOffset - (MAX_LINES - 3)
                    if (dst < 0) dst = 0
                    selectFile(dst)
                } else if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                    selectedLine = 3
                    var dst = uploadOffset + (MAX_LINES - 3)
                    if (dst >= uploadFiles.size) dst = uploadFiles.size - 1
                    selectFile(dst)
                } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    val file = uploadFiles[uploadOffset + selectedLine - 3]
                    if (file.isDirectory) {
                        uploadCD(file)
                        updateUploadScreen()
                    } else {
                        startFileUpload(file, true)
                    }
                } else if (keyCode == GLFW.GLFW_KEY_F5) {
                    uploadCD(uploadDir)
                    updateUploadScreen()
                }
            }

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                quitUploadWizard()
                return true
            }

            return booleanSupplier.get()
        } else {
            val value = booleanSupplier.get()

            if (keyState) {
                val ctrl = Screen.hasControlDown()

                if (keyCode == GLFW.GLFW_KEY_L && ctrl) {
                    lines.clear()
                } else if (keyCode == GLFW.GLFW_KEY_V && ctrl) {
                    prompt += Minecraft.getInstance().keyboardHandler.clipboard
                    if (prompt.length > MAX_LINE_LEN) prompt = prompt.substring(0, MAX_LINE_LEN)
                } else if (keyCode == GLFW.GLFW_KEY_UP) {
                    val lc = lastCmd
                    if (lc != null) {
                        val tmp = prompt
                        prompt = lc
                        lastCmd = tmp
                    }
                }
            }

            return value
        }
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        return super.charTyped(codePoint, modifiers)
    }

    @Throws(IOException::class)
    protected fun keyTyped(typedChar: Char, keyCode: Int, modifier: Int) {
        var ch = typedChar
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (queue.isNotEmpty()) {
                writeLine(queue.removeAt(0))
                return
            }
        }
        if (queue.isNotEmpty()) return

        if (uploadWizard) {
            var found = false
            uploadFilter += ch.lowercaseChar()
            uploadFilterTime = System.currentTimeMillis()

            for (i in (if (uploadFirstIsParent) 1 else 0) until uploadFiles.size) {
                if (uploadFiles[i].name.lowercase().startsWith(uploadFilter)) {
                    selectFile(i)
                    found = true
                    break
                }
            }

            if (!found && uploadFilter.length == 1) uploadFilter = ""
            return
        } else if (promptLocked) {
            return
        }

        if (keyCode == GLFW.GLFW_KEY_SPACE) ch = ' '
        if ((ch == 'v' || ch == 'V') && (modifier and 2) == 2) return

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (prompt.isNotEmpty()) prompt = prompt.substring(0, prompt.length - 1)
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (prompt.isNotEmpty()) {
                writeLine(userPrompt + prompt)
                evaluateCommand(prompt)
                lastCmd = prompt
                prompt = ""
            } else {
                writeLine(userPrompt)
            }
        } else if (prompt.length + 1 < MAX_LINE_LEN && ch.code in 32..126) {
            prompt += ch
        }

        blinkTime = 0
    }

    private fun evaluateCommand(str: String) {
        val args = str.trim().split("\\s+".toRegex()).toTypedArray()
        val handler = COMMAND_MAP[args[0].lowercase()]

        if (handler == null) {
            writeLine(tr("unknowncmd"))
            return
        }

        val params: Array<Any> = if (handler.parameterCount == 0) {
            emptyArray()
        } else {
            val args2 = arrayOfNulls<String>(args.size - 1)
            System.arraycopy(args, 1, args2, 0, args2.size)
            arrayOf(args2)
        }

        try {
            handler.invoke(this, *params)
        } catch (e: IllegalAccessException) {
            Log.errorEx("Caught exception while running command \"%s\"", e, str)
            writeLine(tr("error"))
        } catch (e: InvocationTargetException) {
            Log.errorEx("Caught exception while running command \"%s\"", e, str)
            writeLine(tr("error"))
        }
    }

    private fun writeLine(line: String) {
        val maxl = if (uploadWizard) MAX_LINES else MAX_LINES - 1
        while (lines.size >= maxl) lines.removeAt(0)
        lines.add(line)
    }

    private fun quitUploadWizard() {
        lines.clear()
        promptLocked = false
        uploadWizard = false
        selectedLine = -1
    }

    override fun onClose() {
        super.onClose()
        val snd = accessSound
        if (snd != null) Minecraft.getInstance().soundManager.stop(snd)
    }

    private fun queueTask(task: ClientTask<*>): Boolean {
        return if (Client.getInstance().addTask(task)) {
            promptLocked = true
            queryTime = System.currentTimeMillis()
            currentTask = task
            true
        } else {
            writeLine(tr("queryerr"))
            false
        }
    }

    private fun clearTask() {
        promptLocked = false
        currentTask = null
    }

    @CommandHandler("clear")
    fun commandClear() {
        lines.clear()
    }

    @CommandHandler("help")
    fun commandHelp(args: Array<String>) {
        queueRead = lines.size

        if (args.isNotEmpty()) {
            val cmd = args[0].lowercase()
            if (COMMAND_MAP.containsKey(cmd)) queueLine(tr("help.$cmd"))
            else queueLine(tr("unknowncmd"))
        } else {
            for (c in COMMAND_MAP.keys) queueLine("$c - ${tr("help.$c")}")
        }
    }

    @CommandHandler("exit")
    fun commandExit() {
        minecraft!!.setScreen(null)
    }

    @CommandHandler("access")
    fun commandAccess(args: Array<String>) {
        var handled = false

        if (args.isNotEmpty() && args[0].equals("security", ignoreCase = true)) {
            if (args.size == 1 || (args.size == 2 && args[1].equals("grid", ignoreCase = true))) {
                handled = true
            }
        } else if (args.size == 3 && args[0].equals("main", ignoreCase = true) && args[1].equals("security", ignoreCase = true) && args[2].equals("grid", ignoreCase = true)) {
            handled = true
        }

        if (handled) {
            writeLine("access: PERMISSION DENIED.")
            if (++accessTrials >= 3) {
                promptLocked = true
                accessState = 0
                accessTime = 20
            }
        } else {
            writeLine(tr("argerror"))
        }
    }

    @CommandHandler("owner")
    fun commandOwner() {
        writeLine(tr("ownername", owner.name))
        writeLine(tr("owneruuid"))
        writeLine(owner.uuid.toString())
    }

    @CommandHandler("quota")
    fun commandQuota() {
        if (minecraft!!.player!!.gameProfile.id != owner.uuid) {
            writeLine(tr("errowner"))
            return
        }

        val task = ClientTaskGetQuota()
        task.setFinishCallback { t ->
            writeLine(tr("quota", Util.sizeString(t.getQuota()), Util.sizeString(t.getMaxQuota())))
            clearTask()
        }
        queueTask(task)
    }

    @CommandHandler("ls")
    fun commandList() {
        val task = ClientTaskGetFileList(owner.uuid)
        task.setFinishCallback { t ->
            val files = t.getFileList()
            if (files != null) Arrays.stream(files).forEach(::writeLine)
            clearTask()
        }
        queueTask(task)
    }

    @CommandHandler("url")
    fun commandURL(args: Array<String>) {
        if (args.isEmpty()) {
            writeLine(tr("fnamearg"))
            return
        }

        val fname = Util.join(args, " ")
        if (Util.isFileNameInvalid(fname)) {
            writeLine(tr("nameerr"))
            return
        }

        val task = ClientTaskCheckFile(owner.uuid, fname)
        task.setFinishCallback { t ->
            val status = t.getStatus()
            if (status == 0) {
                writeLine(tr("urlcopied"))
                Minecraft.getInstance().keyboardHandler.clipboard = t.getURL()
            } else if (status == Constants.GETF_STATUS_NOT_FOUND) {
                writeLine(tr("notfound"))
            } else {
                writeLine(tr("error2", status))
            }
            clearTask()
        }
        queueTask(task)
    }

    private fun uploadCD(newDir: File) {
        uploadDir = try {
            newDir.canonicalFile
        } catch (_: IOException) {
            newDir
        }

        uploadFiles.clear()
        val parent = uploadDir.parentFile
        if (parent != null && parent.exists()) {
            uploadFiles.add(parent)
            uploadFirstIsParent = true
        } else {
            uploadFirstIsParent = false
        }

        val children = uploadDir.listFiles()
        if (children != null) {
            val c = Collator.getInstance()
            c.strength = Collator.SECONDARY
            c.decomposition = Collator.CANONICAL_DECOMPOSITION

            Arrays.stream(children)
                .filter { f -> !f.isHidden && (f.isDirectory || f.isFile) }
                .sorted { a, b -> c.compare(a.name, b.name) }
                .forEach(uploadFiles::add)
        }

        uploadOffset = 0
        uploadFilter = ""
        if (uploadWizard) selectedLine = 3
    }

    private fun updateUploadScreen() {
        lines.clear()
        lines.add(tr("upload.info"))
        lines.add(trimStringL(uploadDir.path))
        lines.add("")

        var i = uploadOffset
        while (i < uploadFiles.size && lines.size < MAX_LINES) {
            if (i == 0 && uploadFirstIsParent) lines.add(tr("upload.parent"))
            else lines.add(trimStringR(uploadFiles[i].name))
            i++
        }
    }

    private fun selectFile(i: Int) {
        val pos = 3 + i - uploadOffset
        if (pos in 3 until MAX_LINES) {
            selectedLine = pos
            return
        }

        uploadOffset = i
        if (uploadOffset + MAX_LINES - 3 > uploadFiles.size) {
            uploadOffset = uploadFiles.size - MAX_LINES + 3
        }

        updateUploadScreen()
        selectedLine = 3 + i - uploadOffset
    }

    @CommandHandler("upload")
    fun commandUpload(args: Array<String>) {
        if (minecraft!!.player!!.gameProfile.id != owner.uuid) {
            writeLine(tr("errowner"))
            return
        }

        if (args.isNotEmpty()) {
            val fle = File(Util.join(args, " "))
            if (!fle.exists()) {
                writeLine(tr("notfound"))
                return
            }

            if (fle.isDirectory) {
                uploadCD(fle)
            } else if (fle.isFile) {
                startFileUpload(fle, false)
                return
            } else {
                writeLine(tr("notfound"))
                return
            }
        }

        uploadWizard = true
        promptLocked = true
        uploadOffset = 0
        selectedLine = 3
        updateUploadScreen()
    }

    @CommandHandler("rm")
    fun commandDelete(args: Array<String>) {
        if (minecraft!!.player!!.gameProfile.id != owner.uuid) {
            writeLine(tr("errowner"))
            return
        }

        if (args.isEmpty()) {
            writeLine(tr("fnamearg"))
            return
        }

        val fname = Util.join(args, " ")
        if (Util.isFileNameInvalid(fname)) {
            writeLine(tr("nameerr"))
            return
        }

        val task = ClientTaskDeleteFile(fname)
        task.setFinishCallback { t ->
            val status = t.getStatus()
            if (status == 1) writeLine(tr("notfound"))
            else if (status != 0) writeLine(tr("error"))
            clearTask()
        }
        queueTask(task)
    }

    @CommandHandler("reconnect")
    fun commandReconnect() {
        Client.getInstance().stop()
        WDNetworkRegistry.INSTANCE.sendToServer(Client.getInstance().beginConnection())
    }

    private fun startFileUpload(f: File, quit: Boolean) {
        if (quit) quitUploadWizard()

        if (Util.isFileNameInvalid(f.name) || f.name.length >= MAX_LINE_LEN - 3) {
            writeLine(tr("nameerr"))
            return
        }

        val task: ClientTaskUploadFile = try {
            ClientTaskUploadFile(f)
        } catch (ex: IOException) {
            writeLine(tr("error"))
            ex.printStackTrace()
            return
        }

        task.setProgressCallback { _, _ ->
            synchronized(this@GuiServer) {
                queryTime = System.currentTimeMillis()
            }
        }

        task.setFinishCallback { t ->
            val status = t.getUploadStatus()
            if (status == 0) writeLine(tr("upload.done"))
            else if (status == Constants.FUPA_STATUS_FILE_EXISTS) writeLine(tr("upload.exists"))
            else if (status == Constants.FUPA_STATUS_EXCEEDS_QUOTA) writeLine(tr("upload.quota"))
            else writeLine(tr("error2", status))
            clearTask()
        }

        if (queueTask(task)) writeLine(tr("upload.uploading"))
    }

    override fun isForBlock(bp: BlockPos, side: BlockSide): Boolean = serverPos.equalsBlockPos(bp)

    override fun getWikiPageName(): String = "Server"

    private fun queueLine(line: String) {
        val maxl = if (uploadWizard) MAX_LINES else MAX_LINES - 1
        if (lines.size < maxl) {
            writeLine(line)
        } else if (queueRead > 1) {
            writeLine(line)
            queueRead -= 1
        } else {
            queue.add(line)
        }
    }

    companion object {
        private val BG_IMAGE = ResourceLocation.fromNamespaceAndPath("webdisplays", "textures/gui/server_bg.png")
        private val FG_IMAGE = ResourceLocation.fromNamespaceAndPath("webdisplays", "textures/gui/server_fg.png")
        private val COMMAND_MAP = HashMap<String, Method>()
        private const val MAX_LINE_LEN = 32
        private const val MAX_LINES = 12

        private fun tr(key: String, vararg args: Any?): String {
            return I18n.get("webdisplays.server.$key", *args)
        }

        private fun buildCommandMap() {
            COMMAND_MAP.clear()
            for (m in GuiServer::class.java.methods) {
                val cmd = m.getAnnotation(CommandHandler::class.java)
                if (cmd != null && Modifier.isPublic(m.modifiers)) {
                    if (m.parameterCount == 0 || (m.parameterCount == 1 && m.parameterTypes[0] == Array<String>::class.java)) {
                        COMMAND_MAP[cmd.value.lowercase()] = m
                    }
                }
            }
        }

        private fun trimStringL(str: String): String {
            val delta = str.length - MAX_LINE_LEN
            if (delta <= 0) return str
            return "..." + str.substring(delta + 3)
        }

        private fun trimStringR(str: String): String {
            return if (str.length <= MAX_LINE_LEN) str else str.substring(0, MAX_LINE_LEN - 3) + "..."
        }
    }
}
