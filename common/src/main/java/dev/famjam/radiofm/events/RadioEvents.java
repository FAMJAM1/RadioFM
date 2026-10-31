package dev.famjam.radiofm.events;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.network.RadioServerHandlers;
import dev.famjam.radiofm.network.RadioStatusPacket;
import dev.famjam.radiofm.radio.RadioBans;
import dev.famjam.radiofm.radio.LegacyHeads;
import dev.famjam.radiofm.radio.PlacedRadios;
import dev.famjam.radiofm.radio.RadioItem;
import dev.famjam.radiofm.radio.RadioManager;
import dev.famjam.radiofm.radio.RadioStation;
import dev.famjam.radiofm.radio.RadioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.famjam.radiofm.platform.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RU: что делает мод на события игры; подписывается на них каждый загрузчик сам и зовёт эти методы
 * US: what the mod does on game events; each loader subscribes to them itself and calls these methods
 */
public class RadioEvents {

    private static final long STATUS_INTERVAL_MS = 1000L;

    private static long lastStatusSent;

    /** @return RU: true - клик забрало радио, дальше его не пускать | US: true when the radio took the click and it must go no further */
    public static boolean onRightClickBlock(Player clicker, Level level, InteractionHand hand, BlockPos pos) {
        if (level.isClientSide()) {
            return false;
        }
        if (!(clicker instanceof ServerPlayer player)) {
            return false;
        }
        // RU: за клик событие приходит на каждую руку, иначе переключим дважды
        // US: the event arrives once per hand, without this we would toggle twice
        if (hand != InteractionHand.MAIN_HAND) {
            return false;
        }

        if (RadioBans.denied(player)) {
            return false;
        }

        Optional<RadioStation> placed = PlacedRadios.read(level, pos)
                .or(() -> LegacyHeads.convertPlaced((ServerLevel) level, pos));
        if (placed.isEmpty()) {
            return false;
        }
        RadioStation station = placed.get();

        if (player.isShiftKeyDown()) {
            RadioServerHandlers.openScreenFor(player, station, Optional.of(pos));
        } else {
            toggleBlockRadio(player, (ServerLevel) level, pos, station);
        }
        return true;
    }

    private static void toggleBlockRadio(ServerPlayer player, ServerLevel level, BlockPos pos, RadioStation station) {
        boolean turningOn = !station.on();

        // RU: причины отказа разбираем здесь, applyBlockState на все отвечает false
        // US: reasons are told apart here, applyBlockState answers false to all of them
        if (turningOn) {
            if (!station.hasTracks()) {
                player.displayClientMessage(Component.translatable("message.radiofm.no_tracks"), true);
                return;
            }
            if (RadioServerHandlers.refuseToStart(player)) {
                return;
            }
        }

        RadioStation updated = station.toggled();
        boolean playing = applyBlockState(level, pos, updated, player.getUUID());

        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1F, 1F);

