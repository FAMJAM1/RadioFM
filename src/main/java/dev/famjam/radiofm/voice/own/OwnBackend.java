package dev.famjam.radiofm.voice.own;

import com.sedmelluq.discord.lavaplayer.natives.opus.OpusEncoder;
import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.network.RadioAudioEndPacket;
import dev.famjam.radiofm.network.RadioAudioPacket;
import dev.famjam.radiofm.network.RadioTrackPacket;
import dev.famjam.radiofm.voice.ReadyQueue;
import dev.famjam.radiofm.voice.VoiceBackend;
import dev.famjam.radiofm.voice.VoiceOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * RU: свой канал: кадры кодируются в Opus и уходят пакетами игрокам в зоне слышимости
 * US: our own channel: frames are encoded to Opus and sent as packets to players within range
 */
public final class OwnBackend extends ReadyQueue {

    private static final int FRAME_SIZE = 960;
    private static final long FRAME_MS = 20L;
    /** RU: сложность кодировщика Opus, 0..10 | US: Opus encoder complexity, 0..10 */
    private static final int OPUS_COMPLEXITY = 8;
    /**
     * RU: шлём чуть дальше слышимости, иначе на краю звук рвётся по таймауту клиента
     * US: send a little past the range, or the sound breaks on the client timeout at the edge
     */
    private static final double SEND_MARGIN = 8D;

    private static OwnBackend instance;

    /**
     * RU: у каждого радио свой повтор: декодер может подождать кадр до 20мс, и с одним
     *     общим потоком медленное радио задерживало бы остальные
     * US: every radio has its own repeating task: the decoder may wait up to 20ms for a frame,
     *     and with one shared thread a slow radio would hold up the rest
     */
    private static final int PUMP_THREADS = 4;

    private final List<Output> outputs = new CopyOnWriteArrayList<>();
    private volatile List<Listener> listeners = List.of();
    private volatile Map<UUID, Listener> listenersById = Map.of();
    private ScheduledExecutorService pump;

    private record Listener(ServerPlayer player, ResourceKey<Level> level, Vec3 position, int entityId) {
    }

    private record Told(int serial, long at) {
    }

    private static final long TRACK_RESEND_MS = 3000L;

    private OwnBackend() {
    }

    public static VoiceBackend create() {
        instance = new OwnBackend();
        instance.markReady();
        return instance;
    }

    public static boolean isActive(VoiceBackend backend) {
        return backend != null && backend == instance;
    }

