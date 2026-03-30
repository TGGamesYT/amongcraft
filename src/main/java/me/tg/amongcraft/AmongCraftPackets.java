package me.tg.amongcraft;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class AmongCraftPackets {

    public static final Identifier OPEN_SCREEN = new Identifier("amongcraft", "open_screen");

    private static final Map<String, TriConsumer<ServerPlayerEntity, PacketByteBuf, String[]>> serverHandlers = new HashMap<>();

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(OPEN_SCREEN, (server, player, handler, buf, responseSender) -> {
            String screenName = buf.readString();
            String arg1 = buf.readString();
            String arg2 = buf.readString();

            server.execute(() -> {
                TriConsumer<ServerPlayerEntity, PacketByteBuf, String[]> handlerFunc = serverHandlers.get(screenName);
                if (handlerFunc != null) {
                    handlerFunc.accept(player, buf, new String[]{arg1, arg2});
                } else {
                    player.sendMessage(Text.literal("§cUnknown screen: " + screenName), false);
                }
            });
        });
    }

    public static void registerServerHandler(String screen, TriConsumer<ServerPlayerEntity, PacketByteBuf, String[]> handler) {
        serverHandlers.put(screen, handler);
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}
