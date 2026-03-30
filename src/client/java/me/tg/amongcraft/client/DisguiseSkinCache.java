package me.tg.amongcraft.client;

import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DisguiseSkinCache {
    // Thread-safe map to store player UUID to custom skin Identifier
    private static final Map<UUID, Identifier> disguiseSkinMap = new ConcurrentHashMap<>();

    // New map to store disguised names per player UUID
    private static final Map<UUID, String> disguiseNameMap = new ConcurrentHashMap<>();

    /**
     * Returns the custom skin Identifier for the given player UUID,
     * or null if the player is not disguised.
     */
    public static Identifier getCustomSkinFor(UUID playerUUID) {
        return disguiseSkinMap.get(playerUUID);
    }

    /**
     * Sets the custom skin Identifier for the given player UUID.
     * Pass null to remove the custom skin (unshift).
     */
    public static void setCustomSkinFor(UUID playerUUID, Identifier skinIdentifier) {
        if (skinIdentifier == null) {
            disguiseSkinMap.remove(playerUUID);
        } else {
            disguiseSkinMap.put(playerUUID, skinIdentifier);
        }
    }

    /**
     * Returns whether the given player UUID currently has a disguise skin.
     */
    public static boolean hasDisguiseFor(UUID playerUUID) {
        return disguiseSkinMap.containsKey(playerUUID);
    }

    /**
     * Returns the disguised name for the given player UUID,
     * or null if none is set.
     */
    public static String getDisguisedName(UUID playerUUID) {
        return disguiseNameMap.get(playerUUID);
    }

    /**
     * Sets the disguised name for the given player UUID.
     * Pass null to remove the disguise name.
     */
    public static void setDisguisedName(UUID playerUUID, String name) {
        if (name == null) {
            disguiseNameMap.remove(playerUUID);
        } else {
            disguiseNameMap.put(playerUUID, name);
        }
    }

    /**
     * Clears all cached disguise skins and names.
     */
    public static void clear() {
        disguiseSkinMap.clear();
        disguiseNameMap.clear();
    }
}