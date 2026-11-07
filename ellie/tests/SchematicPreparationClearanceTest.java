import com.player2.playerengine.schematic.*;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import java.util.*;
import net.minecraft.*;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
import static com.player2.playerengine.schematic.SchematicPlan.*;

/** Real coordinator + server preflight. World, protection events and native workers are boundaries. */
public final class SchematicPreparationClearanceTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static SchematicPlan selection(){
  int[] blocks=new int[20*133*20];for(int y=0;y<128;y++)for(int x=9;x<=10;x++)for(int z=9;z<=10;z++)blocks[(y*20+z)*20+x]=1;
  return new SchematicPlan("123.litematic","regression",20,133,20,List.of(new State("minecraft:air",Map.of()),new State("minecraft:cobblestone",Map.of())),blocks);
 }
 static final class Replay extends SchematicProjectTest.Env {
  final SchematicWorld nativeWorld;final SchematicWorldSlotsTest.World game;
  Replay(SchematicPlan plan,SchematicWorldSlotsTest.World game)throws Exception{
   super(plan);this.game=game;nativeWorld=new SchematicWorld(new SchematicWorldSlotsTest.Controller(game));
  }
  public void initialize(SchematicPlan p,Pos origin){super.initialize(p,origin);try{set(nativeWorld,"plan",p);set(nativeWorld,"origin",origin);set(nativeWorld,"dimension","minecraft:overworld");}catch(Exception e){throw new RuntimeException(e);}}
  public boolean preflight(){try{return nativeWorld.preflight();}catch(Exception e){throw new RuntimeException(e);}}
  public void recheck(){nativeWorld.recheck();}public Map<String,Integer> requirements(){return nativeWorld.requirements();}
  public String worldMismatch(){return nativeWorld.worldMismatch();}public String buildSiteConflict(){return nativeWorld.buildSiteConflict();}
 }
 static SchematicProjectTask begin(Replay e,boolean build)throws Exception {
  var task=new SchematicProjectTask("123.litematic",100,104,286,build,e);SchematicProjectTest.START.invoke(task);return task;
 }
 public static void main(String[]args)throws Exception {
  var plan=selection();var world=new SchematicWorldSlotsTest.World();var torch=new Pos(104,106,289);
  var wallTorch=class_2680.state(new class_7923.Block("minecraft:wall_torch"),"facing","west");world.blocks.put(torch,wallTorch);
  var store=PlayerPlacedBlockStore.get();store.clear();store.add("minecraft:overworld",new class_2338(104,106,289));
  var prep=new Replay(plan,world);var task=begin(prep,false);SchematicProjectTest.run(task,2000);
  check(task.isSuccessful(),task.controlledReason());check(prep.chests.getFirst().get("minecraft:cobblestone")==576,"full remaining bill and access reserve staged after the logged torch conflict");
  check(prep.nativeWorld.buildSiteConflict().contains("104,106,289")&&prep.nativeWorld.buildSiteConflict().contains("wall_torch"),"exact logged coordinates and block retained in site diagnostics");
  check(world.blocks.get(torch).equals(wallTorch)&&world.blocks.size()==1,"preparation does not remove the protected torch or build the selection");
  check(prep.events.stream().filter(s->s.startsWith("SCHEMATIC_SITE_WARNING")).count()==1,"one distinct warning after complete 53200-cell preflight and recheck");
  var build=new Replay(plan,world);task=begin(build,true);SchematicProjectTest.run(task,2000);
  check(!task.isSuccessful()&&task.controlledReason().startsWith("BLOCKED_SCHEMATIC_PROTECTED_SITE")&&task.controlledReason().contains("104,106,289"),"construction keeps the protection gate and useful failure reason");
  check(build.acquires==0&&build.depots.isEmpty()&&!build.active,"blocked construction starts no acquisition, storage placement or builder");
  world.blocks.remove(torch);store.clear();var cleared=new Replay(plan,world);task=begin(cleared,true);
  for(int tick=0;tick<2000&&cleared.acquires==0&&!task.isFinished();tick++)SchematicProjectTest.tick(task);
  check(cleared.acquires>0&&!task.isFinished()&&cleared.nativeWorld.buildSiteConflict()==null,"after owner removes conflict, a fresh build passes the gate and starts actual work");
  SchematicProjectTest.STOP.invoke(task,new Object[]{null});
  var lateChest=new Pos(109,104,295);var chestState=class_2680.state(new class_7923.Block("minecraft:chest"));
  world.blocks.put(lateChest,chestState);world.inventories.put(lateChest,new SchematicWorldSlotsTest.Inventory());
  cleared.nativeWorld.section(0);
  check(cleared.nativeWorld.buildSiteConflict()!=null&&cleared.nativeWorld.buildSiteConflict().contains("109,104,295"),"actual layer scan detects a container introduced after initial preflight");
  check(world.blocks.get(lateChest).equals(chestState),"late conflict scan preserves the container");
  world.blocks.remove(lateChest);world.inventories.remove(lateChest);cleared.nativeWorld.recheck();while(!cleared.nativeWorld.preflight()){}
  check(cleared.nativeWorld.buildSiteConflict()==null,"fresh scan clears a resolved protection conflict");store.clear();
  System.out.println("SchematicPreparationClearanceTest: "+checks+" checks replaying the logged selection size/origin/torch through production preflight and coordinator; native world/actions substituted");
 }
}
