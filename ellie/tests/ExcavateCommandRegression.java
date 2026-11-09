import com.player2.playerengine.tasks.construction.ExcavateAreaTask;
import com.player2.playerengine.nativecommands.ExcavationCommandRoute;
import com.player2.playerengine.tasks.base.TaskSuspensionCause;
import java.util.Arrays;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Pure geometry and command grammar checks; no world or NPC substituted. */
public final class ExcavateCommandRegression {
    static int checks;
    static void check(boolean valid,String reason){checks++;if(!valid)throw new AssertionError(reason);}
    static void reject(Runnable action,String reason){
        try{action.run();throw new AssertionError(reason);}
        catch(IllegalArgumentException expected){checks++;}
    }
    private static Object get(Object o,String field)throws Exception{
        Field f=o.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(o);
    }
    private static void set(Object o,String field,Object value)throws Exception{
        Field f=o.getClass().getDeclaredField(field);f.setAccessible(true);f.set(o,value);
    }
    private static void resume(ExcavateAreaTask t,boolean builderRetained)throws Exception{
        Method m=ExcavateAreaTask.class.getDeclaredMethod("reconcileTransientResume",boolean.class);
        m.setAccessible(true);m.invoke(t,builderRetained);
    }
    public static final class FakeBuilder {
        public Object schematic;
        public Object realSchematic;
        public boolean active=true;
        public int releases;
        public boolean isActive(){return active;}
        public void onLostControl(){active=false;schematic=null;realSchematic=null;releases++;}
    }
    private static void interruptionRegression()throws Exception{
        ExcavateAreaTask t=new ExcavateAreaTask(91,80,277,120,80,296);
        Object plan=new Object();FakeBuilder builder=new FakeBuilder();builder.schematic=plan;
        set(t,"building",true);set(t,"builder",builder);set(t,"ownedSchematic",plan);
        set(t,"remaining",261);set(t,"buildTicks",3000);set(t,"noWorkTicks",40);
        set(t,"scan",500);set(t,"verifyScan",210);
        check(t.prepareForTransientResume(TaskSuspensionCause.HIGHER_PRIORITY_CHAIN),
              "Mob Defense and ForceEquipGearChain suspension must be accepted");
        check(t.prepareForTransientResume(TaskSuspensionCause.GESTURE_OVERLAY),
              "gesture overlay suspension must be accepted");
        for(TaskSuspensionCause cause:TaskSuspensionCause.values())
            if(cause!=TaskSuspensionCause.HIGHER_PRIORITY_CHAIN &&
               cause!=TaskSuspensionCause.GESTURE_OVERLAY)
                check(!t.prepareForTransientResume(cause),"other suspension cause must not become owner bypass");
        t.afterChildrenStopped();
        t.afterChildrenStopped(); // repeated suspension callback must be idempotent
        check(!t.isTerminal(),"temporary chain interruption marked excavation terminal");
        check((Boolean)get(t,"transientSuspended"),"suspension state missing");
        check((Integer)get(t,"acceptedInterrupts")==1,"duplicate interrupt inflated retry budget");
        check(builder.releases==1,"native builder not safely relinquished or released twice");
        check((Integer)get(t,"remaining")==261,"interrupted material progress lost");
        check((Integer)get(t,"buildTicks")==3000&&(Integer)get(t,"noWorkTicks")==40,
              "temporary suspension reset bounded work/watchdog counters");
        resume(t,false);
        check(!(Boolean)get(t,"transientSuspended"),"resume lease not consumed");
        check(!(Boolean)get(t,"building"),"stopped native builder must be rebuilt");
        check((Integer)get(t,"scan")==0&&(Integer)get(t,"verifyScan")==0,
              "resumed native plan must recheck safety before clearing");
        check((Integer)get(t,"remaining")==261,"revalidation erased prior verified progress");
        check((Integer)get(t,"buildTicks")==3000&&(Integer)get(t,"noWorkTicks")==40,
              "restarted Baritone should retain bounded retry history");

        // A second gear chain can interrupt the restarted native plan.
        Object plan2=new Object();FakeBuilder builder2=new FakeBuilder();builder2.schematic=plan2;
        set(t,"building",true);set(t,"builder",builder2);set(t,"ownedSchematic",plan2);
        set(t,"remaining",125);set(t,"buildTicks",3200);
        t.afterChildrenStopped();
        check((Integer)get(t,"acceptedInterrupts")==2,"repeated chain interruption ignored");
        resume(t,false);
        check(!t.isTerminal()&&(Integer)get(t,"remaining")==125,
              "second transient chain stop erased work");
        check((Integer)get(t,"buildTicks")==3200,"second interruption reset total work");

        // If the native process wasn't replaced, do not rebuild its plan.
        set(t,"building",true);set(t,"ownedSchematic",new Object());
        t.afterChildrenStopped();resume(t,true);
        check((Boolean)get(t,"building"),"retained native builder was needlessly restarted");

        // Explicit owner cancellation and abandoned resume are terminal.
        ExcavateAreaTask canceled=new ExcavateAreaTask(1,80,1,2,80,2);
        set(canceled,"building",true);set(canceled,"builder",new FakeBuilder());
        set(canceled,"ownedSchematic",new Object());
        canceled.afterChildrenStopped();
        Method stop=ExcavateAreaTask.class.getDeclaredMethod("onStop",
            com.player2.playerengine.tasks.base.Task.class);
        stop.setAccessible(true);stop.invoke(canceled,new Object[]{null});
        check(canceled.isTerminal()&&!canceled.isSuccessful(),
              "explicit user stop must cancel task");
        check(!canceled.prepareForTransientResume(TaskSuspensionCause.HIGHER_PRIORITY_CHAIN),
              "cancelled task was resurrectable");
        ExcavateAreaTask abandoned=new ExcavateAreaTask(1,80,1,2,80,2);
        abandoned.afterChildrenStopped();
        abandoned.onTransientResumeAbandoned();
        check(abandoned.isTerminal()&&!abandoned.isSuccessful(),
              "abandoned temporary resume must be terminal");
    }
    public static void main(String[]args)throws Exception{
        int[] bounds=ExcavateAreaTask.normalize(120,74,120,100,64,100);
        check(Arrays.equals(bounds,new int[]{100,64,100,120,74,120,4851}),
            "inclusive corners/dimensions must be normalized");
        check(ExcavateAreaTask.normalize(100,64,100,100,64,100)[6]==1,
            "single block allowed");
        check(ExcavateAreaTask.normalize(100,-64,100,100,-64,100)[6]==1,
            "vanilla minimum Y allowed");
        reject(()->ExcavateAreaTask.normalize(0,64,0,100,84,100),
            "unbounded huge area should be refused");
        reject(()->ExcavateAreaTask.normalize(0,320,0,1,320,1),
            "above max build height should be refused");
        reject(()->ExcavateAreaTask.normalize(0,-65,0,1,-65,1),
            "below min build height should be refused");
        check(ExcavationCommandRoute.exact("Ellie, excavate 100 64 100 120 74 120")
                .equals("excavate 100 64 100 120 74 120"),
            "exact NPC owner syntax");
        check(ExcavationCommandRoute.exact("excavate 120 74 120 100 64 100")
                .equals("excavate 120 74 120 100 64 100"),
            "reversed corners still retained for native normalization");
        check(ExcavationCommandRoute.exact("@excavate -30 64 -40 12 68 0")
                .equals("excavate -30 64 -40 12 68 0"),
            "signed coordinates preserve absolute meaning");
        check(ExcavationCommandRoute.exact("excavate 1 2 3")==null,
            "never guess missing coordinates");
        check(ExcavationCommandRoute.exact("excavate this valley")==null,
            "never invent a huge selection from natural language");
        check(ExcavationCommandRoute.exact("excavate 1 2 3 4 5 9999999999")==null,
            "overflow is refused");
        check(ExcavationCommandRoute.exact("get string 64")==null,
            "unrelated material commands unchanged");
        interruptionRegression();
        System.out.println("ExcavateCommandRegression "+checks+
            " bounds and exact-command checks; no game simulation");
    }
}
