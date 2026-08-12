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

    /** ru: сколько радио на игрока, вместе с тем что в руке | en: radios per player, the held one included */
    public static final net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.IntegerValue> MAX_RADIOS =
            net.minecraft.world.level.GameRules.register("radiofm:max",
                    net.minecraft.world.level.GameRules.Category.MISC,
                    net.minecraft.world.level.GameRules.IntegerValue.create(3));

    /** ru: потолок слышимости | en: the ceiling on range */
    public static final net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.IntegerValue> MAX_RADIUS =
            net.minecraft.world.level.GameRules.register("radiofm:maxRadius",
                    net.minecraft.world.level.GameRules.Category.MISC,
                    net.minecraft.world.level.GameRules.IntegerValue.create(64));

    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    /** ru: настройки на предмете | en: settings on the item */
    public static final Supplier<DataComponentType<RadioStation>> STATION_COMPONENT =
            COMPONENTS.register("station", () -> DataComponentType.<RadioStation>builder()
                    .persistent(RadioStation.CODEC)
                    .networkSynchronized(RadioStation.STREAM_CODEC)
                    .build());

    /**
     * ru: настройки на голове; аттачмент вместо миксина — NeoForge сам их хранит
     * en: settings on the head; an attachment, not a mixin — NeoForge stores them
     */
    public static final Supplier<AttachmentType<RadioStation>> STATION_ATTACHMENT =
            ATTACHMENTS.register("station", () -> AttachmentType
                    .<RadioStation>builder(() -> RadioStation.create("", java.util.List.of()))
                    .serialize(RadioStation.CODEC)
                    .build());

    public RadioFM(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, MODID + "-server.toml");

        COMPONENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);

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
