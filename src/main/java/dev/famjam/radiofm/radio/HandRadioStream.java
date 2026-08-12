package dev.famjam.radiofm.radio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import dev.famjam.radiofm.RadioVoicechatPlugin;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** ru: радио в руке — звук идёт от игрока | en: a held radio — the sound comes from the player */
public class HandRadioStream extends RadioStream {

    private final ServerPlayer player;

    public HandRadioStream(RadioStation station, ServerPlayer player) {
        super(station, player.getUUID());
        this.player = player;
    }

    @Override
    protected AudioChannel openChannel(VoicechatServerApi api) {
        EntityAudioChannel channel = api.createEntityAudioChannel(
                UUID.randomUUID(), api.fromServerPlayer(player));
        if (channel == null) {
            return null;
        }
        channel.setDistance(configuredRange());
        channel.setCategory(RadioVoicechatPlugin.RADIOS_CATEGORY);
        return channel;
    }

    @Override
    protected String threadPrefix() {
        return "hand-radio";
    }

    @Override
    protected void onStartFailed() {
        // ru: трек грузится в фоне, поэтому об ошибке сообщаем отсюда
        // en: the track loads in the background, so the error is reported here
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> player.displayClientMessage(
                Component.translatable("message.radiofm.start_error").withStyle(ChatFormatting.RED), true));
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}