    /**
     * RU: мир трогаем только с серверного потока, поэтому раз в тик снимаем игроков,
     *     а поток звука работает со снимком
     * US: the world is touched from the server thread only, so players are snapshotted
     *     once a tick and the audio thread works from the snapshot
     */
    public static void tick(MinecraftServer server) {
        OwnBackend backend = instance;
        if (backend == null || backend.outputs.isEmpty()) {
            return;
        }
        List<Listener> all = new ArrayList<>();
        Map<UUID, Listener> byId = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Listener listener = new Listener(player, player.level().dimension(), player.getEyePosition(), player.getId());
            all.add(listener);
            byId.put(player.getUUID(), listener);
        }
        backend.listeners = all;
        backend.listenersById = byId;
    }

    @Override
    public VoiceOutput openAt(ServerLevel level, BlockPos pos, float range, Supplier<short[]> frames) {
        return add(new Output(level.dimension(), pos, null, range, frames));
    }

    @Override
    public VoiceOutput openOn(ServerPlayer player, float range, Supplier<short[]> frames) {
        return add(new Output(null, null, player.getUUID(), range, frames));
    }

    private Output add(Output output) {
        outputs.add(output);
        output.task = pump().scheduleAtFixedRate(output::pumpSafely, FRAME_MS, FRAME_MS, TimeUnit.MILLISECONDS);
        return output;
    }

    private synchronized ScheduledExecutorService pump() {
        if (pump == null) {
            pump = Executors.newScheduledThreadPool(PUMP_THREADS, runnable -> {
                Thread thread = new Thread(runnable, "radiofm-audio");
                thread.setDaemon(true);
                return thread;
            });
        }
        return pump;
    }

    /** RU: свой канал всегда готов, сбрасываем только играющие | US: our channel is always ready, only playing outputs are dropped */
    @Override
    public void reset() {
        outputs.forEach(Output::stop);
        listeners = List.of();
        listenersById = Map.of();
    }

    private final class Output implements VoiceOutput {

        private final UUID id = UUID.randomUUID();
        private final ResourceKey<Level> level;
        private final BlockPos pos;
        private final UUID holder;
        private volatile float range;
        private final Supplier<short[]> frames;
        private volatile java.util.concurrent.ScheduledFuture<?> task;
        private final Set<ServerPlayer> heard = new HashSet<>();
        /**
         * RU: когда и какой трек сообщили игроку. Название повторяем раз в TRACK_RESEND_MS: при смене
         *     измерения клиент сбрасывает звуки уже после того, как принял название, и оно терялось.
         *     Только поток звука
         * US: when and which track a player was told. The title is repeated every TRACK_RESEND_MS: on a
         *     dimension change the client drops its sounds after it has already taken the title, which got lost.
         *     Audio thread only
         */
        private final Map<ServerPlayer, Told> toldTrack = new HashMap<>();
        private volatile RadioTrackPacket track;
        private volatile int trackSerial;

        private volatile boolean started;
        private volatile boolean stopRequested;

        // RU: трогаются только из потока звука | US: touched from the audio thread only
        private OpusEncoder encoder;
        private ShortBuffer pcmBuffer;
        private ByteBuffer opusBuffer;

        private Output(ResourceKey<Level> level, BlockPos pos, UUID holder, float range, Supplier<short[]> frames) {
            this.level = level;
            this.pos = pos;
            this.holder = holder;
            this.range = range;
            this.frames = frames;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            stopRequested = true;
        }

        @Override
        public void setRange(float range) {
            this.range = range;
        }

        @Override
        public void setTrack(String title, String author) {
            track = title == null || title.isBlank() ? null
                    : new RadioTrackPacket(id, clip(title), author == null ? "" : clip(author));
            trackSerial++;
        }

        private static String clip(String text) {
            return text.length() > 256 ? text.substring(0, 256) : text;
        }

        private void pumpSafely() {
            try {
                pump();
            } catch (Throwable e) {
                RadioFM.LOGGER.warn("Radio audio frame failed", e);
                finish();
            }
        }

        private void pump() {
            if (stopRequested) {
                finish();
                return;
            }
            if (!started) {
                return;
            }
            short[] pcm = frames.get();
            if (pcm == null) {
                finish();
                return;
            }

            ResourceKey<Level> sourceLevel = level;
            Vec3 at;
            int entityId = -1;
            BlockPos sourcePos = pos;
            if (holder != null) {
                Listener holderNow = listenersById.get(holder);
                if (holderNow == null) {
                    return;
                }
                sourceLevel = holderNow.level();
                at = holderNow.position();
                entityId = holderNow.entityId();
                sourcePos = BlockPos.containing(at);
            } else {
                at = Vec3.atCenterOf(pos);
            }

            float currentRange = range;
            RadioAudioPacket packet = new RadioAudioPacket(id, sourcePos, entityId, currentRange, encode(pcm));
            double reach = (currentRange + SEND_MARGIN) * (currentRange + SEND_MARGIN);
            Set<ServerPlayer> inRange = new HashSet<>();
            for (Listener listener : listeners) {
                if (listener.level() != sourceLevel || listener.position().distanceToSqr(at) > reach) {
                    continue;
                }
                if (listener.player().hasDisconnected()) {
                    continue;
                }
                // RU: название шлём при смене трека и тем, кто только вошёл в зону
                // US: the title goes out on a track change and to those who just came into range
                RadioTrackPacket current = track;
                int serial = trackSerial;
                long nowMs = System.currentTimeMillis();
                Told told = toldTrack.get(listener.player());
                if (current != null && (told == null || told.serial() != serial || nowMs - told.at() >= TRACK_RESEND_MS)) {
                    PacketDistributor.sendToPlayer(listener.player(), current);
                    toldTrack.put(listener.player(), new Told(serial, nowMs));
                }
                PacketDistributor.sendToPlayer(listener.player(), packet);
                heard.add(listener.player());
                inRange.add(listener.player());
            }
            // RU: вышедшему из зоны клиент забывает радио, при возврате название шлём заново
            // US: the client forgets the radio for whoever left range, so the title is sent again on return
            toldTrack.keySet().retainAll(inRange);
        }

        private byte[] encode(short[] pcm) {
            if (encoder == null) {
                encoder = new OpusEncoder(48000, 1, OPUS_COMPLEXITY);
                pcmBuffer = ByteBuffer.allocateDirect(FRAME_SIZE * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
                opusBuffer = ByteBuffer.allocateDirect(4000).order(ByteOrder.nativeOrder());
            }
            pcmBuffer.clear();
            pcmBuffer.put(pcm, 0, Math.min(pcm.length, FRAME_SIZE));
            while (pcmBuffer.position() < FRAME_SIZE) {
                pcmBuffer.put((short) 0);
            }
            pcmBuffer.flip();
            opusBuffer.clear();
            int length = encoder.encode(pcmBuffer, FRAME_SIZE, opusBuffer);
            byte[] frame = new byte[length];
            opusBuffer.get(frame, 0, length);
            return frame;
        }

        private void finish() {
            if (!outputs.remove(this)) {
                return;
            }
            java.util.concurrent.ScheduledFuture<?> scheduled = task;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
            RadioAudioEndPacket end = new RadioAudioEndPacket(id);
            for (ServerPlayer player : heard) {
                if (!player.hasDisconnected()) {
                    PacketDistributor.sendToPlayer(player, end);
                }
            }
            if (encoder != null) {
                encoder.close();
                encoder = null;
            }
        }
    }
}
