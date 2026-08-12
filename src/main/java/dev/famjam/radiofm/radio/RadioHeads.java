package dev.famjam.radiofm.radio;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

/**
 * ru: радио на поставленной голове; аттачмент на BlockEntity вместо правки ванили
 * en: a radio on a placed head; an attachment on the BlockEntity, no vanilla patching
 */
public final class RadioHeads {

    private RadioHeads() {
    }

    public static Optional<RadioStation> read(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return Optional.empty();
        }
        // ru: getData повесил бы станцию на любой череп | en: getData would attach one to any head
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
