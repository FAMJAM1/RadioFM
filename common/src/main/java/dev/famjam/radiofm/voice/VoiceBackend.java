package dev.famjam.radiofm.voice;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * RU: голосовой мод, через который звучит радио; кадры - 960 сэмплов 48кГц моно,
 *     null из поставщика означает конец потока
 * US: the voice mod the radio plays through; frames are 960 samples of 48kHz mono,
 *     null from the supplier means the end of the stream
 */
public interface VoiceBackend {

    /** RU: голосовой мод поднимается позже нашего | US: the voice mod comes up after ours */
    void runWhenReady(Runnable task);

    /** RU: null, если открыть не вышло | US: null when it could not be opened */
    VoiceOutput openAt(ServerLevel level, BlockPos pos, float range, Supplier<short[]> frames);

    VoiceOutput openOn(ServerPlayer player, float range, Supplier<short[]> frames);

    void reset();
}
