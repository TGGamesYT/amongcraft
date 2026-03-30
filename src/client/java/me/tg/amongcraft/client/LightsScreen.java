package me.tg.amongcraft.client;

import io.netty.buffer.Unpooled;
import me.tg.amongcraft.Amongcraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static me.tg.amongcraft.Amongcraft.CLIENT_SABOTAGE_PACKET;

public class LightsScreen extends Screen {
    private static boolean[] switches = new boolean[5];
    private static BlockPos pos = null;
    private static ButtonWidget[] buttons = new ButtonWidget[5];  // Store button references

    public LightsScreen(BlockPos pos, boolean[] initialStates) {
        super(Text.literal("Fix Lights"));
        LightsScreen.pos = pos;
        this.switches = initialStates;
    }

    @Override
    protected void init() {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            buttons[i] = ButtonWidget.builder(Text.literal(switches[idx] ? "On" : "Off"), btn -> {
                // Player clicked: toggle locally, update label and send update to server
                switches[idx] = !switches[idx];
                btn.setMessage(Text.literal(switches[idx] ? "On" : "Off"));
                updateLeverState(idx, switches[idx]);

                // Check if all ON and close screen
                boolean allOn = true;
                for (boolean s : switches) {
                    if (!s) {
                        allOn = false;
                        break;
                    }
                }
                if (allOn) {
                    MinecraftClient.getInstance().setScreen(null);
                }
            }).position(width / 2 - 100 + i * 40, height / 2).size(30, 20).build();

            addDrawableChild(buttons[i]);
        }
    }

    /**
     * Called when the player clicks a button locally. Sends the update to the server.
     */
    public static void updateLeverState(int index, boolean state) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeString("lights");
        buf.writeInt(index);
        buf.writeBoolean(state);
        ClientPlayNetworking.send(CLIENT_SABOTAGE_PACKET, buf);
    }

    /**
     * Called when receiving an update from the server.
     * Updates local switches and buttons without sending packets to avoid loops.
     */
    public static void applyLeverUpdate(int index, boolean state) {
        switches[index] = state;
        if (buttons[index] != null) {
            buttons[index].setMessage(Text.literal(state ? "On" : "Off"));
        }

        // ✅ Check if all are now ON (for multiplayer sync)
        boolean allOn = true;
        for (boolean s : switches) {
            if (!s) {
                allOn = false;
                break;
            }
        }

        // ✅ If all are ON, close screen
        if (allOn) {
            MinecraftClient.getInstance().setScreen(null);
        }
    }
}
