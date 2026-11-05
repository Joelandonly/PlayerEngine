import com.player2.playerengine.*;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.setup.*;
import java.lang.reflect.*;
import java.util.*;

/** Executes the production setup and workshop state machines together.
 * Inventory, acquisition, placement and light propagation are substituted boundaries.
 * This is NOT evidence of Minecraft pathfinding, recipe execution or survival success.
 */
public final class SetupChainTest {
    static final Method SETUP, BASE;
    static { try {
        SETUP=EllieSetupTask.class.getDeclaredMethod("onTick"); SETUP.setAccessible(true);
        BASE=StarterBaseTask.class.getDeclaredMethod("onTick"); BASE.setAccessible(true);
    } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    static class World extends SurfaceLightingTest.World {
        final PlayerEngineController controller;
        boolean obstructed, uneven, repaired, neverPlace, noLighting;
        int repairs;
        World(PlayerEngineController c) { controller=c; inventory.clear(); }
        public int count(String id) { return controller.getItemStorage().counts.getOrDefault(id,0); }
        public Task acquire(String id) { return new SurfaceLightingTest.Op(()->controller.getItemStorage().counts.merge(id,1,Integer::sum)); }
        public Task place(StarterBaseLayout.Placement p) { return new SurfaceLightingTest.Op(()->{
            if (!neverPlace) { blocks.add(p); controller.getItemStorage().counts.merge(p.resource(),-1,Integer::sum); if(p.resource().equals("torch"))torchPlaced++; }
        }); }
        public boolean safe(StarterBaseLayout.Placement p) { return !obstructed && (!uneven||repaired); }
        public List<StarterBaseLayout.Placement> loadExisting() { return blocks.stream().anyMatch(SurfaceLightingTest.BASE::contains)?SurfaceLightingTest.BASE:List.of(); }
        public List<StarterBaseLayout.Placement> terrainPlan(List<StarterBaseLayout.Placement> p) {
            return uneven?List.of(new StarterBaseLayout.Placement(new StarterBaseLayout.Pos(10,63,10),"cobblestone")):List.of();
        }
        public boolean prepared(StarterBaseLayout.Placement p) { return repaired; }
        public boolean canPrepare(StarterBaseLayout.Placement p) { return !obstructed; }
        public Task prepareTerrain(StarterBaseLayout.Placement p) { return new SurfaceLightingTest.Op(()->{repaired=true;repairs++;}); }
        public SurfaceLightingPlan.Survey surveyLighting(List<StarterBaseLayout.Placement> p,Set<StarterBaseLayout.Pos> skip) { throw new AssertionError("setup must not survey or place surface lighting"); }
        public boolean canPlace(StarterBaseLayout.Pos p) { return !noLighting && super.canPlace(p); }
    }
    static class Run {
        final PlayerEngineController c=new PlayerEngineController(null);
        final World world=new World(c);
        final EllieSetupTask task=new EllieSetupTask();
        final List<String> requests=new ArrayList<>();
        final Set<Task> supplied=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean missingIron;
        int ticks;
        Run() throws Exception {
            task.controller=c;
            Field f=EllieSetupTask.class.getDeclaredField("baseTask"); f.setAccessible(true);
            StarterBaseTask base=new StarterBaseTask(world);base.controller=c;f.set(task,base);
        }
        void execute(Task next) throws Exception {
            if(next instanceof StarterBaseTask base)execute((Task)BASE.invoke(base));
            else if(next instanceof SurfaceLightingTest.Op op)op.execute();
            else if(next instanceof TaskCatalogue.NativeAcquisition get && supplied.add(next)) {
                requests.add(get.id);
                if(!(missingIron && get.id.startsWith("iron_"))) c.getItemStorage().counts.merge(get.id,get.count,Math::max);
            }
        }
        void run() throws Exception {
            Field f=EllieSetupTask.class.getDeclaredField("baseTask");f.setAccessible(true);
            ((StarterBaseTask)f.get(task)).prepareExisting();
            for(;ticks<16000&&!task.isTerminal();ticks++)execute((Task)SETUP.invoke(task));
            check(task.isTerminal(),"setup must terminate within a finite work budget");
        }
    }
    static void check(boolean ok,String reason) { if(!ok)throw new AssertionError(reason); }
    public static void main(String[] args) throws Exception {
        Run fresh=new Run();fresh.run();
        check(fresh.task.isSuccessful(),"fresh kit and three verified stations complete together");
        check(fresh.requests.equals(Arrays.stream(EllieSetupTask.plannedSteps()).map(s->s.resource).toList()),"all eleven requests retain exact order; no excluded acquisitions");
        check(fresh.world.blocks.stream().filter(p->!p.resource().equals("torch")).count()==3,"all stations physically verified");
        check(fresh.world.torchPlaced==0 && fresh.c.getItemStorage().counts.get("torch")==32,"all 32 torches remain in inventory; none placed");
        check(fresh.requests.indexOf("shield")<fresh.requests.indexOf("torch") && fresh.requests.indexOf("iron_sword")<fresh.requests.indexOf("torch"),"shield and iron tools precede torch acquisition");
        Run partial=new Run();partial.c.getItemStorage().counts.put("stone_axe",1);partial.c.getItemStorage().counts.put("stone_pickaxe",2);
        partial.world.blocks.addAll(SurfaceLightingTest.BASE);partial.run();
        check(partial.task.isSuccessful()&&!partial.requests.contains("stone_axe")&&!partial.requests.contains("stone_pickaxe"),"partial inventory reused without repeating owned tools");
        check(!partial.requests.contains("chest")&&!partial.requests.contains("furnace"),"existing placed stations credit consumed inventory");
        Run uneven=new Run();uneven.world.uneven=true;uneven.run();
        check(uneven.task.isSuccessful()&&uneven.world.repairs==1,"uneven ground is repaired before station verification");
        Run missing=new Run();missing.missingIron=true;missing.run();
        check(missing.task.isBlocked()&&missing.task.controlledReason().contains("iron_pickaxe"),"missing iron ends specifically BLOCKED, never skipped or completed");
        Run obstructed=new Run();obstructed.world.obstructed=true;obstructed.run();
        check(obstructed.task.isBlocked()&&obstructed.task.controlledReason().contains("Unsafe workstation"),"obstructed table without safe repair blocks the whole setup");
        Run falsePlace=new Run();falsePlace.world.neverPlace=true;falsePlace.run();
        check(falsePlace.task.isBlocked()&&!falsePlace.task.isSuccessful(),"finished child without a verified placed block cannot complete setup");
        Run dark=new Run();dark.world.noLighting=true;dark.run();
        check(dark.task.isSuccessful()&&dark.world.torchPlaced==0,"dark outdoor samples with no safe torch position cannot block setup");
        System.out.println("SetupChainTest: 7 complete-chain scenarios passed (game boundaries substituted; no live survival claim)");
    }
}
