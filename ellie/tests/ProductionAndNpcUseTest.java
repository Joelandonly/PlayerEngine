import com.player2.playerengine.reliability.*;import com.player2.playerengine.tasks.base.Task;import com.player2.playerengine.tasks.container.SmeltInFurnaceTask;import net.minecraft.*;
public final class ProductionAndNpcUseTest {
 public static final class Context{Object player;public Object method_8036(){return player;}}
 public static final class Stack{class_1792 item;boolean empty;int uses;Stack(class_1792 item){this.item=item;}public class_1792 method_7909(){return item;}public boolean method_7960(){return empty;}public Object method_7981(Context context){uses++;if(context.player==null&&item instanceof class_1743)throw new NullPointerException("vanilla axe needs player");return class_1269.SUCCESS;}}
 public static final class Target{public class_1792[] getMatches(){return new class_1792[]{new class_1792("minecraft:iron_ingot")};}public int getTargetCount(){return 6;}}
 public static final class SmeltTarget{public Target getItem(){return new Target();}}
 public static final class Macro extends Task{private Task sub;Macro(Task sub){this.sub=sub;}}
 public static final class Storage{int count=5,invalidations;public int getItemCountInventoryOnly(class_1792[] items){return count;}public void registerSlotAction(){invalidations++;}}
 public static final class Furnace{Stack input=new Stack(new class_1792("minecraft:raw_iron")),output=new Stack(new class_1792("minecraft:iron_ingot"));public Stack method_5438(int slot){return slot==0?input:output;}}
 public static final class World{Furnace furnace=new Furnace();public Furnace method_8321(Object pos){return furnace;}}
 public static final class Controller{World world=new World();Storage storage=new Storage();public World getWorld(){return world;}public Storage getItemStorage(){return storage;}}
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception{
  Context npc=new Context();Stack axe=new Stack(new class_1743());check(NpcItemUse.use(axe,npc)==class_1269.field_5811&&axe.uses==0,"NPC axe use must pass without entering the crashing vanilla method");
  Context player=new Context();player.player=new Object();check(NpcItemUse.use(axe,player)==class_1269.SUCCESS&&axe.uses==1,"real player axe use must be unchanged");
  Stack blocks=new Stack(new class_1792("minecraft:cobblestone"));check(NpcItemUse.use(blocks,npc)==class_1269.SUCCESS&&blocks.uses==1,"NPC block-item placement must remain supported");
  Controller controller=new Controller();SmeltInFurnaceTask child=new SmeltInFurnaceTask(new SmeltTarget());Macro macro=new Macro(child);
  check(ProductionHandoff.pending(macro,controller)==child,"five of six ingots must retain the running native child");
  controller.world.furnace.input.empty=true;child.isSmelting=false;
  check(ProductionHandoff.pending(macro,controller)==child,"last ingot in furnace output must be collected before resolving the craft");
  controller.storage.count=6;controller.world.furnace.output.empty=true;
  check(ProductionHandoff.pending(macro,controller)==null&&controller.storage.invalidations>=2,"actual inventory completion releases handoff and invalidates stale counts");
  controller.storage.count=5;check(ProductionHandoff.pending(macro,controller)==null,"empty inactive furnace must not cause an infinite hold");
  System.out.println("ProductionAndNpcUseTest: 7 passed; supplied null-player axe and final-ingot handoff regressions");
 }
}
