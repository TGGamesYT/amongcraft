package me.tg.amongcraft;

import com.google.gson.JsonElement;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static me.tg.amongcraft.AmongMapManager.getAllMatchingSpawns;
import static me.tg.amongcraft.Amongcraft.*;
import static me.tg.amongcraft.TaskProgressTracker.currentMap;

/**
 * Server-authoritative meeting / voting controller.
 *
 * The whole meeting lifecycle (discussion -> voting -> ejection) is driven by
 * {@link #tick(MinecraftServer)}, which runs every server tick. Clients only
 * render what the server tells them via packets, so the meeting can no longer
 * desync or get stuck.
 */
public class MeetingManager {

    public enum Phase { DISCUSSION, VOTING, EJECTION }

    private static boolean active = false;
    private static Phase phase = Phase.DISCUSSION;
    private static int ticksRemaining = 0;

    /** voter -> target. A {@code null} value means the voter skipped. */
    private static final Map<UUID, UUID> votes = new HashMap<>();
    /** Players that were alive when the meeting started (the only valid voters). */
    private static final Set<UUID> alive = new HashSet<>();
    private static UUID caller = null;

    @Nullable
    private static UUID pendingElimination = null;

    /** How long the ejection animation is shown before the game resumes. */
    private static final int EJECTION_TICKS = 15 * 20;

