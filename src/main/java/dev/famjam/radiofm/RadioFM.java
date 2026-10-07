package dev.famjam.radiofm;

import com.mojang.serialization.Codec;
import dev.famjam.radiofm.config.ServerConfig;
import dev.famjam.radiofm.network.AdjustRangePacket;
import dev.famjam.radiofm.network.OpenRadioScreenPacket;
import dev.famjam.radiofm.network.RadioControlPacket;
import dev.famjam.radiofm.network.RadioStatusPacket;
import dev.famjam.radiofm.network.RadioServerHandlers;
import dev.famjam.radiofm.network.SaveRadioPacket;
import dev.famjam.radiofm.network.ToggleHandRadioPacket;
import dev.famjam.radiofm.radio.RadioRecipeSerializer;
import dev.famjam.radiofm.radio.RadioStation;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

@Mod(RadioFM.MODID)
public class RadioFM {

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
    private static final String NETWORK_VERSION = "2";
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

    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    public static final Supplier<DataComponentType<RadioStation>> STATION_COMPONENT =
            COMPONENTS.register("station", () -> DataComponentType.<RadioStation>builder()
                    .persistent(RadioStation.CODEC)
                    .networkSynchronized(RadioStation.STREAM_CODEC)
                    .build());

    /**
     * RU: только для чтения старых данных: так станцию хранили радио до 1.3.0 и головы из 1.0.0;
     *     теперь она в данных RadioBlockEntity
     * US: for reading old data only: radios before 1.3.0 and heads from 1.0.0 kept the station
     *     this way; it now lives in RadioBlockEntity's data
     */
    public static final Supplier<AttachmentType<RadioStation>> STATION_ATTACHMENT =
            ATTACHMENTS.register("station", () -> AttachmentType
                    .<RadioStation>builder(() -> RadioStation.create("", java.util.List.of()))
                    .serialize(RadioStation.CODEC)
                    .build());

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.famjam.radiofm.radio.RadioBlock> RADIO_BLOCK =
            BLOCKS.registerBlock("radio", dev.famjam.radiofm.radio.RadioBlock::new,
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .strength(1.5F)
                            .sound(net.minecraft.world.level.block.SoundType.WOOD)
                            .noOcclusion());

    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> RADIO_ITEM =
            // RU: не стакается: копии в стаке несут одну станцию, и выброшенное играющее радио
            //     продолжало бы играть, пока в инвентаре лежит хоть одна копия
            // US: does not stack: copies in a stack share one station, and a dropped playing radio
            //     would keep playing while a single copy stayed in the inventory
            ITEMS.registerSimpleBlockItem("radio", RADIO_BLOCK, new net.minecraft.world.item.Item.Properties().stacksTo(1));

    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<dev.famjam.radiofm.radio.RadioBlockEntity>> RADIO_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("radio", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                    .of(dev.famjam.radiofm.radio.RadioBlockEntity::new, RADIO_BLOCK.get())
                    .build(null));

    public RadioFM(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, MODID + "-server.toml");
        container.registerConfig(ModConfig.Type.CLIENT, dev.famjam.radiofm.config.ClientConfig.SPEC, MODID + "-client.toml");
        dev.famjam.radiofm.voice.VoiceBackends.select();

        COMPONENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerRecipeSerializers);
        modEventBus.addListener(this::addToCreativeTab);
    }

    private void addToCreativeTab(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.REDSTONE_BLOCKS)) {
            event.accept(dev.famjam.radiofm.radio.RadioItem.createDefault());
        }
    }

    private void registerPayloads(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(OpenRadioScreenPacket.TYPE, OpenRadioScreenPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> ClientEventHandlers.openScreen(packet)));
        registrar.playToClient(RadioStatusPacket.TYPE, RadioStatusPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> ClientEventHandlers.updateStatus(packet)));
        // RU: звук разбираем прямо на сетевом потоке: через основной кадры ждали бы отрисовку
        // US: audio is handled right on the network thread: through the main one frames would wait for rendering
        var audio = registrar.executesOn(net.neoforged.neoforge.network.registration.HandlerThread.NETWORK);
        audio.playToClient(dev.famjam.radiofm.network.RadioAudioPacket.TYPE, dev.famjam.radiofm.network.RadioAudioPacket.STREAM_CODEC,
                (packet, ctx) -> dev.famjam.radiofm.client.OwnAudioClient.onAudio(packet));
        audio.playToClient(dev.famjam.radiofm.network.RadioTrackPacket.TYPE, dev.famjam.radiofm.network.RadioTrackPacket.STREAM_CODEC,
                (packet, ctx) -> dev.famjam.radiofm.client.OwnAudioClient.onTrack(packet));
        audio.playToClient(dev.famjam.radiofm.network.RadioAudioEndPacket.TYPE, dev.famjam.radiofm.network.RadioAudioEndPacket.STREAM_CODEC,
                (packet, ctx) -> dev.famjam.radiofm.client.OwnAudioClient.onEnd(packet));
        registrar.playToServer(SaveRadioPacket.TYPE, SaveRadioPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() ->
                        RadioServerHandlers.handleSave(packet, (ServerPlayer) ctx.player())));
        registrar.playToServer(RadioControlPacket.TYPE, RadioControlPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() ->
                        RadioServerHandlers.handleControl(packet, (ServerPlayer) ctx.player())));
        registrar.playToServer(ToggleHandRadioPacket.TYPE, ToggleHandRadioPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() ->
                        RadioServerHandlers.handleToggleHandRadio((ServerPlayer) ctx.player())));
        registrar.playToServer(AdjustRangePacket.TYPE, AdjustRangePacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() ->
                        RadioServerHandlers.handleAdjustRange(packet, (ServerPlayer) ctx.player())));
    }

    private void registerRecipeSerializers(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.RECIPE_SERIALIZER,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, MODID),
                () -> RadioRecipeSerializer.INSTANCE);
    }
}
