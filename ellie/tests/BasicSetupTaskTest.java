import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.setup.BasicSetupTask;
import java.lang.reflect.Method;
import java.util.*;

/** Exercise the production setup state machine with explicit, observable game boundaries.
 * This does not pretend that a Minecraft world or Player2 NPC was simulated.
 */
public final class BasicSetupTaskTest {
    private static final Method TICK;
    static {
        try {
            TICK=BasicSetupTask.class.getDeclaredMethod("onTick");
            TICK.setAccessible(true);
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    static final class Op extends Task {
        private final Runnable operation;
        boolean finished;
        Op(Runnable operation){this.operation=operation;}
        void execute(){if(!finished){operation.run();finished=true;}}
        protected void onStart(){}
        protected Task onTick(){execute();return null;}
        protected void onStop(Task other){}
        protected boolean isEqual(Task other){return this==other;}
        protected String toDebugString(){return "Test native operation";}
        public boolean isFinished(){return finished;}
    }
    static class World implements BasicSetupTask.Environment {
        final Map<String,Integer> counts=new LinkedHashMap<>();
        final List<String> actions=new ArrayList<>();
        int stations, releases, coalRequests, furnaceRequests, workshops;
        boolean home, suppressFurnace, suppressCoal, suppressPlacement;
        public int inventoryCount(String id){return counts.getOrDefault(id,0);}
        public Task acquire(String id,int missing){
            actions.add("get "+id+" "+missing);
            if(id.equals("coal"))coalRequests++;
            if(id.equals("furnace"))furnaceRequests++;
            return new Op(()->{
                if(id.equals("furnace")&&suppressFurnace || id.equals("coal")&&suppressCoal)return;
                counts.merge(id,missing,Integer::sum);
            });
        }
        public Task placeWorkshop(int count){
            actions.add("place stations "+count);
            workshops++;
            return new Op(()->{
                if(suppressPlacement)return;
                stations=count;
                if(count==1)home=true;
                if(count==3){counts.remove("chest");counts.remove("furnace");}
            });
        }
        public boolean workshopVerified(int requested){
            return stations>=requested && home;
        }
        public boolean homeVerified(){return home;}
        public void release(){releases++;}
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static BasicSetupTask run(World world,int limit)throws Exception {
        var task=new BasicSetupTask(world);
        for(int tick=0;tick<limit&&!task.isTerminal();tick++){
            Task next=(Task)TICK.invoke(task);
            if(next instanceof Op operation)operation.execute();
        }
        return task;
    }
    public static void main(String[] args)throws Exception {
        var plan=BasicSetupTask.plannedStages();
        check(plan.length==14,"exact 14 bounded stages");
        check(Arrays.stream(plan).map(s->s.resource()==null?"station"+s.stations():s.resource()).toList().equals(List.of(
            "log","station1","wooden_axe","wooden_pickaxe","cobblestone",
            "stone_pickaxe","stone_axe","stone_shovel","stone_sword","coal",
            "torch","chest","furnace","station3")),"batch-first order and complete stone kit");
        check(plan[0].amount()==12&&plan[4].amount()==20&&
              plan[9].amount()==16&&plan[10].amount()==64,
              "wood/stone/coal bills and torch quantity exact");
        World fresh=new World();
        BasicSetupTask done=run(fresh,400);
        check(done.isSuccessful()&&fresh.home&&fresh.stations==3,"finished requires home and three placed stations");
        check(fresh.actions.getFirst().equals("get log 12"),"first action gathers ALL wood");
        check(fresh.actions.indexOf("place stations 1")<fresh.actions.indexOf("get wooden_axe 1"),
              "first verified bench precedes wooden tool production");
        check(fresh.actions.indexOf("get cobblestone 20")<fresh.actions.indexOf("get stone_pickaxe 1")&&
              fresh.actions.indexOf("get cobblestone 20")<fresh.actions.indexOf("get stone_sword 1"),
              "batch stone before four stone tools");
        check(fresh.actions.indexOf("get coal 16")<fresh.actions.indexOf("get torch 64"),
              "batch coal before torch crafting");
        check(fresh.actions.indexOf("get furnace 1")<fresh.actions.indexOf("place stations 3")&&
              fresh.actions.indexOf("get chest 1")<fresh.actions.indexOf("place stations 3"),
              "chest and furnace crafted before placing at HOME");
        check(fresh.actions.stream().noneMatch(x->x.contains("iron")||x.contains("shield")||
              x.contains("bucket")||x.contains("diamond")||x.contains("lighting")||
              x.contains("shelter")),"no stray survival/gear goals");
        check(fresh.actions.stream().filter(x->x.startsWith("get log")).count()==1&&
              fresh.actions.stream().filter(x->x.startsWith("get cobblestone")).count()==1,
              "each raw material batch is a single acquisition request");
        World missing=new World();missing.suppressFurnace=true;
        BasicSetupTask failed=run(missing,400);
        check(failed.isTerminal()&&!failed.isSuccessful()&&
              failed.controlledReason().contains("furnace")&&missing.furnaceRequests==2,
              "furnace output missing twice produces a specific bounded failure");
        World placeMissing=new World();placeMissing.suppressPlacement=true;
        BasicSetupTask blocked=run(placeMissing,100);
        check(blocked.isTerminal()&&!blocked.isSuccessful()&&
              blocked.controlledReason().contains("Workshop"),"missing physical bench never passes");
        World partial=new World();partial.counts.put("log",12);
        partial.counts.put("stone_pickaxe",1);
        BasicSetupTask reused=run(partial,400);
        check(reused.isSuccessful()&&partial.actions.stream().noneMatch(x->x.startsWith("get log")||
              x.startsWith("get stone_pickaxe")),"reuse owned batch and tools");
        World cannotMine=new World();cannotMine.suppressCoal=true;
        BasicSetupTask lacking=run(cannotMine,400);
        check(lacking.isTerminal()&&!lacking.isSuccessful()&&
              lacking.controlledReason().contains("coal"),"missing coal stops specifically before fake torches");
        System.out.println("BasicSetupTaskTest: 16 integration-contract checks passed; game boundaries substituted");
    }
}
