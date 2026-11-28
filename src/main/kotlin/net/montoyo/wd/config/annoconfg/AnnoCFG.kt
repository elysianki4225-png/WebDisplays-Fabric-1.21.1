package net.montoyo.wd.config.annoconfg

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.montoyo.wd.config.annoconfg.annotation.format.CFGSegment
import net.montoyo.wd.config.annoconfg.annotation.format.Comment
import net.montoyo.wd.config.annoconfg.annotation.format.Config
import net.montoyo.wd.config.annoconfg.annotation.format.ConfigSide
import net.montoyo.wd.config.annoconfg.annotation.format.Name
import net.montoyo.wd.config.annoconfg.annotation.format.Skip
import net.montoyo.wd.config.annoconfg.annotation.format.Translation
import net.montoyo.wd.config.annoconfg.annotation.value.Default
import net.montoyo.wd.config.annoconfg.annotation.value.DoubleRange
import net.montoyo.wd.config.annoconfg.annotation.value.IntRange
import net.montoyo.wd.config.annoconfg.annotation.value.LongRange
import net.montoyo.wd.config.annoconfg.handle.UnsafeHandle
import net.montoyo.wd.config.annoconfg.util.EnumType
import net.montoyo.wd.utilities.Log
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Annotation driven config. Values live in static fields of the annotated class and are kept in a JSON file
 * under the config directory.
 */
class AnnoCFG(private val side: ConfigSide, private val root: Class<*>) {
    class Entry internal constructor(
        @JvmField val path: String,
        @JvmField val type: Class<*>,
        @JvmField val handle: UnsafeHandle,
        @JvmField val comment: String,
        @JvmField val translationKey: String,
        @JvmField val min: Double,
        @JvmField val max: Double,
        @JvmField val hasRange: Boolean
    ) {
        fun get(): Any? = handle.get()

        fun set(value: Any?) {
            handle.set(value)
        }
    }

    private val file: Path
    private val postInit: Method?
    private val entries = LinkedHashMap<String, Entry>()
    private val defaults = LinkedHashMap<String, Any?>()
    private val values = LinkedHashMap<String, Any?>()

    init {
        val descriptor = root.getAnnotation(Config::class.java)
        val prefix = if (descriptor == null || descriptor.path.isEmpty()) "" else descriptor.path + "/"
        val name = prefix + "webdisplays_" + side.name.lowercase(Locale.ROOT) + ".json"
        file = FabricLoader.getInstance().configDir.resolve(name)

        postInit = try {
            root.getDeclaredMethod("postLoad")
        } catch (_: Throwable) {
            null
        }

        setup("", root)
        configs.add(this)
        load()
        apply()
    }

    fun getRootClass(): Class<*> = root

    fun getSide(): ConfigSide = side

    fun getFile(): Path = file

    fun getEntries(): Map<String, Entry> = entries

    private fun setup(dir: String, clazz: Class<*>) {
        val cleanDir = if (dir.startsWith(".")) dir.substring(1) else dir

        for (field in clazz.fields) {
            if (!Modifier.isStatic(field.modifiers)) continue
            if (field.name == "INSTANCE" && field.type == clazz) continue

            val skip = field.getAnnotation(Skip::class.java)
            if (skip != null) continue

            val name = field.getAnnotation(Name::class.java)
            val fieldName = name?.value ?: field.name
            val path = if (cleanDir.isEmpty()) fieldName else "$cleanDir.$fieldName"

            val defaultValue = field.getAnnotation(Default::class.java)
                ?: throw RuntimeException("Config entry $path has no @Default annotation.")

            var def: Any?
            var minVal = Double.MIN_VALUE
            var maxVal = Double.MAX_VALUE
            var hasRange = false

            when (EnumType.forClass(field.type)) {
                EnumType.INT -> {
                    def = defaultValue.valueI
                    val range = field.getAnnotation(IntRange::class.java)
                    if (range != null) {
                        def = clamp(def, range.minV, range.maxV)
                        minVal = range.minV.toDouble()
                        maxVal = range.maxV.toDouble()
                        hasRange = true
                    }
                }
                EnumType.LONG -> {
                    def = defaultValue.valueL
                    val range = field.getAnnotation(LongRange::class.java)
                    if (range != null) {
                        def = clamp(def, range.minV, range.maxV)
                        minVal = range.minV.toDouble()
                        maxVal = range.maxV.toDouble()
                        hasRange = true
                    }
                }
                EnumType.DOUBLE -> {
                    def = defaultValue.valueD
                    val range = field.getAnnotation(DoubleRange::class.java)
                    if (range != null) {
                        def = clamp(def, range.minV, range.maxV)
                        minVal = range.minV
                        maxVal = range.maxV
                        hasRange = true
                    }
                }
                EnumType.BOOLEAN -> def = defaultValue.valueBoolean
                else -> {
                    def = if (field.type == Array<String>::class.java || field.type == String::class.java) {
                        defaultValue.valueStr
                    } else {
                        throw RuntimeException("NYI ${field.type}")
                    }
                }
            }

            defaults[path] = def
            values[path] = def
            entries[path] = Entry(
                path, field.type, UnsafeHandle(field),
                commentOf(field), translationOf(field), minVal, maxVal, hasRange
            )
        }

        for (nestMember in clazz.classes) {
            if (nestMember == clazz || !nestMember.name.startsWith(clazz.name)) continue

            val segment = nestMember.getAnnotation(CFGSegment::class.java)
                ?: throw RuntimeException("NYI: default name for nested config class ${nestMember.name}")

            setup(if (cleanDir.isEmpty()) segment.value else "$cleanDir.${segment.value}", nestMember)
        }
    }

