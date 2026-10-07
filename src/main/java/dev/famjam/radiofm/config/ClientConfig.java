package dev.famjam.radiofm.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue RADIO_VOLUME;
    public static final int MAX_VOLUME = 200;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        RADIO_VOLUME = builder
                .comment("RU: громкость всех радио на своём канале, в процентах, до 200 | US: volume of all radios on our own channel, in percent, up to 200")
                .defineInRange("radioVolume", 100, 0, MAX_VOLUME);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
