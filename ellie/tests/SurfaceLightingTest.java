import com.player2.playerengine.tasks.setup.*;
import com.player2.playerengine.tasks.base.Task;
import java.lang.reflect.Method;
import java.util.*;

/** Real task scheduling against a surface/light simulation, plus terrain exclusions. */
public final class SurfaceLightingTest {
 static final StarterBaseLayout.Pos ANCHOR = new StarterBaseLayout.Pos(10,64,10);
 static final List<StarterBaseLayout.Placement> BASE = StarterBaseLayout.at(ANCHOR);
 static void ok(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
 static class World implements StarterBaseTask.Environment, SurfaceLightingPlan.Probe {
  final Set<StarterBaseLayout.Placement> blocks = new HashSet<>();
  final Map<String,Integer> inventory = new HashMap<>(Map.of("crafting_table",1,"furnace",1,"chest",1,"torch",32));
  boolean lightingAllowed = true;
  int torchPlaced;
  public List<StarterBaseLayout.Placement> loadOrSelect(){return BASE;}
  public boolean matches(StarterBaseLayout.Placement p){return blocks.contains(p);}
  public boolean safe(StarterBaseLayout.Placement p){return true;}
  public int count(String key){return inventory.getOrDefault(key,0);}
  public Task acquire(String key){return new Op(()->inventory.merge(key,1,Integer::sum));}
  public Task place(StarterBaseLayout.Placement p){return new Op(()->{blocks.add(p);inventory.merge(p.resource(),-1,Integer::sum);if(p.resource().equals("torch"))torchPlaced++;});}
  public SurfaceLightingPlan.Survey surveyLighting(List<StarterBaseLayout.Placement> p,Set<StarterBaseLayout.Pos> skip)throws Exception{return SurfaceLightingPlan.inspect(p,this,skip);}
  public StarterBaseLayout.Pos surface(int x,int z,int baseY){return new StarterBaseLayout.Pos(x,64,z);}
  public int blockLight(StarterBaseLayout.Pos sample){int light=0;for(var p:blocks)if(p.resource().equals("torch")){
    var l=p.pos();light=Math.max(light,Math.max(0,14-Math.abs(l.x()-sample.x())-Math.abs(l.y()-sample.y())-Math.abs(l.z()-sample.z())));
   }return light;}
  public boolean canPlace(StarterBaseLayout.Pos p){return lightingAllowed && BASE.stream().noneMatch(s -> s.pos().y()==p.y() && Math.abs(s.pos().x()-p.x())+Math.abs(s.pos().z()-p.z())<=1);}
 }
 static final class Op extends Task {
  boolean done;final Runnable action;Op(Runnable action){this.action=action;}
  public boolean isFinished(){return done;}
  void execute(){if(!done){action.run();done=true;}}
 }
 static void run(StarterBaseTask task) throws Exception {
  var tick=StarterBaseTask.class.getDeclaredMethod("onTick");tick.setAccessible(true);
  for(int i=0;i<300&&!task.isTerminal();i++){
   Task next=(Task)tick.invoke(task);if(next instanceof Op op)op.execute();
  }
 }
 public static void main(String[] args)throws Exception {
  World w=new World();var t=new StarterBaseTask(w);run(t);
  ok(t.isSuccessful(),"worksite and outdoor lights must complete");
  ok(w.torchPlaced>0 && w.torchPlaced<=SurfaceLightingPlan.MAX_NEW_TORCHES,"torch count bounded");
  ok(w.blocks.stream().filter(p->p.resource().equals("torch")).allMatch(p->p.pos().y()>=ANCHOR.y()),"never descend below base");
  ok(w.blocks.stream().filter(p->p.resource().equals("torch")).noneMatch(p -> BASE.stream().anyMatch(q ->
      Math.abs(p.pos().x()-q.pos().x())+Math.abs(p.pos().z()-q.pos().z())<=1)),"torch cannot occupy a workstation service lane");
  ok(w.blockLight(new StarterBaseLayout.Pos(10,64,10))>0,"block light is provided around workshop");
  ok(t.verifiedPlacedCount("torch")==w.torchPlaced,"physical torch count must be verifiable");
  int count=w.torchPlaced;run(t);ok(w.torchPlaced==count,"completion must be stable");
  w.blocks.removeIf(p->p.resource().equals("torch"));
  ok(!t.isSuccessful(),"removed light sources must invalidate final success");
  World impossible=new World();impossible.lightingAllowed=false;var blocked=new StarterBaseTask(impossible);run(blocked);
  ok(blocked.isTerminal()&&!blocked.isSuccessful(),"cannot claim a secured surface when no torches can be placed");
  var noCaves=SurfaceLightingPlan.inspect(BASE,new SurfaceLightingPlan.Probe(){
   public StarterBaseLayout.Pos surface(int x,int z,int baseY){return new StarterBaseLayout.Pos(x,baseY-3,z);}
   public int blockLight(StarterBaseLayout.Pos p){return 0;}
   public boolean canPlace(StarterBaseLayout.Pos p){return true;}
  },Set.of());
  ok(noCaves.eligible()==0&&noCaves.next().isEmpty(),"underground candidates must never be selected");
  System.out.println("SurfaceLightingTest: workshop lighting, bounded torches, live reverify, no caves, failure cases passed");
 }
}
