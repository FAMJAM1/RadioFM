package dev.famjam.radiofm.neoforge;

import dev.famjam.radiofm.RadioFM;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * RU: окно настроек строит сам NeoForge; названия и подсказки - в lang по ключам radiofm.configuration.*,
 *     без них окно показывает имя ключа и комментарий из файла на двух языках сразу
 * US: NeoForge builds the settings screen itself; labels and tooltips are in lang under radiofm.configuration.*,
 *     without them the screen shows the key name and the file's comment in both languages at once
 */
@Mod(value = RadioFM.MODID, dist = Dist.CLIENT)
public final class RadioFMClient {

    public RadioFMClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) ->
                new ConfigurationScreen(mod, parent, (screen, type, config, title) -> new RadioConfigSection(screen, type, config, title)));
    }
}
