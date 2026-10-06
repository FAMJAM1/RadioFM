package dev.famjam.radiofm.voice.svc;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import dev.famjam.radiofm.CategoryIcon;
import dev.famjam.radiofm.RadioFM;

/** RU: стыковка с Simple Voice Chat | US: the Simple Voice Chat hook */
@ForgeVoicechatPlugin // RU: на NeoForge ищут по аннотации | US: NeoForge finds plugins by this annotation
public class RadioVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return RadioFM.MODID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        VoicechatServerApi api = event.getVoicechat();

        // RU: setName - запасной вариант без перевода | US: setName is the untranslated fallback
        VolumeCategory.Builder builder = api.volumeCategoryBuilder()
                .setId(SvcBackend.RADIOS_CATEGORY)
                .setName("Radios")
                .setNameTranslationKey("category.radiofm.radios")
                .setDescription("Volume of radios")
                .setDescriptionTranslationKey("category.radiofm.radios.description");

        int[][] icon = CategoryIcon.load();
        if (icon != null) {
            builder.setIcon(icon);
        }
        api.registerVolumeCategory(builder.build());

        RadioFM.LOGGER.info("Voice chat is up, radio can start");
        SvcBackend.onVoiceChatStarted(api);
    }
}
