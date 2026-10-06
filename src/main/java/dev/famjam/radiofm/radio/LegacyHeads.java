package dev.famjam.radiofm.radio;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;

import java.util.Optional;

/**
 * RU: в 1.0.0 радио было головой игрока; такие предметы и поставленные головы
 *     переводятся в нынешние радио с той же станцией, иначе после обновления они молчат
 * US: in 1.0.0 the radio was a player head; such items and placed heads are turned
 *     into today's radios with the same station, or they go silent after the update
 */
public final class LegacyHeads {

    private LegacyHeads() {
    }

    public static void convertInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            // RU: заодно радио без станции, взятые средней кнопкой | US: also station-less radios taken with middle click
            if (stack.is(RadioFM.RADIO_ITEM.get()) && !stack.has(RadioFM.STATION_COMPONENT.get())) {
                RadioItem.write(stack, RadioStation.create("", java.util.List.of()));
                continue;
            }
            if (!stack.is(Items.PLAYER_HEAD)) {
                continue;
            }
            RadioStation station = stack.get(RadioFM.STATION_COMPONENT.get());
            if (station != null) {
                inventory.setItem(slot, toItem(station, stack.getCount()));
            }
        }
    }

    public static ItemStack toItem(RadioStation station, int count) {
        ItemStack radio = RadioItem.create(station.withOn(false));
        radio.setCount(count);
        return radio;
    }

    public static Optional<RadioStation> readPlaced(BlockEntity blockEntity) {
        if (!(blockEntity instanceof SkullBlockEntity)) {
            return Optional.empty();
        }
        return blockEntity.getExistingData(RadioFM.STATION_ATTACHMENT.get());
    }

    /** RU: ставит блок радио на место головы | US: puts a radio block where the head was */
    public static Optional<RadioStation> convertPlaced(ServerLevel level, BlockPos pos) {
        Optional<RadioStation> station = readPlaced(level.getBlockEntity(pos));
        if (station.isEmpty()) {
            return Optional.empty();
        }
        BlockState radio = RadioFM.RADIO_BLOCK.get().defaultBlockState()
                .setValue(RadioBlock.ROTATION, rotationOf(level.getBlockState(pos)));
        level.setBlock(pos, radio, Block.UPDATE_ALL);

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return Optional.empty();
        }
        RadioStation off = station.get().withOn(false);
        PlacedRadios.write(blockEntity, off);
        return Optional.of(off);
    }

    private static int rotationOf(BlockState head) {
        if (head.hasProperty(BlockStateProperties.ROTATION_16)) {
            return head.getValue(BlockStateProperties.ROTATION_16);
        }
        if (head.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return RotationSegment.convertToSegment(head.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        return 0;
    }
}
