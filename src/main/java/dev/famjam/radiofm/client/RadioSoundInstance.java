package dev.famjam.radiofm.client;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/**
 * RU: звук без файла: игра берёт поток у {@link #getStream}, звук в sounds.json не нужен.
 *     Не «тикающий» нарочно: такому игра каждый тик сбрасывает громкость к своей (не выше 100%
 *     и с общей громкостью), а позицию и громкость ведёт OwnAudioClient
 * US: a sound with no file: the game takes the stream from {@link #getStream}, no sounds.json entry needed.
 *     Not tickable on purpose: for those the game resets the volume to its own every tick (capped at 100%
 *     and scaled by master), while OwnAudioClient drives position and volume
 */
final class RadioSoundInstance extends AbstractSoundInstance {

    private static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "radio_stream");

    private final ClientRadioSource source;

    RadioSoundInstance(ClientRadioSource source) {
        // RU: общая категория: иначе звук ещё умножается на ползунок «Музыкальные блоки», и радио
        //     тише, чем через SVC и PV, которые ползунков игры не слушают; громкость задаёт наш ползунок
        // US: the master category: otherwise the sound is also scaled by the "Jukebox/Note Blocks" slider
        //     and the radio is quieter than through SVC and PV, which ignore the game's sliders; our slider sets the volume
        super(SoundEvent.createVariableRangeEvent(LOCATION), SoundSource.MASTER, SoundInstance.createUnseededRandom());
        this.source = source;
        this.looping = false;
        this.delay = 0;
        // RU: затухание считаем сами в ClientRadioSource.gain: игровое задаётся один раз при старте,
        //     и смена дальности требовала бы перезапуска звука
        // US: attenuation is ours, in ClientRadioSource.gain: the game's is set once at start,
        //     and a range change would need the sound restarted
        this.attenuation = Attenuation.NONE;
        this.relative = false;
        this.volume = 1F;
        updatePosition();
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        this.sound = new Sound(LOCATION, ConstantFloat.of(1F), ConstantFloat.of(1F), 1,
                Sound.Type.FILE, true, false, 16);
        return new WeighedSoundEvents(LOCATION, null);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(new RadioAudioStream(source));
    }

    /** RU: иначе при громкости 0 звук не стартует и не вернётся, когда её прибавят | US: or at volume 0 the sound never starts and does not come back when turned up */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    void moveTo(Vec3 at) {
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
    }

    static ResourceLocation location() {
        return LOCATION;
    }

    private void updatePosition() {
        Vec3 at = position(source);
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
    }

    static Vec3 position(ClientRadioSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (source.entityId() >= 0 && minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(source.entityId());
            if (entity != null) {
                return entity.getEyePosition();
            }
        }
        return Vec3.atCenterOf(source.pos());
    }
}
