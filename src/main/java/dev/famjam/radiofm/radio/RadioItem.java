package dev.famjam.radiofm.radio;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.config.ServerConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Optional;

public class RadioItem {

    private RadioItem() {
    }

    public static ItemStack create(RadioStation station) {
        ItemStack stack = new ItemStack(RadioFM.RADIO_ITEM.get());
        write(stack, station);
        return stack;
    }

    /** RU: то, что выходит из верстака | US: what comes out of the crafting table */
    public static ItemStack createDefault() {
        return create(RadioStation.create("", List.of()));
    }

    public static void write(ItemStack stack, RadioStation station) {
        stack.set(RadioFM.STATION_COMPONENT.get(), station);
        stack.set(DataComponents.CUSTOM_NAME, nameFor(station));

        float range = effectiveRange(station);
        if (range > 0) {
            stack.set(DataComponents.LORE, new ItemLore(List.of(
                    plain(Component.translatable("item.radiofm.lore.range", range))
                            .withStyle(ChatFormatting.GRAY))));
        } else {
            stack.remove(DataComponents.LORE);
        }
    }

    /**
     * RU: значение запекается в предмет: подсказку рисует клиент, а конфиг серверный
     * US: baked into the item: the tooltip is drawn by the client, the config is the server's
     */
    private static float effectiveRange(RadioStation station) {
        if (station.range() > 0) {
            return station.range();
        }
        if (!ServerConfig.SPEC.isLoaded()) {
            return -1F;
        }
        return RadioFM.SERVER_CONFIG.radioRange.get().floatValue();
    }

    /** RU: игра рисует такое курсивом | US: the game renders these in italics */
    private static MutableComponent plain(MutableComponent text) {
        return text.withStyle(style -> style.withItalic(false));
    }

    public static Optional<RadioStation> read(ItemStack stack) {
        if (!stack.is(RadioFM.RADIO_ITEM.get())) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(RadioFM.STATION_COMPONENT.get()));
    }

    public static boolean isRadio(ItemStack stack) {
        return read(stack).isPresent();
    }

    private static Component nameFor(RadioStation station) {
        if (station.name().isBlank()) {
            return plain(Component.translatable("item.radiofm.name"));
        }
        return plain(Component.translatable("item.radiofm.lore.playing", station.name()));
    }
}
