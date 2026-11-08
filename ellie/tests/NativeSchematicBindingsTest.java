import com.player2.playerengine.reliability.FoodAcquisition;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.agentic.ResourceAgenticIntegration;
import com.player2.playerengine.agentic.AgenticStepSpec;
import java.lang.reflect.Array;
import java.util.*;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/** Real 1.21.1 registries and native task constructors; no world, NPC or gameplay is simulated. */
public final class NativeSchematicBindingsTest {
 static final String P="com.player2.playerengine.";static int checks;
 static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 static Object make(String name,Object...args)throws Exception{return FoodAcquisition.construct(P+name,args);}
 static Object registered(String registry,String id)throws Exception {
  Object r=Class.forName("net.minecraft.class_7923").getField(registry).get(null);
  return call(r,"method_10223",callStatic("net.minecraft.class_2960","method_60654",id));
 }
 static Task task(String name,Object...args)throws Exception{Object result=make(name,args);check(result instanceof Task,"native task type "+name);return (Task)result;}
 public static void main(String[]args)throws Exception{
  callStatic("net.minecraft.class_155","method_36208");callStatic("net.minecraft.class_2966","method_12851");
  Object pos=FoodAcquisition.construct("net.minecraft.class_2338",-439,79,95);
  check(((Number)call(pos,"method_10263")).intValue()==-439,"real negative block position preserved");
  Object cobble=registered("field_41175","minecraft:cobblestone"),chest=registered("field_41175","minecraft:chest");
  Object blockArray=Array.newInstance(Class.forName("net.minecraft.class_2248"),1);Array.set(blockArray,0,cobble);
  Task foundation=task("tasks.construction.PlaceBlockTask",pos,blockArray,false,false);
  check(get(foundation,"target").equals(pos)&&!(Boolean)get(foundation,"useThrowaways")&&!(Boolean)get(foundation,"autoCollectStructureBlocks"),"foundation receives exact location/material and both false policy flags");
  Task placedChest=task("tasks.construction.PlaceItemBlockAtPosTask",pos,chest);
  check(get(placedChest,"placePos").equals(pos)&&get(placedChest,"expectedBlock")==chest,"depot chest placement binds its actual target and block");
  Task travel=task("tasks.movement.GetToBlockTask",pos,false);
  check(get(travel,"position").equals(pos)&&!(Boolean)get(travel,"preferStairs"),"logged failed travel constructor now binds primitive false");
  Object cobbleItem=registered("field_41178","minecraft:cobblestone");Object target=make("util.ItemTarget",cobbleItem,64);
  check(((Number)call(target,"getTargetCount")).intValue()==64,"acquisition ItemTarget retains exact count");
  Object query=make("containeraccess.StorageItemArgs$ItemQuery",cobbleItem,"minecraft:cobblestone",32);
  check(((Number)call(query,"countOrAll")).intValue()==32&&!Boolean.TRUE.equals(call(query,"isAll")),"actual transfer query keeps explicit count");
  for(String mode:List.of("DEPOSIT","WITHDRAW")){
   Object direction=Class.forName(P+"containeraccess.ScanReportFormatter$TransferDirection").getField(mode).get(null);
   Task transfer=task("tasks.container.BoundedContainerTransferTask",direction,pos,List.of(query));
   check(get(transfer,"direction")==direction&&get(transfer,"targetPos").equals(pos)&&get(transfer,"withdrawQuantity").toString().equals("EXACT")&&((List<?>)get(transfer,"entries")).size()==1,"native "+mode+" keeps target, query and exact quantity mode");
  }
  Task open=task("tasks.InteractWithBlockTask",pos,false);check(get(open,"target").equals(pos)&&!(Boolean)get(open,"walkInto"),"trapdoor/door interaction binds primitive flag");
  Object up=Class.forName("net.minecraft.class_2350").getField("field_11036").get(null),slab=registered("field_41178","minecraft:cobblestone_slab");
  Task doubleSlab=task("tasks.InteractWithBlockTask",slab,up,pos,false);
  check(get(doubleSlab,"direction")==up&&!(Boolean)get(doubleSlab,"walkInto"),"double slab uses item/direction/position/boolean constructor");
  Object bucket=registered("field_41178","minecraft:water_bucket"),right=Class.forName(P+"automaton.api.utils.input.Input").getField("CLICK_RIGHT").get(null);
  Task fluid=task("tasks.InteractWithBlockTask",bucket,up,pos,right,false,true);
  check(get(fluid,"direction")==up&&get(fluid,"interactInput")==right&&!(Boolean)get(fluid,"walkInto")&&(Boolean)get(fluid,"shiftClick"),"source/waterlogging action retains separate false and true flags");
  check(foundation.getClass().getProtectionDomain().getCodeSource().getLocation().equals(Task.class.getProtectionDomain().getCodeSource().getLocation()),"constructor bodies come from the packaged mod, not action stubs");
  var factory=ResourceAgenticIntegration.class.getDeclaredMethod("task",AgenticStepSpec.class,Class.forName(P+"agentic.AgenticExecutionContext"));factory.setAccessible(true);
  for(String id:List.of("cobblestone_slab","chest","cobblestone","hopper","ladder","oak_trapdoor","water_bucket")){
   Task shared=ResourceAgenticIntegration.nativeAcquisition("minecraft:"+id,64).orElseThrow(()->new AssertionError("Native project material missing: "+id));
   Task agentic=(Task)((Optional<?>)factory.invoke(null,new AgenticStepSpec("collect_resource","get_resource",Map.of("item",id,"count","64"),"test shared native worker"),null)).orElseThrow();
   check(shared.getClass()==agentic.getClass(),"schematic and agentic share actual native worker for "+id);
   Object[] targets=(Object[])call(shared,"getItemTargets");
   check(targets.length==1&&((Number)call(targets[0],"getTargetCount")).intValue()==64,"native acquisition preserves exact requested output quantity for "+id);
   if(id.equals("cobblestone_slab"))check(shared.getClass().getSimpleName().equals("CraftInTableTask"),"slabs choose the owner-tested native table worker");
  }
  check(ResourceAgenticIntegration.nativeAcquisition("not_an_actual_item",64).isEmpty(),"unknown catalogue item remains eligible for explicit recipe fallback");
  check(ResourceAgenticIntegration.nativeAcquisition("minecraft:white_carpet",48).isEmpty(),
    "the pinned native catalogue lacks a direct white carpet worker");
  Task shearsWorker=ResourceAgenticIntegration.nativeAcquisition("minecraft:shears",1)
    .orElseThrow(()->new AssertionError("wool-first route needs native shears acquisition"));
  Task woolWorker=ResourceAgenticIntegration.nativeAcquisition("minecraft:white_wool",32)
    .orElseThrow(()->new AssertionError("wool-first route needs native white wool acquisition"));
  check(shearsWorker instanceof Task,"shears use the existing native resource runner");
  check(woolWorker.getClass().getSimpleName().equals("CollectWoolTask"),
    "white wool must use native sheep/wool acquisition rather than 128 string");
  Object wool=registered("field_41178","minecraft:white_wool");
  Object carpet=registered("field_41178","minecraft:white_carpet");
  Task carpetWorker=com.player2.playerengine.schematic.SchematicWhiteCarpetAcquisition
    .directCraftTask(wool,carpet,48);
  check(carpetWorker.getClass().getSimpleName().equals("CraftInInventoryTask"),
    "native 2x2 carpet crafting bypasses the generic string macro");
  Object recipeTarget=get(carpetWorker,"target");
  Object recipe=call(recipeTarget,"getRecipe");
  check(((Number)call(recipe,"outputCount")).intValue()==3 &&
    ((Number)call(recipe,"getSlotCount")).intValue()==4,
    "native carpet recipe must output three from a 2x2 inventory crafting grid");
  Object first=call(recipe,"getSlot",0),second=call(recipe,"getSlot",1);
  check(((Object[])call(first,"getMatches"))[0]==wool &&
    ((Object[])call(second,"getMatches"))[0]==wool,
    "only registered white wool may satisfy the explicit carpet recipe");
  check(Boolean.FALSE.equals(get(carpetWorker,"collect")),
    "crafting stage must not recursively collect string or choose another recipe");
  check(ResourceAgenticIntegration.nativeAcquisition("minecraft:cobblestone_slab",0).isEmpty()&&ResourceAgenticIntegration.nativeAcquisition("minecraft:cobblestone_slab",577).isEmpty(),"invalid native batch counts are rejected");
  System.out.println("NativeSchematicBindingsTest: "+checks+" checks using actual Minecraft 1.21.1 and packaged task constructors; no world/NPC execution or gameplay claim");
 }
}
