package dev.famjam.radiofm.events;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.RadioVoicechatPlugin;
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** RU: события NeoForge вместо миксинов в ваниль | US: NeoForge events, not vanilla mixins */
@EventBusSubscriber(modid = RadioFM.MODID)
public class RadioEvents {

    private static final long STATUS_INTERVAL_MS = 1000L;

    private static long lastStatusSent;

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // RU: за клик событие приходит на каждую руку, иначе переключим дважды
        // US: the event arrives once per hand, without this we would toggle twice
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (RadioBans.denied(player)) {
            return;
        }

        BlockPos pos = event.getPos();
        Optional<RadioStation> placed = PlacedRadios.read(level, pos)
                .or(() -> LegacyHeads.convertPlaced((ServerLevel) level, pos));
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

        // RU: причины отказа разбираем здесь, applyBlockState на все отвечает false
        // US: reasons are told apart here, applyBlockState answers false to all of them
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

    /** RU: иначе запрет обходится киркой по чужому | US: or the ban is dodged with a pickaxe */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBannedBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (PlacedRadios.read(event.getLevel(), event.getPos()).isEmpty()) {
            return;
        }
        if (RadioBans.denied(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * RU: не по дропу: в креативе его нет, и звук доигрывал бы до проверки блока
     * US: not on drops: creative has none, and the sound would outlive the block
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }
        PlacedRadios.read(event.getLevel().getBlockEntity(event.getPos()))
                .ifPresent(station -> RadioManager.get().forget(station.id()));
    }

    /** RU: взрыв идёт мимо BreakEvent | US: an explosion bypasses BreakEvent */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            PlacedRadios.read(level, pos)
                    .ifPresent(station -> RadioManager.get().forget(station.id()));
        }
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        Optional<RadioStation> legacy = LegacyHeads.readPlaced(event.getBlockEntity());
        if (legacy.isPresent()) {
            event.getDrops().stream().findFirst()
                    .ifPresent(itemEntity -> itemEntity.setItem(LegacyHeads.toItem(legacy.get(), 1)));
            return;
        }
        Optional<RadioStation> station = PlacedRadios.read(event.getBlockEntity());
        if (station.isEmpty()) {
            return;
        }
        RadioManager.get().forget(station.get().id());

        // RU: выпавший предмет без настроек | US: the dropped item carries no settings
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
        if (player.tickCount % 20 == 0) {
            LegacyHeads.convertInventory(player);
        }
        UUID playerId = player.getUUID();
        if (!RadioManager.get().isHandRadioActive(playerId)) {
            return;
        }
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

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (now - lastStatusSent < STATUS_INTERVAL_MS) {
            return;
        }
        lastStatusSent = now;

        RadioManager manager = RadioManager.get();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            manager.viewing(player.getUUID()).ifPresent(key -> PacketDistributor.sendToPlayer(player,
                    manager.liveStream(key)
                            .filter(RadioStream::isActive)
                            .map(stream -> new RadioStatusPacket(stream.getElapsedSeconds(), stream.getDurationSeconds()))
                            .orElse(new RadioStatusPacket(0, -1))));
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        dev.famjam.radiofm.command.RadioCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RadioManager.get().stopAll();
        RadioVoicechatPlugin.reset();
    }
}
