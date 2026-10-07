package dev.famjam.radiofm.screen;

import dev.famjam.radiofm.network.OpenRadioScreenPacket;
import dev.famjam.radiofm.ClientEventHandlers;
import dev.famjam.radiofm.network.RadioControlPacket;
import dev.famjam.radiofm.network.SaveRadioPacket;
import net.minecraft.client.gui.GuiGraphics;
import dev.famjam.radiofm.config.ClientConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class RadioScreen extends Screen {

    private static final int VOLUME_MARGIN = 3;
    private static final int VOLUME_WIDTH = 120;

    // RU: пусто - радио в руке | US: empty means a held radio
    private final java.util.Optional<BlockPos> pos;
    private String currentStationName;
    private final List<String> trackUrls = new ArrayList<>();
    private String currentState;
    private boolean shuffle;

    private EditBox stationNameField;
    private final List<EditBox> urlFields = new ArrayList<>();
    private Button stopPlayButton;
    private EditBox playlistUrlField;
    private int scrollOffset = 0;
    private int repeatTrackIndex = -1;
    private final boolean ownChannel;

    public RadioScreen(OpenRadioScreenPacket packet) {
        super(Component.translatable("gui.radiofm.title"));
        this.pos = packet.pos();
        this.currentStationName = packet.stationName();
        this.trackUrls.addAll(packet.urls());
        this.currentState = packet.state();
        this.shuffle = packet.shuffle();
        this.repeatTrackIndex = packet.repeatTrackIndex();
        this.ownChannel = packet.ownChannel();
        if (this.trackUrls.isEmpty()) this.trackUrls.add("");
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        stationNameField = new EditBox(this.font, cx - 150, 35, 300, 20, Component.literal(""));
        stationNameField.setMaxLength(64);
        stationNameField.setValue(currentStationName);
        stationNameField.setHint(Component.translatable("gui.radiofm.station_hint"));
        this.addRenderableWidget(stationNameField);

        // RU: громкость наша, только когда звук идёт своим каналом; у SVC и PV свои ползунки
        // US: the volume is ours only when the sound goes through our channel; SVC and PV have their own sliders
        if (ownChannel) {
            this.addRenderableWidget(new VolumeSlider(VOLUME_MARGIN, VOLUME_MARGIN, VOLUME_WIDTH, 20));
        }

        urlFields.clear();
        int maxVisible = Math.max(1, (this.height - 200) / 26);
        scrollOffset = Math.min(scrollOffset, Math.max(0, trackUrls.size() - maxVisible));
        int lastTrackIndex = trackUrls.size() - 1;

        for (int vi = 0; vi < maxVisible && (vi + scrollOffset) < trackUrls.size(); vi++) {
            int i = vi + scrollOffset;
            int y = 70 + vi * 26;
            boolean isLast = (i == lastTrackIndex);
            final int trackIdx = i;
            final int maxVis = maxVisible;

            this.addRenderableWidget(Button.builder(Component.literal(repeatTrackIndex == i ? "[\uD83D\uDD01]" : "\uD83D\uDD01"), btn -> {
                if (repeatTrackIndex == trackIdx) {
                    repeatTrackIndex = -1;
                    PacketDistributor.sendToServer(new RadioControlPacket(pos, RadioControlPacket.REPEAT, -1));
                } else {
                    repeatTrackIndex = trackIdx;
                    PacketDistributor.sendToServer(new RadioControlPacket(pos, RadioControlPacket.REPEAT_TRACK, trackIdx));
                }
                clearWidgets(); init();
            }).bounds(cx - 175, y, 20, 20).build());

            EditBox field = new EditBox(this.font, cx - 150, y, 250, 20, Component.literal(""));
            field.setMaxLength(512);
            field.setValue(trackUrls.get(i));
            field.setHint(Component.translatable("gui.radiofm.url_hint"));
            urlFields.add(field);
            this.addRenderableWidget(field);

            this.addRenderableWidget(Button.builder(Component.literal("✕"), btn -> {
                syncFieldsToList();
                if (trackUrls.size() > 1) {
                    trackUrls.remove(trackIdx);
                    // RU: метка привязана к треку, а не к строке | US: the marker follows the track, not the row
                    if (repeatTrackIndex == trackIdx) {
                        repeatTrackIndex = -1;
                    } else if (repeatTrackIndex > trackIdx) {
                        repeatTrackIndex--;
                    }
                    scrollOffset = Math.min(scrollOffset, Math.max(0, trackUrls.size() - maxVis));
                    clearWidgets(); init();
                }
            }).bounds(cx + 105, y, 20, 20).build());

            Button up = Button.builder(Component.literal("▲"), btn -> moveTrack(trackIdx, -1, maxVis))
                    .bounds(cx + 130, y, 20, 20).build();
            up.active = trackIdx > 0;
            this.addRenderableWidget(up);

            Button down = Button.builder(Component.literal("▼"), btn -> moveTrack(trackIdx, 1, maxVis))
                    .bounds(cx + 155, y, 20, 20).build();
            down.active = !isLast;
            this.addRenderableWidget(down);
        }

        int controlY = this.height - 80;
        this.addRenderableWidget(Button.builder(Component.literal("⏮"), btn ->
                PacketDistributor.sendToServer(new RadioControlPacket(pos, RadioControlPacket.PREV, -1))
        ).bounds(cx - 95, controlY, 40, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("⏸"), btn -> {
            PacketDistributor.sendToServer(new RadioControlPacket(pos, RadioControlPacket.PAUSE, -1));
            currentState = currentState.equals("PAUSED") ? "ACTIVE" : "PAUSED";
        }).bounds(cx - 50, controlY, 40, 20).build());

        stopPlayButton = Button.builder(Component.literal(currentState.equals("STOPPED") ? "▶" : "⏹"), btn -> {
            if (currentState.equals("STOPPED")) {
                PacketDistributor.sendToServer(new RadioControlPacket(pos, RadioControlPacket.PLAY, -1));
                currentState = "ACTIVE";
            } else {
                PacketDistributor.sendToServer(new RadioControlPacket(pos, RadioControlPacket.STOP, -1));
                currentState = "STOPPED";
            }
            btn.setMessage(Component.literal(currentState.equals("STOPPED") ? "▶" : "⏹"));
        }).bounds(cx - 5, controlY, 40, 20).build();
        this.addRenderableWidget(stopPlayButton);

        this.addRenderableWidget(Button.builder(Component.literal("⏭"), btn ->
                PacketDistributor.sendToServer(new RadioControlPacket(pos, RadioControlPacket.NEXT, -1))
        ).bounds(cx + 40, controlY, 40, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal(shuffle ? "[\uD83D\uDD00]" : "\uD83D\uDD00"), btn -> {
            shuffle = !shuffle;
            PacketDistributor.sendToServer(new RadioControlPacket(pos, RadioControlPacket.SHUFFLE, -1));
            if (shuffle) PacketDistributor.sendToServer(new RadioControlPacket(pos, RadioControlPacket.NEXT, -1));
            btn.setMessage(Component.literal(shuffle ? "[\uD83D\uDD00]" : "\uD83D\uDD00"));
        }).bounds(cx + 85, controlY, 30, 20).build());

        int playlistY = this.height - 110;
        playlistUrlField = new EditBox(this.font, cx - 150, playlistY, 220, 20, Component.literal(""));
        playlistUrlField.setMaxLength(512);
        playlistUrlField.setHint(Component.translatable("gui.radiofm.playlist_hint"));
        this.addRenderableWidget(playlistUrlField);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.radiofm.load_playlist"), btn -> {
            String txtUrl = playlistUrlField.getValue().trim();
            if (!txtUrl.isEmpty()) loadPlaylistFromUrl(txtUrl);
        }).bounds(cx + 75, playlistY, 75, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.radiofm.add_track"), btn -> {
            syncFieldsToList();
            trackUrls.add("");
            clearWidgets();
            init();
        }).bounds(cx - 150, this.height - 50, 140, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.radiofm.save"), btn -> save())
                .bounds(cx + 10, this.height - 50, 140, 20).build());
    }

    private void loadPlaylistFromUrl(String url) {
        new Thread(() -> {
            try {
                final String finalUrl = dev.famjam.radiofm.radio.DropboxUrls.toDirect(url);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URI(finalUrl).toURL().openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();

                java.util.List<String> loaded = new java.util.ArrayList<>();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
                        if (line.toLowerCase().startsWith("file")) {
                            int eq = line.indexOf('=');
                            if (eq >= 0) line = line.substring(eq + 1).trim();
                        }
                        if (line.startsWith("http://") || line.startsWith("https://")) {
                            loaded.add(line);
                        }
                    }
                }

                if (!loaded.isEmpty()) {
                    net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        trackUrls.clear();
                        trackUrls.addAll(loaded);
                        clearWidgets();
                        init();
                    });
                }
            } catch (Exception e) {
                String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                System.err.println("[Radio] Playlist load error: " + msg);
                net.minecraft.client.Minecraft.getInstance().execute(() ->
                    net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("Ошибка: " + msg), true
                    )
                );
            }
        }, "RadioPlaylistLoader").start();
    }

    private void syncFieldsToList() {
        if (stationNameField != null) {
            currentStationName = stationNameField.getValue();
        }
        // RU: только видимые, по реальным индексам | US: visible rows only, by their real indices
        for (int vi = 0; vi < urlFields.size(); vi++) {
            int realIdx = vi + scrollOffset;
            if (realIdx < trackUrls.size()) {
                trackUrls.set(realIdx, urlFields.get(vi).getValue());
            }
        }
    }

    /**
     * RU: пустые строки на сервер не уходят, поэтому номера расходятся
     * US: blank rows are not sent, so the indices drift apart
     */
    private int repeatIndexInSavedList() {
        if (repeatTrackIndex < 0 || repeatTrackIndex >= trackUrls.size()) return -1;
        if (trackUrls.get(repeatTrackIndex).trim().isEmpty()) return -1;

        int index = 0;
        for (int i = 0; i < repeatTrackIndex; i++) {
            if (!trackUrls.get(i).trim().isEmpty()) index++;
        }
        return index;
    }

    private void save() {
        syncFieldsToList();
        List<String> urls = trackUrls.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (!urls.isEmpty()) {
            PacketDistributor.sendToServer(new SaveRadioPacket(pos, currentStationName, urls));
            int savedRepeatIndex = repeatIndexInSavedList();
            PacketDistributor.sendToServer(new RadioControlPacket(pos,
                    savedRepeatIndex >= 0 ? RadioControlPacket.REPEAT_TRACK : RadioControlPacket.REPEAT,
                    savedRepeatIndex));
        }
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, Component.translatable("gui.radiofm.title"), this.width / 2, 12, 0xFF4444);

        int indicatorColor = switch (currentState) {
            case "ACTIVE" -> 0xFF00CC00;
            case "PAUSED" -> 0xFFFFAA00;
            default ->       0xFFCC0000;
        };
        String title = Component.translatable("gui.radiofm.title").getString();
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, "●", this.width / 2 + titleWidth / 2 + 4, 12, indicatorColor);

        graphics.drawString(this.font, Component.translatable("gui.radiofm.station_name").getString(), this.width / 2 - 150, 27, 0xCCCCCC);
        graphics.drawString(this.font, Component.translatable("gui.radiofm.tracks").getString(), this.width / 2 - 150, 60, 0xCCCCCC);

        long elapsed = ClientEventHandlers.clientElapsed;
        long duration = ClientEventHandlers.clientDuration;
        String timeStr;
        if (duration > 0) {
            timeStr = String.format("%02d:%02d / %02d:%02d", elapsed / 60, elapsed % 60, duration / 60, duration % 60);
        } else {
            timeStr = String.format("%02d:%02d", elapsed / 60, elapsed % 60);
        }
        graphics.drawCenteredString(this.font, timeStr, this.width / 2, this.height - 15, 0xAAAAAA);

        Component track = trackLine(ClientEventHandlers.clientTitle, ClientEventHandlers.clientAuthor);
        if (track != null) {
            graphics.drawCenteredString(this.font, fit(track.getString(), this.width - 20), this.width / 2, this.height - 27, 0xDDDDDD);
        }

        boolean hasDiscord = trackUrls.stream().anyMatch(u -> u.contains("cdn.discordapp.com"));
        if (hasDiscord) {
            graphics.drawCenteredString(this.font, "⚠ Discord ссылки истекают через ~24ч", this.width / 2, this.height - 39, 0xFFFFAA00);
        }

        int maxVisible = Math.max(1, (this.height - 200) / 26);
        if (trackUrls.size() > maxVisible) {
            int scrollAreaTop = 70;
            int scrollAreaHeight = maxVisible * 26;
            int scrollBarX = this.width / 2 + 160;
            int scrollBarW = 4;
            graphics.fill(scrollBarX, scrollAreaTop, scrollBarX + scrollBarW, scrollAreaTop + scrollAreaHeight, 0x44FFFFFF);

            float thumbRatio = (float) maxVisible / trackUrls.size();
            int thumbH = Math.max(10, (int)(scrollAreaHeight * thumbRatio));
            float scrollRatio = trackUrls.size() > maxVisible ? (float) scrollOffset / (trackUrls.size() - maxVisible) : 0;
            int thumbY = scrollAreaTop + (int)((scrollAreaHeight - thumbH) * scrollRatio);
            graphics.fill(scrollBarX, thumbY, scrollBarX + scrollBarW, thumbY + thumbH, 0xAAFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double scroll = scrollY != 0 ? scrollY : scrollX;
        int maxVisible = Math.max(1, (this.height - 200) / 26);
        if (trackUrls.size() > maxVisible) {
            int delta = scroll > 0 ? -1 : 1;
            int newOffset = Math.max(0, Math.min(scrollOffset + delta, trackUrls.size() - maxVisible));
            if (newOffset != scrollOffset) {
                syncFieldsToList();
                scrollOffset = newOffset;
                clearWidgets();
                init();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void moveTrack(int index, int step, int maxVisible) {
        int target = index + step;
        syncFieldsToList();
        if (target < 0 || target >= trackUrls.size()) {
            return;
        }
        java.util.Collections.swap(trackUrls, index, target);
        // RU: метка повтора едет вместе со своим треком | US: the repeat marker travels with its track
        if (repeatTrackIndex == index) {
            repeatTrackIndex = target;
        } else if (repeatTrackIndex == target) {
            repeatTrackIndex = index;
        }
        // RU: строка не должна уехать за край прокрутки | US: the row must not slide out of the scrolled view
        if (target < scrollOffset) {
            scrollOffset = target;
        } else if (target >= scrollOffset + maxVisible) {
            scrollOffset = target - maxVisible + 1;
        }
        clearWidgets();
        init();
    }

    /** RU: null - трек неизвестен, тогда строки нет | US: null when the track is unknown, then there is no line */
    public static Component trackLine(String title, String author) {
        if (title == null || title.isBlank()) {
            return null;
        }
        if (author == null || author.isBlank()) {
            return Component.translatable("gui.radiofm.track", title);
        }
        return Component.translatable("gui.radiofm.track_by", title, author);
    }

    private String fit(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("...")) + "...";
    }

    @Override
    public void removed() {
        super.removed();
        if (ownChannel) {
            ClientConfig.RADIO_VOLUME.save();
        }
    }

    private static final class VolumeSlider extends AbstractSliderButton {

        private VolumeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), ClientConfig.RADIO_VOLUME.get() / (double) ClientConfig.MAX_VOLUME);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.radiofm.volume", (int) Math.round(value * ClientConfig.MAX_VOLUME)));
        }

        @Override
        protected void applyValue() {
            ClientConfig.RADIO_VOLUME.set((int) Math.round(value * ClientConfig.MAX_VOLUME));
        }
    }
}
