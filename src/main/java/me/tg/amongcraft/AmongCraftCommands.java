package me.tg.amongcraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.network.PacketByteBuf;
import static me.tg.amongcraft.AmongCraftPackets.OPEN_SCREEN;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import io.netty.buffer.Unpooled;
import com.google.gson.Gson;

import static net.minecraft.command.argument.EntityArgumentType.getPlayers;
import static net.minecraft.command.argument.EntityArgumentType.players;
import static net.minecraft.command.argument.IdentifierArgumentType.getIdentifier;
import static net.minecraft.command.argument.IdentifierArgumentType.identifier;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class AmongCraftCommands {

    public static final Set<UUID> impostors = new HashSet<>();
    public static final Set<UUID> crewmates = new HashSet<>();
    public static final Set<UUID> dead = new HashSet<>();
    private static final Map<UUID, String> playerRoles = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void setPlayerRole(UUID uuid, String roleName) {
        playerRoles.put(uuid, roleName);
    }

    public static String getPlayerRole(UUID uuid) {
        return playerRoles.getOrDefault(uuid, "Unknown");
    }

    public static void setPlayerImpostor(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        if (impostors.contains(uuid)) {
            removeItem(player, "amongcraft:killer_knife");
            removeItem(player, "amongcraft:switch_went");
            removeItem(player, "amongcraft:sabotage");
        }
        if (crewmates.contains(uuid)) {
            removeItem(player, "amongcraft:use");
            player.removeStatusEffect(StatusEffects.WEAKNESS);
        }
        removeItem(player, "amongcraft:report");

        impostors.add(uuid);
        crewmates.remove(uuid);
        dead.remove(uuid);

        giveItem(player, "amongcraft:killer_knife");
        giveItem(player, "amongcraft:switch_went");
        giveItem(player, "amongcraft:sabotage");
        giveItem(player, "amongcraft:report");

        player.sendMessage(Text.literal("§cYou are now an Impostor."), false);
    }

    public static void setPlayerCrewmate(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        if (impostors.contains(uuid)) {
            removeItem(player, "amongcraft:killer_knife");
            removeItem(player, "amongcraft:switch_went");
            removeItem(player, "amongcraft:sabotage");
        }
        if (crewmates.contains(uuid)) {
            removeItem(player, "amongcraft:use");
        }
        removeItem(player, "amongcraft:report");

        impostors.remove(uuid);
        crewmates.add(uuid);
        dead.remove(uuid);

        giveItem(player, "amongcraft:report");
        giveItem(player, "amongcraft:use");

        player.sendMessage(Text.literal("§aYou are now a Crewmate."), false);
    }

    public static void setPlayerDead(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        if (impostors.contains(uuid)) {
            removeItem(player, "amongcraft:killer_knife");
            removeItem(player, "amongcraft:switch_went");
            removeItem(player, "amongcraft:sabotage");
        }
        if (crewmates.contains(uuid)) {
            removeItem(player, "amongcraft:use");
            player.removeStatusEffect(StatusEffects.WEAKNESS);
        }
        removeItem(player, "amongcraft:report");

        impostors.remove(uuid);
        crewmates.remove(uuid);
        dead.add(uuid);

        player.changeGameMode(GameMode.SPECTATOR);

        player.sendMessage(Text.literal("§aYou are now Dead."), false);

        PlayerListS2CPacket.Entry entry = new PlayerListS2CPacket.Entry(
                player.getUuid(),
                player.getGameProfile(),
                true,
                player.pingMilliseconds,
                GameMode.SURVIVAL,
                player.getPlayerListName(),
                null
        );

        PlayerListS2CPacket fakeModePacket = new PlayerListS2CPacket(
                PlayerListS2CPacket.Action.UPDATE_GAME_MODE,
                player
        );

        // Send fake tab packet to all players
        for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
            target.networkHandler.sendPacket(fakeModePacket);
        }

    }

    public static void resetSpectatorTabList(MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam("amongcraft_dead");

        if (team != null) {
            for (String name : team.getPlayerList()) {
                scoreboard.removePlayerFromTeam(name, team);
            }
            scoreboard.removeTeam(team);
        }
    }

    public static final Map<String, String> ROLE_DESCRIPTIONS = Map.ofEntries(
            Map.entry("engineer", "A crewmate who can use vents to quickly move around. Use this power to surprise impostors or escape danger."),
            Map.entry("angel", "A protective ghost that can shield living players from death after dying. Time your shield wisely."),
            Map.entry("noisemaker", "When killed, your body emits a loud noise, alerting nearby players to your location."),
            Map.entry("scientist", "Has access to a tablet to check player vitals. Monitor player status and catch impostors red-handed."),
            Map.entry("sheriff", "A crewmate with a weapon. Can kill impostors — but if you shoot a crewmate, you die aswell."),
            Map.entry("jester", "Your goal is to get voted out. Act suspicious, mislead others, and if you're ejected — you win."),
            Map.entry("shapeshifter", "An impostor who can transform into other players. Use this ability to sow chaos and confusion."),
            Map.entry("phantom", "An impostor with the ability to turn invisible using a special item. Use stealth to escape, sneak up, or hide after sabotage.")
    );

    private static final List<String> TASKS = List.of(
            "default", "align_engine_output", "calibrate", "chart_course", "chute", "clean_vent", "clear_asteroids",
            "divert_power_1", "divert_power_2", "download", "fuelcan", "inspect_sample", "o2_filter", "scan", "shields",
            "stabilize_steering", "start_reactor", "swipe_card", "task1", "task2", "unlock_manifolds", "upload", "wiring", "fuelengine"
    );


    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("amongcraft")
                        .then(literal("debug")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(literal("list")
                                        .executes(ctx -> {
                                            ctx.getSource().sendFeedback(() -> Text.literal("§e--- AmongCraft Roles ---"), false);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§cImpostors: §r" + listNames(ctx.getSource(), impostors)), false);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§aCrewmates: §r" + listNames(ctx.getSource(), crewmates)), false);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§7Dead: §r" + listNames(ctx.getSource(), dead)), false);
                                            return 1;
                                        }))
                                .then(literal("impostor")
                                        .then(argument("player", EntityArgumentType.player())
                                                .executes(ctx -> {
                                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                                    setPlayerImpostor(player);
                                                    ctx.getSource().sendFeedback(() -> Text.literal("§c" + player.getName().getString() + " is now an Impostor."), false);
                                                    return 1;
                                                })))
                                .then(literal("crewmate")
                                        .then(argument("player", EntityArgumentType.player())
                                                .executes(ctx -> {
                                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                                    setPlayerCrewmate(player);
                                                    ctx.getSource().sendFeedback(() -> Text.literal("§a" + player.getName().getString() + " is now a Crewmate."), false);
                                                    return 1;
                                                })))
                                .then(literal("dead")
                                        .then(argument("player", EntityArgumentType.player())
                                                .executes(ctx -> {
                                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                                    setPlayerDead(player);
                                                    ctx.getSource().sendFeedback(() -> Text.literal("§7" + player.getName().getString() + " is now Dead."), false);
                                                    return 1;
                                                })))
                                .then(literal("reset")
                                        .executes(ctx -> {
                                            MeetingManager.resetMeetingData();
                                            DeathListener.clearBodies(ctx.getSource().getWorld());
                                            AmongCraftCommands.resetSpectatorTabList(ctx.getSource().getServer());
                                            TaskProgressTracker.resetProgress();
                                            return 1;
                                        }))
                                .then(literal("setrole")
                                .then(argument("player", EntityArgumentType.player())
                                        .then(argument("role", StringArgumentType.string())
                                                .executes(ctx -> runSetRole(
                                                        ctx,
                                                        EntityArgumentType.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "role")
                                                )))))
                                .then(literal("toggledoor")
                                .then(argument("id", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");

                                            List<String> doors = AmongcraftDoorBlock.getAllDoorIds();
                                            if (doors.contains(id)) {
                                                AmongcraftDoorBlock.toggleDoor(id);
                                                ctx.getSource().sendFeedback(() -> Text.literal("§aToggled Door:" + id), false);
                                                return 1;
                                            } else {
                                                ctx.getSource().sendFeedback(() -> Text.literal("§cDid not find Door with that id"), false);
                                                return 0;
                                            }
                                        })
                                ))
                                .then(literal("givetasks")
                                        .then(argument("player", EntityArgumentType.player())
                                                .executes(ctx -> {
                                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");

                                                    for (String task : TASKS) {
                                                        ItemStack stack = new ItemStack(Registries.BLOCK.get(new Identifier("amongcraft", "task_button")));
                                                        NbtCompound blockStateTag = new NbtCompound();
                                                        blockStateTag.putString("task", task);
                                                        blockStateTag.putString("map", "test");

                                                        NbtCompound tag = stack.getOrCreateNbt();
                                                        tag.put("BlockStateTag", blockStateTag);

                                                        NbtCompound display = new NbtCompound();
                                                        display.put("Name", NbtString.of(Text.Serializer.toJson(Text.literal(task).formatted(Formatting.YELLOW))));
                                                        tag.put("display", display);

                                                        stack.setCount(1);
                                                        player.giveItemStack(stack);
                                                    }

                                                    return 1;
                                                })
                                        )
                                        )
                                .then(literal("startcutscene")
                                        .executes(ctx -> {
                                            try {
                                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                            MinecraftServer server = player.getServer();
                                            UUID playerUUID = player.getUuid();

                                            boolean isImpostor = AmongCraftCommands.impostors.contains(playerUUID);
                                            String role = AmongCraftCommands.getPlayerRole(playerUUID);

                                            // 0 ticks: start cutscene
                                            TickDelayedExecutor.schedule(server, 0, () -> {
                                                try {
                                                    AmongMapManager.sendCutsceneToPlayer(
                                                            player,
                                                            new Identifier("amongcraft", "start.png"),
                                                            new Identifier("amongcraft", "sus.start"),
                                                            true,
                                                            Collections.emptySet(),
                                                            false
                                                    );
                                                    System.out.println("[cutscenetest] Start cutscene sent");
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                    System.out.println("[cutscenetest] Error during start cutscene: " + e.getMessage());
                                                }
                                            });

                                            TickDelayedExecutor.schedule(server, 120, () -> {
                                                try {
                                                    Identifier baseBg = isImpostor ? new Identifier("amongcraft", "impostor.png") : new Identifier("amongcraft", "crewmate.png");
                                                    Identifier baseSound = isImpostor ? new Identifier("amongcraft", "sus.role_impostor") : new Identifier("amongcraft", "sus.role_crew");

                                                    AmongMapManager.sendCutsceneToPlayer(
                                                            player,
                                                            baseBg,
                                                            baseSound,
                                                            true,
                                                            Set.of(playerUUID),
                                                            isImpostor
                                                    );
                                                    System.out.println("[cutscenetest] Base role cutscene sent");
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                    System.out.println("[cutscenetest] Error during base role cutscene: " + e.getMessage());
                                                }
                                            });

                                            if (role != null && !role.isEmpty()) {
                                                TickDelayedExecutor.schedule(server, 240, () -> {
                                                    try {
                                                        Identifier roleBg = new Identifier("amongcraft", role.toLowerCase() + ".png");
                                                        Identifier roleSound = new Identifier("amongcraft", "sus.role_" + role.toLowerCase());

                                                        AmongMapManager.sendCutsceneToPlayer(
                                                                player,
                                                                roleBg,
                                                                roleSound,
                                                                true,
                                                                Set.of(playerUUID),
                                                                isImpostor
                                                        );
                                                        System.out.println("[cutscenetest] Sub-role cutscene sent");
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                        System.out.println("[cutscenetest] Error during sub-role cutscene: " + e.getMessage());
                                                    }
                                                });
                                            }

                                            ctx.getSource().sendFeedback(() -> Text.literal("§aCutscene test scheduled."), false);
                                            return 1;
                                            } catch (Exception e) {
                                                System.out.println("[cutscenetest] Top-level exception in executes block:");
                                                e.printStackTrace();
                                                ctx.getSource().sendError(Text.literal("§cInternal error: " + e.getMessage()));
                                                return 0;
                                            }
                                        })
                                )
                                .then(literal("peutron")
                                        .requires(source -> source.hasPermissionLevel(2))
                                        .executes(ctx -> {
                                            Amongcraft.peutron = !Amongcraft.peutron;
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("settings")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(ctx -> {
                                    ServerPlayerEntity player;
                                    try {
                                        player = ctx.getSource().getPlayer();
                                    } catch (Exception e) {
                                        ctx.getSource().sendFeedback(() -> Text.literal("§cOnly players can use this command."), false);
                                        return 0;
                                    }

                                    String settingsJson = GSON.toJson(SettingsManager.getSettings());

                                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                                    buf.writeString("settings"); // Screen name
                                    buf.writeString(settingsJson); // Full settings JSON
                                    buf.writeString(""); // Optional 3rd arg

                                    ServerPlayNetworking.send(player, OPEN_SCREEN, buf);

                                    ctx.getSource().sendFeedback(() -> Text.literal("§bOpening settings screen..."), false);
                                    return 1;
                                }))
                        .then(literal("tasks")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(literal("disable")
                                    .executes(ctx -> {
                                        // Disables task GUI — this would set a flag or send packet
                                        ctx.getSource().sendFeedback(() -> Text.literal("§cTasks disabled."), false);
                                        return 1;
                                    }))
                                .then(literal("enable")
                                    .executes(ctx -> {
                                        ctx.getSource().sendFeedback(() -> Text.literal("§aTasks enabled."), false);
                                        return 1;
                                    })))
                        .then(literal("info")
                                .executes(ctx -> {
                                    String version = getModVersion();
                                    if (version == null) version = "unknown";

                                    String finalVersion = version;
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "§6AmongCraft Mod §ev" + finalVersion + "\n" +
                                                    "§7Use this mod to play Among Us in Minecraft!\n" +
                                                    "§7Useful Commands:\n" +
                                                    " - §e/amongcraft lobby {map}§7: Go to the lobby of a map\n" +
                                                    " - §e/amongcraft start§7: Start a game\n" +
                                                    " - §e/amongcraft role§7: Check your current role and info about it\n" +
                                                    "§8Made by TGGamesYT"
                                    ), false);
                                    return 1;
                                })
                        )
                        .then(literal("roleinfo")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    UUID uuid = player.getUuid();

                                    // Check player status
                                    String status;
                                    if (AmongCraftCommands.dead.contains(uuid)) {
                                        status = "§cYou are dead.";
                                    } else if (AmongCraftCommands.impostors.contains(uuid)) {
                                        status = "§4You are an impostor.";
                                    } else if (AmongCraftCommands.crewmates.contains(uuid)) {
                                        status = "§aYou are a crewmate.";
                                    } else {
                                        ctx.getSource().sendFeedback(() -> Text.literal("§7You are not currently in a game."), false);
                                        return 0;
                                    }

                                    String roleId = AmongCraftCommands.getPlayerRole(uuid);
                                    if (roleId == null) {
                                        ctx.getSource().sendFeedback(() -> Text.literal("§7Your role could not be found."), false);
                                        return 0;
                                    }

                                    String description = ROLE_DESCRIPTIONS.getOrDefault(roleId.toLowerCase(), "No description available for this role.");

                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            status + "\n§eRole: §f" + roleId + "\n§7" + description
                                    ), false);

                                    return 1;
                                })
                        )

                        .then(literal("cutscene")
                                // background namespace and path
                                .then(argument("bg_namespace", StringArgumentType.word())
                                        .then(argument("bg_path", StringArgumentType.word())
                                                // sound namespace and path
                                                .then(argument("sound_namespace", StringArgumentType.word())
                                                        .then(argument("sound_path", StringArgumentType.word())
                                                                .then(argument("fading", BoolArgumentType.bool())
                                                                        .then(argument("players", StringArgumentType.string())
                                                                                .then(argument("redColor", BoolArgumentType.bool())
                                                                                        .executes(ctx -> {
                                                                                            // Compose Identifiers from namespace + path
                                                                                            String bgNamespace = StringArgumentType.getString(ctx, "bg_namespace");
                                                                                            String bgPath = StringArgumentType.getString(ctx, "bg_path");
                                                                                            Identifier bg = new Identifier(bgNamespace, bgPath);

                                                                                            String soundNamespace = StringArgumentType.getString(ctx, "sound_namespace");
                                                                                            String soundPath = StringArgumentType.getString(ctx, "sound_path");
                                                                                            Identifier sound = new Identifier(soundNamespace, soundPath);

                                                                                            boolean fading = BoolArgumentType.getBool(ctx, "fading");
                                                                                            boolean redColor = BoolArgumentType.getBool(ctx, "redColor");

                                                                                            String playersJson = StringArgumentType.getString(ctx, "players");
                                                                                            Set<UUID> uuids = null;
                                                                                            if (!playersJson.equalsIgnoreCase("null")) {
                                                                                                try {
                                                                                                    uuids = Arrays.stream(playersJson.split("\\."))
                                                                                                            .filter(s -> !s.isEmpty())
                                                                                                            .map(UUID::fromString)
                                                                                                            .collect(Collectors.toSet());
                                                                                                } catch (Exception e) {
                                                                                                    ctx.getSource().sendError(Text.literal("Invalid UUID list (or use 'null')"));
                                                                                                    return 0;
                                                                                                }
                                                                                            }

                                                                                            for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                                                                                                PacketByteBuf buf = new PacketByteBuf(PacketByteBufs.create());

                                                                                                buf.writeIdentifier(bg);
                                                                                                buf.writeIdentifier(sound);
                                                                                                buf.writeBoolean(fading);
                                                                                                if (uuids == null) {
                                                                                                    buf.writeVarInt(0);
                                                                                                } else {
                                                                                                    buf.writeVarInt(uuids.size());
                                                                                                    for (UUID uuid : uuids) {
                                                                                                        buf.writeUuid(uuid);
                                                                                                    }
                                                                                                }
                                                                                                buf.writeBoolean(redColor);

                                                                                                player.networkHandler.sendPacket(new CustomPayloadS2CPacket(Amongcraft.CUTSCENE_PACKET_ID, buf));
                                                                                            }

                                                                                            ctx.getSource().sendFeedback(() -> Text.literal("Sent cutscene to all players."), false);
                                                                                            return 1;
                                                                                        })))))))))

        );
    }

    public static final Identifier CUTSCENE_PACKET = new Identifier("amongcraft", "cutscene_packet");

    public static void sendCutscenePacket(ServerPlayerEntity viewer, String cutsceneId, List<PlayerEntity> actors) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeString(cutsceneId);

        buf.writeInt(actors.size());
        for (PlayerEntity actor : actors) {
            UUID uuid = actor.getUuid();
            buf.writeUuid(uuid);
        }

        ServerPlayNetworking.send(viewer, CUTSCENE_PACKET, buf);
    }

    private static String getModVersion() {
        String modId = "amongcraft";
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
    }

    private static int runSetRole(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player, String role) {
        try {
            // Assign role
            AmongCraftCommands.setPlayerRole(player.getUuid(), role);
            AmongMapManager.giveRoleItems(player, role);

            // Send feedback
            player.sendMessage(Text.literal("§aYour role has been set to ").append(Text.literal(role).formatted(Formatting.AQUA)), false);
            ctx.getSource().sendFeedback(() ->
                    Text.literal("§aSet role of " + player.getName().getString() + " to " + role), false
            );

            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            ctx.getSource().sendFeedback(() -> Text.literal("§cError setting role: " + e.getMessage()), false);
            return 0;
        }
    }

    private static int executeShowDummy(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();

        if (player == null) {
            context.getSource().sendError(Text.literal("This command can only be run by a player."));
            return 0;
        }

        // Prepare identifiers
        Identifier animationJson = new Identifier("amongcraft", "dummy");
        Identifier background = new Identifier("amongcraft", "textures/gui/empty.png");
        // Use the player's own UUID for their skin
        UUID skinUUID = player.getUuid();

        // Build the packet buffer in the same order your client expects:
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeUuid(skinUUID);
        buf.writeIdentifier(animationJson);
        buf.writeIdentifier(background);

        // Send the packet
        ServerPlayNetworking.send(player, Amongcraft.SHOW_ANIMATION_PACKET_ID, buf);

        player.sendMessage(Text.literal("Showing dummy cutscene animation!"), false);
        return 1;
    }


    public static void giveItem(ServerPlayerEntity player, String id) {
        var item = Registries.ITEM.get(new Identifier(id));
        if (item != Items.AIR) {
            player.getInventory().insertStack(new ItemStack(item));
        }
    }

    public static void removeItem(ServerPlayerEntity player, String id) {
        var item = Registries.ITEM.get(new Identifier(id));
        int slot = player.getInventory().getSlotWithStack(new ItemStack(item));
        if (slot != -1) {
            player.getInventory().main.set(slot, ItemStack.EMPTY);
        }
    }

    private static String listNames(ServerCommandSource ctx, Set<UUID> set) {
        return set.stream()
                .map(uuid -> {
                    ServerPlayerEntity player = ctx.getServer().getPlayerManager().getPlayer(uuid);
                    return player != null ? player.getName().getString() : "[offline]";
                })
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    public static int getImpostorPercentage() {
        try {
            return SettingsManager.get("impostor-percentage").getAsInt();
        } catch (Exception e) {
            return 25;
        }
    }
}
