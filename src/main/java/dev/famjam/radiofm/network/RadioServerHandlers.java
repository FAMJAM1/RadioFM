package dev.famjam.radiofm.network;

import dev.famjam.radiofm.Notify;
import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.events.RadioEvents;
import dev.famjam.radiofm.radio.RadioBans;
import dev.famjam.radiofm.radio.PlacedRadios;
import dev.famjam.radiofm.radio.RadioItem;
import dev.famjam.radiofm.radio.RadioManager;
import dev.famjam.radiofm.radio.RadioStation;
import dev.famjam.radiofm.radio.RadioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** RU: пустой pos - радио в руке | US: an empty pos means a held radio */
public final class RadioServerHandlers {

    private RadioServerHandlers() {
    }

    public static void openScreenFor(ServerPlayer player, RadioStation station, Optional<BlockPos> pos) {
        UUID key = pos.isPresent() ? station.id() : player.getUUID();
        RadioManager manager = RadioManager.get();
        manager.setViewing(player.getUUID(), key);

        String state = manager.liveStream(key)
                .map(RadioStream::describeState)
                .orElse("STOPPED");

        PacketDistributor.sendToPlayer(player, new OpenRadioScreenPacket(
                pos,
                station.name(),
                station.tracks(),
                state,
                manager.isShuffle(key),
                manager.getRepeatTrackIndex(key),
                dev.famjam.radiofm.voice.own.OwnBackend.isActive(dev.famjam.radiofm.voice.VoiceBackends.current())));
    }

    public static void handleToggleHandRadio(ServerPlayer player) {
        if (RadioBans.denied(player)) {
            return;
        }

        UUID playerId = player.getUUID();
        RadioManager manager = RadioManager.get();

        if (manager.isHandRadioActive(playerId)) {
            manager.stopHandRadio(playerId);
            player.displayClientMessage(Component.translatable("message.radiofm.toggled_off"), true);
            return;
        }

        Optional<RadioStation> station = RadioItem.read(player.getItemInHand(InteractionHand.MAIN_HAND));
        if (station.isEmpty()) {
            return;
        }
        if (!station.get().hasTracks()) {
            player.displayClientMessage(Component.translatable("message.radiofm.no_tracks"), true);
            return;
        }

        if (refuseToStart(player)) {
            return;
        }
        manager.startHandRadio(station.get(), player);
        player.displayClientMessage(Component.translatable("message.radiofm.toggled_on"), true);
    }

    /**
     * RU: всё, что мешает включить: нет голосового мода, потолок из radiofm:max;
     *     игрок уведомляется здесь же
     * US: everything that stops a radio from starting: no voice mod, the radiofm:max
     *     cap; the player is told here
     *
     * @return RU: true - включать нельзя | US: true when it must not start
     */
    public static boolean refuseToStart(ServerPlayer player) {
        return dev.famjam.radiofm.voice.VoiceBackends.refuse(player) || atRadioLimit(player);
    }

    private static boolean atRadioLimit(ServerPlayer player) {
        int max = player.serverLevel().getGameRules().getInt(RadioFM.MAX_RADIOS);
        if (RadioManager.get().countActiveFor(player.getUUID()) < max) {
            return false;
        }
        Notify.refused(player, "message.radiofm.limit_reached", max);
        return true;
    }

    private static void startBlockRadio(ServerPlayer player, ServerLevel level, BlockPos pos, RadioStation station) {
        if (refuseToStart(player)) {
            return;
        }
        RadioEvents.applyBlockState(level, pos, station.withOn(true), player.getUUID());
    }

    /** RU: щелчок колеса и он же с Ctrl | US: one wheel notch, and with ctrl held */
    private static final float RANGE_STEP = 4F;
    private static final float FINE_RANGE_STEP = 1F;
    private static final float MIN_RANGE = 1F;

