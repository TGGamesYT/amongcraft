package me.tg.amongcraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static me.tg.amongcraft.Amongcraft.SHAPESHIFT_PACKET;

public class ShapeshiftHandler {

    public static void shapeshift(ServerPlayerEntity player1, UUID player2UUID) {
        fetchSkinData(player2UUID).thenAccept(profileJson -> {
            if (profileJson == null) return;

            String textureValue = profileJson.get("value").getAsString();
            String textureSignature = profileJson.get("signature").getAsString();

            // Fetch disguised player's name (the player we are shapeshifting as)
            String disguisedName = "";
            ServerPlayerEntity disguisedPlayer = player1.getServer().getPlayerManager().getPlayer(player2UUID);
            if (disguisedPlayer != null) {
                disguisedName = disguisedPlayer.getEntityName(); // or getGameProfile().getName()
            }

            PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
            buf.writeUuid(player1.getUuid());
            buf.writeBoolean(true); // is shapeshift
            buf.writeString(textureValue);
            buf.writeString(textureSignature);
            buf.writeString(disguisedName);  // <-- send disguised player's name

            sendToAllPlayers(player1.getServer(), buf, SHAPESHIFT_PACKET);
        });
    }

    public static void unshift(ServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUuid(player.getUuid());
        buf.writeBoolean(false); // is unshift

        sendToAllPlayers(player.getServer(), buf, SHAPESHIFT_PACKET);
    }

    private static CompletableFuture<JsonObject> fetchSkinData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
                JsonObject profile = JsonParser.parseReader(new InputStreamReader(url.openStream())).getAsJsonObject();
                return profile.getAsJsonArray("properties").get(0).getAsJsonObject();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }, Util.getMainWorkerExecutor());
    }

    private static void sendToAllPlayers(MinecraftServer server, PacketByteBuf buf, Identifier packetId) {
        server.execute(() -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, packetId, new PacketByteBuf(buf.copy()));
            }
        });
    }
}