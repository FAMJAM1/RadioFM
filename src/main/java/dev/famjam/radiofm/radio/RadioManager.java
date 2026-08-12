package dev.famjam.radiofm.radio;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ru: реестр играющих радио; режимы лежат тут, а не в стриме, чтобы пережить выключение
 * en: registry of playing radios; modes live here, not in the stream, so they outlive it
 * <p>
 * ru: ключ — игрок для радио в руке, станция для поставленного
 * en: keyed by player for a held radio, by station for a placed one
 */
public class RadioManager {

    /** ru: дальше радио уже «не рядом» | en: past this a radio is no longer "nearby" */
    private static final double NEAREST_RADIUS_SQR = 1024D;

    private static RadioManager instance;

    public static RadioManager get() {
        if (instance == null) {
            instance = new RadioManager();
        }
        return instance;
    }

    private final Map<UUID, BlockRadioStream> blockStreams = new HashMap<>();
    private final Map<UUID, HandRadioStream> handStreams = new HashMap<>();
    private final Map<UUID, Modes> modes = new HashMap<>();

    /** ru: -1 в repeatTrack — повтор выключен | en: -1 in repeatTrack means repeat off */
    private record Modes(boolean shuffle, int repeatTrack) {
        static final Modes DEFAULT = new Modes(false, -1);

        Modes withShuffle(boolean shuffle) {
            return new Modes(shuffle, repeatTrack);
        }

        Modes withRepeatTrack(int track) {
            return new Modes(shuffle, track);
        }

        boolean isDefault() {
            return !shuffle && repeatTrack < 0;
        }
    }

    // ru: поставленное радио | en: placed radio

    /**
     * ru: каждое радио держит потоки и соединение, поэтому их число ограничено
     * en: every radio holds threads and a connection, hence the cap
     *
     * @return ru: false, если мест нет | en: false when the cap is reached
     */
    public boolean startBlockRadio(RadioStation station, ServerLevel level, BlockPos pos, UUID owner) {
        stopBlockRadio(station.id()); // ru: перезапуск слот не занимает | en: a restart does not take a slot

        int limit = RadioFM.SERVER_CONFIG.maxActiveRadios.get();
        if (blockStreams.size() >= limit) {
            RadioFM.LOGGER.warn("Refusing to start radio at {}: {} already playing", pos, limit);
            return false;
        }

        BlockRadioStream stream = new BlockRadioStream(station, level, pos);
        applyModes(station.id(), stream);
        blockStreams.put(station.id(), stream);
        if (owner != null) {
            owners.put(station.id(), owner);
        }
        stream.start();
        return true;
    }

    public void stopBlockRadio(UUID stationId) {
        owners.remove(stationId);
        BlockRadioStream stream = blockStreams.remove(stationId);
        if (stream != null) {
            stream.stop();
        }
    }

    /** ru: кто включил — по этому считается личный лимит | en: who switched it on, for the per-player cap */
    private final Map<UUID, UUID> owners = new HashMap<>();

    /**
     * ru: поставленные игроком плюс то, что в руке; по этому числу работает radiofm:max
     * en: what the player placed plus what is in hand; radiofm:max counts this
     */
    public int countActiveFor(UUID playerId) {
        int placed = (int) owners.entrySet().stream()
                .filter(entry -> entry.getValue().equals(playerId))
                .filter(entry -> blockStreams.containsKey(entry.getKey()))
                .count();
        return placed + (handStreams.containsKey(playerId) ? 1 : 0);
    }

    public Optional<BlockRadioStream> getBlockStream(UUID stationId) {
        return Optional.ofNullable(blockStreams.get(stationId));
    }

    /** ru: радио сломали или заменили | en: the radio is gone or replaced */
    public void forget(UUID stationId) {
        stopBlockRadio(stationId);
        modes.remove(stationId);
    }

