package dev.famjam.radiofm.radio;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

/** RU: станция поставленного радио; аттачмент на BlockEntity вместо правки ванили | US: the placed radio's station; an attachment on the BlockEntity, no vanilla patching */
public final class PlacedRadios {

    private PlacedRadios() {
    }

    public static Optional<RadioStation> read(BlockEntity blockEntity) {
        if (!(blockEntity instanceof RadioBlockEntity)) {
            return Optional.empty();
        }
        // RU: блоки, поставленные без станции, получают пустую | US: blocks placed without a station get an empty one
        if (!blockEntity.hasData(RadioFM.STATION_ATTACHMENT.get())) {
            write(blockEntity, RadioStation.create("", java.util.List.of()));
        }
        return blockEntity.getExistingData(RadioFM.STATION_ATTACHMENT.get());
    }

    public static Optional<RadioStation> read(BlockGetter level, BlockPos pos) {
        return read(level.getBlockEntity(pos));
    }

    public static void write(BlockEntity blockEntity, RadioStation station) {
        blockEntity.setData(RadioFM.STATION_ATTACHMENT.get(), station);
        blockEntity.setChanged();
    }

    public static boolean isRadio(BlockGetter level, BlockPos pos) {
        return read(level, pos).isPresent();
    }
}
