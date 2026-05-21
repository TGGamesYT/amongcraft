package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Maps a task name (the {@code task} value stored for a task block) to its
 * native minigame screen. Used by the client when the server asks it to open
 * a task.
 */
public final class TaskScreens {

    private TaskScreens() {}

    @Nullable
    public static TaskMinigameScreen create(String taskName, BlockPos pos) {
        return switch (taskName) {
            case "default", "task1", "task2", "wiring" -> new WireTaskScreen(taskName, pos);
            case "download", "upload" -> new ProgressTaskScreen(taskName, pos);
            case "fuelcan", "fuelengine" -> new FuelcanTaskScreen(taskName, pos);
            case "align_engine_output" -> new AlignEngineTaskScreen(taskName, pos);
            case "calibrate" -> new CalibrateTaskScreen(taskName, pos);
            case "chart_course" -> new ChartCourseTaskScreen(taskName, pos);
            case "chute" -> new ChuteTaskScreen(taskName, pos);
            case "clean_vent" -> new CleanVentTaskScreen(taskName, pos);
            case "clear_asteroids" -> new ClearAsteroidsTaskScreen(taskName, pos);
            case "divert_power_1" -> new DivertPower1TaskScreen(taskName, pos);
            case "divert_power_2" -> new DivertPower2TaskScreen(taskName, pos);
            case "inspect_sample" -> new InspectSampleTaskScreen(taskName, pos);
            case "o2_filter" -> new O2FilterTaskScreen(taskName, pos);
            case "scan" -> new ScanTaskScreen(taskName, pos);
            case "shields" -> new ShieldsTaskScreen(taskName, pos);
            case "stabilize_steering" -> new StabilizeSteeringTaskScreen(taskName, pos);
            case "start_reactor" -> new StartReactorTaskScreen(taskName, pos);
            case "swipe_card" -> new SwipeCardTaskScreen(taskName, pos);
            case "unlock_manifolds" -> new UnlockManifoldsTaskScreen(taskName, pos);
            default -> null;
        };
    }
}
