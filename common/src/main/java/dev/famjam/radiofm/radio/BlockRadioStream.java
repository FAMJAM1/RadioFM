package dev.famjam.radiofm.radio;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.voice.VoiceBackend;
import dev.famjam.radiofm.voice.VoiceOutput;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class BlockRadioStream extends RadioStream {

    private static final double PARTICLE_VIEW_DISTANCE = 32D;
    private static final long VALIDITY_CHECK_INTERVAL_MS = 2_000L;

    private final ServerLevel level;
    private final BlockPos position;
    private final UUID owner;

    private long lastParticle;
    private long lastValidityCheck;

    public BlockRadioStream(RadioStation station, ServerLevel level, BlockPos position, UUID owner) {
        super(station, station.id());
        this.level = level;
        this.position = position;
        this.owner = owner;
    }

    @Override
    protected VoiceOutput openOutput(VoiceBackend backend) {
        return backend.openAt(level, position, configuredRange(), this);
    }

    @Override
    protected String threadPrefix() {
        return "block-radio";
    }

    /** RU: как у радио в руке, пишем тому, кто включил | US: as with a held radio, told to whoever switched it on */
    @Override
    protected void tell(String translationKey) {
        if (owner == null) {
            return;
        }
        level.getServer().execute(() -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable(translationKey).withStyle(ChatFormatting.RED), true);
            }
        });
    }

    @Override
    protected void onFrame() {
        long now = System.currentTimeMillis();
        spawnParticles(now);
        checkStillThere(now);
    }

    private void spawnParticles(long now) {
        if (!RadioFM.SERVER_CONFIG.showMusicParticles.get()) {
            return;
        }
        if (now - lastParticle < RadioFM.SERVER_CONFIG.musicParticleFrequency.get()) {
            return;
        }
        lastParticle = now;

        // RU: мир только с серверного потока | US: touch the world on the server thread only
        level.getServer().execute(() -> {
            Vec3 above = Vec3.atBottomCenterOf(position).add(0D, 1D, 0D);
            boolean anyoneNearby = level.players().stream()
                    .anyMatch(player -> player.position().distanceTo(position.getCenter()) <= PARTICLE_VIEW_DISTANCE);
            if (!anyoneNearby) {
                return;
            }
            float offset = level.getRandom().nextInt(4) / 24F;
            level.sendParticles(ParticleTypes.NOTE, above.x(), above.y(), above.z(), 0, offset, 0D, 0D, 1D);
        });
    }

    /** RU: блок могли снять, пока радио играло | US: the block may be gone while it played */
    private void checkStillThere(long now) {
        if (now - lastValidityCheck < VALIDITY_CHECK_INTERVAL_MS) {
            return;
        }
        lastValidityCheck = now;

        level.getServer().execute(() -> {
            if (RadioManager.isRadioAt(level, position, getId())) {
                return;
            }
            RadioFM.LOGGER.info("Radio {} is gone from {}, stopping it", getId(), position);
            RadioManager.get().stopBlockRadio(getId());
        });
    }

    public BlockPos getPosition() {
        return position;
    }

    public ServerLevel getLevel() {
        return level;
    }
}
