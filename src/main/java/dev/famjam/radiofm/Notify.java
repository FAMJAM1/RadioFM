package dev.famjam.radiofm;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * RU: отказы (запрет, потолок, лимит) идут со звуком, иначе их не заметить; обычные сообщения не озвучивать, иначе сигнал обесценится
 * US: refusals (bans, ceilings, caps) come with a sound, or they go unnoticed; keep ordinary messages silent, or the cue stops meaning anything
 */
public final class Notify {

    private Notify() {
    }

    public static void refused(ServerPlayer player, String translationKey, Object... args) {
        player.displayClientMessage(Component.translatable(translationKey, args), true);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1F, 1F);
    }
}
