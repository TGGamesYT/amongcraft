package me.tg.amongcraft;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TickDelayedExecutor {
    private static final Map<MinecraftServer, List<ScheduledTask>> taskMap = new HashMap<>();

    public static void schedule(MinecraftServer server, int delayTicks, Runnable task) {
        taskMap.computeIfAbsent(server, s -> new ArrayList<>()).add(new ScheduledTask(delayTicks, task));
    }

    public static void onServerTick(MinecraftServer server) {
        List<ScheduledTask> tasks = taskMap.computeIfAbsent(server, s -> new ArrayList<>());
        if (tasks.isEmpty()) return;

        List<ScheduledTask> toRun = new ArrayList<>();
        tasks.removeIf(task -> {
            task.ticksRemaining--;
            if (task.ticksRemaining <= 0) {
                toRun.add(task);
                return true;
            }
            return false;
        });

        for (ScheduledTask task : toRun) {
            try {
                task.task.run();
            } catch (Exception e) {
                System.out.println("[TickDelayedExecutor] Error while running delayed task:");
                e.printStackTrace();
            }
        }
    }


    private static class ScheduledTask {
        int ticksRemaining;
        Runnable task;

        ScheduledTask(int ticks, Runnable task) {
            this.ticksRemaining = ticks;
            this.task = task;
        }
    }
}