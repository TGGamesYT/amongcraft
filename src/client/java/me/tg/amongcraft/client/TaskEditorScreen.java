package me.tg.amongcraft.client;

import me.tg.amongcraft.Amongcraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class TaskEditorScreen extends Screen {
    private final BlockPos pos;
    private final ItemStack ordererStack;
    private TextFieldWidget identifierField;
    private TextFieldWidget levelField;
    private TextFieldWidget rarityField;
    private TextFieldWidget mapField;

    public TaskEditorScreen(BlockPos pos, ItemStack stack) {
        super(Text.literal("Edit Task Order"));
        this.pos = pos;
        this.ordererStack = stack;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int spacing = 30;

        // Identifier
        this.addDrawableChild(new TextRendererWidget(this.textRenderer, "Identifier:", centerX - 100, centerY - spacing * 2 - 10));
        this.identifierField = new TextFieldWidget(this.textRenderer, centerX - 100, centerY - spacing * 2, 200, 20, Text.empty());
        this.addSelectableChild(this.identifierField);
        this.addDrawableChild(this.identifierField);

        // Level
        this.addDrawableChild(new TextRendererWidget(this.textRenderer, "Level:", centerX - 100, centerY - spacing - 10));
        this.levelField = new TextFieldWidget(this.textRenderer, centerX - 100, centerY - spacing, 200, 20, Text.empty());
        this.addSelectableChild(this.levelField);
        this.addDrawableChild(this.levelField);

        // Rarity
        this.addDrawableChild(new TextRendererWidget(this.textRenderer, "Rarity:", centerX - 100, centerY - 10));
        this.rarityField = new TextFieldWidget(this.textRenderer, centerX - 100, centerY, 200, 20, Text.empty());
        this.addSelectableChild(this.rarityField);
        this.addDrawableChild(this.rarityField);

        // Map
        this.addDrawableChild(new TextRendererWidget(this.textRenderer, "Map:", centerX - 100, centerY + spacing - 10));
        this.mapField = new TextFieldWidget(this.textRenderer, centerX - 100, centerY + spacing, 200, 20, Text.empty());
        this.addSelectableChild(this.mapField);
        this.addDrawableChild(this.mapField);

        // Save Button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Save"),
                        button -> {
                            String id = identifierField.getText();
                            int lvl = Integer.parseInt(levelField.getText());
                            String rarity = rarityField.getText();
                            String map = mapField.getText();

                            PacketByteBuf buf = PacketByteBufs.create();
                            buf.writeBlockPos(pos);
                            buf.writeString(id);
                            buf.writeInt(lvl);
                            buf.writeString(rarity);
                            buf.writeString(map);

                            ClientPlayNetworking.send(Amongcraft.SAVE_TASK_ORDER, buf);
                            MinecraftClient.getInstance().setScreen(null);
                        })
                .dimensions(centerX - 50, centerY + spacing * 2, 100, 20)
                .build());
    }

    // Helper for text labels
    private static class TextRendererWidget extends ButtonWidget {
        public TextRendererWidget(TextRenderer renderer, String text, int x, int y) {
            super(x, y, renderer.getWidth(text), 10, Text.literal(text), b -> {}, DEFAULT_NARRATION_SUPPLIER);
            this.active = false;
        }
    }
}
