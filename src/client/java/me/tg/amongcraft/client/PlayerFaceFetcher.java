package me.tg.amongcraft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.texture.NativeImage;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;

public class PlayerFaceFetcher {

    public static NativeImage getFace(UUID uuid, String name, boolean overlay) throws IOException {
        if (uuid == null && (name == null || name.isEmpty())) {
            throw new IllegalArgumentException("Either UUID or name must be provided.");
        }

        if (uuid == null) uuid = getUUIDFromName(name);
        String skinUrl = getSkinUrlFromUUID(uuid);
        NativeImage skin = NativeImage.read(new URL(skinUrl).openStream());
        return extractFace(skin, overlay);
    }

    private static UUID getUUIDFromName(String name) throws IOException {
        URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
        try (InputStreamReader reader = new InputStreamReader(url.openStream())) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            String id = obj.get("id").getAsString();
            return UUID.fromString(id.replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"
            ));
        }
    }

    private static String getSkinUrlFromUUID(UUID uuid) throws IOException {
        URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
        try (InputStreamReader reader = new InputStreamReader(url.openStream())) {
            JsonObject profile = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject textureProp = profile.getAsJsonArray("properties").get(0).getAsJsonObject();
            String value = textureProp.get("value").getAsString();

            String decoded = new String(Base64.getDecoder().decode(value));
            JsonObject textureObj = JsonParser.parseString(decoded).getAsJsonObject();
            return textureObj.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
        }
    }

    private static NativeImage extractFace(NativeImage skin, boolean overlay) {
        NativeImage face = new NativeImage(NativeImage.Format.RGBA, 8, 8, true);

        // Base face at (8, 8)
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                face.setColor(x, y, skin.getColor(8 + x, 8 + y));
            }
        }

        if (overlay) {
            // Hat layer at (40, 8)
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int color = skin.getColor(40 + x, 8 + y);
                    // Only apply if pixel isn't fully transparent
                    if ((color >> 24 & 0xFF) > 10) {
                        face.setColor(x, y, color);
                    }
                }
            }
        }

        return face;
    }
}
