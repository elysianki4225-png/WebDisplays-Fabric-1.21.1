package net.montoyo.wd.client.gui

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.montoyo.wd.config.annoconfg.AnnoCFG
import java.util.Locale

@Environment(EnvType.CLIENT)
object WDConfigScreen {
    @JvmStatic
    fun create(parent: Screen?): Screen {
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("webdisplays.config.screen.title"))
        val entryBuilder = builder.entryBuilder()

        for (config in AnnoCFG.all()) {
            val category = builder.getOrCreateCategory(
                Component.translatable("webdisplays.config.screen.category.${config.getSide().name.lowercase(Locale.ROOT)}")
            )

            for (entry in config.getEntries().values) {
                category.addEntry(buildEntry(entryBuilder, config, entry))
            }
        }

        builder.setSavingRunnable {
            for (config in AnnoCFG.all()) {
                config.save()
            }
        }

        return builder.build()
    }

    private fun buildEntry(
        builder: ConfigEntryBuilder,
        config: AnnoCFG,
        entry: AnnoCFG.Entry
    ): AbstractConfigListEntry<*> {
        val label = label(entry)
        val value = entry.get()
        val defVal = config.getDefault(entry.path)
        val path = entry.path

        if (value is Boolean) {
            val defBool = defVal as? Boolean ?: value
            return builder.startBooleanToggle(label, value)
                .setDefaultValue(defBool)
                .setSaveConsumer { v -> config.set(path, v) }
                .build()
        }

        if (value is Int) {
            val defInt = (defVal as? Number)?.toInt() ?: value
            return builder.startIntField(label, value)
                .setDefaultValue(defInt)
                .setMin(if (entry.hasRange) entry.min.toInt() else Int.MIN_VALUE)
                .setMax(if (entry.hasRange) entry.max.toInt() else Int.MAX_VALUE)
                .setSaveConsumer { v -> config.set(path, v) }
                .build()
        }

        if (value is Long) {
            val defLong = (defVal as? Number)?.toLong() ?: value
            return builder.startLongField(label, value)
                .setDefaultValue(defLong)
                .setMin(if (entry.hasRange) entry.min.toLong() else Long.MIN_VALUE)
                .setMax(if (entry.hasRange) entry.max.toLong() else Long.MAX_VALUE)
                .setSaveConsumer { v -> config.set(path, v) }
                .build()
        }

        if (value is Double) {
            val defDouble = (defVal as? Number)?.toDouble() ?: value
            return builder.startDoubleField(label, value)
                .setDefaultValue(defDouble)
                .setMin(if (entry.hasRange) entry.min else -Double.MAX_VALUE)
                .setMax(if (entry.hasRange) entry.max else Double.MAX_VALUE)
                .setSaveConsumer { v -> config.set(path, v) }
                .build()
        }

        if (value is Array<*> && value.isArrayOf<String>()) {
            @Suppress("UNCHECKED_CAST")
            val array = value as Array<String>
            val joined = array.joinToString(",")
            val defStr = defVal?.toString() ?: ""
            return builder.startStrField(label, joined)
                .setDefaultValue(defStr)
                .setSaveConsumer { v ->
                    val trimmed = v?.trim() ?: ""
                    config.set(
                        path,
                        if (trimmed.isEmpty()) emptyArray<String>()
                        else trimmed.split("\\s*,\\s*".toRegex()).toTypedArray()
                    )
                }
                .build()
        }

        val text = value?.toString() ?: ""
        val defText = defVal?.toString() ?: text
        return builder.startStrField(label, text)
            .setDefaultValue(defText)
            .setSaveConsumer { v -> config.set(path, v) }
            .build()
    }

    private fun label(entry: AnnoCFG.Entry): Component {
        return if (entry.translationKey.isNotEmpty()) {
            Component.translatable(entry.translationKey)
        } else {
            Component.literal(entry.path)
        }
    }
}
