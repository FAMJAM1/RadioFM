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
 * RU: режимы лежат тут, а не в стриме, чтобы пережить выключение
 * US: modes live here, not in the stream, so they outlive it
 * <p>
 * RU: ключ - игрок для радио в руке, станция для поставленного
 * US: keyed by player for a held radio, by station for a placed one
 */
public class RadioManager {


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

    /** RU: -1 в repeatTrack - повтор выключен | US: -1 in repeatTrack means repeat off */
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

    /**
     * RU: каждое радио держит потоки и соединение, поэтому их число ограничено
     * US: every radio holds threads and a connection, hence the cap
     *
     * @return RU: false, если мест нет | US: false when the cap is reached
     */
    public boolean startBlockRadio(RadioStation station, ServerLevel level, BlockPos pos, UUID owner) {
        stopBlockRadio(station.id()); // RU: перезапуск слот не занимает | US: a restart does not take a slot

        int limit = RadioFM.SERVER_CONFIG.maxActiveRadios.get();
        if (blockStreams.size() >= limit) {
            RadioFM.LOGGER.warn("Refusing to start radio at {}: {} already playing", pos, limit);
            return false;
        }

        BlockRadioStream stream = new BlockRadioStream(station, level, pos, owner);
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
            // RU: с местом, по нему видно, то ли радио остановили | US: with the spot, so it shows whether the right one stopped
            RadioFM.LOGGER.info("Stopping radio {} at {}", stationId, stream.getPosition());
            stream.stop();
        }
    }

    /** RU: кто включил, по этому считается личный лимит | US: who switched it on, for the per-player cap */
    private final Map<UUID, UUID> owners = new HashMap<>();
    private final Map<UUID, UUID> viewing = new HashMap<>();

    /**
     * RU: поставленные игроком плюс то, что в руке; по этому числу работает radiofm:max
     * US: what the player placed plus what is in hand; radiofm:max counts this
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

    /** RU: радио сломали или заменили | US: the radio is gone or replaced */
    public void forget(UUID stationId) {
        stopBlockRadio(stationId);
        modes.remove(stationId);
    }

    public static boolean isRadioAt(ServerLevel level, BlockPos pos, UUID stationId) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        return PlacedRadios.read(level, pos)
                .map(station -> station.id().equals(stationId))
                .orElse(false);
    }

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

    public void onPlayerLeft(UUID playerId) {
        stopHandRadio(playerId);
        modes.remove(playerId);
        viewing.remove(playerId);
    }

    /** RU: чьё окно открыл игрок, по нему шлётся таймер | US: whose screen the player opened, the timer follows it */
    public void setViewing(UUID playerId, UUID key) {
        viewing.put(playerId, key);
    }

    public Optional<UUID> viewing(UUID playerId) {
        return Optional.ofNullable(viewing.get(playerId));
    }

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

    /** RU: номер вне списка выключает повтор | US: an index outside the list turns repeat off */
    public void setRepeatTrack(UUID key, int index) {
        updateModes(key, current -> current.withRepeatTrack(Math.max(index, -1)));

        liveStream(key).ifPresent(stream -> {
            stream.setRepeatTrack(index);
            // RU: на выключенном это сделает start() | US: start() handles it when switched off
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

    public Optional<RadioStream> liveStream(UUID key) {
        RadioStream stream = handStreams.get(key);
        if (stream == null) {
            stream = blockStreams.get(key);
        }
        return Optional.ofNullable(stream);
    }

    public void stopAll() {
        blockStreams.values().forEach(RadioStream::stop);
        blockStreams.clear();
        handStreams.values().forEach(RadioStream::stop);
        handStreams.clear();
        modes.clear();
        owners.clear();
        viewing.clear();
        RadioFM.LOGGER.info("All radio streams stopped");
    }
}
