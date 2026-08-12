package dev.famjam.radiofm.events;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.RadioVoicechatPlugin;
import dev.famjam.radiofm.network.RadioServerHandlers;
import dev.famjam.radiofm.network.RadioStatusPacket;
import dev.famjam.radiofm.radio.RadioBans;
import dev.famjam.radiofm.radio.RadioHeads;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ru: всё, что радио делает в мире; события NeoForge вместо миксинов в ваниль
 * en: everything the radio does in the world; NeoForge events, not vanilla mixins
 */
@EventBusSubscriber(modid = RadioFM.MODID)
public class RadioEvents {

    /** ru: как часто уходит время трека | en: how often the track time is sent */
    private static final long STATUS_INTERVAL_MS = 1000L;

    /**
     * ru: предмет израсходован до появления блока, поэтому запоминаем заранее
     * en: the item is spent before the block exists, so remember it beforehand
     */
    private static final Map<UUID, RadioStation> beingPlaced = new HashMap<>();

    private static long lastStatusSent;

    // ru: поставленное радио | en: placed radio

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // ru: за клик событие приходит на каждую руку — иначе переключим дважды
        // en: the event arrives once per hand — without this we would toggle twice
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (RadioBans.denied(player)) {
            return;
        }

        // ru: радио в руке ставится, а не открывается | en: a held radio is placed, not opened
        ItemStack held = player.getItemInHand(event.getHand());
        RadioItem.read(held).ifPresent(station -> beingPlaced.put(player.getUUID(), station));

        BlockPos pos = event.getPos();
        Optional<RadioStation> placed = RadioHeads.read(level, pos);
        if (placed.isEmpty()) {
            return;
        }
        RadioStation station = placed.get();

        if (player.isShiftKeyDown()) {
            RadioServerHandlers.openScreenFor(player, station, Optional.of(pos));
        } else {
            toggleBlockRadio(player, (ServerLevel) level, pos, station);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    private static void toggleBlockRadio(ServerPlayer player, ServerLevel level, BlockPos pos, RadioStation station) {
        boolean turningOn = !station.on();

        // ru: причины отказа разбираем здесь — applyBlockState на все отвечает false
        // en: reasons are told apart here — applyBlockState answers false to all of them
        if (turningOn) {
            if (!station.hasTracks()) {
                player.displayClientMessage(Component.translatable("message.radiofm.no_tracks"), true);
                return;
            }
            if (RadioServerHandlers.atRadioLimit(player)) {
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
     * ru: пишет состояние на блок и заводит или снимает стрим
     * en: writes the state onto the block and starts or stops the stream
     *
     * @return ru: играет ли радио | en: whether it is playing
     */
    public static boolean applyBlockState(ServerLevel level, BlockPos pos, RadioStation station, UUID owner) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }

        if (!station.on() || !station.hasTracks()) {
            RadioManager.get().stopBlockRadio(station.id());
            RadioHeads.write(blockEntity, station);
            return false;
        }

        boolean started = RadioManager.get().startBlockRadio(station, level, pos, owner);
        // ru: не завелось — не помечаем играющим | en: did not start, do not mark it playing
        RadioHeads.write(blockEntity, started ? station : station.withOn(false));
        return started;
    }

    // ru: установка и поломка | en: placing and breaking

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RadioStation station = beingPlaced.remove(player.getUUID());
        if (station == null) {
            return;
        }
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity == null) {
            return;
        }
        // ru: поставленное радио не включается само | en: a freshly placed radio stays off
        RadioHeads.write(blockEntity, station.withOn(false));
    }

    /** ru: иначе запрет обходится киркой по чужому | en: or the ban is dodged with a pickaxe */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBannedBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (RadioHeads.read(event.getLevel(), event.getPos()).isEmpty()) {
            return;
        }
        if (RadioBans.denied(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * ru: не по дропу — в креативе его нет, и звук доигрывал бы до проверки блока
     * en: not on drops — creative has none, and the sound would outlive the block
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }
        RadioHeads.read(event.getLevel().getBlockEntity(event.getPos()))
                .ifPresent(station -> RadioManager.get().forget(station.id()));
    }

    /** ru: взрыв идёт мимо BreakEvent | en: an explosion bypasses BreakEvent */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            RadioHeads.read(level, pos)
                    .ifPresent(station -> RadioManager.get().forget(station.id()));
        }
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        Optional<RadioStation> station = RadioHeads.read(event.getBlockEntity());
        if (station.isEmpty()) {
            return;
        }
        RadioManager.get().forget(station.get().id());

        // ru: череп выпадает без настроек | en: the head drops without our settings
        for (ItemEntity itemEntity : event.getDrops()) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            RadioItem.write(stack, station.get().withOn(false));
            itemEntity.setItem(stack);
            return;
        }
    }

    // ru: радио в руке | en: held radio

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return;
        }
        if (RadioBans.denied(player)) {
            return;
        }
        RadioItem.read(player.getItemInHand(InteractionHand.MAIN_HAND)).ifPresent(station -> {
            RadioServerHandlers.openScreenFor(player, station, Optional.empty());
            event.setCanceled(true);
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID playerId = player.getUUID();
        if (!RadioManager.get().isHandRadioActive(playerId)) {
            return;
        }
        // ru: радио больше нет в инвентаре | en: the radio left the inventory
        boolean stillCarried = player.getInventory().items.stream().anyMatch(RadioItem::isRadio);
        if (!stillCarried) {
            RadioManager.get().stopHandRadio(playerId);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RadioManager.get().onPlayerLeft(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadioManager.get().stopHandRadio(player.getUUID());
        }
    }

    // ru: время трека и остановка сервера | en: track time and server shutdown

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (now - lastStatusSent < STATUS_INTERVAL_MS) {
            return;
        }
        lastStatusSent = now;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            currentStreamFor(player).ifPresent(stream -> PacketDistributor.sendToPlayer(player,
                    new RadioStatusPacket(stream.getElapsedSeconds(), stream.getDurationSeconds())));
        }
    }

    /** ru: своё в руке важнее чужого рядом | en: your own beats someone else's nearby */
    private static Optional<? extends RadioStream> currentStreamFor(ServerPlayer player) {
        Optional<? extends RadioStream> hand = RadioManager.get().getHandStream(player.getUUID())
                .filter(RadioStream::isActive);
        return hand.isPresent() ? hand : RadioManager.get().nearestStream(player);
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        dev.famjam.radiofm.command.RadioCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RadioManager.get().stopAll();
        RadioVoicechatPlugin.reset();
        beingPlaced.clear();
    }
}
