package dev.famjam.radiofm.network;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToggleHandRadioPacket() implements CustomPacketPayload {

    public static final Type<ToggleHandRadioPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "toggle_hand_radio")
    );

    public static final StreamCodec<FriendlyByteBuf, ToggleHandRadioPacket> STREAM_CODEC =
            StreamCodec.unit(new ToggleHandRadioPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
