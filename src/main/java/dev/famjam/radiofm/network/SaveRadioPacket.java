package dev.famjam.radiofm.network;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** ru: пустой pos — радио в руке | en: an empty pos means a held radio */
public record SaveRadioPacket(java.util.Optional<BlockPos> pos, String stationName, List<String> urls) implements CustomPacketPayload {

    public static final Type<SaveRadioPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "save_radio")
    );

    public static final StreamCodec<FriendlyByteBuf, SaveRadioPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), SaveRadioPacket::pos,
            ByteBufCodecs.STRING_UTF8, SaveRadioPacket::stationName,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), SaveRadioPacket::urls,
            SaveRadioPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}