    /** ru: то же ли радио на этом месте | en: whether the same radio is still there */
    public static boolean isRadioAt(ServerLevel level, BlockPos pos, UUID stationId) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        return RadioHeads.read(level, pos)
                .map(station -> station.id().equals(stationId))
                .orElse(false);
    }

    /** ru: ближайшее радио — для таймера в окне | en: nearest radio, for the timer on screen */
    public Optional<BlockRadioStream> nearestStream(ServerPlayer player) {
        return blockStreams.values().stream()
                .filter(RadioStream::isActive)
                .filter(stream -> stream.getLevel().dimension().equals(player.level().dimension()))
                .filter(stream -> stream.getPosition().distSqr(player.blockPosition()) < NEAREST_RADIUS_SQR)
                .findFirst();
    }

    // ru: радио в руке | en: held radio

    public void startHandRadio(RadioStation station, ServerPlayer player) {
        stopHandRadio(player.getUUID());

        HandRadioStream stream = new HandRadioStream(station, player);
        applyModes(player.getUUID(), stream);
        handStreams.put(player.getUUID(), stream);
        stream.start();
    }

    public void stopHandRadio(UUID playerId) {
        HandRadioStream stream = handStreams.remove(playerId);
        if (stream != null) {
            stream.stop();
        }
    }

    public Optional<HandRadioStream> getHandStream(UUID playerId) {
        return Optional.ofNullable(handStreams.get(playerId));
    }

    public boolean isHandRadioActive(UUID playerId) {
        return getHandStream(playerId).map(RadioStream::isActive).orElse(false);
    }

    /** ru: игрок вышел | en: the player left */
    public void onPlayerLeft(UUID playerId) {
        stopHandRadio(playerId);
        modes.remove(playerId);
    }

    // ru: режимы, общие для обоих видов | en: modes, shared by both kinds

    public boolean isShuffle(UUID key) {
        return modes.getOrDefault(key, Modes.DEFAULT).shuffle();
    }

    public int getRepeatTrackIndex(UUID key) {
        return modes.getOrDefault(key, Modes.DEFAULT).repeatTrack();
    }

    public void setShuffle(UUID key, boolean shuffle) {
        updateModes(key, current -> current.withShuffle(shuffle));
        liveStream(key).ifPresent(stream -> stream.setShuffle(shuffle));
    }

    /** ru: номер вне списка выключает повтор | en: an index outside the list turns repeat off */
    public void setRepeatTrack(UUID key, int index) {
        updateModes(key, current -> current.withRepeatTrack(Math.max(index, -1)));

        liveStream(key).ifPresent(stream -> {
            stream.setRepeatTrack(index);
            // ru: на выключенном это сделает start() | en: start() handles it when switched off
            if (index >= 0 && index != stream.getCurrentTrackIndex() && stream.isActive()) {
                stream.skipToIndex(index);
            }
        });
    }

    private void updateModes(UUID key, java.util.function.UnaryOperator<Modes> change) {
        Modes updated = change.apply(modes.getOrDefault(key, Modes.DEFAULT));
        if (updated.isDefault()) {
            modes.remove(key);
        } else {
            modes.put(key, updated);
        }
    }

    private void applyModes(UUID key, RadioStream stream) {
        Modes saved = modes.getOrDefault(key, Modes.DEFAULT);
        stream.setShuffle(saved.shuffle());
        stream.setRepeatTrack(saved.repeatTrack());
    }

    /** ru: стрим по ключу, любой из двух | en: the stream for a key, either kind */
    public Optional<RadioStream> liveStream(UUID key) {
        RadioStream stream = handStreams.get(key);
        if (stream == null) {
            stream = blockStreams.get(key);
        }
        return Optional.ofNullable(stream);
    }

    // ru: жизненный цикл | en: lifecycle

    public void stopAll() {
        blockStreams.values().forEach(RadioStream::stop);
        blockStreams.clear();
        handStreams.values().forEach(RadioStream::stop);
        handStreams.clear();
        modes.clear();
        owners.clear();
        RadioFM.LOGGER.info("All radio streams stopped");
    }
}
