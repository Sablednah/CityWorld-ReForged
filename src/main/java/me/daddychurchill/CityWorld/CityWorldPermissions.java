package me.daddychurchill.CityWorld;

import java.util.function.Predicate;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Permission nodes for CityWorld's commands.
 *
 * <p>Every command used to gate on {@code Commands.hasPermission(LEVEL_GAMEMASTERS)} — vanilla
 * operator level 2 — which meant a permissions manager could not grant or deny any of them. The only
 * lever a server owner had was whether somebody was an operator, and that is all-or-nothing: handing
 * a moderator {@code /cityfind} also handed them {@code /cityschem}, which writes blocks, and
 * {@code /cityexport}, which writes files.
 *
 * <p>These nodes make each command separately grantable through NeoForge's {@link PermissionAPI}, so
 * LuckPerms, SableCraft Standards' own {@code /rank} handler, or anything else on that API can hand
 * out world-jumping and finding without handing out world-editing.
 *
 * <p><b>Nothing changes for a server with no permissions manager installed.</b> Each node's default
 * resolver returns exactly what the old op-level check returned, so out of the box the behaviour is
 * identical — this is a new lever, not a new policy.
 *
 * <p>See {@code NODES.md} for the player-facing table.
 */
public final class CityWorldPermissions {

    private CityWorldPermissions() {}

    /**
     * Read the plan under your feet ({@code /cityinfo}). <b>Default: everyone.</b>
     *
     * <p>It reads, it moves nobody and it writes nothing, so it was ungated before nodes existed and
     * stays ungated now. A server that would rather keep the world's workings quiet can revoke it.
     */
    public static final PermissionNode<Boolean> INFO = new PermissionNode<>(
            CityWorldMod.MODID, "info", PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> Boolean.TRUE);

    /**
     * Jump in and out of the CityWorld dimension ({@code /cityworld}). <b>Default: operators.</b>
     *
     * <p>Kept to ops by default because crossing dimensions on demand is a movement power, not an
     * information one — the owner's example of the node most likely to stay op-only.
     */
    public static final PermissionNode<Boolean> TELEPORT = new PermissionNode<>(
            CityWorldMod.MODID, "teleport", PermissionTypes.BOOLEAN, CityWorldPermissions::opByDefault);

    /**
     * Search the world ({@code /cityfind}, {@code /cwlocate}). <b>Default: operators.</b>
     *
     * <p>The node most worth delegating: finding a hospital or a biome is a guide's tool rather than
     * an administrator's, and this is the one a server is likely to grant to moderators or to
     * everybody. Note the {@code tp} forms of both commands ride on it, so granting it grants
     * teleporting <em>to what was found</em> — deliberately, since a search you cannot travel to is
     * of little use, but worth knowing before handing it out.
     */
    public static final PermissionNode<Boolean> FIND = new PermissionNode<>(
            CityWorldMod.MODID, "find", PermissionTypes.BOOLEAN, CityWorldPermissions::opByDefault);

    /**
     * List and paste schematics ({@code /cityschem}). <b>Default: operators.</b>
     *
     * <p>Pasting writes blocks into the world wherever the player stands. This is a building tool and
     * should be treated like one.
     */
    public static final PermissionNode<Boolean> SCHEMATIC = new PermissionNode<>(
            CityWorldMod.MODID, "schematic", PermissionTypes.BOOLEAN, CityWorldPermissions::opByDefault);

    /**
     * Export the world's settings as a datapack ({@code /cityexport}). <b>Default: operators.</b>
     *
     * <p>Writes a file to the server's disk, which is a different kind of permission from anything
     * else here.
     */
    public static final PermissionNode<Boolean> EXPORT = new PermissionNode<>(
            CityWorldMod.MODID, "export", PermissionTypes.BOOLEAN, CityWorldPermissions::opByDefault);

    /**
     * The old gate, as a default resolver: exactly what {@code LEVEL_GAMEMASTERS} used to answer.
     *
     * <p>1.21.11 turned {@code Commands.LEVEL_GAMEMASTERS} into a {@code PermissionCheck} rather than
     * an {@code int} level, and both {@code ServerPlayer} and {@code CommandSourceStack} supply a
     * {@code PermissionSet} — so the check is asked of the permission set directly instead of
     * comparing op numbers.
     */
    private static Boolean opByDefault(ServerPlayer player, java.util.UUID playerUUID, Object... context) {
        return player != null && Commands.LEVEL_GAMEMASTERS.check(player.permissions());
    }

    /** Registered from {@link CityWorldServerEvents} on {@link PermissionGatherEvent.Nodes}. */
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(INFO, TELEPORT, FIND, SCHEMATIC, EXPORT);
    }

    /**
     * A Brigadier {@code requires} predicate for a node.
     *
     * <p><b>Non-player sources fall back to the op-level check, and must.</b> {@link PermissionAPI}
     * answers about a {@link ServerPlayer}; the console, a command block and a function have no
     * player to ask about. Without this fallback the console would silently lose every CityWorld
     * command the moment nodes were introduced — a console that cannot run its own admin commands is
     * a worse bug than the one being fixed.
     */
    public static Predicate<CommandSourceStack> check(PermissionNode<Boolean> node) {
        return source -> {
            ServerPlayer player = source.getPlayer();
            if (player == null)
                return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
            return PermissionAPI.getPermission(player, node);
        };
    }
}
