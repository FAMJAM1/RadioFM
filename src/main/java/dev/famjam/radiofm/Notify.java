package dev.famjam.radiofm;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * ru: отказы — запрет, потолок, лимит; со звуком, иначе их не заметить
 * en: refusals — bans, ceilings, caps; with a sound, or they go unnoticed
 * <p>
 * ru: обычные сообщения не озвучивать, иначе сигнал перестанет значить что-либо
 * en: leave ordinary messages silent, or the cue stops meaning anything
 */
public final class Notify {

    private Notify() {
    }

    public static void refused(ServerPlayer player, String translationKey, Object... args) {
        player.displayClientMessage(Component.translatable(translationKey, args), true);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1F, 1F);
    }
}
