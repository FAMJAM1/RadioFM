package dev.famjam.radiofm;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * ru: стыковка с Simple Voice Chat; чат поднимается позже мода, поэтому очередь
 * en: the Simple Voice Chat hook; the chat starts after the mod, hence the queue
 */
@ForgeVoicechatPlugin // ru: на NeoForge ищут по аннотации | en: NeoForge finds plugins by this annotation
public class RadioVoicechatPlugin implements VoicechatPlugin {

    /**
     * ru: своя категория громкости; каналу передаётся id, а не объект
     * en: our own volume category; the channel takes the id, not the object
     */
    public static final String RADIOS_CATEGORY = "radiofm";

    public static volatile VoicechatServerApi voicechatServerApi;

    private static final List<Runnable> waiting = new ArrayList<>();

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

        // ru: setName — запасной вариант без перевода | en: setName is the untranslated fallback
        VolumeCategory.Builder builder = api.volumeCategoryBuilder()
                .setId(RADIOS_CATEGORY)
                .setName("Radios")
                .setNameTranslationKey("category.radiofm.radios")
                .setDescription("Volume of radios")
                .setDescriptionTranslationKey("category.radiofm.radios.description");

        int[][] icon = CategoryIcon.load();
        if (icon != null) {
            builder.setIcon(icon);
        }

        VolumeCategory category = builder.build();
        api.registerVolumeCategory(category);

        voicechatServerApi = api;

        RadioFM.LOGGER.info("Voice chat is up, radio can start");
        drainQueue();
    }

    /**
     * ru: сразу, если чат готов, иначе в очередь; порядок сохраняется
     * en: at once when the chat is up, queued otherwise; order is kept
     */
    public static void runWhenReady(Runnable task) {
        synchronized (waiting) {
            if (voicechatServerApi == null) {
                waiting.add(task);
                return;
            }
        }
        run(task);
    }

    private static void drainQueue() {
        List<Runnable> queued;
        synchronized (waiting) {
            queued = List.copyOf(waiting);
            waiting.clear();
        }
        queued.forEach(RadioVoicechatPlugin::run);
    }

    private static void run(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            RadioFM.LOGGER.error("Deferred voice chat task failed", e);
        }
    }

    /** ru: сервер остановился | en: the server stopped */
    public static void reset() {
        voicechatServerApi = null;
        synchronized (waiting) {
            waiting.clear();
        }
    }
}
