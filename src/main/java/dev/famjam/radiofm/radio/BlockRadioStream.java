package dev.famjam.radiofm.radio;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.RadioVoicechatPlugin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** ru: поставленное радио — звук из точки мира | en: a placed radio — sound from a point in the world */
public class BlockRadioStream extends RadioStream {

    private static final double PARTICLE_VIEW_DISTANCE = 32D;
    /** ru: как часто сверять, что блок на месте | en: how often to check the block is still there */
    private static final long VALIDITY_CHECK_INTERVAL_MS = 2_000L;

    private final ServerLevel level;
    private final BlockPos position;

    private long lastParticle;
    private long lastValidityCheck;

    public BlockRadioStream(RadioStation station, ServerLevel level, BlockPos position) {
        super(station, station.id());
        this.level = level;
        this.position = position;
    }

    @Override
    protected AudioChannel openChannel(VoicechatServerApi api) {
        Position pos = api.createPosition(
                position.getX() + 0.5D, position.getY() + 0.5D, position.getZ() + 0.5D);

        LocationalAudioChannel channel = api.createLocationalAudioChannel(
                UUID.randomUUID(), api.fromServerLevel(level), pos);
        if (channel == null) {
            return null;
        }
        channel.setDistance(configuredRange());
        channel.setCategory(RadioVoicechatPlugin.RADIOS_CATEGORY);
        return channel;
    }

    @Override
    protected String threadPrefix() {
        return "block-radio";
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

        // ru: мир только с серверного потока | en: touch the world on the server thread only
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

    /** ru: блок могли снять, пока радио играло | en: the block may be gone while it played */
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
