import com.player2.playerengine.tasks.setup.*;import com.player2.playerengine.tasks.base.Task;import java.util.*;
public final class SetupTerraformTest {
 static final class Op extends Task{boolean done;Runnable action;Op(Runnable a){action=a;}public boolean isFinished(){return done;}void execute(){action.run();done=true;}}
 static final class World implements StarterBaseTask.Environment{
  List<StarterBaseLayout.Placement> plan=StarterBaseLayout.at(new StarterBaseLayout.Pos(10,64,10));
  Map<StarterBaseLayout.Pos,String> blocks=new HashMap<>();int cleared,filled,placed;boolean protectedSite;
  World(){for(var job:StarterBaseLayout.terrain(plan,plan.getFirst().pos()))if(job.resource().equals("cobblestone"))blocks.put(job.pos(),"water");blocks.put(plan.getFirst().pos(),"stone");blocks.put(plan.getFirst().pos().offset(0,1,0),"stone");}
  String at(StarterBaseLayout.Pos p){return blocks.getOrDefault(p,"air");}
  public List<StarterBaseLayout.Placement> loadOrSelect(){return plan;}
  public List<StarterBaseLayout.Placement> terrainPlan(List<StarterBaseLayout.Placement> p){return StarterBaseLayout.terrain(p,p.getFirst().pos());}
  public boolean prepared(StarterBaseLayout.Placement job){return job.resource().equals("air")?at(job.pos()).equals("air"):at(job.pos()).equals("cobblestone");}
  public boolean canPrepare(StarterBaseLayout.Placement job){return !protectedSite;}
  public Task prepareTerrain(StarterBaseLayout.Placement job){return new Op(()->{blocks.put(job.pos(),job.resource());if(job.resource().equals("air"))cleared++;else filled++;});}
  public boolean matches(StarterBaseLayout.Placement p){return at(p.pos()).equals(p.resource());}
  public boolean safe(StarterBaseLayout.Placement p){return at(p.pos().offset(0,-1,0)).equals("cobblestone")&&at(p.pos().offset(0,1,0)).equals("air");}
  public int count(String resource){return 100;}
  public Task acquire(String item){throw new AssertionError();}
  public Task place(StarterBaseLayout.Placement p){return new Op(()->{blocks.put(p.pos(),p.resource());placed++;});}
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void run(StarterBaseTask task)throws Exception{var tick=StarterBaseTask.class.getDeclaredMethod("onTick");tick.setAccessible(true);for(int i=0;i<400&&!task.isTerminal();i++){Task child=(Task)tick.invoke(task);if(child instanceof Op op)op.execute();}}
 public static void main(String[] args)throws Exception{
  World world=new World();StarterBaseTask task=new StarterBaseTask(world);run(task);
  check(task.isSuccessful()&&world.cleared==2&&world.filled==63&&world.placed==10,"must clear obstacles, build deck over fluid, then verify stations");
  World protectedWorld=new World();protectedWorld.protectedSite=true;StarterBaseTask blocked=new StarterBaseTask(protectedWorld);run(blocked);
  check(!blocked.isSuccessful()&&blocked.isTerminal()&&protectedWorld.cleared==0,"protected or out-of-bounds preparation must not execute");
  StarterBaseLayout.Probe terrain=new StarterBaseLayout.Probe(){public boolean air(StarterBaseLayout.Pos p){return false;}public boolean safeFloor(StarterBaseLayout.Pos p){return false;}public boolean matches(StarterBaseLayout.Placement p){return false;}public boolean canClear(StarterBaseLayout.Pos p){return true;}public boolean canFound(StarterBaseLayout.Pos p){return true;}};
  check(StarterBaseLayout.selectBuildable(new StarterBaseLayout.Pos(0,64,0),terrain).isPresent(),"ordinary mineable/unsupported terrain is preparable");
  StarterBaseLayout.Probe forbidden=new StarterBaseLayout.Probe(){public boolean air(StarterBaseLayout.Pos p){return false;}public boolean safeFloor(StarterBaseLayout.Pos p){return false;}public boolean matches(StarterBaseLayout.Placement p){return false;}};
  check(StarterBaseLayout.selectBuildable(new StarterBaseLayout.Pos(0,64,0),forbidden).isEmpty(),"fluid living space/unbreakable/bounds refusal must remain effective");
  System.out.println("SetupTerraformTest: 4 passed; native-task handoff simulation for clearing, foundations and protected sites");
 }
}
