package com.player2.playerengine.reliability;

import com.player2.playerengine.tasks.base.Task;
import java.lang.reflect.Array;
import java.lang.ref.WeakReference;
import java.util.LinkedHashSet;
import java.util.Set;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/** Retains fuel children on the existing cooking task; never owns movement or schedules a new root. */
public final class FuelRecovery {
    public static final int STRATEGY_TICKS = 600;
    public static final int MAX_STRATEGIES = 3;

    public interface Environment {
        boolean fuelReady() throws Exception;
        boolean finished(Task task);
        boolean stopped(Task task);
        Task alternative(Set<String> tried) throws Exception;
        void cancel(Task task) throws Exception;
        void blocked(String reason) throws Exception;
        void event(String type, String detail);
    }

    public static final class State {
        public Task child;
        public final Set<String> tried = new LinkedHashSet<>();
        public int activeTicks, attempts;
        public String failure;
    }

    private FuelRecovery() {}

    /** Returns the same active child until it finishes or its actual active-tick budget expires. */
    public static Task pending(State state, Environment world) throws Exception {
        if (state.failure != null || state.child == null) return null;
        if (world.fuelReady()) {
            state.child = null;
            return null;
        }
        state.activeTicks++;
        if (!world.finished(state.child) && !world.stopped(state.child)
                && state.activeTicks < STRATEGY_TICKS) return state.child;

        String cause = state.activeTicks >= STRATEGY_TICKS ? "fuel strategy timed out"
                : "fuel child ended without sufficient usable fuel";
        world.cancel(state.child);
        state.child = null;
        Task next = state.attempts < MAX_STRATEGIES ? world.alternative(state.tried) : null;
        if (next != null) {
            state.child = next;
            state.activeTicks = 0;
            state.attempts++;
            world.event("FUEL_ALTERNATIVE", cause + "; attempt=" + state.attempts
                    + "/" + MAX_STRATEGIES + " strategy=" + state.tried);
            return next;
        }
        state.failure = "BLOCKED_FUEL: " + cause + "; exhausted=" + state.tried;
        world.blocked(state.failure);
        return null;
    }

    public static State state(Object root) throws Exception {
        State state = (State)get(root, "ellieFuelRecovery");
        if (state == null) { state = new State(); set(root, "ellieFuelRecovery", state); }
        return state;
    }

    /** Exact hook after FuelPlanner.gatherTaskFor; dispatch is not credited as acquired fuel. */
    public static Task issued(Object candidate, Object root) {
        try {
            State state = state(root);
            String item = String.valueOf(call(candidate, "item"));
            String family=item.contains("planks") ? "planks" : item.contains("log")||item.contains("stem") ? "log" : "coal";
            Object owner=owner(root);
            Set<String> failed=owner==null ? Set.of() : FoodAcquisition.progress(owner).failedFuel;
            state.tried.addAll(failed);
            Object task=failed.contains(family) ? new NativeEnvironment(root,get(root,"controller")).alternative(state.tried)
                    : callStatic("com.player2.playerengine.tasks.cooking.FuelPlanner", "gatherTaskFor", candidate);
            if (!(task instanceof Task child)) return null;
            state.child = child;
            state.activeTicks = 0;
            state.attempts++;
            if(!failed.contains(family))state.tried.add(family);
            FoodAcquisition.event("FUEL_GATHER_STARTED", "attempt=" + state.attempts
                    + "/" + MAX_STRATEGIES + " item=" + item + "; awaiting actual inventory gain");
            return child;
        } catch (Exception | LinkageError failure) {
            FoodAcquisition.blockResource(root, "BLOCKED_FUEL: cannot start fuel acquisition: " + failure.getMessage());
            return null;
        }
    }

    private static Object owner(Object root) throws Exception {
        Object owner=get(root,"ellieFoodOwner");
        return owner instanceof WeakReference<?> weak ? weak.get() : null;
    }

    public static Task before(Object root, Object controller) {
        try {
            ProductionHandoff.beforeSmeltTick(root, controller);
            if (Boolean.TRUE.equals(get(root, "isSmelting"))) return null;
            State state = state(root);
            if (state.child == null) return null;
            return pending(state, new NativeEnvironment(root, controller));
        } catch (Exception | LinkageError failure) {
            FoodAcquisition.blockResource(root, "BLOCKED_FUEL: cannot verify fuel acquisition: " + failure.getMessage());
            return null;
        }
    }

    public static void stopped(Object root) {
        try {
            State state = (State)get(root, "ellieFuelRecovery");
            if (state != null && state.child != null) {
                call(state.child, "stop");
                state.child = null;
            }
            set(root, "ellieFuelRecovery", null);
        } catch (Exception | LinkageError failure) {
            FoodAcquisition.event("FUEL_CLEANUP_UNAVAILABLE", failure.getClass().getSimpleName());
        }
    }

    private static final class NativeEnvironment implements Environment {
        private final Object root, controller;
        private final int neededTicks;
        NativeEnvironment(Object root, Object controller) throws Exception {
            this.root = root;
            this.controller = controller;
            int ticks = root.getClass().getSimpleName().equals("SmeltInSmokerTask") ? 100 : 200;
            Object targets = get(root, "targets");
            int remaining = 0;
            for (int i=0; i<Array.getLength(targets); i++) {
                Object target = call(Array.get(targets, i), "getItem");
                remaining = Math.max(remaining, FoodAcquisition.remaining(controller, target));
            }
            neededTicks = Math.max(1, remaining) * ticks;
        }
        public boolean fuelReady() throws Exception {
            Object planner = get(root, "FUEL_PLANNER");
            int ticks = root.getClass().getSimpleName().equals("SmeltInSmokerTask") ? 100 : 200;
            return ((java.util.Optional<?>)call(planner, "plan", Math.max(1, neededTicks/ticks),
                    ticks, controller, null)).isPresent();
        }
        public boolean finished(Task task) { return task.isFinished(); }
        public boolean stopped(Task task) { return task.stopped(); }
        public void cancel(Task task) throws Exception {
            if (!task.stopped()) call(task, "stop");
            Object owner=owner(root);
            if(owner!=null){
                String last=null;for(String strategy:state(root).tried)last=strategy;
                if(last!=null)FoodAcquisition.progress(owner).failedFuel.add(last);
            }
        }
        public Task alternative(Set<String> tried) throws Exception {
            // A failed coal attempt switches to wood. A failed plank recipe switches to burnable logs.
            for (String name : new String[]{"planks", "log"}) {
                if (!tried.add(name)) continue;
                int held = FoodAcquisition.countNamed(controller, name);
                int extra = Math.max(1, (neededTicks + 299)/300);
                Object next = callStatic("com.player2.playerengine.TaskCatalogue", "getItemTask", name, held + extra);
                if (next instanceof Task task) return task;
            }
            return null;
        }
        public void blocked(String reason) { FoodAcquisition.blockResource(root, reason); }
        public void event(String type, String detail) { FoodAcquisition.event(type, detail); }
    }
}
