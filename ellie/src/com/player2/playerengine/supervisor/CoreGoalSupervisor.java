package com.player2.playerengine.supervisor;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.executor.StepExecution;
import com.player2.playerengine.executor.StepState;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.deferred.DeferredJobStore;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Source recovery of the pinned baseline supervisor, preserving its public entry points.
 * The native executor remains the single task runner; this layer owns goal receipts.
 */
public final class CoreGoalSupervisor {
    private static final Map<PlayerEngineController, Slot> REGISTRY = new WeakHashMap<>();
    private CoreGoalSupervisor() {}

    public static StepExecution submit(PlayerEngineController controller, String commandId,
            String kind, Task task, RollbackPolicy policy, Runnable onComplete) {
        Objects.requireNonNull(controller, "Missing PlayerEngine controller");
        Objects.requireNonNull(task, "Missing project task");
        Objects.requireNonNull(policy, "Missing rollback policy");
        if (commandId == null || commandId.isBlank() || kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("Project command ID and kind must be nonempty");
        }
        // Keep submission, synchronous completion, and ownership changes in one order.
        // Reentrant native callbacks can submit another goal; generation checks protect it.
        synchronized (REGISTRY) {
            Slot slot = REGISTRY.computeIfAbsent(controller, ignored -> new Slot());
            if (slot.journal == null) slot.journal = journalFor(controller);
            GoalLifecycle.Snapshot previous = slot.lifecycle.current();
            if (previous != null && (previous.status() == GoalLifecycle.Status.RUNNING
                    || previous.status() == GoalLifecycle.Status.SUSPENDED)) {
                event("CORE_GOAL_SUPERSEDED", "old_generation=" + previous.generation() + " new=" + kind);
            }
            com.player2.playerengine.reliability.SharedTargetRecovery.begin(task);
            GoalLifecycle.Snapshot started = slot.lifecycle.begin(commandId, kind);
            checkpoint(slot, started);
            event("CORE_GOAL_SUBMIT", "generation=" + started.generation() + " kind=" + kind);
            AtomicReference<StepExecution> receipt = new AtomicReference<>();
            AtomicBoolean requested = new AtomicBoolean();
            AtomicBoolean fired = new AtomicBoolean();
            Runnable completion = () -> {
                requested.set(true);
                synchronized (REGISTRY) {
                    StepExecution execution = receipt.get();
                    // Some adapters call onComplete inside submit(). Wait for its receipt,
                    // never borrow the previous goal's same-ID receipt from the controller.
                    if (execution == null || !fired.compareAndSet(false, true)) return;
                    GoalLifecycle.Snapshot terminal = slot.lifecycle.finish(started.generation(),
                            terminalStatus(task, execution), outcomeDetail(task, execution));
                    if (terminal == null) {
                        event("CORE_GOAL_STALE_CALLBACK", "ignored generation=" + started.generation());
                        return;
                    }
                    checkpoint(slot, terminal);
                    event("CORE_GOAL_TERMINAL", "generation=" + terminal.generation()
                            + " status=" + terminal.status() + " detail=" + terminal.detail());
                    CommandCompletion.completed(execution);
                    // Stale goals must not schedule more work after ownership has changed.
                    if (onComplete != null && slot.lifecycle.current().generation()==started.generation()) onComplete.run();
                }
            };
            try {
                CommandCompletion.prepare(task);
                StepExecution execution = Objects.requireNonNull(controller.getStepExecutorAdapter()
                        .submit(commandId, kind, task, policy, completion), "Executor returned no receipt");
                receipt.set(execution);
                CommandCompletion.bind(task, execution);
                if (requested.get()) completion.run();
                return execution;
            } catch (RuntimeException failure) {
                GoalLifecycle.Snapshot failed = slot.lifecycle.finish(started.generation(),
                        GoalLifecycle.Status.FAILED, "Executor rejected request: " + failure.getClass().getSimpleName());
                if (failed != null) checkpoint(slot, failed);
                throw failure;
            }
        }
    }

