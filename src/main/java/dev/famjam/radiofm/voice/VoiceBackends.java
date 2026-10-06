package dev.famjam.radiofm.voice;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.voice.plasmo.PlasmoBackend;
import dev.famjam.radiofm.voice.svc.SvcBackend;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.fml.ModList;

public final class VoiceBackends {

    public static final String SVC = "svc";
    public static final String PLASMO = "plasmo";

    private static final int ADMIN_LEVEL = 2;

    private static VoiceBackend svc;
    private static VoiceBackend plasmo;

    private VoiceBackends() {
    }

    /**
     * RU: классы API трогаем, только если мод стоит, иначе ClassNotFoundException;
     *     PV принимает аддоны только при загрузке модов, поэтому оба заводим сразу
     * US: API classes are touched only when the mod is present, or ClassNotFoundException;
     *     PV only takes addons while mods load, so both are set up right away
     */
    public static void select() {
        ModList mods = ModList.get();
        if (mods.isLoaded("voicechat")) {
            svc = SvcBackend.create();
        }
        if (mods.isLoaded("plasmovoice")) {
            plasmo = PlasmoBackend.create();
        }
        RadioFM.LOGGER.info("Voice mods for the radio: SVC {}, Plasmo Voice {}",
                svc != null ? "found" : "missing", plasmo != null ? "found" : "missing");
    }

    public static boolean bothInstalled() {
        return svc != null && plasmo != null;
    }

    /** RU: null - мода нет или из двух ещё не выбрали | US: null when there is none, or two and none chosen yet */
    public static VoiceBackend current() {
        if (!bothInstalled()) {
            return svc != null ? svc : plasmo;
        }
        return switch (RadioFM.SERVER_CONFIG.voiceMod.get()) {
            case SVC -> svc;
            case PLASMO -> plasmo;
            default -> null;
        };
    }

    /**
     * RU: объясняет игроку, почему радио не включится; в своём мире без читов
     *     команда недоступна, поэтому там просим убрать лишний мод
     * US: tells the player why the radio will not start; in a singleplayer world
     *     without cheats the command is out of reach, so we ask to remove the extra mod
     *
     * @return RU: true - включать нельзя | US: true when it must not start
     */
    public static boolean refuse(ServerPlayer player) {
        if (current() != null) {
            return false;
        }
        String key;
        if (!bothInstalled()) {
            key = "message.radiofm.no_voice";
        } else if (player.hasPermissions(ADMIN_LEVEL)) {
            key = "message.radiofm.voice_conflict_admin";
        } else if (!player.server.isDedicatedServer()) {
            key = "message.radiofm.voice_conflict_local";
        } else {
            key = "message.radiofm.voice_conflict_player";
        }
        // RU: в чат, а не над хотбаром: строка длинная и с командой
        // US: to the chat rather than above the hotbar: the line is long and holds a command
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1F, 1F);
        return true;
    }

    public static void reset() {
        if (svc != null) {
            svc.reset();
        }
        if (plasmo != null) {
            plasmo.reset();
        }
    }
}