    public static void handleAdjustRange(AdjustRangePacket packet, ServerPlayer player) {
        if (RadioBans.denied(player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        int max = level.getGameRules().getInt(RadioFM.MAX_RADIUS);

        if (packet.pos().isPresent()) {
            BlockPos pos = packet.pos().get();
            PlacedRadios.read(level, pos).ifPresent(station -> {
                float range = shift(station, packet, max);
                PlacedRadios.write(level.getBlockEntity(pos), station.withRange(range));
                RadioManager.get().updateRange(station.id(), range);
                report(player, range, max);
            });
            return;
        }

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        RadioItem.read(held).ifPresent(station -> {
            float range = shift(station, packet, max);
            RadioItem.write(held, station.withRange(range));
            RadioManager.get().updateRange(player.getUUID(), range);
            report(player, range, max);
        });
    }

    private static float shift(RadioStation station, AdjustRangePacket packet, int max) {
        float current = station.range() > 0
                ? station.range()
                : RadioFM.SERVER_CONFIG.radioRange.get().floatValue();
        float step = packet.fine() ? FINE_RANGE_STEP : RANGE_STEP;
        return Math.clamp(current + packet.steps() * step, MIN_RANGE, (float) max);
    }

    private static void report(ServerPlayer player, float range, int max) {
        if (range >= max) {
            Notify.refused(player, "message.radiofm.range_max", range);
            return;
        }
        player.displayClientMessage(
                Component.translatable("message.radiofm.radius_changed", range), true);
    }

    public static void handleSave(SaveRadioPacket packet, ServerPlayer player) {
        if (RadioBans.denied(player)) {
            return;
        }

        List<String> tracks = packet.urls().stream()
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .toList();

        if (packet.pos().isEmpty()) {
            saveHandRadio(player, packet.stationName(), tracks);
        } else {
            saveBlockRadio(player, packet.pos().get(), packet.stationName(), tracks);
        }
    }

    private static void saveHandRadio(ServerPlayer player, String name, List<String> tracks) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        RadioItem.read(stack).ifPresent(station -> {
            boolean tracksChanged = !station.tracks().equals(tracks);
            RadioStation updated = station.withName(name).withTracks(tracks);
            RadioItem.write(stack, updated);

            // RU: иначе сохранение сбивало бы паузу | US: otherwise saving would break the pause
            if (tracksChanged && RadioManager.get().isHandRadioActive(player.getUUID())
                    && !RadioManager.get().reorder(player.getUUID(), tracks)) {
                RadioManager.get().startHandRadio(updated, player);
            }
        });
    }

    private static void saveBlockRadio(ServerPlayer player, BlockPos pos, String name, List<String> tracks) {
        ServerLevel level = player.serverLevel();
        PlacedRadios.read(level, pos).ifPresent(station -> {
            boolean tracksChanged = !station.tracks().equals(tracks);
            RadioStation updated = station.withName(name).withTracks(tracks);

            if (tracksChanged && !RadioManager.get().reorder(station.id(), tracks)) {
                RadioEvents.applyBlockState(level, pos, updated, player.getUUID());
            } else {
                PlacedRadios.write(level.getBlockEntity(pos), updated);
            }
            RadioFM.LOGGER.info("Radio at {} now has {} tracks", pos, tracks.size());
        });
    }

    public static void handleControl(RadioControlPacket packet, ServerPlayer player) {
        if (RadioBans.denied(player)) {
            return;
        }

        if (packet.pos().isEmpty()) {
            controlHandRadio(packet, player);
        } else {
            controlBlockRadio(packet, player, packet.pos().get());
        }
    }

    private static void controlHandRadio(RadioControlPacket packet, ServerPlayer player) {
        UUID playerId = player.getUUID();
        RadioManager manager = RadioManager.get();

        switch (packet.action()) {
            case RadioControlPacket.PLAY -> RadioItem.read(player.getItemInHand(InteractionHand.MAIN_HAND))
                    .ifPresent(station -> {
                        if (!refuseToStart(player)) {
                            manager.startHandRadio(station, player);
                        }
                    });
            case RadioControlPacket.STOP -> manager.stopHandRadio(playerId);
            default -> applyCommon(packet, playerId);
        }
    }

    private static void controlBlockRadio(RadioControlPacket packet, ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        Optional<RadioStation> found = PlacedRadios.read(level, pos);
        if (found.isEmpty()) {
            return;
        }
        RadioStation station = found.get();
        switch (packet.action()) {
            case RadioControlPacket.PLAY -> startBlockRadio(player, level, pos, station);
            case RadioControlPacket.STOP -> RadioEvents.applyBlockState(level, pos, station.withOn(false), player.getUUID());
            default -> applyCommon(packet, station.id());
        }
    }

    /**
     * RU: режимы идут через менеджер - их жмут и на выключенном радио, где стрима нет
     * US: modes go through the manager - they are set on a radio that is off and has no stream
     */
    private static void applyCommon(RadioControlPacket packet, UUID key) {
        RadioManager manager = RadioManager.get();
        switch (packet.action()) {
            case RadioControlPacket.SHUFFLE -> manager.setShuffle(key, !manager.isShuffle(key));
            // RU: REPEAT только снимает повтор, включает REPEAT_TRACK | US: REPEAT only clears it, REPEAT_TRACK sets it
            case RadioControlPacket.REPEAT -> manager.setRepeatTrack(key, -1);
            case RadioControlPacket.REPEAT_TRACK -> manager.setRepeatTrack(key, packet.data());
            case RadioControlPacket.PAUSE -> manager.liveStream(key).ifPresent(RadioStream::togglePause);
            case RadioControlPacket.NEXT -> manager.liveStream(key).ifPresent(RadioStream::skipNext);
            case RadioControlPacket.PREV -> manager.liveStream(key).ifPresent(RadioStream::skipPrev);
            default -> RadioFM.LOGGER.warn("Unknown radio action {}", packet.action());
        }
    }
}
