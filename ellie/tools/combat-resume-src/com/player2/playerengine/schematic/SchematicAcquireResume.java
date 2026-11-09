package com.player2.playerengine.schematic;

import com.player2.playerengine.tasks.base.Task;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import static com.player2.playerengine.nativecommands.NativeAccess.get;
import static com.player2.playerengine.nativecommands.NativeAccess.set;

/**
 * Keep the SCHEMATIC owner's pending acquisition through a transient
 * higher-priority chain (especially Mob Defense).
 *
 * The native child itself is stopped by the existing task runner. We only
 * retain its verified material target/high-water/watchdog, then construct a
 * NEW native child on resume. No parallel scheduler or combat override.
 */
public final class SchematicAcquireResume {
    private static final Map<SchematicProjectTask, Boolean> PENDING =
        Collections.synchronizedMap(new WeakHashMap<>());

    private SchematicAcquireResume() {}

    /**
     * Called from SchematicProjectTask.afterChildrenStopped(), ONLY after
     * the native task system has approved a transient suspension.
     * A non-ACQUIRE action retains the coordinator's original reset behavior.
     */
    public static boolean hold(SchematicProjectTask owner) {
        SchematicProjectTask.Environment world = null;
        try {
            Object action = get(owner,"action");
            if (action == null || !"ACQUIRE".equals(String.valueOf(get(action,"kind"))))
                return false;

            world=(SchematicProjectTask.Environment)get(owner,"environment");
            if (world == null) return false;
            String item=(String)get(action,"item");
            int target=((Number)get(action,"target")).intValue();
            if (item == null || target < 1) return false;

            world.pauseBuilder();
            PENDING.put(owner,Boolean.TRUE);
            world.report("SCHEMATIC_ACQUIRE_SUSPENDED",
                "item="+item+" absoluteTarget="+target+
                " child_stopped_by_higher_priority_chain=true"+
                " watchdog_preserved=true");
            return true;
        } catch (Exception | LinkageError failure) {
            try {
                if(world != null)world.report("SCHEMATIC_ACQUIRE_SUSPEND_FAILED",
                    failure.getClass().getSimpleName()+": "+failure.getMessage());
            }catch(Exception ignored){}
            PENDING.remove(owner);
            return false; // use the original coordinator's conservative cleanup
        }
    }

    /**
     * Invoked at the beginning of pollAction(), in the ordinary onTick()
     * exception boundary. No runUserTask, no new top-level goal, no depot
     * travel, and critically NO resetting of Action.idle/ticks/highWater.
     */
    public static void beforePoll(SchematicProjectTask owner) throws Exception {
        if (!Boolean.TRUE.equals(PENDING.get(owner)))return;
        Object action=get(owner,"action");
        if (action==null || !"ACQUIRE".equals(String.valueOf(get(action,"kind")))) {
            PENDING.remove(owner);
            return;
        }

        String item=(String)get(action,"item");
        int target=((Number)get(action,"target")).intValue();
        if(item==null||target<1)
            throw new IllegalStateException("Transient resource acquisition has no verified target");

        SchematicProjectTask.Environment world=
            (SchematicProjectTask.Environment)get(owner,"environment");
        if(world==null)throw new IllegalStateException("Transient schematic world unavailable");

        int held=world.inventory().getOrDefault(item,0);
        if(held>=target){
            PENDING.remove(owner);
            world.report("SCHEMATIC_ACQUIRE_RESUMED",
                "item="+item+" target="+target+" carried="+held+
                " already_satisfied=true no_native_restart_needed=true");
            return; // pollAction verifies the actual inventory postcondition
        }

        Task fresh=world.acquire(item,target);
        if(fresh==null)throw new IllegalStateException("Native acquisition unavailable: "+item);
        set(fresh,"controller",get(owner,"controller"));
        set(action,"task",fresh); // no replacement Action or altered progress counters
        PENDING.remove(owner);
        world.report("SCHEMATIC_ACQUIRE_RESUMED",
            "item="+item+" absoluteTarget="+target+" carried="+held+
            " native_child_recreated=true no_depot_roundtrip=true"+
            " watchdog_preserved=true");
    }

    /** Only used when the project stops for good, never for transient child stop. */
    public static void forget(SchematicProjectTask owner){PENDING.remove(owner);}

    /** Read-only test/diagnostic hook, does not drive task execution. */
    public static boolean pending(SchematicProjectTask owner){
        return Boolean.TRUE.equals(PENDING.get(owner));
    }
}
