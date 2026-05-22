package dev.tggamesyt.amongcraft;

public class GameState {
    private static boolean running = false;

    /** When true the game never auto-ends; toggled via /amongcraft debug. */
    public static boolean debugMode = false;

    public static boolean isRunning() {
        return running;
    }

    public static void setRunning(boolean value) {
        running = value;
    }
}
