package dev.famjam.radiofm;

import dev.famjam.radiofm.network.AdjustRangePacket;
import dev.famjam.radiofm.network.OpenRadioScreenPacket;
import dev.famjam.radiofm.network.RadioStatusPacket;
import dev.famjam.radiofm.network.ToggleHandRadioPacket;
import dev.famjam.radiofm.radio.RadioItem;
import dev.famjam.radiofm.screen.RadioScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import dev.famjam.radiofm.platform.Platform;

/**
 * RU: что делает клиент на события игры; подписывается на них каждый загрузчик сам
 * US: what the client does on game events; each loader subscribes to them itself
 */
public class ClientEventHandlers {

    public static long clientElapsed = 0;
    public static long clientDuration = -1;
    public static String clientTitle = "";
    public static String clientAuthor = "";

    public static void updateStatus(RadioStatusPacket packet) {
        clientElapsed = packet.elapsedSeconds();
        clientDuration = packet.durationSeconds();
        clientTitle = packet.title();
        clientAuthor = packet.author();
    }

    public static void onClientTick() {
        dev.famjam.radiofm.client.OwnAudioClient.tick();
    }

    /** RU: смена измерения или возрождение: звуки старого мира стояли бы не там | US: dimension change or respawn: old-world sounds would sit in the wrong place */
    public static void onClone() {
        dev.famjam.radiofm.client.OwnAudioClient.clear();
    }

    public static void onLoggingOut() {
        dev.famjam.radiofm.client.OwnAudioClient.clear();
    }

    public static void openScreen(OpenRadioScreenPacket packet) {
        // RU: иначе до первого пакета видно время прошлого радио | US: or the last radio's time shows until the first packet
        clientElapsed = 0;
        clientDuration = -1;
        clientTitle = "";
        clientAuthor = "";
        Minecraft.getInstance().setScreen(new RadioScreen(packet));
    }

    /** @return RU: true - нажатие забрало радио | US: true when the radio took the press */
    public static boolean onRightMousePress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return false;

        var stack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (RadioItem.read(stack).isEmpty()) return false;

        // RU: смотрит на блок - пусть ставит | US: aiming at a block, let them place it
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return false;

        if (mc.player.isShiftKeyDown()) {
            return false; // RU: окно откроет сервер по клику предметом | US: the server opens the screen on the item click
        }

        Platform.INSTANCE.sendToServer(new ToggleHandRadioPacket());
        return true;
    }

    /**
     * RU: клиент не знает, радио ли это в руке - настройки на сервере, он и решает
     * US: the client cannot tell if the held item is a radio - the settings are server side
     */
    /** @return RU: true - прокрутку забрало радио, слот не менять | US: true when the radio took the scroll and the hotbar slot must stay */
    public static boolean onScroll(double deltaY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return false;
        if (!mc.player.isShiftKeyDown()) return false;

        int steps = (int) Math.signum(deltaY);
        if (steps == 0) return false;

        java.util.Optional<net.minecraft.core.BlockPos> target = lookedAtRadio(mc);
        boolean holdingRadio = RadioItem.read(mc.player.getItemInHand(InteractionHand.MAIN_HAND)).isPresent();
        if (target.isEmpty() && !holdingRadio) return false;

        boolean fine = net.minecraft.client.gui.screens.Screen.hasControlDown();
        Platform.INSTANCE.sendToServer(new AdjustRangePacket(target, steps, fine));
        return true;
    }

    private static java.util.Optional<net.minecraft.core.BlockPos> lookedAtRadio(Minecraft mc) {
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) {
            return java.util.Optional.empty();
        }
        var block = mc.level.getBlockState(hit.getBlockPos()).getBlock();
        if (block != dev.famjam.radiofm.RadioFM.RADIO_BLOCK.get()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(hit.getBlockPos());
    }
}