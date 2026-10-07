package dev.famjam.radiofm.voice;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.voice.own.OwnBackend;
import dev.famjam.radiofm.voice.plasmo.PlasmoBackend;
import dev.famjam.radiofm.voice.svc.SvcBackend;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

public final class VoiceBackends {

    public static final String SVC = "svc";
    public static final String PLASMO = "plasmo";
    public static final String OWN = "our";

    private static final int ADMIN_LEVEL = 2;

    private static VoiceBackend svc;
    private static VoiceBackend plasmo;
    private static VoiceBackend own;

    private VoiceBackends() {
    }

    /**
     * RU: классы API трогаем, только если мод стоит, иначе ClassNotFoundException;
     *     PV принимает аддоны только при загрузке модов, поэтому все заводим сразу
     * US: API classes are touched only when the mod is present, or ClassNotFoundException;
     *     PV only takes addons while mods load, so all of them are set up right away
     */
    public static void select() {
        ModList mods = ModList.get();
        if (mods.isLoaded("voicechat")) {
            svc = SvcBackend.create();
        }
        if (mods.isLoaded("plasmovoice")) {
            plasmo = PlasmoBackend.create();
        }
        own = OwnBackend.create();
        RadioFM.LOGGER.info("Voice mods for the radio: SVC {}, Plasmo Voice {}",
                svc != null ? "found" : "missing", plasmo != null ? "found" : "missing");
    }

    public static boolean hasSvc() {
        return svc != null;
    }

    public static boolean hasPlasmo() {
        return plasmo != null;
    }

    /** RU: без голосовых модов выбирать не из чего, играем своим каналом | US: with no voice mod there is nothing to choose, our channel plays */
    public static boolean needsChoice() {
        return svc != null || plasmo != null;
    }

    /** RU: null - есть из чего выбрать, но ещё не выбрали | US: null when there is a choice and it has not been made */
    public static VoiceBackend current() {
        if (!needsChoice()) {
            return own;
        }
        return switch (RadioFM.SERVER_CONFIG.voiceMod.get()) {
            case SVC -> svc;
            case PLASMO -> plasmo;
            case OWN -> own;
            default -> null;
        };
    }

    /** RU: варианты для команды, как их вводить | US: the choices for the command, as typed */
    public static String choices() {
        List<String> names = new ArrayList<>();
        if (svc != null) {
            names.add("svc");
        }
        if (plasmo != null) {
            names.add("pv");
        }
        names.add(OWN);
        return String.join(", ", names);
    }

    /**
     * RU: объясняет игроку, почему радио не включится; в своём мире без читов
     *     команда недоступна, поэтому там подсказываем открыть мир для сети с читами
     * US: tells the player why the radio will not start; in a singleplayer world
     *     without cheats the command is out of reach, so we suggest opening to LAN with cheats
     *
     * @return RU: true - включать нельзя | US: true when it must not start
     */
    public static boolean refuse(ServerPlayer player) {
        if (current() != null) {
            return false;
        }
        Component message;
        if (player.hasPermissions(ADMIN_LEVEL)) {
            message = Component.translatable("message.radiofm.voice_choose_admin", choices());
        } else if (!player.server.isDedicatedServer()) {
            message = Component.translatable("message.radiofm.voice_choose_local");
        } else {
            message = Component.translatable("message.radiofm.voice_conflict_player");
        }
        // RU: в чат, а не над хотбаром: строка длинная и с командой
        // US: to the chat rather than above the hotbar: the line is long and holds a command
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
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
        if (own != null) {
            own.reset();
        }
    }
}
