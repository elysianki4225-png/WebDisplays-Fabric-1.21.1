package net.montoyo.wd.client.gui;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.montoyo.wd.config.annoconfg.AnnoCFG;

import java.util.Locale;

/**
 * Builds a config screen out of the AnnoCFG annotated config classes.
 */
@Environment(EnvType.CLIENT)
public final class WDConfigScreen {
    private WDConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("webdisplays.config.screen.title"));
        ConfigEntryBuilder entryBuilder = builder.getEntryBuilder();

        for (AnnoCFG config : AnnoCFG.all()) {
            ConfigCategory category = builder.getOrCreateCategory(Component.translatable(
                    "webdisplays.config.screen.category." + config.getSide().name().toLowerCase(Locale.ROOT)));

            for (AnnoCFG.Entry entry : config.getEntries().values())
                category.addEntry(buildEntry(entryBuilder, config, entry));
        }

        builder.setSavingRunnable(() -> {
            for (AnnoCFG config : AnnoCFG.all())
                config.save();
        });

        return builder.build();
    }

    private static AbstractConfigListEntry<?> buildEntry(ConfigEntryBuilder builder, AnnoCFG config, AnnoCFG.Entry entry) {
        Component label = label(entry);
        Object value = entry.get();
        Object defVal = config.getDefault(entry.path);
        String path = entry.path;

        if (value instanceof Boolean bool) {
            boolean defBool = defVal instanceof Boolean b ? b : bool;
            return builder.startBooleanToggle(label, bool)
                    .setDefaultValue(defBool)
                    .setSaveConsumer(v -> config.set(path, v))
                    .build();
        }

        if (value instanceof Integer i) {
            int defInt = defVal instanceof Number n ? n.intValue() : i;
            return builder.startIntField(label, i)
                    .setDefaultValue(defInt)
                    .setMin(entry.hasRange ? (int) entry.min : Integer.MIN_VALUE)
                    .setMax(entry.hasRange ? (int) entry.max : Integer.MAX_VALUE)
                    .setSaveConsumer(v -> config.set(path, v))
                    .build();
        }

        if (value instanceof Long l) {
            long defLong = defVal instanceof Number n ? n.longValue() : l;
            return builder.startLongField(label, l)
                    .setDefaultValue(defLong)
                    .setMin(entry.hasRange ? (long) entry.min : Long.MIN_VALUE)
                    .setMax(entry.hasRange ? (long) entry.max : Long.MAX_VALUE)
                    .setSaveConsumer(v -> config.set(path, v))
                    .build();
        }

        if (value instanceof Double d) {
            double defDouble = defVal instanceof Number n ? n.doubleValue() : d;
            return builder.startDoubleField(label, d)
                    .setDefaultValue(defDouble)
                    .setMin(entry.hasRange ? entry.min : -Double.MAX_VALUE)
                    .setMax(entry.hasRange ? entry.max : Double.MAX_VALUE)
                    .setSaveConsumer(v -> config.set(path, v))
                    .build();
        }

        if (value instanceof String[] array) {
            String joined = String.join(",", array);
            String defStr = defVal != null ? defVal.toString() : "";
            return builder.startStrField(label, joined)
                    .setDefaultValue(defStr)
                    .setSaveConsumer(v -> {
                        String trimmed = v == null ? "" : v.trim();
                        config.set(path, trimmed.isEmpty() ? new String[0] : trimmed.split("\\s*,\\s*"));
                    })
                    .build();
        }

        String text = value == null ? "" : value.toString();
        String defText = defVal == null ? text : defVal.toString();
        return builder.startStrField(label, text)
                .setDefaultValue(defText)
                .setSaveConsumer(v -> config.set(path, v))
                .build();
    }

    private static Component label(AnnoCFG.Entry entry) {
        if (!entry.translationKey.isEmpty())
            return Component.translatable(entry.translationKey);

        return Component.literal(entry.path);
    }
}
