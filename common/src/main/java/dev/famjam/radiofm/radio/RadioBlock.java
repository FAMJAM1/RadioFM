package dev.famjam.radiofm.radio;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * RU: свой блок, а не голова игрока: скин головы грузится из сети и зависит от того,
 *     что творят с этой загрузкой другие моды; своя модель ни от чего не зависит
 * US: a block of its own rather than a player head: a head skin is fetched over the
 *     network and hangs on what other mods do to that fetch, while a model of our own
 *     depends on nothing
 */
public class RadioBlock extends BaseEntityBlock {

    public static final MapCodec<RadioBlock> CODEC = simpleCodec(RadioBlock::new);

    /**
     * RU: шестнадцать положений, как у головы; четыре четверти даёт поворот "y" в
     *     blockstate, промежуточные 22.5 - поворот элемента в самой модели, и крутят
     *     они в разные стороны, поэтому углы в моделях со знаком минус
     * US: sixteen settings, the way a head turns; the four quarters come from the "y" in
     *     the blockstate and the 22.5 between them from an element rotation inside the
     *     model, and the two turn opposite ways, hence the minus signs in the models
     */
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;

    private static final int SEGMENTS = 16;
    private static final VoxelShape[] SHAPES = shapesByRotation();

    public RadioBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ROTATION, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    /**
     * RU: лицом к тому, кто ставит; таблички в этом месте доворачивают модель на
     *     пол-оборота, нам это не нужно: у них перёд смотрит на юг, а у нас на север
     * US: facing whoever places it; signs add a half turn here, which we do not need,
     *     since their model fronts south while ours fronts north
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(ROTATION, RotationSegment.convertToSegment(context.getRotation()));
    }

    /**
     * RU: станция переезжает с предмета на блок здесь, а не по событию установки:
     *     событие не знает, чем ставили, и прежняя догадка могла достаться чужому блоку
     * US: the station moves from the item onto the block here rather than on the place
     *     event: that event does not know what was used, and the guess standing in for
     *     it could land on an unrelated block
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        // RU: новый id: копии из креатива несут один и тот же, а по нему ключуется стрим;
        //     предмет со средней кнопки приходит вовсе без станции
        // US: a new id: creative copies all carry the same one and it keys the stream;
        //     a middle-click item comes with no station at all
        RadioStation station = RadioItem.read(stack)
                .map(RadioStation::withNewId)
                .orElseGet(() -> RadioStation.create("", java.util.List.of()));
        PlacedRadios.write(blockEntity, station.withOn(false));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), SEGMENTS));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), SEGMENTS));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(ROTATION)];
    }

    /**
     * RU: корпус, повёрнутый вокруг середины блока; антенну не берём, иначе она
     *     ловила бы курсор и мешала ставить рядом
     * US: the body turned about the middle of the block; the aerial is left out, or it
     *     would catch the cursor and get in the way of building alongside
     */
    private static VoxelShape[] shapesByRotation() {
        VoxelShape[] shapes = new VoxelShape[SEGMENTS];
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double angle = Math.toRadians(RotationSegment.convertToDegrees(segment));
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            double halfX = 6D;   // RU: корпус 12 в ширину | US: the body is 12 across
            double halfZ = 3D;   // RU: и 6 в глубину | US: and 6 deep
            double spanX = Math.abs(halfX * cos) + Math.abs(halfZ * sin);
            double spanZ = Math.abs(halfX * sin) + Math.abs(halfZ * cos);
            shapes[segment] = box(8D - spanX, 0D, 8D - spanZ, 8D + spanX, 8D, 8D + spanZ);
        }
        return shapes;
    }

    /**
     * RU: иначе блок с сущностью рисуется только через свой рендерер и в мире его не видно
     * US: without this a block with an entity is drawn only by its own renderer and stays invisible
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioBlockEntity(pos, state);
    }
}
