package dev.famjam.radiofm.voice.plasmo;

import dev.famjam.radiofm.BuildInfo;
import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.voice.ReadyQueue;
import dev.famjam.radiofm.voice.VoiceBackend;
import dev.famjam.radiofm.voice.VoiceOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import su.plo.slib.api.server.entity.McServerEntity;
import su.plo.slib.api.server.position.ServerPos3d;
import su.plo.slib.api.server.world.McServerWorld;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.audio.codec.AudioEncoder;
import su.plo.voice.api.encryption.Encryption;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.provider.AudioFrameProvider;
import su.plo.voice.api.server.audio.provider.AudioFrameResult;
import su.plo.voice.api.server.audio.source.AudioSender;
import su.plo.voice.api.server.audio.source.ServerProximitySource;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Addon(id = "radiofm", name = "RadioFM", version = BuildInfo.VERSION, authors = {"FAMJAM"}, scope = AddonLoaderScope.SERVER)
public final class PlasmoBackend extends ReadyQueue implements AddonInitializer {

    private static final String ICON = "/assets/" + RadioFM.MODID + "/textures/gui/category_icon.png";

    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    private volatile ServerSourceLine line;

    private PlasmoBackend() {
    }

    public static VoiceBackend create() {
        PlasmoBackend backend = new PlasmoBackend();
        PlasmoVoiceServer.getAddonsLoader().load(backend);
        return backend;
    }

    @Override
    public void onAddonInitialize() {
        try (InputStream icon = PlasmoBackend.class.getResourceAsStream(ICON)) {
            // RU: ключ перевода - тот же, что у SVC, переводит клиент нашего мода
            // US: the same translation key as for SVC, our mod on the client translates it
            line = voiceServer.getSourceLineManager()
                    .createBuilder(this, RadioFM.MODID, "category.radiofm.radios", icon, 10)
                    .withPlayers(false)
                    .build();
        } catch (Exception e) {
            RadioFM.LOGGER.error("Could not register the radio line in Plasmo Voice", e);
            return;
        }
        RadioFM.LOGGER.info("Plasmo Voice is up, radio can start");
        markReady();
    }

    @Override
    public void onAddonShutdown() {
        markNotReady();
        ServerSourceLine current = line;
        line = null;
        if (current != null) {
            voiceServer.getSourceLineManager().unregister(current);
        }
    }

    /** RU: готовность ведут события самого PV | US: readiness follows PV's own lifecycle */
    @Override
    public void reset() {
        clearWaiting();
    }

    @Override
    public VoiceOutput openAt(ServerLevel level, BlockPos pos, float range, Supplier<short[]> frames) {
        ServerSourceLine current = line;
        if (current == null) {
            return null;
        }
        McServerWorld world = voiceServer.getMinecraftServer().getWorld(level);
        ServerPos3d position = new ServerPos3d(world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        return play(current.createStaticSource(position, false), range, frames);
    }

    @Override
    public VoiceOutput openOn(ServerPlayer player, float range, Supplier<short[]> frames) {
        ServerSourceLine current = line;
        if (current == null) {
            return null;
        }
        McServerEntity entity = voiceServer.getMinecraftServer().getEntityByInstance(player);
        return play(current.createEntitySource(entity, false), range, frames);
    }

    private VoiceOutput play(ServerProximitySource<?> source, float range, Supplier<short[]> frames) {
        AudioEncoder encoder = voiceServer.createOpusEncoder(false);
        Encryption encryption = voiceServer.getDefaultEncryption();

        AudioFrameProvider provider = () -> {
            short[] pcm = frames.get();
            if (pcm == null) {
                return AudioFrameResult.Finished.INSTANCE;
            }
            try {
                return new AudioFrameResult.Provided(encryption.encrypt(encoder.encode(pcm)));
            } catch (Exception e) {
                RadioFM.LOGGER.warn("Plasmo Voice could not encode a radio frame", e);
                return AudioFrameResult.Finished.INSTANCE;
            }
        };

        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                source.remove();
                encoder.close();
            }
        };

        // RU: PV спрашивает дальность на каждом кадре, поэтому её можно менять на лету
        // US: PV asks for the distance on every frame, so it can change live
        short[] distance = {toDistance(range)};
        AudioSender sender = source.createAudioSender(provider, () -> distance[0]);
        // RU: кодировщик закрываем, только когда отправка встала, иначе кадр может лечь в закрытый
        // US: close the encoder only once sending has stopped, or a frame may hit a closed one
        sender.onStop(release);

        return new VoiceOutput() {
            private volatile boolean started;

            @Override
            public void start() {
                started = true;
                sender.start();
            }

            @Override
            public void stop() {
                if (started) {
                    sender.stop();
                } else {
                    release.run();
                }
            }

            @Override
            public void setRange(float range) {
                distance[0] = toDistance(range);
            }
        };
    }

    private static short toDistance(float range) {
        return (short) Math.min(range, Short.MAX_VALUE);
    }
}
