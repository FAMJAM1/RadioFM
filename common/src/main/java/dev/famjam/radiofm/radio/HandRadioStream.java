package dev.famjam.radiofm.radio;

import dev.famjam.radiofm.voice.VoiceBackend;
import dev.famjam.radiofm.voice.VoiceOutput;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;


public class HandRadioStream extends RadioStream {

    private final ServerPlayer player;

    public HandRadioStream(RadioStation station, ServerPlayer player) {
        super(station, player.getUUID());
        this.player = player;
    }

    @Override
    protected VoiceOutput openOutput(VoiceBackend backend) {
        return backend.openOn(player, configuredRange(), this);
    }

    @Override
    protected String threadPrefix() {
        return "hand-radio";
    }

    @Override
    protected void tell(String translationKey) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> player.displayClientMessage(
                Component.translatable(translationKey).withStyle(ChatFormatting.RED), true));
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}
