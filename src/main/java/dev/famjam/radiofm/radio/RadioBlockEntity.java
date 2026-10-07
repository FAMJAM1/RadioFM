package dev.famjam.radiofm.radio;

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
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide() || !hasData(RadioFM.STATION_ATTACHMENT.get())) {
            return;
        }
        RadioStation old = getData(RadioFM.STATION_ATTACHMENT.get());
        removeData(RadioFM.STATION_ATTACHMENT.get());
        if (station == null) {
            setStation(old);
        } else {
            setChanged();
        }
    }
}
