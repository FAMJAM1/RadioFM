package dev.famjam.radiofm;

import dev.famjam.radiofm.config.ServerConfig;
import dev.famjam.radiofm.platform.Platform;
import dev.famjam.radiofm.radio.RadioBlock;
import dev.famjam.radiofm.radio.RadioBlockEntity;
import dev.famjam.radiofm.radio.RadioRecipeSerializer;
import dev.famjam.radiofm.radio.RadioStation;
import dev.famjam.radiofm.voice.VoiceBackends;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

public final class RadioFM {

    public static final String MODID = "radiofm";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /**
     * RU: версия пакетов мода; поднимать при любой их правке, тогда клиент другой версии
     *     получит отказ при входе, а не вылет на незнакомом пакете. До 1.3.0 здесь по ошибке
     *     стоял id мода, поэтому старые клиенты отличаются и от этого значения
     * US: the mod's packet version; bump it on any packet change, so a client of another
     *     version is refused on join instead of crashing on an unknown packet. Before 1.3.0
     *     the mod id sat here by mistake, so old clients differ from this value as well
     */
    public static final String NETWORK_VERSION = "2";
    public static final ServerConfig SERVER_CONFIG = ServerConfig.INSTANCE;

    /** RU: сколько радио на игрока, вместе с тем что в руке | US: radios per player, the held one included */
    public static final net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.IntegerValue> MAX_RADIOS =
            net.minecraft.world.level.GameRules.register("radiofm:max",
                    net.minecraft.world.level.GameRules.Category.MISC,
                    net.minecraft.world.level.GameRules.IntegerValue.create(3));

    public static final net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.IntegerValue> MAX_RADIUS =
            net.minecraft.world.level.GameRules.register("radiofm:maxRadius",
                    net.minecraft.world.level.GameRules.Category.MISC,
                    net.minecraft.world.level.GameRules.IntegerValue.create(64));

    public static final Supplier<DataComponentType<RadioStation>> STATION_COMPONENT =
            Platform.INSTANCE.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "station", () -> DataComponentType.<RadioStation>builder()
                    .persistent(RadioStation.CODEC)
                    .networkSynchronized(RadioStation.STREAM_CODEC)
                    .build());

    public static final Supplier<RadioBlock> RADIO_BLOCK =
            Platform.INSTANCE.register(BuiltInRegistries.BLOCK, "radio", () -> new RadioBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final Supplier<BlockItem> RADIO_ITEM =
            // RU: не стакается: копии в стаке несут одну станцию, и выброшенное играющее радио
            //     продолжало бы играть, пока в инвентаре лежит хоть одна копия
            // US: does not stack: copies in a stack share one station, and a dropped playing radio
            //     would keep playing while a single copy stayed in the inventory
            Platform.INSTANCE.register(BuiltInRegistries.ITEM, "radio", () -> new BlockItem(RADIO_BLOCK.get(), new Item.Properties().stacksTo(1)));

    public static final Supplier<BlockEntityType<RadioBlockEntity>> RADIO_BLOCK_ENTITY =
            Platform.INSTANCE.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, "radio", () -> BlockEntityType.Builder
                    .of(RadioBlockEntity::new, RADIO_BLOCK.get())
                    .build(null));

    static {
        Platform.INSTANCE.register(BuiltInRegistries.RECIPE_SERIALIZER, MODID, () -> RadioRecipeSerializer.INSTANCE);
    }

    private RadioFM() {
    }

    /** RU: зовёт загрузчик при старте мода: регистрация идёт при загрузке класса | US: the loader calls this at mod start: registration happens as the class loads */
    public static void init() {
        VoiceBackends.select();
    }
}