        if (turningOn && !playing) {
            dev.famjam.radiofm.Notify.refused(player, "message.radiofm.too_many");
            return;
        }
        player.displayClientMessage(Component.translatable(
                updated.on() ? "message.radiofm.toggled_on" : "message.radiofm.toggled_off"), true);
    }

    /**
     * RU: пишет состояние на блок и заводит или снимает стрим
     * US: writes the state onto the block and starts or stops the stream
     *
     * @return RU: играет ли радио | US: whether it is playing
     */
    public static boolean applyBlockState(ServerLevel level, BlockPos pos, RadioStation station, UUID owner) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }

        if (!station.on() || !station.hasTracks()) {
            RadioManager.get().stopBlockRadio(station.id());
            PlacedRadios.write(blockEntity, station);
            return false;
        }

        boolean started = RadioManager.get().startBlockRadio(station, level, pos, owner);
        PlacedRadios.write(blockEntity, started ? station : station.withOn(false));
        return started;
    }

    /**
     * RU: иначе запрет обходится киркой по чужому
     * US: or the ban is dodged with a pickaxe
     *
     * @return RU: true - ломать нельзя | US: true when breaking must be refused
     */
    public static boolean onBannedBreak(Player breaker, Level level, BlockPos pos) {
        if (!(breaker instanceof ServerPlayer player)) {
            return false;
        }
        if (PlacedRadios.read(level, pos).isEmpty()) {
            return false;
        }
        return RadioBans.denied(player);
    }

    /**
     * RU: не по дропу: в креативе его нет, и звук доигрывал бы до проверки блока
     * US: not on drops: creative has none, and the sound would outlive the block
     */
    public static void onBlockBroken(BlockEntity blockEntity) {
        PlacedRadios.read(blockEntity)
                .ifPresent(station -> RadioManager.get().forget(station.id()));
    }

    /** RU: взрыв идёт мимо поломки игроком | US: an explosion bypasses breaking by a player */
    public static void onExplosion(Level eventLevel, List<BlockPos> affected) {
        if (!(eventLevel instanceof ServerLevel level)) {
            return;
        }
        for (BlockPos pos : affected) {
            PlacedRadios.read(level, pos)
                    .ifPresent(station -> RadioManager.get().forget(station.id()));
        }
    }

    public static void onBlockDrops(BlockEntity blockEntity, List<ItemEntity> drops) {
        Optional<RadioStation> legacy = LegacyHeads.readPlaced(blockEntity);
        if (legacy.isPresent()) {
            drops.stream().findFirst()
                    .ifPresent(itemEntity -> itemEntity.setItem(LegacyHeads.toItem(legacy.get())));
            return;
        }
        Optional<RadioStation> station = PlacedRadios.read(blockEntity);
        if (station.isEmpty()) {
            return;
        }
        RadioManager.get().forget(station.get().id());

        // RU: выпавший предмет без настроек | US: the dropped item carries no settings
        for (ItemEntity itemEntity : drops) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            RadioItem.write(stack, station.get().withOn(false));
            itemEntity.setItem(stack);
            return;
        }
    }

    /** @return RU: true - открыли окно, предмет не использовать | US: true when the screen was opened and the item must not be used */
    public static boolean onRightClickItem(Player user, Level level, InteractionHand hand) {
        if (level.isClientSide()) {
            return false;
        }
        if (!(user instanceof ServerPlayer player)) {
            return false;
        }
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return false;
        }
        if (RadioBans.denied(player)) {
            return false;
        }
        Optional<RadioStation> station = RadioItem.read(player.getItemInHand(InteractionHand.MAIN_HAND));
        station.ifPresent(found -> RadioServerHandlers.openScreenFor(player, found, Optional.empty()));
        return station.isPresent();
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (player.tickCount % 20 == 0) {
            LegacyHeads.convertInventory(player);
        }
        UUID playerId = player.getUUID();
        if (!RadioManager.get().isHandRadioActive(playerId)) {
            return;
        }
        // RU: ищем именно играющее радио по id станции: с любым радио выброшенное играло дальше,
        //     пока в инвентаре лежало другое
        // US: look for the playing radio itself by station id: with any radio the dropped one kept
        //     playing while another sat in the inventory
        UUID playing = RadioManager.get().getHandStream(playerId).map(stream -> stream.getStation().id()).orElse(null);
        if (playing == null || !carries(player, playing)) {
            RadioManager.get().stopHandRadio(playerId);
        }
    }

    private static boolean carries(ServerPlayer player, UUID stationId) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            boolean match = RadioItem.read(inventory.getItem(slot))
                    .map(station -> station.id().equals(stationId))
                    .orElse(false);
            if (match) {
                return true;
            }
        }
        return false;
    }

    public static void onPlayerLoggedOut(Player player) {
        RadioManager.get().onPlayerLeft(player.getUUID());
    }

    public static void onDeath(Player player) {
        RadioManager.get().stopHandRadio(player.getUUID());
    }

    public static void onServerTick(MinecraftServer server) {
        dev.famjam.radiofm.voice.own.OwnBackend.tick(server);
        long now = System.currentTimeMillis();
        if (now - lastStatusSent < STATUS_INTERVAL_MS) {
            return;
        }
        lastStatusSent = now;

        RadioManager manager = RadioManager.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            manager.viewing(player.getUUID()).ifPresent(key -> Platform.INSTANCE.sendToPlayer(player,
                    manager.liveStream(key)
                            .filter(RadioStream::isActive)
                            .map(stream -> RadioStatusPacket.of(stream.getElapsedSeconds(), stream.getDurationSeconds(),
                                    stream.getTrackTitle(), stream.getTrackAuthor()))
                            .orElse(RadioStatusPacket.of(0, -1, null, null))));
        }
    }

    public static void onServerStopping() {
        RadioManager.get().stopAll();
        dev.famjam.radiofm.voice.VoiceBackends.reset();
    }
}
