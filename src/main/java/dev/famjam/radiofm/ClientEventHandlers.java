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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = dev.famjam.radiofm.RadioFM.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandlers {

    public static long clientElapsed = 0;
    public static long clientDuration = -1;

    @OnlyIn(Dist.CLIENT)
    public static void updateStatus(RadioStatusPacket packet) {
        clientElapsed = packet.elapsedSeconds();
        clientDuration = packet.durationSeconds();
    }

    @OnlyIn(Dist.CLIENT)
    public static void openScreen(OpenRadioScreenPacket packet) {
        // RU: иначе до первого пакета видно время прошлого радио | US: or the last radio's time shows until the first packet
        clientElapsed = 0;
        clientDuration = -1;
        Minecraft.getInstance().setScreen(new RadioScreen(packet));
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        var stack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (RadioItem.read(stack).isEmpty()) return;

        // RU: смотрит на блок - пусть ставит | US: aiming at a block, let them place it
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return;

        if (mc.player.isShiftKeyDown()) {
            return; // RU: окно откроет сервер по RightClickItem | US: the server opens the screen on RightClickItem
        }

        PacketDistributor.sendToServer(new ToggleHandRadioPacket());
        event.setCanceled(true);
    }

    /**
     * RU: клиент не знает, радио ли это в руке - настройки на сервере, он и решает
     * US: the client cannot tell if the held item is a radio - the settings are server side
     */
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        if (!mc.player.isShiftKeyDown()) return;

        int steps = (int) Math.signum(event.getScrollDeltaY());
        if (steps == 0) return;

        java.util.Optional<net.minecraft.core.BlockPos> target = lookedAtRadio(mc);
        boolean holdingRadio = RadioItem.read(mc.player.getItemInHand(InteractionHand.MAIN_HAND)).isPresent();
        if (target.isEmpty() && !holdingRadio) return;

        boolean fine = net.minecraft.client.gui.screens.Screen.hasControlDown();
        PacketDistributor.sendToServer(new AdjustRangePacket(target, steps, fine));
        event.setCanceled(true); // RU: иначе сменится слот | US: or the hotbar slot changes too
    }

    @OnlyIn(Dist.CLIENT)
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