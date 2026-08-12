package dev.famjam.radiofm.radio;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.config.ServerConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** ru: радио как предмет — голова игрока с настройками станции | en: the radio as an item — a player head carrying its settings */
public class RadioItem {

    private static final String PROFILE_NAME = "radiofm";

    private RadioItem() {
    }

    public static ItemStack create(RadioStation station) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        applySkin(stack);
        write(stack, station);
        return stack;
    }

    /** ru: то, что выходит из верстака | en: what comes out of the crafting table */
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
     * ru: значение запекается в предмет: подсказку рисует клиент, а конфиг серверный
     * en: baked into the item: the tooltip is drawn by the client, the config is the server's
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

    /** ru: игра рисует такое курсивом | en: the game renders these in italics */
    private static MutableComponent plain(MutableComponent text) {
        return text.withStyle(style -> style.withItalic(false));
    }

    public static Optional<RadioStation> read(ItemStack stack) {
        if (stack.isEmpty()) {
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

    /**
     * ru: голова хранит не картинку, а ссылку на сервер текстур Mojang в base64
     * en: a head stores a link to Mojang's texture server in base64, not an image
     * <p>
     * ru: со свойством textures профиль считается готовым, в сеть игра не идёт,
     *     поэтому UUID произвольный — он лишь выводится из ссылки
     * en: with textures set the profile counts as resolved and nothing is fetched,
     *     so the UUID is arbitrary and merely derived from the link
     */
    private static void applySkin(ItemStack stack) {
        // ru: рецепт строит результат до чтения конфига | en: recipes build their result before the config is read
        if (!ServerConfig.SPEC.isLoaded()) {
            return;
        }
        String url = RadioFM.SERVER_CONFIG.skinUrl.get();
        if (url == null || url.isEmpty()) {
            return;
        }

        JsonObject skin = new JsonObject();
        skin.addProperty("url", url);
        JsonObject textures = new JsonObject();
        textures.add("SKIN", skin);
        JsonObject payload = new JsonObject();
        payload.add("textures", textures);

        String encoded = Base64.getEncoder()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));

        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)), PROFILE_NAME);
        profile.getProperties().put("textures", new Property("textures", encoded));
        stack.set(DataComponents.PROFILE, new ResolvableProfile(profile));
    }
}
