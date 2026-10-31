package dev.famjam.radiofm.neoforge;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.platform.Platform;
import dev.famjam.radiofm.radio.RadioStation;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class NeoForgePlatform implements Platform {

    private static final Map<ResourceKey<?>, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, RadioFM.MODID);

    /**
     * RU: только для чтения старых данных: так станцию хранили радио до 1.3.0 и головы из 1.0.0;
     *     теперь она в данных RadioBlockEntity
     * US: for reading old data only: radios before 1.3.0 and heads from 1.0.0 kept the station
     *     this way; it now lives in RadioBlockEntity's data
     */
    static final Supplier<AttachmentType<RadioStation>> STATION_ATTACHMENT =
            ATTACHMENTS.register("station", () -> AttachmentType
                    .<RadioStation>builder(() -> RadioStation.create("", List.of()))
                    .serialize(RadioStation.CODEC)
                    .build());

    static void registerAll(IEventBus modBus) {
        REGISTERS.values().forEach(register -> register.register(modBus));
        ATTACHMENTS.register(modBus);
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory) {
        DeferredRegister<R> register = (DeferredRegister<R>) REGISTERS.computeIfAbsent(registry.key(),
                key -> DeferredRegister.create(registry.key(), RadioFM.MODID));
        return register.register(name, factory);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public Optional<RadioStation> legacyStation(BlockEntity blockEntity) {
        return blockEntity.getExistingData(STATION_ATTACHMENT.get());
    }

    @Override
    public void removeLegacyStation(BlockEntity blockEntity) {
        blockEntity.removeData(STATION_ATTACHMENT.get());
    }

    @Override
    public DataFixTypes savedDataFixType() {
        return null;
    }
}