    private fun parse(path: String, element: JsonElement?): Any? {
        val def = defaults[path]
        if (element == null || element.isJsonNull) return def

        val entry = entries[path]!!

        return try {
            when (def) {
                is Int -> {
                    val v = element.asInt
                    if (entry.hasRange) clamp(v, entry.min.toInt(), entry.max.toInt()) else v
                }
                is Long -> {
                    val v = element.asLong
                    if (entry.hasRange) clamp(v, entry.min.toLong(), entry.max.toLong()) else v
                }
                is Double -> {
                    val v = element.asDouble
                    if (entry.hasRange) clamp(v, entry.min, entry.max) else v
                }
                is Boolean -> element.asBoolean
                else -> element.asString
            }
        } catch (t: Throwable) {
            Log.warningEx("Invalid value for config entry %s, using default.", t, path)
            def
        }
    }

    fun load() {
        try {
            if (!Files.exists(file)) {
                save()
                return
            }

            val json = Files.newBufferedReader(file).use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }
            for (path in values.keys) {
                var element: JsonElement? = json
                for (part in path.split(".")) {
                    if (element == null || !element.isJsonObject || !element.asJsonObject.has(part)) {
                        element = null
                        break
                    }
                    element = element.asJsonObject.get(part)
                }
                values[path] = parse(path, element)
            }
        } catch (t: Throwable) {
            Log.warningEx("Could not read config file %s.", t, file.toString())
        }

        apply()
    }

    fun save() {
        try {
            val rootJson = JsonObject()

            for ((key, value) in values) {
                var target = rootJson
                val parts = key.split(".")
                for (i in 0 until parts.size - 1) {
                    var child = target.get(parts[i])
                    if (child == null || !child.isJsonObject) {
                        child = JsonObject()
                        target.add(parts[i], child)
                    }
                    target = child.asJsonObject
                }

                val leaf = parts[parts.size - 1]
                when (value) {
                    is Int -> target.addProperty(leaf, value)
                    is Long -> target.addProperty(leaf, value)
                    is Double -> target.addProperty(leaf, value)
                    is Boolean -> target.addProperty(leaf, value)
                    else -> target.addProperty(leaf, value.toString())
                }
            }

            Files.createDirectories(file.parent)
            Files.writeString(file, GSON.toJson(rootJson))
        } catch (t: Throwable) {
            Log.warningEx("Could not write config file %s.", t, file.toString())
        }
    }

    /**
     * Pushes the stored values into the static fields, then lets the config class recompute its derived values.
     */
    fun apply() {
        for (entry in entries.values) {
            val value = values[entry.path]

            if (entry.type == Array<String>::class.java) {
                val raw = value?.toString()?.trim() ?: ""
                if (raw.isEmpty()) {
                    entry.set(emptyArray<String>())
                } else {
                    val parts = raw.split(",").map { it.trim() }.toTypedArray()
                    entry.set(parts)
                }
            } else if (value is Double && entry.type == java.lang.Float.TYPE) {
                entry.set(value.toFloat())
            } else {
                entry.set(value)
            }
        }

        try {
            postInit?.invoke(null)
        } catch (err: Throwable) {
            err.printStackTrace()
        }
    }

    fun set(path: String, value: Any?) {
        val entry = entries[path] ?: throw IllegalArgumentException("Unknown config entry $path")

        var stored: Any? = value
        if (entry.type == Array<String>::class.java) {
            @Suppress("UNCHECKED_CAST")
            stored = java.lang.String.join(",", *(value as Array<out CharSequence>))
        } else if (value is Number) {
            when (entry.type) {
                Integer.TYPE -> stored = value.toInt()
                java.lang.Long.TYPE -> stored = value.toLong()
                java.lang.Double.TYPE -> stored = value.toDouble()
            }
        }

        if (entry.hasRange && stored is Number) {
            val d = stored.toDouble()
            if (d < entry.min || d > entry.max) {
                throw IllegalArgumentException("Value $d out of range for $path")
            }
        }

        values[path] = stored
        apply()
        save()
    }

    fun get(path: String): Any? = values[path]

    fun getDefault(path: String): Any? = defaults[path]

    companion object {
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
        private val configs = ArrayList<AnnoCFG>()

        @JvmStatic
        fun all(): List<AnnoCFG> = configs

        private fun commentOf(element: AnnotatedElement): String {
            val comment = element.getAnnotation(Comment::class.java) ?: return ""
            return comment.value.joinToString("\n")
        }

        private fun translationOf(element: AnnotatedElement): String {
            val translation = element.getAnnotation(Translation::class.java)
            return translation?.value ?: ""
        }

        private fun clamp(v: Int, minVal: Int, maxVal: Int): Int = max(minVal, min(maxVal, v))

        private fun clamp(v: Long, minVal: Long, maxVal: Long): Long = max(minVal, min(maxVal, v))

        private fun clamp(v: Double, minVal: Double, maxVal: Double): Double = max(minVal, min(maxVal, v))
    }
}
