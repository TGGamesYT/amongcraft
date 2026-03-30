package me.tg.amongcraft;

public class GameState {
    private static boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    public static void setRunning(boolean value) {
        running = value;
    }
}
