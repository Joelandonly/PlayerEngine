package com.player2.playerengine.supervisor;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.tasks.base.Task;
import java.util.Objects;
import java.util.UUID;

/** Entry point for legacy command tasks. Does not create another task scheduler. */
public final class ProjectTaskGateway {
    private ProjectTaskGateway() {}
    public static void run(PlayerEngineController controller, Task task, Runnable completion) {
        Objects.requireNonNull(task, "Missing command task");
        String kind = task.getClass().getSimpleName();
        if (kind.isBlank()) kind = task.getClass().getName();
        CoreGoalSupervisor.submit(controller, "tool-" + UUID.randomUUID(), kind,
                task, RollbackPolicy.NONE, completion);
    }
}
