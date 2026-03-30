package me.tg.amongcraft;

import com.google.gson.JsonElement;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.core.jmx.Server;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static me.tg.amongcraft.AmongMapManager.getAllMatchingSpawns;
import static me.tg.amongcraft.Amongcraft.*;
import static me.tg.amongcraft.TaskProgressTracker.currentMap;

public class MeetingManager {

    public enum MeetingPhase {
        START, DISCUSSION, VOTING, REVEAL
    }

    private static MeetingPhase phase = MeetingPhase.START;
    private static int ticksRemaining = 0;

    private static final Map<UUID, UUID> currentVotes = new HashMap<>();
    private static boolean votingActive = false;

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            if (world.getBlockState(pos).getBlock() == EMERGENCY_BUTTON) {
                ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

                if (!canCallMeeting(serverPlayer)) {
                    return ActionResult.SUCCESS;
                }

                if (!GameState.isRunning()) {
                    player.sendMessage(Text.literal("The game hasn't started yet, but starting meeting for testing"), false);
                    // return;
                }

                callMeeting(serverPlayer, false);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    public static void tick(MinecraftServer server) {
        if (!votingActive) return;

        if (ticksRemaining-- <= 0) {
            switch (phase) {
                case DISCUSSION -> {
                    LOGGER.info("phase: " + phase);
                    phase = MeetingPhase.VOTING;
                    if (SettingsManager.get("meeting-voting") == null) {
                        ticksRemaining = SettingsManager.getDefault("meeting-voting").getAsInt() * 20;
                    } else {
                        ticksRemaining = SettingsManager.get("meeting-voting").getAsInt() * 20;
                    }
                }
                case VOTING -> {
                    LOGGER.info("phase: " + phase);
                    phase = MeetingPhase.REVEAL;
                    tallyVotes(server, null);
                    ticksRemaining = 100; // 5 seconds to show results
                }
            }
        }
    }

    private static void endMeeting(ServerPlayerEntity voter) {
        votingActive = false;
        currentVotes.clear();
        MinecraftServer server = voter.getServer();
        ServerWorld world = voter.getServerWorld();

        List<ServerPlayerEntity> players = voter.getServer().getPlayerManager().getPlayerList();
        List<BlockPos> spawnPoints = getAllMatchingSpawns(world, AmongMapManager.MAP_SPAWN_BLOCK, currentMap);

        Collections.shuffle(spawnPoints);

        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity player = players.get(i);
            BlockPos pos = spawnPoints.get(i % spawnPoints.size());
            player.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), player.getPitch());
        }

        Collections.shuffle(players);
        Collections.shuffle(spawnPoints);

        DeathListener.clearBodies(world);
        if (Objects.equals(SettingsManager.get("task-updates").getAsString(), "meetings")) {
            TaskProgressTracker.updateXpBars();
        }
        for (UUID uuid : AmongCraftCommands.impostors) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.WEAKNESS,
                        SettingsManager.get("kill-cooldown").getAsInt() * 20,
                        255,
                        false,
                        false
                ));
            }
        }
        broadcastMessage(server, Text.literal("Meeting ended. Resuming game."));
    }


    public static void resetMeetingData() {
        meetingsCalled.clear();
        lastMeetingTime = 0;
    }

    public static boolean canCallMeeting(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        ServerWorld world = (ServerWorld) player.getWorld();
        DeathListener.clearBodies(world);

        // Cooldown
        JsonElement cooldownElement = SettingsManager.get("meeting-cooldown");
        if (cooldownElement == null) {
            LOGGER.info("Failed to get meeting-cooldown, getting default value.");
            cooldownElement = SettingsManager.getDefault("meeting-cooldown");
        }
        int cooldown = cooldownElement.getAsInt();

        long elapsed = (System.currentTimeMillis() - lastMeetingTime) / 1000;
        if (elapsed < cooldown) {
            player.sendMessage(Text.literal("Wait " + (cooldown - elapsed) + "s before calling a meeting."), false);
            return false;
        }

        // Limit per player
        JsonElement maxCallsElement = SettingsManager.get("meeting-per-player");
        if (maxCallsElement == null) {
            LOGGER.info("Failed to get meeting-per-player, getting default value.");
            maxCallsElement = SettingsManager.getDefault("meeting-per-player");
        }
        int maxCalls = maxCallsElement.getAsInt();

        if (maxCalls != -1 && meetingsCalled.getOrDefault(uuid, 0) >= maxCalls) {
            player.sendMessage(Text.literal("You’ve used all your meetings."), false);
            return false;
        }

        return true;
    }

    public static void callMeeting(ServerPlayerEntity caller, Boolean isBodyReported) {
        LOGGER.info("Meeting Started");
        votingActive = true;
        UUID uuid = caller.getUuid();
        if (!isBodyReported) {
        meetingsCalled.put(uuid, meetingsCalled.getOrDefault(uuid, 0) + 1);
        }
        lastMeetingTime = System.currentTimeMillis();
        beginMeeting(caller.getServer(), caller);
    }

    private static void beginMeeting(MinecraftServer server, ServerPlayerEntity caller) {
        LOGGER.info("beginning meeting");

        Set<UUID> aliveUUIDs = new HashSet<>();
        aliveUUIDs.addAll(AmongCraftCommands.impostors);
        aliveUUIDs.addAll(AmongCraftCommands.crewmates);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeUuid(caller.getUuid());

            buf.writeInt(aliveUUIDs.size());
            for (UUID uuid : aliveUUIDs) {
                buf.writeUuid(uuid);
            }

            LOGGER.info("sending MEETING_PACKET to " + player.getEntityName() + " and the buffer is: " + buf + " with size: " + buf.readableBytes());
            ServerPlayNetworking.send(player, MEETING_PACKET, buf);
        }
    }
    public static void handleVote(ServerPlayerEntity voter, @Nullable UUID votedFor) {
        Amongcraft.LOGGER.info(voter + " voted for " + votedFor);
        if (!votingActive) {
            LOGGER.info("Voting is not active");
            return;
        } // ignore if voting not active

        UUID voterUUID = voter.getUuid();

        // Don't allow multiple votes
        if (currentVotes.containsKey(voterUUID)) {
            // Optionally send message to voter that they've already voted
            voter.sendMessage(Text.literal("You have already voted!"), false);
            return;
        }

        // Record vote (votedFor == null means skip)
        currentVotes.put(voterUUID, votedFor);

        // Check if all players have voted or timer expired (simple example)
        if (allVotesIn(voter.getServer())) {
            tallyVotes(voter.getServer(), voter);
        }
    }
    private static boolean allVotesIn(MinecraftServer server) {
        Set<UUID> aliveUUIDs = new HashSet<>();
        aliveUUIDs.addAll(AmongCraftCommands.impostors);
        aliveUUIDs.addAll(AmongCraftCommands.crewmates);
        LOGGER.info("All alive players:" + aliveUUIDs);
        for (UUID uuid : aliveUUIDs) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;

            if (!currentVotes.containsKey(uuid)) {
                LOGGER.info("not all votes in");
                return false;
            }
        }
        LOGGER.info("all votes in");
        return true;
    }
    private static void tallyVotes(MinecraftServer server, ServerPlayerEntity voter) {
        votingActive = false;

        Map<UUID, Integer> voteCounts = new HashMap<>();
        int skipVotes = 0;

        for (UUID votedFor : currentVotes.values()) {
            if (votedFor == null) {
                skipVotes++;
                continue;
            }
            LOGGER.info("registering vote for: " + votedFor);
            voteCounts.put(votedFor, voteCounts.getOrDefault(votedFor, 0) + 1);
        }

        // Find max votes
        int maxVotes = 0;
        UUID eliminated = null;

        for (Map.Entry<UUID, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                eliminated = entry.getKey();
            } else if (entry.getValue() == maxVotes) {
                // Tie - eliminate nobody or handle tie logic
                eliminated = null;
            }
        }

        if (eliminated == null) {
            // No elimination (tie or skip majority)
            LOGGER.info("tie");
            broadcastMessage(server, Text.literal("No one was eliminated."));
        } else {
            // Eliminate player
            LOGGER.info("elminating:" + eliminated);
            eliminatePlayer(voter.getServer(), eliminated);
        }
        currentVotes.clear();

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeInt(currentVotes.size());
        for (Map.Entry<UUID, UUID> entry : currentVotes.entrySet()) {
            buf.writeUuid(entry.getKey()); // voter
            buf.writeBoolean(entry.getValue() != null); // votedFor is present
            if (entry.getValue() != null) {
                buf.writeUuid(entry.getValue()); // voted for whom
            }
        }

        buf.writeBoolean(eliminated != null);
        if (eliminated != null) {
            buf.writeUuid(eliminated);
            buf.writeBoolean(AmongCraftCommands.impostors.contains(eliminated));
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, Amongcraft.VOTE_END_PACKET, buf);
        }
        AmongCraftCommands.resetSpectatorTabList(server);
        endMeeting(voter);
    }
    private static void eliminatePlayer(MinecraftServer server, UUID playerUUID) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUUID);
        if (player == null) return;

        // Check for Jester role
        if (AmongCraftCommands.getPlayerRole(playerUUID).equals("Jester")) {
            broadcastMessage(server, Text.literal("§d" + player.getEntityName() + " was voted out... and they were the §lJESTER§r§d!"));
            broadcastMessage(server, Text.literal("§l§6The Jester wins!"));

            // Kill everyone to end the game
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                AmongCraftCommands.setPlayerDead(p);
            }
            return; // Do not continue with regular elimination logic
        }

        // Notify everyone of role
        if (AmongCraftCommands.impostors.contains(player.getUuid())) {
            if (AmongCraftCommands.impostors.size() == 1) {
                broadcastMessage(server, Text.literal(player.getEntityName() + " was voted out, and they were the impostor!"));
            } else {
                broadcastMessage(server, Text.literal(player.getEntityName() + " was voted out, and they were an impostor!"));
            }
        } else {
            broadcastMessage(server, Text.literal(player.getEntityName() + " was voted out, and they were a crewmate!"));
        }

        AmongCraftCommands.setPlayerDead(player);
    }

    private static void broadcastMessage(MinecraftServer server, Text message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(message, false);
        }
    }
}
