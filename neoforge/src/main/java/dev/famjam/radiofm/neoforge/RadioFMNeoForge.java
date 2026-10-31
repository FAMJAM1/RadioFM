package dev.famjam.radiofm.neoforge;

import dev.famjam.radiofm.ClientEventHandlers;
import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.client.OwnAudioClient;
import dev.famjam.radiofm.config.ClientConfig;
import dev.famjam.radiofm.config.ServerConfig;
import dev.famjam.radiofm.network.AdjustRangePacket;
import dev.famjam.radiofm.network.OpenRadioScreenPacket;
import dev.famjam.radiofm.network.RadioAudioEndPacket;
import dev.famjam.radiofm.network.RadioAudioPacket;
import dev.famjam.radiofm.network.RadioControlPacket;
import dev.famjam.radiofm.network.RadioServerHandlers;
import dev.famjam.radiofm.network.RadioStatusPacket;
import dev.famjam.radiofm.network.RadioTrackPacket;
import dev.famjam.radiofm.network.SaveRadioPacket;
import dev.famjam.radiofm.network.ToggleHandRadioPacket;
import dev.famjam.radiofm.radio.RadioItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

@Mod(RadioFM.MODID)
public final class RadioFMNeoForge {

    public RadioFMNeoForge(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, RadioFM.MODID + "-server.toml");
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, RadioFM.MODID + "-client.toml");
        // RU: до registerAll: регистрация в общей части идёт при загрузке класса RadioFM
        // US: before registerAll: the common part registers as the RadioFM class loads
        RadioFM.init();
        NeoForgePlatform.registerAll(modBus);

        modBus.addListener(RadioFMNeoForge::registerPayloads);
        modBus.addListener(RadioFMNeoForge::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.REDSTONE_BLOCKS)) {
            event.accept(RadioItem.createDefault());
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(RadioFM.NETWORK_VERSION);
        registrar.playToClient(OpenRadioScreenPacket.TYPE, OpenRadioScreenPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> ClientEventHandlers.openScreen(packet)));
        registrar.playToClient(RadioStatusPacket.TYPE, RadioStatusPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> ClientEventHandlers.updateStatus(packet)));
        // RU: звук разбираем прямо на сетевом потоке: через основной кадры ждали бы отрисовку
        // US: audio is handled right on the network thread: through the main one frames would wait for rendering
        var audio = registrar.executesOn(HandlerThread.NETWORK);
        audio.playToClient(RadioAudioPacket.TYPE, RadioAudioPacket.STREAM_CODEC,
                (packet, ctx) -> OwnAudioClient.onAudio(packet));
        audio.playToClient(RadioTrackPacket.TYPE, RadioTrackPacket.STREAM_CODEC,
                (packet, ctx) -> OwnAudioClient.onTrack(packet));
        audio.playToClient(RadioAudioEndPacket.TYPE, RadioAudioEndPacket.STREAM_CODEC,
                (packet, ctx) -> OwnAudioClient.onEnd(packet));
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
}
