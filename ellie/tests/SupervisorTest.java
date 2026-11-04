import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.executor.*;
import com.player2.playerengine.supervisor.*;
import com.player2.playerengine.tasks.base.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class SupervisorTest {
    private static int passed, failed;
    private static final class Outcome extends Task implements TrackedTaskOutcomeProvider {
        boolean terminal = true, success = true;
        public boolean isTerminal() { return terminal; }
        public boolean isSuccessful() { return success; }
        public String controlledReason() { return success ? "verified outputs" : "materials missing"; }
    }
    private static final class Adapter implements IStepExecutorAdapter {
        StepState state = StepState.SUCCEEDED;
        String reason = "finished";
        boolean synchronous;
        boolean reject;
        final List<Runnable> completions = new ArrayList<>();
        StepExecution active;
        public StepExecution submit(String id, String kind, Task task, RollbackPolicy policy, Runnable completion) {
            if (reject) throw new IllegalStateException("rejected");
            StepExecution result = ExecutionFixture.receipt(id, kind, state, reason);
            completions.add(completion);
            // Expose an old same-ID execution until submit returns, like an immediate rejection.
            if (synchronous) completion.run();
            active = result;
            return result;
        }
        public void cancel(String reason) {}
        public Optional<StepExecution> getActiveExecution() { return Optional.ofNullable(active); }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void scenario(String name, Runnable test) {
        try { test.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable e) { failed++; System.err.println("FAIL " + name + ": " + e); }
    }
    private static void submit(PlayerEngineController controller, Task task, Runnable callback) {
        CoreGoalSupervisor.submit(controller, "project", "build", task, RollbackPolicy.NONE, callback);
    }
    private static void outcome(StepState state, boolean terminal, boolean success, String reason, GoalLifecycle.Status expected) {
        Adapter adapter = new Adapter(); adapter.state = state; adapter.reason = reason;
        PlayerEngineController controller = new PlayerEngineController(adapter);
        Outcome task = new Outcome(); task.terminal = terminal; task.success = success;
        submit(controller, task, null); adapter.completions.getFirst().run();
        check(CoreGoalSupervisor.snapshot(controller).status() == expected,
            "expected " + expected + " got " + CoreGoalSupervisor.snapshot(controller));
    }
    public static void main(String[] args) {
        scenario("synchronous rejection uses its own receipt", () -> {
            Adapter adapter = new Adapter(); adapter.synchronous = true; adapter.state = StepState.BLOCKED;
            adapter.active = ExecutionFixture.receipt("project", "build", StepState.SUCCEEDED, "old job succeeded");
            PlayerEngineController controller = new PlayerEngineController(adapter);
            AtomicInteger callbacks = new AtomicInteger(); submit(controller, new Outcome(), callbacks::incrementAndGet);
            check(CoreGoalSupervisor.snapshot(controller).status() == GoalLifecycle.Status.BLOCKED, "rejection reported success");
            check(callbacks.get() == 1, "synchronous callback lost or duplicated");
        });
        scenario("stale completion cannot continue superseded project", () -> {
            Adapter adapter = new Adapter(); PlayerEngineController controller = new PlayerEngineController(adapter);
            AtomicInteger old = new AtomicInteger(), current = new AtomicInteger();
            submit(controller, new Outcome(), old::incrementAndGet);
            submit(controller, new Outcome(), current::incrementAndGet);
            adapter.completions.get(0).run();
            check(old.get() == 0, "old continuation executed");
            check(CoreGoalSupervisor.snapshot(controller).status() == GoalLifecycle.Status.RUNNING, "new ownership changed");
            adapter.completions.get(1).run(); adapter.completions.get(1).run();
            check(current.get() == 1, "completion must run once");
        });
        scenario("terminal success requires executor receipt", () -> outcome(StepState.RUNNING,true,true,"",GoalLifecycle.Status.FAILED));
        scenario("executor success requires typed terminal verification", () -> outcome(StepState.SUCCEEDED,false,true,"",GoalLifecycle.Status.FAILED));
        scenario("verified success", () -> outcome(StepState.SUCCEEDED,true,true,"",GoalLifecycle.Status.SUCCEEDED));
        scenario("cancelled blocked receipt beats task success", () -> outcome(StepState.BLOCKED,true,true,"CANCELLED_SUPERSEDED",GoalLifecycle.Status.CANCELLED));
        scenario("executor failure beats task success", () -> outcome(StepState.FAILED,true,true,"FATAL",GoalLifecycle.Status.FAILED));
        scenario("rollback is not successful completion", () -> outcome(StepState.ROLLED_BACK,true,true,"rollback",GoalLifecycle.Status.FAILED));
        scenario("invalid submission preserves current ownership", () -> {
            Adapter adapter = new Adapter(); PlayerEngineController controller = new PlayerEngineController(adapter);
            submit(controller, new Outcome(), null); long generation = CoreGoalSupervisor.snapshot(controller).generation();
            try { submit(controller, null, null); throw new AssertionError("null task accepted"); }
            catch (NullPointerException expected) {}
            check(CoreGoalSupervisor.snapshot(controller).generation() == generation, "invalid task superseded valid goal");
        });
        scenario("executor rejection recorded without success", () -> {
            Adapter adapter = new Adapter(); adapter.reject = true;
            PlayerEngineController controller = new PlayerEngineController(adapter);
            try { submit(controller, new Outcome(), null); throw new AssertionError("rejection swallowed"); }
            catch (IllegalStateException expected) {}
            check(CoreGoalSupervisor.snapshot(controller).status() == GoalLifecycle.Status.FAILED,"rejection not recorded");
        });
        scenario("lifecycle rejects null terminal status", () -> {
            GoalLifecycle life = new GoalLifecycle(); long generation = life.begin("a", "build").generation();
            try { life.finish(generation, null, "bad"); throw new AssertionError("null accepted"); }
            catch (NullPointerException expected) {}
            check(life.current().status() == GoalLifecycle.Status.RUNNING,"invalid transition mutated lifecycle");
        });
        scenario("gateway installs project task through native tracked executor", () -> {
            Adapter adapter = new Adapter(); PlayerEngineController controller = new PlayerEngineController(adapter);
            AtomicInteger callbacks = new AtomicInteger();
            ProjectTaskGateway.run(controller, new Outcome(), callbacks::incrementAndGet);
            check(adapter.completions.size() == 1, "gateway skipped tracked executor");
            check(CoreGoalSupervisor.snapshot(controller).status() == GoalLifecycle.Status.RUNNING, "no active project");
            adapter.completions.getFirst().run();
            check(callbacks.get() == 1, "gateway callback lost");
        });
        scenario("completion may submit next project without stale mutation", () -> {
            Adapter adapter = new Adapter(); PlayerEngineController controller = new PlayerEngineController(adapter);
            submit(controller, new Outcome(), () -> submit(controller, new Outcome(), null));
            adapter.completions.getFirst().run();
            check(CoreGoalSupervisor.snapshot(controller).generation() == 2, "followup not installed");
            check(CoreGoalSupervisor.snapshot(controller).status() == GoalLifecycle.Status.RUNNING, "followup terminalized by old callback");
        });
        scenario("concurrent duplicate completion runs once", () -> {
            Adapter adapter = new Adapter(); PlayerEngineController controller = new PlayerEngineController(adapter);
            AtomicInteger callbacks = new AtomicInteger();
            submit(controller, new Outcome(), callbacks::incrementAndGet);
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 8; i++) threads.add(Thread.ofPlatform().start(adapter.completions.getFirst()));
            for (Thread thread : threads) {
                try { thread.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
            }
            check(callbacks.get() == 1, "concurrent duplicate continuation");
        });
        scenario("suspend resume and stale transitions", () -> {
            GoalLifecycle life = new GoalLifecycle(); long first = life.begin("a", "build").generation();
            check(life.suspend(first,"survival interruption") != null,"suspend rejected");
            check(life.resume(first) != null,"resume rejected");
            long second = life.begin("b", "get").generation();
            check(life.finish(first,GoalLifecycle.Status.SUCCEEDED,"late") == null,"stale terminal accepted");
            life.finish(second,GoalLifecycle.Status.CANCELLED,"operator");
            check(life.resume(second) == null,"cancelled goal resumed");
            check(life.finish(second,GoalLifecycle.Status.SUCCEEDED,"late") == null,"cancelled result overwritten");
        });
        System.out.println("SupervisorTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) throw new AssertionError("supervisor regressions");
    }
}
