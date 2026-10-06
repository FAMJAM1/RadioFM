package dev.famjam.radiofm.radio;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * RU: сама по себе пустая, станция живёт аттачментом и читается через PlacedRadios
 * US: empty in itself, the station lives on an attachment and is read through PlacedRadios
 */
public class RadioBlockEntity extends BlockEntity {

    public RadioBlockEntity(BlockPos pos, BlockState state) {
        super(RadioFM.RADIO_BLOCK_ENTITY.get(), pos, state);
    }
}
