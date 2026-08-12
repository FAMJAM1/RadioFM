package dev.famjam.radiofm.radio;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ru: кому запрещено радио; в сохранении мира, а не в конфиге — едет вместе с миром
 * en: who may not use radios; in the world save, not the config, so it travels with it
 */
public class RadioBans extends SavedData {

    private static final String FILE = "radiofm_bans";
    private static final String KEY = "banned";

    private final Set<UUID> banned = new LinkedHashSet<>();

    public static RadioBans of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RadioBans::new, RadioBans::load), FILE);
    }

    private static RadioBans load(CompoundTag tag, HolderLookup.Provider registries) {
        RadioBans bans = new RadioBans();
        ListTag list = tag.getList(KEY, Tag.TAG_INT_ARRAY);
        for (Tag entry : list) {
            bans.banned.add(UUIDUtil.CODEC.parse(NbtOps.INSTANCE, entry).result().orElse(null));
        }
        bans.banned.remove(null);
        return bans;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (UUID id : banned) {
            UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, id).result().ifPresent(list::add);
        }
        tag.put(KEY, list);
        return tag;
    }

    public boolean isBanned(UUID playerId) {
        return banned.contains(playerId);
    }

    /** @return ru: false — уже был забанен | en: false when already banned */
    public boolean ban(UUID playerId) {
        if (!banned.add(playerId)) {
            return false;
        }
        setDirty();
        return true;
    }

    /** @return ru: false — и не был забанен | en: false when not banned anyway */
    public boolean unban(UUID playerId) {
        if (!banned.remove(playerId)) {
            return false;
        }
        setDirty();
        return true;
    }

    public Set<UUID> all() {
        return Set.copyOf(banned);
    }

    /** ru: единая точка отказа, сама уведомляет | en: the single refusal point, tells the player itself */
    public static boolean denied(net.minecraft.server.level.ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !of(server).isBanned(player.getUUID())) {
            return false;
        }
        dev.famjam.radiofm.Notify.refused(player, "message.radiofm.banned");
        return true;
    }
}
