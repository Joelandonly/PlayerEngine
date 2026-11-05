import com.player2.playerengine.reliability.StructuralMaterials;
import java.util.*;

/** Execute the real inventory/BlockItem/property adapter on explicit game boundaries. */
public final class StructuralMaterialInventoryTest {
    public record State(boolean full,boolean blockEntity){public boolean method_26234(Object world,Object pos){return full;}public boolean method_31709(){return blockEntity;}}
    public record Block(State state){public State method_9564(){return state;}}
    public static final class Falling extends net.minecraft.class_2346 {public State method_9564(){return new State(true,false);}}
    public record Item(String id,Object block){public Object method_7711(){return block;}public String toString(){return id;}}
    public static final class Sword {public String toString(){return "minecraft:iron_sword";}}
    public record Stack(Object item,int count){public boolean method_7960(){return count==0;}public Object method_7909(){return item;}public int method_7947(){return count;}}
    public static final class Inventory {public final List<Stack> main=new ArrayList<>(Collections.nCopies(36,new Stack(new Sword(),0)));}
    public static final class Player {public double method_23317(){return -37.5;}public double method_23318(){return 92;}public double method_23321(){return 147;}}
    public static final class Controller {final Inventory inv=new Inventory();public Inventory getInventory(){return inv;}public Player getPlayer(){return new Player();}public Object getWorld(){return this;}}
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        Controller c=new Controller();
        check(StructuralMaterials.catalogueName("minecraft:dirt").equals("dirt"),"soil fallback must resolve against actual TaskCatalogue key");
        check(StructuralMaterials.catalogueName("minecraft:cobblestone").equals("cobblestone"),"rock fallback must resolve against catalogue");
        check(StructuralMaterials.catalogueName("dirt").equals("dirt"),"bare catalogue keys stay unchanged");
        check(StructuralMaterials.catalogueName("othermod:stone").equals("othermod:stone"),"do not silently remove non-vanilla namespaces");
        c.inv.main.set(0,new Stack(new Item("minecraft:dirt",new Block(new State(true,false))),16));
        c.inv.main.set(1,new Stack(new Item("minecraft:cobblestone",new Block(new State(true,false))),8));
        c.inv.main.set(2,new Stack(new Item("minecraft:red_terracotta",new Block(new State(true,false))),3));
        c.inv.main.set(3,new Stack(new Item("minecraft:sand",new Falling()),64));
        c.inv.main.set(4,new Stack(new Item("minecraft:chest",new Block(new State(true,true))),64));
        c.inv.main.set(5,new Stack(new Item("minecraft:torch",new Block(new State(false,false))),64));
        c.inv.main.set(6,new Stack(new Item("minecraft:diamond_block",new Block(new State(true,false))),4));
        c.inv.main.set(7,new Stack(new Sword(),1));
        c.inv.main.set(8,new Stack(new Item("minecraft:oak_planks",new Block(new State(true,false))),7));
        var stock=StructuralMaterials.carried(c);
        check(stock.size()==4&&stock.values().stream().mapToInt(Integer::intValue).sum()==34,"real adapter counts dirt, cobble, decorative cubes and planks from actual slots");
        check(StructuralMaterials.preferred(stock,"minecraft:cobblestone").equals("minecraft:dirt"),"existing dirt wins over a hardcoded cobblestone request");
        check(!stock.containsKey("minecraft:sand")&&!stock.containsKey("minecraft:chest")&&!stock.containsKey("minecraft:torch")&&!stock.containsKey("minecraft:diamond_block"),"actual falling/type/shape/entity/utility exclusions applied");
        c.inv.main.set(0,new Stack(new Sword(),0));
        check(StructuralMaterials.preferred(StructuralMaterials.carried(c),"minecraft:dirt").equals("minecraft:cobblestone"),"selection refreshes actual inventory rather than stale tracker counts");
        System.out.println("StructuralMaterialInventoryTest: 8 checks passed (BlockItem/world boundaries substituted)");
    }
}
