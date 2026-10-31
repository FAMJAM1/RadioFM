package dev.famjam.radiofm.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * RU: раздел настроек NeoForge с кнопкой «Назад», которая выходит без сохранения;
 *     «Готово» и Esc по-прежнему сохраняют
 * US: a NeoForge settings section with a Back button that leaves without saving;
 *     Done and Esc still save
 */
public final class RadioConfigSection extends ConfigurationScreen.ConfigurationSectionScreen {

    // RU: четыре кнопки шириной 120 не влезают в окно 854x480 | US: four 120-wide buttons do not fit an 854x480 window
    private static final int NARROW_WIDTH = 96;

    private final Button backButton = Button.builder(Component.translatable("radiofm.configuration.back"), button -> back())
            .tooltip(Tooltip.create(Component.translatable("radiofm.configuration.back.tooltip")))
            .width(Button.SMALL_WIDTH)
            .build();

    public RadioConfigSection(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
        super(parent, type, modConfig, title);
    }

    private RadioConfigSection(Context parentContext, Screen parent, Map<String, Object> valueSpecs, String key,
            Set<? extends UnmodifiableConfig.Entry> entrySet, Component title) {
        super(parentContext, parent, valueSpecs, key, entrySet, title);
    }

    /** RU: NeoForge открывает подраздел своим классом, без нашей кнопки | US: NeoForge opens a subsection with its own class, without our button */
    @Override
    protected Element createSection(String key, UnmodifiableConfig subconfig, UnmodifiableConfig subsection) {
        Element original = super.createSection(key, subconfig, subsection);
        if (original == null || original.widget() == null) {
            return original;
        }
        Button open = Button.builder(original.widget().getMessage(), button -> minecraft.setScreen(sectionCache.computeIfAbsent(key,
                        k -> new RadioConfigSection(context, this, subconfig.valueMap(), key, subsection.entrySet(),
                                Component.translatable(getTranslationKey(key))).rebuild())))
                .tooltip(Tooltip.create(original.tooltip()))
                .width(original.widget().getWidth())
                .build();
        return new Element(original.name(), original.tooltip(), open, false);
    }

    /** RU: у NeoForge нет русского, а свои Undo, Reset и Range он берёт из общих строк | US: NeoForge has no Russian, and takes its Undo, Reset and Range from shared strings */
    @Override
    protected Component getTooltipComponent(String key, @Nullable ModConfigSpec.Range<?> range) {
        Component tooltip = super.getTooltipComponent(key, null);
        if (range == null) {
            return tooltip;
        }
        return Component.empty().append(tooltip).append("\n\n")
                .append(Component.translatable("radiofm.configuration.range_hint", range.toString()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected void addFooter() {
        if (undoButton != null) {
            undoButton.setMessage(Component.translatable("radiofm.configuration.undo"));
            undoButton.setTooltip(Tooltip.create(Component.translatable("radiofm.configuration.undo.tooltip")));
        }
        if (resetButton != null) {
            resetButton.setMessage(Component.translatable("radiofm.configuration.reset"));
            resetButton.setTooltip(Tooltip.create(Component.translatable("radiofm.configuration.reset.tooltip")));
        }
        LinearLayout row = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        var buttons = Stream.of(undoButton, resetButton, backButton, doneButton).filter(Objects::nonNull).toList();
        int width = buttons.size() > 3 ? NARROW_WIDTH : Button.SMALL_WIDTH;
        for (Button button : buttons) {
            button.setWidth(width);
            row.addChild(button);
        }
    }

    private void back() {
        discard();
        onClose();
    }

    /**
     * RU: правки уже лежат в значениях конфига, поэтому их откатываем, а не просто не сохраняем; подразделы,
     *     закрытые через «Готово», тоже откатываем, иначе их правки сохранил бы следующий «Готово» выше
     * US: the edits already sit in the config values, so they are rolled back rather than just left unsaved;
     *     subsections closed with Done are rolled back too, or the next Done above would save their edits
     */
    private void discard() {
        while (undoManager.canUndo()) {
            undoManager.undo();
        }
        for (var child : sectionCache.values()) {
            if (child instanceof RadioConfigSection section) {
                section.discard();
            }
        }
        // RU: сам откат снова ставит отметку правки | US: the rollback itself sets the changed mark again
        changed = false;
        needsRestart = ModConfigSpec.RestartType.NONE;
    }
}
