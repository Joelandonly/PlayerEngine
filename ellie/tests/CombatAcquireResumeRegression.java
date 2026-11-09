import com.player2.playerengine.schematic.*;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.nativecommands.NativeAccess;
import java.util.*;

/**
 * Execute the real SchematicProjectTask, patched with the exact shipping ASM
 * hook, using only the EXISTING SchematicProjectTest world/native substitutes.
 * This is NOT a live NPC test.
 */
public final class CombatAcquireResumeRegression {
    static int checks;
    static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    static Object action(SchematicProjectTask task)throws Exception {
        return NativeAccess.get(task,"action");
    }
    static Object field(Object object,String field)throws Exception{
        return NativeAccess.get(object,field);
    }
    static Task tick(SchematicProjectTask task)throws Exception {
        return (Task)SchematicProjectTest.TICK.invoke(task);
    }

    static final class StringEnv extends SchematicProjectTest.Env {
        final List<Integer> nativeTargets=new ArrayList<>();
        int travelStarts;
        StringEnv(){super(SchematicProjectTest.small());
            allocateDepot(-1);placedDepots.add(0);}
        @Override public boolean preflight(){bill=Map.of("minecraft:string",8);return true;}
        @Override public Task acquire(String item,int target){
            check(item.equals("minecraft:string"),"wrong material on resume: "+item);
            nativeTargets.add(target);
            return super.acquire(item,target);
        }
        @Override public Task travelToDepot(){travelStarts++;return super.travelToDepot();}
    }
    static SchematicProjectTask begin(StringEnv env)throws Exception {
        SchematicProjectTask task=SchematicProjectTest.task(env,false);
        Task child=tick(task); // create ACQUIRE, do NOT finish its native leaf
        check(child instanceof SchematicProjectTest.Leaf,"expected active native gatherer");
        check("ACQUIRE".equals(String.valueOf(field(action(task),"kind"))),"action not ACQUIRE");
        check(Objects.equals(field(action(task),"item"),"minecraft:string"),"wrong string goal");
        check(((Number)field(action(task),"target")).intValue()==8,"wrong absolute target");
        return task;
    }
    public static void main(String[]args)throws Exception {
        StringEnv env=new StringEnv();
        SchematicProjectTask t=begin(env);
        Object original=action(t);
        env.near=false; // spider hunting occurs away from depot
        for(int n=0;n<33;n++)tick(t); // accumulate bounded no-output history
        int beforeTicks=((Number)field(original,"ticks")).intValue();
        int beforeIdle=((Number)field(original,"idle")).intValue();
        check(beforeTicks>=33&&beforeIdle>=33,"preemption must preserve prior watchdog state");
        env.inv.put("minecraft:string",3); // three actual string gained before defense
        t.afterChildrenStopped();
        check(action(t)==original,"transient Mob Defense discarded ACQUIRE intent");
        check(SchematicAcquireResume.pending(t),"temporary acquisition lease not retained");
        check(!env.closed,"transient defense accidentally closed the project");
        check(env.travelStarts==0,"transient suspension scheduled depot travel");
        Task firstResume=tick(t);
        check(firstResume instanceof SchematicProjectTest.Leaf,"native child not recreated");
        check(firstResume!=field(original,"task")?false:true,"active action child not refreshed");
        check(action(t)==original,"new action replaced old goal/counters");
        check(env.nativeTargets.equals(List.of(8,8)),"native request must remain ABSOLUTE 8, not missing 5");
        check(env.travelStarts==0,"resuming acquisition forced depot roundtrip");
        check(!SchematicAcquireResume.pending(t),"lease not cleared after successful resume");
        check(((Number)field(original,"ticks")).intValue()>=beforeTicks+1,"action tick history reset");
        check(env.events.stream().anyMatch(s->s.contains("SCHEMATIC_ACQUIRE_RESUMED")&&
            s.contains("carriedAfter=3")&&s.contains("remaining=5")&&s.contains("next=ACQUIRE")),
            "correlated progress evidence not logged");

        // Native child stopped again by a skeleton. Retry history must survive
        // and the parent must NOT schedule TRAVEL while items remain missing.
        int ticksAfterFirst=((Number)field(original,"ticks")).intValue();
        env.inv.put("minecraft:string",5);
        t.afterChildrenStopped();
        Task secondResume=tick(t);
        check(secondResume instanceof SchematicProjectTest.Leaf,"second resume child missing");
        check(action(t)==original,"repeated defense reset action");
        check(env.nativeTargets.equals(List.of(8,8,8)),"repeat recreated wrong absolute target");
        check(((Number)field(original,"ticks")).intValue()>ticksAfterFirst,"second interrupt reset attempt history");
        check(env.travelStarts==0,"second interrupt triggered unneeded depot travel");

        // While suspended, a legitimate loot pickup completes the requirement.
        // Do NOT resurrect a stopped child merely to re-collect full inventory.
        t.afterChildrenStopped();
        env.inv.put("minecraft:string",8);
        int attempts=env.nativeTargets.size();
        Task alreadyComplete=tick(t);
        check(alreadyComplete==null,"completed ACQUIRE should verify actual stock");
        check(action(t)==null,"completed action not cleared");
        check(env.nativeTargets.size()==attempts,"full inventory launched redundant hunt");
        check(env.travelStarts==0,"verified completion incorrectly traveled during ACQUIRE");
        check(env.events.stream().anyMatch(s->s.contains("SCHEMATIC_ACQUIRE_RESUMED")&&
            s.contains("remaining=0")&&s.contains("next=VERIFY")),
            "pickup completion lacked observable resume evidence");
        tick(t); // now TRAVEL is legitimate because gathering is complete
        check(env.travelStarts==1,"depot travel must wait for acquisition completion");

        // Explicit user cancellation is terminal, not a hidden resume request.
        StringEnv stopped=new StringEnv();
        SchematicProjectTask cancel=begin(stopped);
        cancel.afterChildrenStopped();
        check(SchematicAcquireResume.pending(cancel),"missing pending state before explicit cancellation");
        SchematicProjectTest.STOP.invoke(cancel,new Object[]{null});
        check(cancel.isTerminal()&&!cancel.isSuccessful()&&stopped.closed,
            "explicit stop did not end schematic");
        check(!SchematicAcquireResume.pending(cancel),"explicit stop retained resurrection lease");
        check(tick(cancel)==null,"canceled project was restarted by following tick");

        // Abandoned transient resume is terminal; a changed world is too.
        StringEnv abandoned=new StringEnv();
        SchematicProjectTask lost=begin(abandoned);
        lost.afterChildrenStopped();
        lost.onTransientResumeAbandoned();
        check(lost.isTerminal()&&!SchematicAcquireResume.pending(lost),
            "abandoned transient resume resurrected project");

        StringEnv changed=new StringEnv();
        SchematicProjectTask world=begin(changed);
        world.afterChildrenStopped();
        changed.changed=true;
        tick(world);
        check(world.isTerminal()&&!world.isSuccessful()&&!SchematicAcquireResume.pending(world),
            "world change retained a pending acquisition");

        System.out.println("CombatAcquireResumeRegression: "+checks+
            " checks passed; patched coordinator, substituted game and native execution");
    }
}
