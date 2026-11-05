import com.player2.playerengine.agentic.InventorySpace;
import java.util.*;
public final class InventorySpaceTest {
 public static class Stack {boolean empty;Stack(boolean empty){this.empty=empty;}public boolean method_7960(){return empty;}}
 public static class Inventory {public List<Stack> main=new ArrayList<>();}
 public static class Entity {Inventory inventory=new Inventory();public Inventory getLivingInventory(){return inventory;}}
 public static class Tracker {boolean value;public boolean hasEmptyInventorySlot(){return value;}}
 public static class Controller {Entity entity=new Entity();Tracker tracker=new Tracker();public Entity getEntity(){return entity;}public Tracker getItemStorage(){return tracker;}}
 static void yes(boolean x,String msg){if(!x)throw new AssertionError(msg);System.out.println("PASS "+msg);}
 public static void main(String[] x){Controller c=new Controller();for(int i=0;i<36;i++)c.entity.inventory.main.add(new Stack(i>9));
  var s=InventorySpace.inspect(c);yes(s.total()==36 && s.free()==26 && s.used()==10,"occupied and empty counted by actual stacks");
  yes(!InventorySpace.mainFull(c),"false tracker full cannot block real empty inventory");
  for(Stack stack:c.entity.inventory.main)stack.empty=false;
  s=InventorySpace.inspect(c);yes(s.free()==0 && InventorySpace.mainFull(c),"truly full main inventory detected");
  c.entity.inventory.main.clear();yes(!InventorySpace.mainFull(c),"unknown/empty inventory cannot be misreported as full");
  for(int i=0;i<41;i++)c.entity.inventory.main.add(new Stack(true));
  yes(!InventorySpace.inspect(c).known(),"wrong slot shape is unknown, not player main inventory");
  c.entity.inventory.main.clear();for(int i=0;i<36;i++)c.entity.inventory.main.add(new Stack(false));
  c.entity.inventory.main.set(35,new Stack(true));
  yes(InventorySpace.hasEmptyMainSlot(c),"canonical tracker ignores misleading tracker value and includes slot 35");
  c.entity.inventory.main.set(35,new Stack(false));
  yes(!InventorySpace.hasEmptyMainSlot(c),"canonical tracker reports actual full main slots");
  System.out.println("InventorySpaceTest: 7 checks passed");
 }
}
