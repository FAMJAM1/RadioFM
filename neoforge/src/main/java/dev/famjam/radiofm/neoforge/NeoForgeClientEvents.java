package dev.famjam.radiofm.neoforge;

import dev.famjam.radiofm.ClientEventHandlers;
import dev.famjam.radiofm.RadioFM;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = RadioFM.MODID, value = Dist.CLIENT)
public final class NeoForgeClientEvents {

    private NeoForgeClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientEventHandlers.onClientTick();
    }

    /** RU: смена измерения или возрождение: звуки старого мира стояли бы не там | US: dimension change or respawn: old-world sounds would sit in the wrong place */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        ClientEventHandlers.onClone();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEventHandlers.onLoggingOut();
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS
                && ClientEventHandlers.onRightMousePress()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (ClientEventHandlers.onScroll(event.getScrollDeltaY())) {
            event.setCanceled(true); // RU: иначе сменится слот | US: or the hotbar slot changes too
        }
    }
}
