import com.player2.playerengine.tasks.setup.*;
import com.player2.playerengine.tasks.base.Task;
import java.util.*;

/** The native setup may clear a handful of cells, but may never manufacture a foundation. */
public final class SetupTerraformTest {
 static final class Op extends Task {
   boolean done; final Runnable action;
   Op(Runnable action){this.action=action;}
   public boolean isFinished(){return done;}
   void execute(){action.run();done=true;}
 }
 static class World implements StarterBaseTask.Environment {
  List<StarterBaseLayout.Placement> plan=StarterBaseLayout.at(new StarterBaseLayout.Pos(10,64,10));
  Map<StarterBaseLayout.Pos,String> blocks=new HashMap<>();
  int cleared,placed; boolean protectedSite;
  World(){
   for(var p:plan)blocks.put(p.pos().offset(0,-1,0),"stone");
   blocks.put(plan.getFirst().pos(),"stone");
   blocks.put(plan.getFirst().pos().offset(0,1,0),"stone");
  }
  String at(StarterBaseLayout.Pos p){return blocks.getOrDefault(p,"air");}
  public List<StarterBaseLayout.Placement> loadOrSelect(){return plan;}
  public List<StarterBaseLayout.Placement> terrainPlan(List<StarterBaseLayout.Placement> p){return StarterBaseLayout.terrain(p,p.getFirst().pos());}
  public boolean prepared(StarterBaseLayout.Placement job){
   if(!job.resource().equals("air"))throw new AssertionError("Foundation job generated: "+job);
   return at(job.pos()).equals("air")||plan.stream().anyMatch(p->p.pos().equals(job.pos())&&matches(p));
  }
  public boolean canPrepare(StarterBaseLayout.Placement job){return !protectedSite && job.resource().equals("air");}
  public Task prepareTerrain(StarterBaseLayout.Placement job){
   if(!job.resource().equals("air"))throw new AssertionError("Floor placement attempted");
   return new Op(()->{blocks.put(job.pos(),"air");cleared++;});
  }
  public boolean matches(StarterBaseLayout.Placement p){return at(p.pos()).equals(p.resource());}
  public boolean safe(StarterBaseLayout.Placement p){
   return at(p.pos().offset(0,-1,0)).equals("stone")
    &&at(p.pos().offset(0,1,0)).equals("air")
    &&(at(p.pos()).equals("air")||matches(p));
  }
  public int count(String resource){return 100;}
  public Task acquire(String item){throw new AssertionError("Unexpected floor/material acquisition");}
  public Task place(StarterBaseLayout.Placement p){
   check(terrainPlan(plan).stream().allMatch(this::prepared),"all clearance must precede workstation placement");
   return new Op(()->{blocks.put(p.pos(),p.resource());placed++;});
  }
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void run(StarterBaseTask task)throws Exception{
  var tick=StarterBaseTask.class.getDeclaredMethod("onTick");tick.setAccessible(true);
  for(int i=0;i<200&&!task.isTerminal();i++){
   Task child=(Task)tick.invoke(task);if(child instanceof Op op)op.execute();
  }
 }
 public static void main(String[] args)throws Exception{
  World world=new World();StarterBaseTask task=new StarterBaseTask(world);run(task);
  check(task.isSuccessful()&&world.cleared==2&&world.placed==3,
        "clear only obstructed station cells; no platform and one chest");
  check(StarterBaseLayout.terrain(world.plan,world.plan.getFirst().pos()).size()==6,
        "terrain jobs must be six clearance cells, not a platform");
  World protectedWorld=new World();protectedWorld.protectedSite=true;
  StarterBaseTask blocked=new StarterBaseTask(protectedWorld);run(blocked);
  check(!blocked.isSuccessful()&&blocked.isTerminal()&&protectedWorld.cleared==0,
        "protected land must not be excavated");
  StarterBaseLayout.Probe supported=new StarterBaseLayout.Probe(){
   public boolean air(StarterBaseLayout.Pos p){return p.y()>=64;}
   public boolean safeFloor(StarterBaseLayout.Pos p){return p.y()==63;}
   public boolean matches(StarterBaseLayout.Placement p){return false;}
  };
  check(StarterBaseLayout.selectBuildable(new StarterBaseLayout.Pos(0,64,0),supported).isPresent(),
        "supported surface must be selectable");
  StarterBaseLayout.Probe hole=new StarterBaseLayout.Probe(){
   public boolean air(StarterBaseLayout.Pos p){return true;}
   public boolean safeFloor(StarterBaseLayout.Pos p){return false;}
   public boolean matches(StarterBaseLayout.Placement p){return false;}
   public boolean canFound(StarterBaseLayout.Pos p){return true;}
  };
  check(StarterBaseLayout.selectBuildable(new StarterBaseLayout.Pos(0,64,0),hole).isEmpty(),
        "unsupported floors must never be filled automatically");

  // A water/void location must have a small native support fallback, not be rejected.
  StarterBaseLayout.Probe water=new StarterBaseLayout.Probe(){
   public boolean air(StarterBaseLayout.Pos p){return p.y()>=64;}
   public boolean safeFloor(StarterBaseLayout.Pos p){return false;}
   public boolean matches(StarterBaseLayout.Placement p){return false;}
   public boolean canClear(StarterBaseLayout.Pos p){return p.y()>=64;}
   public boolean canFound(StarterBaseLayout.Pos p){return p.y()==63;}
  };
  check(StarterBaseLayout.select(new StarterBaseLayout.Pos(0,64,0),water).isEmpty()
        &&StarterBaseLayout.selectBuildable(new StarterBaseLayout.Pos(0,64,0),water).isEmpty()
        &&StarterBaseLayout.selectPlatform(new StarterBaseLayout.Pos(0,64,0),water).isPresent(),
        "water: build a compact support platform only when there is no natural site");
  var platformJobs=StarterBaseLayout.terrain(world.plan,world.plan.getFirst().pos(),true);
  var floorCells=StarterBaseLayout.supportCells(world.plan);
  check(platformJobs.stream().filter(p->p.resource().equals("cobblestone")).count()==8
       &&platformJobs.stream().filter(p->p.resource().equals("air")).count()==16
       &&platformJobs.stream().map(StarterBaseLayout.Placement::pos).distinct().count()==24
       &&floorCells.size()==8,
       "platform is 3 load-bearing station tiles plus five walkway tiles, no self-mine jobs");
  World ocean=new World(){int fills;
   { blocks.clear();for(var floor:StarterBaseLayout.supportCells(plan))blocks.put(floor,"water"); }
   public List<StarterBaseLayout.Placement> terrainPlan(List<StarterBaseLayout.Placement> p){return StarterBaseLayout.terrain(p,p.getFirst().pos(),true);}
   public boolean prepared(StarterBaseLayout.Placement j){
    if(j.resource().equals("air")){
     if(floorCells.contains(j.pos()))throw new AssertionError("Cannot clear foundation");
     return at(j.pos()).equals("air")||plan.stream().anyMatch(p->p.pos().equals(j.pos())&&matches(p));
    }
    return at(j.pos()).equals("stone")||at(j.pos()).equals("cobblestone");
   }
   public boolean canPrepare(StarterBaseLayout.Placement j){return true;}
   public Task prepareTerrain(StarterBaseLayout.Placement j){
    if(j.resource().equals("cobblestone"))return new Op(()->{blocks.put(j.pos(),"cobblestone");fills++;});
    return new Op(()->{blocks.put(j.pos(),"air");cleared++;});
   }
   public boolean safe(StarterBaseLayout.Placement p){
    return (at(p.pos().offset(0,-1,0)).equals("stone")||at(p.pos().offset(0,-1,0)).equals("cobblestone"))
     &&at(p.pos().offset(0,1,0)).equals("air")&&(at(p.pos()).equals("air")||matches(p));
   }
   public Task place(StarterBaseLayout.Placement p){
    check(terrainPlan(plan).stream().allMatch(this::prepared),"platform must finish before placing stations");
    return new Op(()->{blocks.put(p.pos(),p.resource());placed++;});
   }
  };
  StarterBaseTask floating=new StarterBaseTask(ocean);run(floating);
  check(floating.isSuccessful() && ocean.placed==3
        &&floorCells.stream().allMatch(p->ocean.at(p).equals("cobblestone")),
        "floating setup must retain compact support and three stations");
  // If navigation or a miner removes an established support, repair instead of
  // leaving a finished-but-unsafe base. Bounded retries avoid the earlier loop.
  World damagedPlatform=new World(){boolean damaged;
   {blocks.clear();for(var floor:StarterBaseLayout.supportCells(plan))blocks.put(floor,"water");}
   public List<StarterBaseLayout.Placement> terrainPlan(List<StarterBaseLayout.Placement> p){return StarterBaseLayout.terrain(p,p.getFirst().pos(),true);}
   public boolean prepared(StarterBaseLayout.Placement j){return j.resource().equals("air")?
    at(j.pos()).equals("air")||plan.stream().anyMatch(p->p.pos().equals(j.pos())&&matches(p)):
    at(j.pos()).equals("cobblestone");}
   public boolean canPrepare(StarterBaseLayout.Placement j){return true;}
   public Task prepareTerrain(StarterBaseLayout.Placement j){
    if(j.resource().equals("air"))return new Op(()->{blocks.put(j.pos(),"air");cleared++;});
    return new Op(()->{blocks.put(j.pos(),"cobblestone");});
   }
   public boolean safe(StarterBaseLayout.Placement p){return at(p.pos().offset(0,-1,0)).equals("cobblestone");}
   public Task place(StarterBaseLayout.Placement p){
    return new Op(()->{blocks.put(p.pos(),p.resource());placed++;
       if(!damaged){damaged=true;blocks.put(plan.getFirst().pos().offset(0,-1,0),"air");}
    });
   }
  };
  StarterBaseTask repairedPlatform=new StarterBaseTask(damagedPlatform);run(repairedPlatform);
  check(repairedPlatform.isSuccessful()&&damagedPlatform.placed==3&&
      damagedPlatform.at(damagedPlatform.plan.getFirst().pos().offset(0,-1,0)).equals("cobblestone"),
      "lost foundation must be restored and fully reverified");
  StarterBaseLayout.Probe elevatedWater=new StarterBaseLayout.Probe(){
   public boolean air(StarterBaseLayout.Pos p){return p.y()>=66;}
   public boolean safeFloor(StarterBaseLayout.Pos p){return false;}
   public boolean matches(StarterBaseLayout.Placement p){return false;}
   public boolean canClear(StarterBaseLayout.Pos p){return p.y()>=66;}
   public boolean canFound(StarterBaseLayout.Pos p){return p.y()==65;}
  };
  check(StarterBaseLayout.selectPlatform(new StarterBaseLayout.Pos(0,64,0),elevatedWater)
       .orElseThrow().getFirst().pos().y()==66,
       "nearby raised sites must be considered when the requested Y is too low");
  World damaged=new World(){boolean disturbed;
   public Task place(StarterBaseLayout.Placement p){
    Task child=super.place(p);
    if(!disturbed && placed==1){disturbed=true;blocks.put(plan.getFirst().pos().offset(0,1,0),"stone");}
    return child;
   }
  };
  StarterBaseTask repaired=new StarterBaseTask(damaged);run(repaired);
  check(repaired.isSuccessful()&&damaged.cleared==3&&damaged.placed==3,
        "changed headroom should be repaired without rebuilding floors");
  World impossible=new World(){public Task prepareTerrain(StarterBaseLayout.Placement job){return new Op(()->{});}};
  StarterBaseTask stalled=new StarterBaseTask(impossible);run(stalled);
  check(stalled.isTerminal()&&!stalled.isSuccessful(),
        "native clearance that produces no real world change must fail boundedly");
  System.out.println("SetupTerraformTest: 12 passed; natural ground, water fallback, no floor mining, bounded foundation repair");
 }
}
