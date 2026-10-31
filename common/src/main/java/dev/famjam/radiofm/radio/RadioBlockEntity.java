package dev.famjam.radiofm.radio;

import java.util.Optional;
import dev.famjam.radiofm.platform.Platform;
import dev.famjam.radiofm.RadioFM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * RU: станция хранится в собственных данных блока, а не аттачментом NeoForge: их одинаково
 *     читают NeoForge и Fabric, и мир переживёт смену загрузчика
 * US: the station lives in the block's own data rather than a NeoForge attachment: NeoForge
 *     and Fabric read it alike, so a world survives switching loaders
 */
public class RadioBlockEntity extends BlockEntity {

    private static final String STATION_TAG = "Station";

    private RadioStation station;

    public RadioBlockEntity(BlockPos pos, BlockState state) {
        super(RadioFM.RADIO_BLOCK_ENTITY.get(), pos, state);
    }

    public RadioStation getStation() {
        return station;
    }

    public void setStation(RadioStation station) {
        this.station = station;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (station != null) {
            RadioStation.CODEC.encodeStart(NbtOps.INSTANCE, station)
                    .ifSuccess(encoded -> tag.put(STATION_TAG, encoded));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(STATION_TAG)) {
            RadioStation.CODEC.parse(NbtOps.INSTANCE, tag.get(STATION_TAG))
                    .ifSuccess(decoded -> station = decoded);
        }
    }

    /**
     * RU: радио, поставленные до 1.3.0, держат станцию в аттачменте; переносим при загрузке,
     *     а не при первом клике, иначе нетронутые радио потеряют станцию при переходе на Fabric
     * US: radios placed before 1.3.0 keep the station in an attachment; it moves on load
     *     rather than on the first click, or untouched radios lose it when moving to Fabric
     *
     * RU: без @Override: на NeoForge метод перекрывает его onLoad, в чистой игре и на Fabric его
     *     никто не зовёт, но там и старых данных нет; родительский onLoad NeoForge обновляет
     *     данные модели, которых у радио нет
     * US: no @Override: on NeoForge this overrides its onLoad, the plain game and Fabric never
     *     call it, but there is no old data there either; NeoForge's parent onLoad refreshes
     *     model data, which the radio does not have
     */
    public void onLoad() {
        if (level == null || level.isClientSide()) {
            return;
        }
        Optional<RadioStation> legacy = Platform.INSTANCE.legacyStation(this);
        if (legacy.isEmpty()) {
            return;
        }
        RadioStation old = legacy.get();
        Platform.INSTANCE.removeLegacyStation(this);
        if (station == null) {
            setStation(old);
        } else {
            setChanged();
        }
    }
}
