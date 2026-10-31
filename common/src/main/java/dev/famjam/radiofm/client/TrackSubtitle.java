package dev.famjam.radiofm.client;

import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** RU: субтитр берётся у звукового события, а нам нужен готовый текст, а не ключ перевода | US: the subtitle comes from the sound event, and we need ready text rather than a translation key */
final class TrackSubtitle extends WeighedSoundEvents {

    private final Component text;

    TrackSubtitle(ResourceLocation location, Component text) {
        super(location, null);
        this.text = text;
    }

    @Override
    public Component getSubtitle() {
        return text;
    }
}
