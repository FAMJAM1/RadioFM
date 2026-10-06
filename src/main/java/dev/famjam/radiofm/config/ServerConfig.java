package dev.famjam.radiofm.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ServerConfig {

    public final ModConfigSpec.BooleanValue allowPrivateNetworks;
    public final ModConfigSpec.IntValue maxActiveRadios;
    public final ModConfigSpec.IntValue maxTracksPerRadio;
    public final ModConfigSpec.IntValue maxPlaylistBytes;
    public final ModConfigSpec.DoubleValue radioRange;
    public final ModConfigSpec.BooleanValue showMusicParticles;
    public final ModConfigSpec.IntValue musicParticleFrequency;
    public final ModConfigSpec.ConfigValue<String> voiceMod;

    public ServerConfig(ModConfigSpec.Builder builder) {
        builder.push("security");

        allowPrivateNetworks = builder
                .comment("RU: разрешить радио ходить по адресам внутри сети сервера;",
                        "    ссылку вписывает игрок, а запрос делает сервер, поэтому включать",
                        "    это стоит только когда музыка лежит в своей же сети",
                        "US: let radios reach addresses inside the server's own network; the link",
                        "    is typed in by a player but fetched by the server, so turn this on",
                        "    only when the music sits on that same network")
                .define("allowPrivateNetworks", false);

        maxActiveRadios = builder
                .comment("RU: сколько радио может играть одновременно на всём сервере;",
                        "    каждое держит свои потоки и открытое соединение",
                        "US: how many radios may play at once across the whole server; each one",
                        "    holds its own streams and an open connection")
                .defineInRange("maxActiveRadios", 32, 1, 512);

        maxTracksPerRadio = builder
                .comment("RU: сколько треков брать из одного плейлиста | US: how many tracks to take from one playlist")
                .defineInRange("maxTracksPerRadio", 128, 1, 4096);

        maxPlaylistBytes = builder
                .comment("RU: сколько байт читать из файла плейлиста, прежде чем бросить",
                        "US: how many bytes to read from a playlist file before giving up")
                .defineInRange("maxPlaylistBytes", 262144, 1024, 16777216);

        builder.pop();
        builder.push("radio");

        radioRange = builder
                .comment("RU: слышимость радио в блоках | US: how far a radio can be heard, in blocks")
                .defineInRange("range", 48D, 1D, 512D);

        showMusicParticles = builder
                .comment("RU: показывать нотки над играющим радио | US: show notes above a playing radio")
                .define("showMusicParticles", true);

        musicParticleFrequency = builder
                .comment("RU: пауза между нотками в миллисекундах | US: gap between notes, in milliseconds")
                .defineInRange("musicParticleFrequency", 1000, 50, 60000);

        builder.pop();
        builder.push("voice");

        voiceMod = builder
                .comment("RU: через какой голосовой мод играть, если стоят оба: svc или plasmo; задаётся командой /radiofm choice",
                        "US: which voice mod to play through when both are installed: svc or plasmo; set with /radiofm choice")
                .define("voiceMod", "", value -> value instanceof String s
                        && (s.isEmpty() || s.equals("svc") || s.equals("plasmo")));

        builder.pop();
    }

    public static final ModConfigSpec SPEC;
    public static final ServerConfig INSTANCE;

    static {
        var pair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SPEC = pair.getRight();
        INSTANCE = pair.getLeft();
    }
}
