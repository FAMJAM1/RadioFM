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
     * RU: настройки на поставленном радио; аттачмент вместо миксина - NeoForge сам их хранит
     * US: settings on the placed radio; an attachment, not a mixin - NeoForge stores them
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
            ITEMS.registerSimpleBlockItem("radio", RADIO_BLOCK);

    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<dev.famjam.radiofm.radio.RadioBlockEntity>> RADIO_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("radio", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                    .of(dev.famjam.radiofm.radio.RadioBlockEntity::new, RADIO_BLOCK.get())
                    .build(null));

    public RadioFM(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, MODID + "-server.toml");

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
        var registrar = event.registrar(MODID);
        registrar.playToClient(OpenRadioScreenPacket.TYPE, OpenRadioScreenPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> ClientEventHandlers.openScreen(packet)));
        registrar.playToClient(RadioStatusPacket.TYPE, RadioStatusPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> ClientEventHandlers.updateStatus(packet)));
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