    public static GoalLifecycle.Snapshot snapshot(PlayerEngineController controller) {
        synchronized (REGISTRY) {
            Slot slot = REGISTRY.get(controller);
            return slot == null ? null : slot.lifecycle.current();
        }
    }

    private static GoalLifecycle.Status terminalStatus(Task task, StepExecution execution) {
        if (com.player2.playerengine.reliability.SharedTargetRecovery.failure(task) != null) return GoalLifecycle.Status.BLOCKED;
        StepState state = execution.getState();
        String log = execution.getLastLogEntry();
        if (log != null && log.contains("CANCELLED_")) return GoalLifecycle.Status.CANCELLED;
        if (state == StepState.FAILED && log != null && log.contains("BLOCKED_")) return GoalLifecycle.Status.BLOCKED;
        if (state == StepState.FAILED || state == StepState.ROLLED_BACK) return GoalLifecycle.Status.FAILED;
        if (state == StepState.BLOCKED) return GoalLifecycle.Status.BLOCKED;
        if (task instanceof TrackedTaskOutcomeProvider outcome) {
            if (!outcome.isTerminal()) return GoalLifecycle.Status.FAILED;
            String reason = outcome.controlledReason();
            if (!outcome.isSuccessful()) {
                if (reason != null && (reason.contains("INTERRUPTED") || reason.contains("CANCELLED"))) {
                    return GoalLifecycle.Status.CANCELLED;
                }
                return GoalLifecycle.Status.BLOCKED;
            }
        }
        // A task's own claim cannot override a running/nonterminal executor receipt.
        return state == StepState.SUCCEEDED ? GoalLifecycle.Status.SUCCEEDED : GoalLifecycle.Status.FAILED;
    }

    private static String outcomeDetail(Task task, StepExecution execution) {
        String sharedFailure = com.player2.playerengine.reliability.SharedTargetRecovery.failure(task);
        if (sharedFailure != null) return sharedFailure;
        // Keep the executor's cancellation/failure reason even if the task claims success.
        if (execution.getState() != StepState.SUCCEEDED) {
            String log = execution.getLastLogEntry();
            return log == null || log.isBlank() ? "native executor: " + execution.getState() : log;
        }
        if (task instanceof TrackedTaskOutcomeProvider outcome) {
            return outcome.isTerminal() ? outcome.controlledReason() : "task stopped before terminal verification";
        }
        return "native executor: " + execution.getState();
    }

    private static CoreGoalJournal journalFor(PlayerEngineController controller) {
        try {
            return CoreGoalJournal.open(DeferredJobStore.get().worldRoot(),
                    controller.getAIPersistantData().getCharacterId(), "supervisor_active_goal");
        } catch (LinkageError | RuntimeException failure) {
            event("CORE_GOAL_WARNING", "Goal journal unavailable: " + failure.getClass().getSimpleName());
            return CoreGoalJournal.transientJournal();
        }
    }

    private static void checkpoint(Slot slot, GoalLifecycle.Snapshot snapshot) {
        try {
            slot.journal.put(snapshot.status().name(), (int)Math.min(Integer.MAX_VALUE, snapshot.generation()),
                    snapshot.kind(), snapshot.commandId() + ": " + snapshot.detail());
            if (slot.journal.error() != null) event("CORE_GOAL_WARNING", slot.journal.error());
        } catch (RuntimeException failure) {
            event("CORE_GOAL_WARNING", "Goal journal write failed: " + failure.getClass().getSimpleName());
        }
    }

    private static void event(String name, String detail) {
        String safe = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ');
        System.out.println("[EllieCore] " + name + ": " + safe);
        try {
            Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry")
                    .getMethod("event", String.class, String.class).invoke(null, name, safe);
        } catch (LinkageError | ReflectiveOperationException ignored) {
            // Observability must not change task ownership.
        }
    }

    private static final class Slot {
        final GoalLifecycle lifecycle = new GoalLifecycle();
        CoreGoalJournal journal;
    }
}
