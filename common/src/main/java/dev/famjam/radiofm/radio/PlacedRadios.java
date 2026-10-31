package dev.famjam.radiofm.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

public final class PlacedRadios {

    private PlacedRadios() {
    }

    public static Optional<RadioStation> read(BlockEntity blockEntity) {
        if (!(blockEntity instanceof RadioBlockEntity radio)) {
            return Optional.empty();
        }
        // RU: блоки, поставленные без станции, получают пустую | US: blocks placed without a station get an empty one
        if (radio.getStation() == null) {
            radio.setStation(RadioStation.create("", java.util.List.of()));
        }
        return Optional.of(radio.getStation());
    }

    public static Optional<RadioStation> read(BlockGetter level, BlockPos pos) {
        return read(level.getBlockEntity(pos));
    }

    public static void write(BlockEntity blockEntity, RadioStation station) {
        if (blockEntity instanceof RadioBlockEntity radio) {
            radio.setStation(station);
        }
    }

    public static boolean isRadio(BlockGetter level, BlockPos pos) {
        return read(level, pos).isPresent();
    }
}
