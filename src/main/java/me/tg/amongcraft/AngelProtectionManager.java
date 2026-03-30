package me.tg.amongcraft;

import java.util.*;

public class AngelProtectionManager {

    public static final Map<UUID, Long> protectedUntil = new HashMap<>();
    public static final Map<UUID, Long> cooldownUntil = new HashMap<>();

    /**
     * Check if the player is currently protected.
     */
    public static boolean isPlayerProtected(UUID uuid) {
        return protectedUntil.getOrDefault(uuid, 0L) > System.currentTimeMillis();
    }

    /**
     * Optionally, use this to clear protections (e.g., at round reset).
     */
    public static void reset() {
        protectedUntil.clear();
        cooldownUntil.clear();
    }
}
