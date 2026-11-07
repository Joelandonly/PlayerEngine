import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.schematic.*;
import net.minecraft.*;
import java.util.*;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
import static com.player2.playerengine.schematic.SchematicPlan.*;
/** Execute the real server-thread adapter against explicit block/entity/registry boundaries. */
public final class SchematicWorldSlotsTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static final class Inventory {
  final class_1747.Stack[] slots=new class_1747.Stack[27];Inventory(){Arrays.fill(slots,new class_1747.Stack(new class_1747("minecraft:air"),0,64));}
  public int method_5439(){return slots.length;}public Object method_5438(int i){return slots[i];}
 }
 public static final class World {
  final Map<Pos,class_2680> blocks=new HashMap<>();final Map<Pos,Inventory> inventories=new HashMap<>();boolean loaded=true;
  Pos point(class_2338 p){return new Pos(p.x(),p.y(),p.z());}
  public boolean method_22340(class_2338 p){return loaded;}
  public Object method_8320(class_2338 p){return blocks.getOrDefault(point(p),class_2680.state(new class_7923.Block("minecraft:air")));}
  public Object method_8321(class_2338 p){return inventories.get(point(p));}
 }
 public static final class Controller extends PlayerEngineController {final World world;Controller(World world){super(null);this.world=world;}public Object getWorld(){return world;}public Object getEntity(){return this;}}
 public static void main(String[]a)throws Exception {
  World w=new World();SchematicWorld e=new SchematicWorld(new Controller(w));Pos chest=new Pos(10,64,10);
  @SuppressWarnings("unchecked") List<Pos> positions=(List<Pos>)get(e,"positions");positions.add(chest);
  var empty=new Inventory();w.inventories.put(chest,empty);w.blocks.put(chest,class_2680.state(new class_7923.Block("minecraft:chest"),"type","single"));
  var snapshot=e.depots().getFirst();check(snapshot.freeSlots()==27&&snapshot.stock().isEmpty(),"fresh actual empty chest slots");
  empty.slots[0]=new class_1747.Stack(new class_1747("minecraft:cobblestone"),60,64);empty.slots[1]=new class_1747.Stack(new class_1747("minecraft:water_bucket"),1,1);
  snapshot=e.depots().getFirst();check(snapshot.freeSlots()==25&&snapshot.stock().get("minecraft:cobblestone")==60,"actual slot counts without tracker maps");check(snapshot.mergeRoom().get("minecraft:cobblestone")==4&&snapshot.room("minecraft:water_bucket",1)==25,"per-stack and unstackable capacity");
  empty.slots[0]=new class_1747.Stack(new class_1747("minecraft:cobblestone"),3,64);check(e.depots().getFirst().stock().get("minecraft:cobblestone")==3,"new snapshot never trusts previous stock");
  w.blocks.put(chest.add(0,1,0),class_2680.state(new class_7923.Block("minecraft:stone")));try{e.depots();throw new AssertionError("obstructed chest accepted");}catch(IllegalStateException expected){check(expected.getMessage().contains("OBSTRUCTED"),"obstruction has specific reason");}w.blocks.remove(chest.add(0,1,0));
  w.blocks.put(chest,class_2680.state(new class_7923.Block("minecraft:chest"),"type","left"));try{e.depots();throw new AssertionError("merged chest double-counted");}catch(IllegalStateException expected){checks++;}
  w.blocks.remove(chest);check(!e.depotPresent(0),"removed chest not remembered as present");w.loaded=false;try{e.depotPresent(0);throw new AssertionError("unloaded chest accepted");}catch(IllegalStateException expected){checks++;}w.loaded=true;
  // Run the actual bounded preflight and live block-state comparison with a tiny valid plan.
  var plan=new SchematicPlan("floor.litematic","abc",1,1,1,List.of(new State("minecraft:cobblestone",Map.of())),new int[]{0});set(e,"plan",plan);set(e,"origin",new Pos(0,64,0));set(e,"dimension","minecraft:overworld");
  while(!e.preflight()){}check(e.requirements().equals(Map.of("minecraft:cobblestone",1))&&e.worldMismatch()!=null,"empty world requires actual block item");
  w.blocks.put(new Pos(0,64,0),class_2680.state(new class_7923.Block("minecraft:cobblestone")));e.recheck();while(!e.preflight()){}check(e.requirements().isEmpty()&&e.worldMismatch()==null,"existing correct world block reduces bill");
  State wetOpen=new State("minecraft:oak_trapdoor",Map.of("open","true","waterlogged","true"));set(e,"plan",new SchematicPlan("wet.litematic","wet",1,1,1,List.of(wetOpen),new int[]{0}));
  w.blocks.put(new Pos(0,64,0),class_2680.state(new class_7923.Block("minecraft:oak_trapdoor"),"open","false","waterlogged","false"));
  var section=e.section(0);check(section.baseCorrect()==1&&!section.complete()&&section.post().size()==1&&!section.post().getFirst().state().waterlogged(),"dry pass still requires opening a waterlogged trapdoor");
  w.blocks.put(new Pos(0,64,0),class_2680.state(new class_7923.Block("minecraft:oak_trapdoor"),"open","true","waterlogged","false"));section=e.section(0);check(section.complete(),"dry final properties finish solid pass");
  e.enableFluids();section=e.section(0);check(!section.complete()&&section.post().getFirst().state().waterlogged(),"fluid pass requires remaining waterlogging");
  w.blocks.put(new Pos(0,64,0),class_2680.state(new class_7923.Block("minecraft:oak_trapdoor"),"open","true","waterlogged","true"));check(e.section(0).complete(),"full final state verified after waterlogging");
  System.out.println("SchematicWorldSlotsTest: "+checks+" production adapter checks; actual game world/registries/slots substituted");
 }
}
