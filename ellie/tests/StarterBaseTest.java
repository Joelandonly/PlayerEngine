import com.player2.playerengine.tasks.setup.*;
import com.player2.playerengine.tasks.base.Task;
import java.util.*;
import java.lang.reflect.*;

public final class StarterBaseTest {
    static final class Op extends Task {
        boolean done;
        final Runnable action;
        Op(Runnable action) {this.action=action;}
        void execute() {if(!done){action.run();done=true;}}
        public boolean isFinished(){return done;}
    }
    static final class World implements StarterBaseTask.Environment {
        final List<StarterBaseLayout.Placement> plan=StarterBaseLayout.at(new StarterBaseLayout.Pos(10,64,10));
        final Set<StarterBaseLayout.Placement> placed=new HashSet<>();
        final Map<String,Integer> inventory=new HashMap<>(Map.of("crafting_table",1,"furnace",1,"chest",1));
        boolean safe=true, falseCompletion;
        int acquisitions,placements;
        public List<StarterBaseLayout.Placement> loadOrSelect(){return plan;}
        public boolean matches(StarterBaseLayout.Placement p){return placed.contains(p);}
        public boolean safe(StarterBaseLayout.Placement p){return safe;}
        public int count(String item){return inventory.getOrDefault(item,0);}
        public Task acquire(String item){acquisitions++;return new Op(()->inventory.merge(item,1,Integer::sum));}
        public Task place(StarterBaseLayout.Placement p){placements++;return new Op(()->{if(!falseCompletion){placed.add(p);inventory.merge(p.resource(),-1,Integer::sum);}});}
    }
    static Task tick(StarterBaseTask task)throws Exception{
        Method tick=StarterBaseTask.class.getDeclaredMethod("onTick");tick.setAccessible(true);return (Task)tick.invoke(task);
    }
    static void run(StarterBaseTask task)throws Exception{
        for(int i=0;i<100&&!task.isTerminal();i++){Task child=tick(task);if(child instanceof Op op)op.execute();}
    }
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static StarterBaseTask completedBase()throws Exception {
        World world=new World();world.placed.addAll(world.plan);StarterBaseTask task=new StarterBaseTask(world);tick(task);return task;
    }
    public static void main(String[] args)throws Exception{
        int passed=0;
        World world=new World();StarterBaseTask task=new StarterBaseTask(world);run(task);
        check(task.isSuccessful()&&world.placements==3&&world.placed.size()==3,"all three blocks must be verified");passed++;
        check(task.verifiedPlacedCount("chest")==1&&world.inventory.get("chest")==0,"placed chest must replace consumed chest item");passed++;
        world.placed.remove(world.plan.getFirst());check(!task.isSuccessful(),"removed workstation cannot remain verified");passed++;
        World unsafe=new World();unsafe.safe=false;StarterBaseTask blocked=new StarterBaseTask(unsafe);run(blocked);
        check(blocked.isTerminal()&&!blocked.isSuccessful()&&unsafe.placements==0,"unsafe site must not be modified");passed++;
        World fakeSuccess=new World();fakeSuccess.falseCompletion=true;StarterBaseTask falseTask=new StarterBaseTask(fakeSuccess);run(falseTask);
        check(falseTask.isTerminal()&&!falseTask.isSuccessful(),"child finished without placed block is not success");passed++;
        World empty=new World();empty.inventory.clear();StarterBaseTask supplied=new StarterBaseTask(empty);run(supplied);
        check(supplied.isSuccessful()&&empty.acquisitions==3,"native acquisition must precede missing placement items");passed++;
        check(completedBase().isSuccessful(),"existing exact base should be reused");passed++;
        World obstructed=new World();obstructed.placed.addAll(obstructed.plan);StarterBaseTask changed=new StarterBaseTask(obstructed);tick(changed);
        obstructed.safe=false;check(!changed.isSuccessful(),"obstructed workstation must not remain usable/verified");passed++;
        var plan=StarterBaseLayout.at(new StarterBaseLayout.Pos(-10,64,-10));
        StarterBaseLayout.Probe clear=new StarterBaseLayout.Probe(){public boolean air(StarterBaseLayout.Pos p){return true;}public boolean safeFloor(StarterBaseLayout.Pos p){return true;}public boolean matches(StarterBaseLayout.Placement p){return false;}};
        check(StarterBaseLayout.safe(plan,clear),"clear supported pad rejected");passed++;
        StarterBaseLayout.Probe hole=new StarterBaseLayout.Probe(){public boolean air(StarterBaseLayout.Pos p){return true;}public boolean safeFloor(StarterBaseLayout.Pos p){return false;}public boolean matches(StarterBaseLayout.Placement p){return false;}};
        check(StarterBaseLayout.select(new StarterBaseLayout.Pos(0,64,0),hole).isEmpty(),"unsupported pad selected");passed++;
        var origin=new StarterBaseLayout.Pos(-1,64,-1);var selected=StarterBaseLayout.select(origin,clear).orElseThrow();
        check(selected.stream().noneMatch(p->Math.abs(p.pos().x()-origin.x())<=1&&Math.abs(p.pos().z()-origin.z())<=1),"initial feet and neighboring edge-overlap cells must stay clear");passed++;
        World resumable=new World();StarterBaseTask resumed=new StarterBaseTask(resumable);
        Task first=tick(resumed);((Op)first).execute();
        check(resumed.prepareForTransientResume(com.player2.playerengine.tasks.base.TaskSuspensionCause.HIGHER_PRIORITY_CHAIN),"safety preemption must be resumable");
        resumed.afterChildrenStopped();run(resumed);
        check(resumed.isSuccessful()&&resumable.placements==3,"resume must not duplicate already verified placement");passed++;
        World stalled=new World();StarterBaseTask waiting=new StarterBaseTask(stalled);
        for(int i=0;i<602&&!waiting.isTerminal();i++)tick(waiting);
        check(waiting.isTerminal()&&!waiting.isSuccessful(),"placement must not wait forever");passed++;
        var reuseOrigin=new StarterBaseLayout.Pos(118,63,-3);
        var existingBench=new StarterBaseLayout.Pos(118,63,-1);
        StarterBaseLayout.Probe benchProbe=new StarterBaseLayout.Probe(){
            public boolean air(StarterBaseLayout.Pos p){return !p.equals(existingBench);}
            public boolean safeFloor(StarterBaseLayout.Pos p){return p.y()==62;}
            public boolean matches(StarterBaseLayout.Placement p){return p.resource().equals("crafting_table")&&p.pos().equals(existingBench);}
        };
        var reusedPlan=StarterBaseLayout.reuse(reuseOrigin,benchProbe).orElseThrow();
        check(reusedPlan.getFirst().pos().equals(existingBench)&&reusedPlan.size()==3,"supplied nearby table must anchor the complete workshop");passed++;
        var oldPlan=StarterBaseLayout.at(new StarterBaseLayout.Pos(119,63,-6));
        check(StarterBaseLayout.reuseUnstarted(oldPlan,reuseOrigin,benchProbe).equals(reusedPlan),"empty saved layout must recover around nearby bench");passed++;
        StarterBaseLayout.Probe startedProbe=new StarterBaseLayout.Probe(){
            public boolean air(StarterBaseLayout.Pos p)throws Exception{return benchProbe.air(p);}
            public boolean safeFloor(StarterBaseLayout.Pos p)throws Exception{return benchProbe.safeFloor(p);}
            public boolean matches(StarterBaseLayout.Placement p)throws Exception{return p.equals(oldPlan.get(1))||benchProbe.matches(p);}
        };
        check(StarterBaseLayout.reuseUnstarted(oldPlan,reuseOrigin,startedProbe).equals(oldPlan),"existing placed furnace must pin recorded project positions");passed++;
        StarterBaseLayout.Probe inaccessible=new StarterBaseLayout.Probe(){
            public boolean air(StarterBaseLayout.Pos p){return false;}
            public boolean safeFloor(StarterBaseLayout.Pos p){return true;}
            public boolean matches(StarterBaseLayout.Placement p)throws Exception{return benchProbe.matches(p);}
        };
        check(StarterBaseLayout.reuse(reuseOrigin,inaccessible).isEmpty(),"bench without standing clearance must not be called reusable");passed++;
        World reuseWorld=new World();reuseWorld.inventory.remove("crafting_table");reuseWorld.placed.add(reuseWorld.plan.getFirst());StarterBaseTask reuseTask=new StarterBaseTask(reuseWorld);run(reuseTask);
        check(reuseTask.isSuccessful()&&reuseWorld.acquisitions==0&&reuseWorld.placements==2,"placed bench must require only furnace and chest placement, without reacquisition");passed++;
        var dir=java.nio.file.Files.createTempDirectory("ellie-base-journal-test-");
        try {
            var journal=CoreGoalJournal.open(dir,"test-character","starter_base_layout");
            journal.put("BASE_LAYOUT_COMPACT_V3",0,"minecraft:overworld","10,64,10");
            var reopened=CoreGoalJournal.open(dir,"test-character","starter_base_layout");
            check(reopened.isPersistent()&&reopened.get("detail","").equals("10,64,10")&&reopened.get("resource","").equals("minecraft:overworld"),"anchor journal must survive reopen");passed++;
            check(CoreGoalJournal.open(dir,"different-character","starter_base_layout").get("status","none").equals("none"),"base journals must not cross character ownership");passed++;
        } finally {try(var paths=java.nio.file.Files.walk(dir)){for(var p:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(p);}}
        System.out.println("StarterBaseTest: "+passed+" passed; layout/task handoff simulation, native Minecraft placement not executed");
    }
}
