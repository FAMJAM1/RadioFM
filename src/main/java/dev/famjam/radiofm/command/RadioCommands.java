package dev.famjam.radiofm.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.events.RadioEvents;
import dev.famjam.radiofm.radio.RadioBans;
import dev.famjam.radiofm.radio.RadioHeads;
import dev.famjam.radiofm.radio.RadioManager;
import dev.famjam.radiofm.radio.RadioItem;
import dev.famjam.radiofm.radio.RadioStation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/** ru: команды /radiofm | en: the /radiofm commands */
public final class RadioCommands {

    private static final int PERMISSION_LEVEL = 2;

    private RadioCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(RadioFM.MODID)
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                // ru: ветка с числом первой — строка съела бы и «64» как название
                // en: the number branch comes first — a string would swallow "64" as a name
                .then(Commands.literal("give")
                        .executes(ctx -> give(ctx, "", RadioStation.RANGE_FROM_CONFIG))
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(1F, 512F))
                                .executes(ctx -> give(ctx, "", FloatArgumentType.getFloat(ctx, "blocks"))))
                        .then(Commands.argument("station", StringArgumentType.string())
                                .executes(ctx -> give(ctx,
                                        StringArgumentType.getString(ctx, "station"),
                                        RadioStation.RANGE_FROM_CONFIG))
                                .then(Commands.argument("blocks", FloatArgumentType.floatArg(1F, 512F))
                                        .executes(ctx -> give(ctx,
                                                StringArgumentType.getString(ctx, "station"),
                                                FloatArgumentType.getFloat(ctx, "blocks"))))))
                .then(Commands.literal("ban")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> setBanned(ctx, true))))
                .then(Commands.literal("unban")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> setBanned(ctx, false))))
                .then(Commands.literal("bans")
                        .executes(RadioCommands::listBans))
                .then(Commands.literal("range")
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(1F, 512F))
                                .executes(ctx -> setHeldRange(ctx, FloatArgumentType.getFloat(ctx, "blocks")))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> setBlockRange(ctx,
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                FloatArgumentType.getFloat(ctx, "blocks")))))));
    }

    private static int give(CommandContext<CommandSourceStack> ctx, String stationName, float range)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        RadioStation station = RadioStation.create(stationName, List.of()).withRange(range);
        if (!player.getInventory().add(RadioItem.create(station))) {
            ctx.getSource().sendFailure(Component.translatable("message.radiofm.give_error"));
            return 0;
        }

        // ru: пустое название — берём переведённое | en: an empty name falls back to the translation
        Component shownName = stationName.isBlank()
                ? Component.translatable("item.radiofm.name")
                : Component.literal(stationName);

        ctx.getSource().sendSuccess(() -> range > 0
                ? Component.translatable("message.radiofm.given_range", shownName, range)
                : Component.translatable("message.radiofm.given", shownName), false);
        return 1;
    }

    private static int setHeldRange(CommandContext<CommandSourceStack> ctx, float range) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);

        Optional<RadioStation> station = RadioItem.read(held);
        if (station.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.radiofm.no_radio_hand"));
            return 0;
        }
        float capped = cap(ctx, range);
        RadioItem.write(held, station.get().withRange(capped));
        ctx.getSource().sendSuccess(
                () -> Component.translatable("message.radiofm.radius_changed", capped), false);
        return 1;
    }

    /** ru: бан отбирает радио целиком | en: a ban takes radios away entirely */
    private static int setBanned(CommandContext<CommandSourceStack> ctx, boolean banned)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        RadioBans bans = RadioBans.of(ctx.getSource().getServer());
        int affected = 0;

        for (GameProfile profile : GameProfileArgument.getGameProfiles(ctx, "player")) {
            boolean changed = banned ? bans.ban(profile.getId()) : bans.unban(profile.getId());
            if (!changed) {
                continue;
            }
            affected++;

            // ru: он мог слушать прямо сейчас | en: they may be listening right now
            if (banned) {
                RadioManager.get().stopHandRadio(profile.getId());
            }
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    banned ? "message.radiofm.ban_added" : "message.radiofm.ban_removed",
                    profile.getName()), true);
        }

        if (affected == 0) {
            ctx.getSource().sendFailure(Component.translatable("message.radiofm.ban_unchanged"));
        }
        return affected;
    }

    private static int listBans(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        var banned = RadioBans.of(server).all();

        if (banned.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("message.radiofm.ban_none"), false);
            return 0;
        }

        String names = banned.stream()
                .map(id -> server.getProfileCache() == null
                        ? id.toString()
                        : server.getProfileCache().get(id).map(GameProfile::getName).orElse(id.toString()))
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));

        ctx.getSource().sendSuccess(
                () -> Component.translatable("message.radiofm.ban_list", banned.size(), names), false);
        return banned.size();
    }

    /** ru: radiofm:maxRadius старше команды и колеса | en: radiofm:maxRadius outranks the command and the wheel */
    private static float cap(CommandContext<CommandSourceStack> ctx, float range) {
        return Math.min(range, ctx.getSource().getLevel().getGameRules().getInt(RadioFM.MAX_RADIUS));
    }

    private static int setBlockRange(CommandContext<CommandSourceStack> ctx, BlockPos pos, float range) {
        ServerLevel level = ctx.getSource().getLevel();

        Optional<RadioStation> station = RadioHeads.read(level, pos);
        if (station.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.radiofm.not_radio", pos.toShortString()));
            return 0;
        }
        // ru: дальность задаётся при создании канала | en: range is fixed when the channel opens
        float capped = cap(ctx, range);
        RadioEvents.applyBlockState(level, pos, station.get().withRange(capped),
                ctx.getSource().getPlayer() == null ? null : ctx.getSource().getPlayer().getUUID());
        ctx.getSource().sendSuccess(
                () -> Component.translatable("message.radiofm.radius_changed", capped), false);
        return 1;
    }
}
