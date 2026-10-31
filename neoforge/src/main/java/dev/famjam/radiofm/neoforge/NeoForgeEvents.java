package dev.famjam.radiofm.neoforge;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.command.RadioCommands;
import dev.famjam.radiofm.events.RadioEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** RU: события NeoForge вместо миксинов в ванильный код | US: NeoForge events, not mixins into vanilla code */
@EventBusSubscriber(modid = RadioFM.MODID)
public final class NeoForgeEvents {

    private NeoForgeEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (RadioEvents.onRightClickBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBannedBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level
                && RadioEvents.onBannedBreak(event.getPlayer(), level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * RU: не по дропу: в креативе его нет, и звук доигрывал бы до проверки блока
     * US: not on drops: creative has none, and the sound would outlive the block
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!event.isCanceled()) {
            RadioEvents.onBlockBroken(event.getLevel().getBlockEntity(event.getPos()));
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        RadioEvents.onExplosion(event.getLevel(), event.getAffectedBlocks());
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        RadioEvents.onBlockDrops(event.getBlockEntity(), event.getDrops());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (RadioEvents.onRightClickItem(event.getEntity(), event.getLevel(), event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadioEvents.onPlayerTick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RadioEvents.onPlayerLoggedOut(event.getEntity());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && player instanceof ServerPlayer) {
            RadioEvents.onDeath(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RadioEvents.onServerTick(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RadioCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RadioEvents.onServerStopping();
    }
}
