package dev.famjam.radiofm.platform;

import dev.famjam.radiofm.radio.RadioStation;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * RU: то, что NeoForge и Fabric делают по-разному; общий код зовёт только это
 * US: what NeoForge and Fabric do differently; the common code calls only this
 */
public interface Platform {

    Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No RadioFM platform implementation"));

    boolean isModLoaded(String modId);

    /**
     * RU: NeoForge регистрирует позже, по событию, поэтому значение берётся через get() уже после регистрации
     * US: NeoForge registers later, on an event, so the value is taken through get() once registered
     */
    <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToServer(CustomPacketPayload payload);

    /** RU: станция в старом хранилище NeoForge (радио до 1.3.0 и головы 1.0.0); на Fabric его нет | US: a station in NeoForge's old storage (radios before 1.3.0 and 1.0.0 heads); Fabric has none */
    Optional<RadioStation> legacyStation(BlockEntity blockEntity);

    void removeLegacyStation(BlockEntity blockEntity);

    /** RU: NeoForge разрешает null, чистая игра на null падает | US: NeoForge allows null, the plain game crashes on it */
    DataFixTypes savedDataFixType();
}
