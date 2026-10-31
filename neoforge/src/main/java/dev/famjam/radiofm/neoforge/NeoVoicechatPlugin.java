package dev.famjam.radiofm.neoforge;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import dev.famjam.radiofm.voice.svc.RadioVoicechatPlugin;

/** RU: на NeoForge SVC ищет плагины по аннотации, на Fabric - по точке входа | US: on NeoForge SVC finds plugins by this annotation, on Fabric by an entrypoint */
@ForgeVoicechatPlugin
public class NeoVoicechatPlugin extends RadioVoicechatPlugin {
}
