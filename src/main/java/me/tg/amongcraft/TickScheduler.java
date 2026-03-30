package me.tg.amongcraft;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.BooleanSupplier;

public class TickScheduler {
    private static final LinkedList<ScheduledTask> tasks = new LinkedList<>();

    public static void schedule(BooleanSupplier task, int intervalTicks) {
        tasks.add(new ScheduledTask(task, intervalTicks));
    }

    public static void tick() {
        Iterator<ScheduledTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            ScheduledTask t = iterator.next();
            if (t.tick()) {
                // Continue scheduling
            } else {
                // Task ended
                iterator.remove();
            }
        }
    }

    private static class ScheduledTask {
        private final BooleanSupplier task;
        private final int interval;
        private int ticks = 0;

        ScheduledTask(BooleanSupplier task, int interval) {
            this.task = task;
            this.interval = interval;
        }

        public boolean tick() {
            ticks++;
            if (ticks >= interval) {
                ticks = 0;
                return task.getAsBoolean();
            }
            return true;
        }
    }
}