    public static boolean isActive() {
        return active;
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            if (world.getBlockState(pos).getBlock() != EMERGENCY_BUTTON) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            if (active) return ActionResult.SUCCESS;
            if (!canCallMeeting(serverPlayer)) return ActionResult.SUCCESS;

            callMeeting(serverPlayer, false);
            return ActionResult.SUCCESS;
        });
    }

    public static boolean canCallMeeting(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        int cooldown = settingInt("meeting-cooldown", 20);
        long elapsed = (System.currentTimeMillis() - lastMeetingTime) / 1000;
        if (lastMeetingTime != 0 && elapsed < cooldown) {
            player.sendMessage(Text.literal("Wait " + (cooldown - elapsed) + "s before calling a meeting."), false);
            return false;
        }

        int maxCalls = settingInt("meeting-per-player", -1);
        if (maxCalls != -1 && meetingsCalled.getOrDefault(uuid, 0) >= maxCalls) {
            player.sendMessage(Text.literal("You've used all your meetings."), false);
            return false;
        }

        return true;
    }

    public static void callMeeting(ServerPlayerEntity callerPlayer, boolean isBodyReport) {
        if (active) return;
        MinecraftServer server = callerPlayer.getServer();
        if (server == null) return;

        active = true;
        phase = Phase.DISCUSSION;
        caller = callerPlayer.getUuid();
        votes.clear();
        pendingElimination = null;
        lastMeetingTime = System.currentTimeMillis();
        if (!isBodyReport) {
            meetingsCalled.merge(caller, 1, Integer::sum);
        }

        alive.clear();
        alive.addAll(AmongCraftCommands.impostors);
        alive.addAll(AmongCraftCommands.crewmates);

        DeathListener.clearBodies(callerPlayer.getServerWorld());

        ticksRemaining = settingInt("meeting-discussion", 15) * 20;

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(caller);
            buf.writeInt(alive.size());
            for (UUID u : alive) {
                buf.writeUuid(u);
            }
            ServerPlayNetworking.send(p, MEETING_PACKET, buf);
        }
        broadcastPhase(server);
        LOGGER.info("Meeting started by " + callerPlayer.getEntityName() + " (bodyReport=" + isBodyReport + ")");
    }

    public static void tick(MinecraftServer server) {
        if (!active) return;

        ticksRemaining--;

        // Keep clients' timers in sync while the meeting is interactive.
        if (phase != Phase.EJECTION && ticksRemaining > 0 && ticksRemaining % 20 == 0) {
            broadcastPhase(server);
        }

        if (ticksRemaining <= 0) {
            switch (phase) {
                case DISCUSSION -> startVoting(server);
                case VOTING -> tallyVotes(server);
                case EJECTION -> endMeeting(server);
            }
        }
    }

    private static void startVoting(MinecraftServer server) {
        phase = Phase.VOTING;
        ticksRemaining = settingInt("meeting-voting", 30) * 20;
        broadcastPhase(server);
    }

    public static void handleVote(ServerPlayerEntity voter, @Nullable UUID votedFor) {
        if (!active || phase != Phase.VOTING) return;

        UUID voterUUID = voter.getUuid();
        if (!alive.contains(voterUUID)) {
            return; // dead players (and spectators) cannot vote
        }
        if (votes.containsKey(voterUUID)) {
            voter.sendMessage(Text.literal("You have already voted!"), false);
            return;
        }

        votes.put(voterUUID, votedFor);
        LOGGER.info(voter.getEntityName() + " voted for " + votedFor);

        if (allVotesIn(voter.getServer())) {
            tallyVotes(voter.getServer());
        }
    }

    private static boolean allVotesIn(MinecraftServer server) {
        for (UUID uuid : alive) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue; // offline players never block the meeting
            if (!votes.containsKey(uuid)) return false;
        }
        return true;
    }

    private static void tallyVotes(MinecraftServer server) {
        Map<UUID, Integer> counts = new HashMap<>();
        int skips = 0;
        for (UUID target : votes.values()) {
            if (target == null) {
                skips++;
            } else {
                counts.merge(target, 1, Integer::sum);
            }
        }

        // The ejected player must have strictly more votes than everyone else
        // AND more votes than "skip". A tie (between players or with skip) ejects no one.
        int topVotes = 0;
        UUID topPlayer = null;
        int topCount = 0;
        for (Map.Entry<UUID, Integer> e : counts.entrySet()) {
            if (e.getValue() > topVotes) {
                topVotes = e.getValue();
                topPlayer = e.getKey();
                topCount = 1;
            } else if (e.getValue() == topVotes) {
                topCount++;
            }
        }

        UUID eliminated = (topPlayer != null && topCount == 1 && topVotes > skips) ? topPlayer : null;
        boolean wasImpostor = eliminated != null && AmongCraftCommands.impostors.contains(eliminated);
        pendingElimination = eliminated;

        // Send the full vote breakdown to every client so the ejection animation
        // can show who voted for whom. A fresh buffer is required per recipient.
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeInt(votes.size());
            for (Map.Entry<UUID, UUID> e : votes.entrySet()) {
                buf.writeUuid(e.getKey());
                buf.writeBoolean(e.getValue() != null);
                if (e.getValue() != null) {
                    buf.writeUuid(e.getValue());
                }
            }
            buf.writeBoolean(eliminated != null);
            if (eliminated != null) {
                buf.writeUuid(eliminated);
                buf.writeBoolean(wasImpostor);
            }
            ServerPlayNetworking.send(p, VOTE_END_PACKET, buf);
        }

        phase = Phase.EJECTION;
        ticksRemaining = EJECTION_TICKS;
        votes.clear();
        LOGGER.info("Vote tallied. Ejected: " + eliminated);
    }

    private static void endMeeting(MinecraftServer server) {
        active = false;
        phase = Phase.DISCUSSION;

        // Apply the elimination only after the ejection animation has played.
        if (pendingElimination != null) {
            eliminatePlayer(server, pendingElimination);
        } else {
            broadcastMessage(server, Text.literal("No one was ejected."));
        }
        pendingElimination = null;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (!players.isEmpty()) {
            ServerWorld world = players.get(0).getServerWorld();
            List<BlockPos> spawnPoints = getAllMatchingSpawns(world, AmongMapManager.MAP_SPAWN_BLOCK, currentMap);
            if (!spawnPoints.isEmpty()) {
                Collections.shuffle(spawnPoints);
                for (int i = 0; i < players.size(); i++) {
                    ServerPlayerEntity player = players.get(i);
                    BlockPos pos = spawnPoints.get(i % spawnPoints.size());
                    player.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                            player.getYaw(), player.getPitch());
                }
            }
            DeathListener.clearBodies(world);
        }

        JsonElement taskUpdates = SettingsManager.get("task-updates");
        if (taskUpdates != null && "meetings".equals(taskUpdates.getAsString())) {
            TaskProgressTracker.updateXpBars();
        }

        int killCooldown = settingInt("kill-cooldown", 30);
        for (UUID uuid : AmongCraftCommands.impostors) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.WEAKNESS, killCooldown * 20, 255, false, false));
            }
        }

        AmongCraftCommands.resetSpectatorTabList(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, END_MEETING_PACKET, new PacketByteBuf(Unpooled.buffer()));
        }
        broadcastMessage(server, Text.literal("Meeting ended. Resuming game."));
    }

    private static void eliminatePlayer(MinecraftServer server, UUID playerUUID) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUUID);
        if (player == null) return;

        if ("Jester".equals(AmongCraftCommands.getPlayerRole(playerUUID))) {
            broadcastMessage(server, Text.literal("§d" + player.getEntityName()
                    + " was voted out... and they were the §lJESTER§r§d!"));
            broadcastMessage(server, Text.literal("§l§6The Jester wins!"));
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                AmongCraftCommands.setPlayerDead(p);
            }
            return;
        }

        if (AmongCraftCommands.impostors.contains(player.getUuid())) {
            String article = AmongCraftCommands.impostors.size() == 1 ? "the" : "an";
            broadcastMessage(server, Text.literal(player.getEntityName()
                    + " was voted out, and they were " + article + " impostor!"));
        } else {
            broadcastMessage(server, Text.literal(player.getEntityName()
                    + " was voted out, and they were a crewmate!"));
        }

        AmongCraftCommands.setPlayerDead(player);
    }

    public static void resetMeetingData() {
        active = false;
        phase = Phase.DISCUSSION;
        ticksRemaining = 0;
        votes.clear();
        alive.clear();
        caller = null;
        pendingElimination = null;
        meetingsCalled.clear();
        lastMeetingTime = 0;
    }

    private static void broadcastPhase(MinecraftServer server) {
        int secondsLeft = Math.max(0, ticksRemaining / 20);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeString(phase.name());
            buf.writeInt(secondsLeft);
            ServerPlayNetworking.send(player, MEETING_PHASE_PACKET, buf);
        }
    }

    private static void broadcastMessage(MinecraftServer server, Text message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(message, false);
        }
    }

    private static int settingInt(String key, int fallback) {
        JsonElement element = SettingsManager.get(key);
        if (element == null) {
            element = SettingsManager.getDefault(key);
        }
        return element != null ? element.getAsInt() : fallback;
    }
}
