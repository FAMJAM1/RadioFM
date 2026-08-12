package dev.famjam.radiofm.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** ru: серверные настройки, config/radiofm-server.toml | en: server settings, config/radiofm-server.toml */
public class ServerConfig {

    public final ModConfigSpec.BooleanValue allowPrivateNetworks;
    public final ModConfigSpec.IntValue maxActiveRadios;
    public final ModConfigSpec.IntValue maxTracksPerRadio;
    public final ModConfigSpec.IntValue maxPlaylistBytes;
    public final ModConfigSpec.ConfigValue<String> skinUrl;
    public final ModConfigSpec.DoubleValue radioRange;
    public final ModConfigSpec.BooleanValue showMusicParticles;
    public final ModConfigSpec.IntValue musicParticleFrequency;

    public ServerConfig(ModConfigSpec.Builder builder) {
        builder.push("security");

        allowPrivateNetworks = builder
                .comment("Разрешить радио ходить по адресам внутри сети сервера.",
                        "Ссылку в радио вписывает игрок, а запрос делает сервер, поэтому",
                        "включать это стоит только когда музыка лежит в своей же сети.")
                .define("allowPrivateNetworks", false);

        maxActiveRadios = builder
                .comment("Сколько радио может играть одновременно на всём сервере.",
                        "Каждое держит свои потоки и открытое соединение.")
                .defineInRange("maxActiveRadios", 32, 1, 512);

        maxTracksPerRadio = builder
                .comment("Сколько треков брать из одного плейлиста.")
                .defineInRange("maxTracksPerRadio", 128, 1, 4096);

        maxPlaylistBytes = builder
                .comment("Сколько байт читать из файла плейлиста, прежде чем бросить.")
                .defineInRange("maxPlaylistBytes", 262144, 1024, 16777216);

        builder.pop();
        builder.push("radio");

        skinUrl = builder
                .comment("Скин головы-радио: ссылка на textures.minecraft.net.",
                        "Пустая строка — обычная голова без скина.")
                .define("skinUrl", "", ServerConfig::isSkinUrl);

        radioRange = builder
                .comment("Слышимость радио в блоках.")
                .defineInRange("range", 48D, 1D, 512D);

        showMusicParticles = builder
                .comment("Показывать нотки над играющим радио.")
                .define("showMusicParticles", true);

        musicParticleFrequency = builder
                .comment("Пауза между нотками в миллисекундах.")
                .defineInRange("musicParticleFrequency", 1000, 50, 60000);

        builder.pop();
    }

    /** ru: игра грузит скины только с домена Mojang | en: the game loads skins from Mojang's domain only */
    private static boolean isSkinUrl(Object value) {
        if (!(value instanceof String url)) {
            return false;
        }
        // ru: Mojang отдаёт ссылку через http | en: Mojang's own API returns http
        return url.isEmpty()
                || url.startsWith("https://textures.minecraft.net/texture/")
                || url.startsWith("http://textures.minecraft.net/texture/");
    }

    public static final ModConfigSpec SPEC;
    public static final ServerConfig INSTANCE;

    static {
        var pair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SPEC = pair.getRight();
        INSTANCE = pair.getLeft();
    }
}
